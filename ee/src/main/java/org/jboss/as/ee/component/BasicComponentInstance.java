/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.component;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.as.ee.logging.EeLogger;
import org.jboss.as.ee.component.interceptors.InvocationType;
import org.jboss.as.naming.ManagedReference;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;

import static org.jboss.as.ee.logging.EeLogger.ROOT_LOGGER;

/**
 * An abstract base component instance.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class BasicComponentInstance implements ComponentInstance {

    private static final long serialVersionUID = -8099216228976950066L;

    public static final Object INSTANCE_KEY = BasicComponentInstanceKey.class;

    private final BasicComponent component;
    private final Interceptor preDestroy;
    @SuppressWarnings("unused")
    private volatile int done;

    private static final AtomicIntegerFieldUpdater<BasicComponentInstance> doneUpdater = AtomicIntegerFieldUpdater.newUpdater(BasicComponentInstance.class, "done");

    private transient Map<Method, Interceptor> methodMap;

    private Map<Object, Object> instanceData = new HashMap<Object, Object>();

    private volatile boolean constructionFinished = false;

    /**
     * Construct a new instance.
     *
     * @param component the component
     */
    protected BasicComponentInstance(final BasicComponent component, final Interceptor preDestroyInterceptor, final Map<Method, Interceptor> methodInterceptors) {
        // Associated component
        this.component = component;
        this.preDestroy = preDestroyInterceptor;
        this.methodMap = Collections.unmodifiableMap(methodInterceptors);
    }

    /**
     * {@inheritDoc}
     */
    public Component getComponent() {
        return component;
    }

    /**
     * {@inheritDoc}
     */
    public Object getInstance() {
        ManagedReference managedReference = (ManagedReference) getInstanceData(INSTANCE_KEY);
        if(managedReference == null) {
            //can happen if around construct chain returns null
            return null;
        }
        return managedReference.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    public Interceptor getInterceptor(final Method method) throws IllegalStateException {
        Interceptor interceptor = methodMap.get(method);
        if (interceptor == null) {
            throw EeLogger.ROOT_LOGGER.methodNotFound(method);
        }
        return interceptor;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<Method> allowedMethods() {
        return methodMap.keySet();
    }

    /**
     * {@inheritDoc}
     */
    public final void destroy() {
        if (doneUpdater.compareAndSet(this, 0, 1)) try {
            preDestroy();
            final Object instance = getInstance();
            if (instance != null) {
                final InterceptorContext interceptorContext = prepareInterceptorContext();
                interceptorContext.setTarget(instance);
                interceptorContext.putPrivateData(InvocationType.class, InvocationType.PRE_DESTROY);
                preDestroy.processInvocation(interceptorContext);
            }
        } catch (Exception e) {
            ROOT_LOGGER.componentDestroyFailure(e, this);
        } finally {
            component.finishDestroy();
        }
    }

    @Override
    public Object getInstanceData(Object key) {
        return instanceData.get(key);
    }

    @Override
    public void setInstanceData(Object key, Object value) {
        if(constructionFinished) {
            throw EeLogger.ROOT_LOGGER.instanceDataCanOnlyBeSetDuringConstruction();
        }
        instanceData.put(key, value);
    }

    public void constructionFinished() {
        this.constructionFinished = true;
    }

    /**
     * Method that sub classes can use to override destroy logic.
     *
     */
    protected void preDestroy() {

    }

    protected InterceptorContext prepareInterceptorContext() {
        final InterceptorContext interceptorContext = new InterceptorContext();
        interceptorContext.putPrivateData(Component.class, component);
        interceptorContext.putPrivateData(ComponentInstance.class, this);
        interceptorContext.setContextData(new HashMap<String, Object>());
        return interceptorContext;
    }

    private static class BasicComponentInstanceKey implements Serializable {

    }
}
