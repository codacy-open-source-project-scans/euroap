/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.component.interceptors;

import java.util.concurrent.Callable;

import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.deployers.StartupCountdown;
import org.jboss.as.ee.component.interceptors.InvocationType;
import org.jboss.as.ejb3.component.session.SessionBeanComponent;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.InterceptorFactoryContext;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * An asynchronous execution interceptor for methods returning {@link java.util.concurrent.Future}.  Because asynchronous invocations
 * necessarily run in a concurrent thread, any thread context setup interceptors should run <b>after</b> this
 * interceptor to prevent that context from becoming lost.  This interceptor should be associated with the client
 * interceptor stack.
 * <p/>
 * Cancellation notification is accomplished via the {@link CancellationFlag} private data attachment.  This interceptor
 * will create and attach a new cancellation flag, which will be set to {@code true} if the request was cancelled.
 * <p/>
 * This interceptor should only be used for local invocations.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AsyncFutureInterceptorFactory implements InterceptorFactory {

    public static final InterceptorFactory INSTANCE = new AsyncFutureInterceptorFactory();

    private AsyncFutureInterceptorFactory() {
    }

    @Override
    public Interceptor create(final InterceptorFactoryContext context) {

        final SessionBeanComponent component = (SessionBeanComponent) context.getContextData().get(Component.class);

        if (component.isSecurityDomainKnown()) {
            return new Interceptor() {
                @Override
                public Object processInvocation(final InterceptorContext context) throws Exception {
                    if (! context.isBlockingCaller()) {
                        return context.proceed();
                    }
                    final InterceptorContext asyncInterceptorContext = context.clone();
                    asyncInterceptorContext.putPrivateData(InvocationType.class, InvocationType.ASYNC);
                    final CancellationFlag flag = new CancellationFlag();

                    final SecurityDomain securityDomain = context.getPrivateData(SecurityDomain.class);
                    final StartupCountdown.Frame frame = StartupCountdown.current();
                    final SecurityIdentity currentIdentity = securityDomain == null ? null : securityDomain.getCurrentSecurityIdentity();

                    Callable<Object> invocationTask = () -> {
                        StartupCountdown.restore(frame);
                        try {
                            return asyncInterceptorContext.proceed();
                        } finally {
                            StartupCountdown.restore(null);
                        }
                    };
                    final AsyncInvocationTask task = new AsyncInvocationTask(flag) {
                        @Override
                        protected Object runInvocation() throws Exception {
                            if(currentIdentity != null) {
                                return currentIdentity.runAs(invocationTask);
                            } else {
                                return invocationTask.call();
                            }
                        }
                    };
                    asyncInterceptorContext.putPrivateData(CancellationFlag.class, flag);
                    asyncInterceptorContext.setBlockingCaller(false);
                    return execute(component, task);
                }
            };
        } else {
            return new Interceptor() {
                @Override
                public Object processInvocation(final InterceptorContext context) throws Exception {
                    if (! context.isBlockingCaller()) {
                        return context.proceed();
                    }
                    final InterceptorContext asyncInterceptorContext = context.clone();
                    asyncInterceptorContext.putPrivateData(InvocationType.class, InvocationType.ASYNC);
                    final CancellationFlag flag = new CancellationFlag();


                    final StartupCountdown.Frame frame = StartupCountdown.current();
                    final AsyncInvocationTask task = new AsyncInvocationTask(flag) {
                        @Override
                        protected Object runInvocation() throws Exception {
                            StartupCountdown.restore(frame);
                            try {
                                return asyncInterceptorContext.proceed();
                            } finally {
                                StartupCountdown.restore(null);
                            }
                        }
                    };
                    asyncInterceptorContext.putPrivateData(CancellationFlag.class, flag);
                    asyncInterceptorContext.setBlockingCaller(false);
                    return execute(component, task);
                }
            };
        }
    }

    private AsyncInvocationTask execute(SessionBeanComponent component, AsyncInvocationTask task) {
        // The interceptor runs in user application's context classloader. Triggering an execute via an executor service from here can potentially lead to
        // new thread creation which will assign themselves the context classloader of the parent thread (i.e. this thread). This effectively can lead to
        // deployment's classloader leak. See https://issues.jboss.org/browse/WFLY-1375
        // To prevent this, we set the TCCL of this thread to null and then trigger the "execute" before "finally" setting the TCCL back to the original one.
        final ClassLoader oldClassLoader = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged((ClassLoader) null);
        try {
            component.getAsynchronousExecutor().execute(task);
        } finally {
            // reset to the original TCCL
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldClassLoader);
        }
        return task;
    }


}
