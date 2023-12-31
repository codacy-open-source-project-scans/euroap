/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.service;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.function.Consumer;

import org.jboss.as.server.deployment.SetupAction;
import org.jboss.as.service.logging.SarLogger;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Service wrapper for legacy JBoss services that controls the service start and stop lifecycle.
 *
 * @author John E. Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class StartStopService extends AbstractService {

    private final Method startMethod;
    private final Method stopMethod;

    StartStopService(final Object mBeanInstance, final Method startMethod, final Method stopMethod, final List<SetupAction> setupActions, final ClassLoader mbeanContextClassLoader, final Consumer<Object> mBeanInstanceConsumer, final Supplier<ExecutorService> executorSupplier) {
        super(mBeanInstance, setupActions, mbeanContextClassLoader, mBeanInstanceConsumer, executorSupplier);
        this.startMethod = startMethod;
        this.stopMethod = stopMethod;
    }

    /** {@inheritDoc} */
    public void start(final StartContext context) {
        super.start(context);
        if (SarLogger.ROOT_LOGGER.isTraceEnabled()) {
            SarLogger.ROOT_LOGGER.tracef("Starting Service: %s", context.getController().getName());
        }
        final Runnable task = new Runnable() {
            @Override
            public void run() {
            try {
                invokeLifecycleMethod(startMethod, context);
                context.complete();
            } catch (Throwable e) {
                context.failed(new StartException(SarLogger.ROOT_LOGGER.failedExecutingLegacyMethod("start()"), e));
            }
            }
        };
        try {
            executorSupplier.get().submit(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            context.asynchronous();
        }
    }

    /** {@inheritDoc} */
    public void stop(final StopContext context) {
        super.stop(context);
        if (SarLogger.ROOT_LOGGER.isTraceEnabled()) {
            SarLogger.ROOT_LOGGER.tracef("Stopping Service: %s", context.getController().getName());
        }
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    invokeLifecycleMethod(stopMethod, context);
                } catch (Exception e) {
                    SarLogger.ROOT_LOGGER.error(SarLogger.ROOT_LOGGER.failedExecutingLegacyMethod("stop()"), e);
                } finally {
                    context.complete();
                }
            }
        };
        try {
            executorSupplier.get().submit(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            context.asynchronous();
        }
    }

}
