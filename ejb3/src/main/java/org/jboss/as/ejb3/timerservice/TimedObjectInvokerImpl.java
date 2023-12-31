/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice;

import static org.jboss.as.ejb3.util.MethodInfoHelper.EMPTY_STRING_ARRAY;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jakarta.ejb.Timer;

import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.interceptors.InvocationType;
import org.jboss.as.ejb3.component.EJBComponent;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.timerservice.spi.TimedObjectInvoker;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.InterceptorFactoryContext;
import org.jboss.invocation.SimpleInterceptorFactoryContext;
import org.jboss.metadata.ejb.spec.MethodInterfaceType;
import org.jboss.modules.Module;
import org.wildfly.extension.requestcontroller.ControlPoint;
import org.wildfly.extension.requestcontroller.RunResult;

/**
 * Timed object invoker for an enterprise bean. This is analogous to a view service for timer invocations
 *
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public class TimedObjectInvokerImpl implements TimedObjectInvoker {

    private final String timedObjectId;
    private final EJBComponent component;
    private final Module module;
    private final Map<Method, Interceptor> interceptors = new HashMap<>();

    public TimedObjectInvokerImpl(Module module, String deploymentName, EJBComponent component) {
        this.module = module;
        this.timedObjectId = deploymentName + '.' + component.getComponentName();
        this.component = component;

        InterceptorFactoryContext factoryContext = new SimpleInterceptorFactoryContext();
        factoryContext.getContextData().put(Component.class, component);
        for (Map.Entry<Method, InterceptorFactory> entry : component.getTimeoutInterceptors().entrySet()) {
            this.interceptors.put(entry.getKey(), entry.getValue().create(factoryContext));
        }
    }

    @Override
    public void callTimeout(Timer timer, Method method) throws Exception {
        ControlPoint controlPoint = this.component.getControlPoint();
        if (controlPoint != null) {
            if (controlPoint.beginRequest() == RunResult.REJECTED) {
                throw EjbLogger.EJB3_TIMER_LOGGER.containerSuspended();
            }
            try {
                this.invoke(timer, method);
            } finally {
                controlPoint.requestComplete();
            }
        } else {
            this.invoke(timer, method);
        }
    }

    private void invoke(Timer timer, Method method) throws Exception {
        Interceptor interceptor = this.interceptors.get(method);
        if (interceptor == null) {
            throw EjbLogger.EJB3_TIMER_LOGGER.failToInvokeTimeout(method);
        }
        InterceptorContext context = new InterceptorContext();
        context.setContextData(new HashMap<>());
        context.setMethod(method);
        context.setParameters(method.getParameterCount() == 0 ? EMPTY_STRING_ARRAY : new Object[] { timer });
        context.setTimer(timer);
        context.putPrivateData(Component.class, this.component);
        context.putPrivateData(MethodInterfaceType.class, MethodInterfaceType.Timer);
        context.putPrivateData(InvocationType.class, InvocationType.TIMER);
        interceptor.processInvocation(context);
    }

    @Override
    public EJBComponent getComponent() {
        return this.component;
    }

    @Override
    public String getTimedObjectId() {
        return this.timedObjectId;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.module.getClassLoader();
    }

    @Override
    public int hashCode() {
        return this.timedObjectId.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof TimedObjectInvoker)) return false;
        return this.timedObjectId.equals(((TimedObjectInvoker) object).getTimedObjectId());
    }

    @Override
    public String toString() {
        return this.timedObjectId;
    }
}
