/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.weld.interceptors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.interceptor.InvocationContext;

import org.jboss.as.ee.component.ComponentInstance;
import org.jboss.as.weld.spi.ComponentInterceptorSupport;
import org.jboss.as.weld.spi.InterceptorInstances;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.weld.ejb.spi.InterceptorBindings;

/**
 * Jakarta Interceptors for applying interceptors bindings.
 * <p/>
 * It is a separate interceptor, as it needs to be applied after all
 * the other existing interceptors.
 *
 * @author Marius Bogoevici
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class Jsr299BindingsInterceptor implements org.jboss.invocation.Interceptor {

    private final InterceptionType interceptionType;
    private final ComponentInterceptorSupport interceptorSupport;
    private final Supplier<InterceptorBindings> interceptorBindingsSupplier;

    private Jsr299BindingsInterceptor(final InterceptionType interceptionType, final ComponentInterceptorSupport interceptorSupport, final Supplier<InterceptorBindings> interceptorBindingsSupplier) {
        this.interceptionType = interceptionType;
        this.interceptorSupport = interceptorSupport;
        this.interceptorBindingsSupplier = interceptorBindingsSupplier;
    }

    public static InterceptorFactory factory(final InterceptionType interceptionType, final ServiceBuilder<?> builder, final ServiceName interceptorBindingServiceName, final ComponentInterceptorSupport interceptorSupport) {
        final Supplier<InterceptorBindings> interceptorBindingsSupplier = builder.requires(interceptorBindingServiceName);
        return new ImmediateInterceptorFactory(new Jsr299BindingsInterceptor(interceptionType, interceptorSupport, interceptorBindingsSupplier));
    }

    protected Object delegateInterception(InvocationContext invocationContext, InterceptionType interceptionType, List<Interceptor<?>> currentInterceptors, InterceptorInstances interceptorInstances)
            throws Exception {
        List<Object> currentInterceptorInstances = new ArrayList<Object>();
        for (Interceptor<?> interceptor : currentInterceptors) {
            currentInterceptorInstances.add(interceptorInstances.getInstances().get(interceptor.getBeanClass().getName()).getInstance());
        }
        if (currentInterceptorInstances.size() > 0) {
            return interceptorSupport.delegateInterception(invocationContext, interceptionType, currentInterceptors, currentInterceptorInstances);
        } else {
            return invocationContext.proceed();
        }

    }


    private Object doMethodInterception(InvocationContext invocationContext, InterceptionType interceptionType, InterceptorInstances interceptorInstances, InterceptorBindings interceptorBindings)
            throws Exception {
        if (interceptorBindings != null) {
            List<Interceptor<?>> currentInterceptors = interceptorBindings.getMethodInterceptors(interceptionType, invocationContext.getMethod());
            return delegateInterception(invocationContext, interceptionType, currentInterceptors, interceptorInstances);
        } else {
            return invocationContext.proceed();
        }
    }

    @Override
    public Object processInvocation(final InterceptorContext context) throws Exception {
        final ComponentInstance componentInstance = context.getPrivateData(ComponentInstance.class);
        final InterceptorInstances interceptorInstances = interceptorSupport.getInterceptorInstances(componentInstance);
        final InterceptorBindings interceptorBindings = interceptorBindingsSupplier.get();
        switch (interceptionType) {
            case AROUND_INVOKE:
                return doMethodInterception(context.getInvocationContext(), InterceptionType.AROUND_INVOKE, interceptorInstances, interceptorBindings);
            case AROUND_TIMEOUT:
                return doMethodInterception(context.getInvocationContext(), InterceptionType.AROUND_TIMEOUT, interceptorInstances, interceptorBindings);
            case PRE_DESTROY:
                try {
                    return doLifecycleInterception(context, interceptorInstances, interceptorBindings);
                } finally {
                    interceptorInstances.getCreationalContext().release();
                }
            case POST_CONSTRUCT:
                return doLifecycleInterception(context, interceptorInstances, interceptorBindings);
            case AROUND_CONSTRUCT:
                return doLifecycleInterception(context, interceptorInstances, interceptorBindings);
            default:
                //should never happen
                return context.proceed();
        }
    }

    private Object doLifecycleInterception(final InterceptorContext context, InterceptorInstances interceptorInstances, final InterceptorBindings interceptorBindings) throws Exception {
        if (interceptorBindings == null) {
            return context.proceed();
        } else {
            List<Interceptor<?>> currentInterceptors = interceptorBindings.getLifecycleInterceptors(interceptionType);
            return delegateInterception(context.getInvocationContext(), interceptionType, currentInterceptors, interceptorInstances);
        }
    }
}
