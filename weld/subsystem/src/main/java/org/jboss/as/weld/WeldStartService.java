/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.jboss.as.server.deployment.SetupAction;
import org.jboss.as.weld.logging.WeldLogger;
import org.jboss.as.weld.services.ModuleGroupSingletonProvider;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.weld.Container;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Service that actually finishes starting the weld container, after it has been bootstrapped by
 * {@link WeldBootstrapService}
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @see WeldStartCompletionService
 */
public class WeldStartService implements Service {

    public static final ServiceName SERVICE_NAME = ServiceNames.WELD_START_SERVICE_NAME;

    private final Supplier<WeldBootstrapService> bootstrapSupplier;
    private final List<SetupAction> setupActions;
    private final ClassLoader classLoader;
    private final ServiceName deploymentServiceName;

    private final AtomicBoolean runOnce = new AtomicBoolean();

    public WeldStartService(final Supplier<WeldBootstrapService> bootstrapSupplier, final List<SetupAction> setupActions, final ClassLoader classLoader, final ServiceName deploymentServiceName) {
        this.bootstrapSupplier = bootstrapSupplier;
        this.setupActions = setupActions;
        this.classLoader = classLoader;
        this.deploymentServiceName = deploymentServiceName;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        /*
         * Weld service restarts are not supported. Therefore, if we detect that Weld is being restarted we
         * trigger restart of the entire deployment.
         */
        if (!runOnce.compareAndSet(false,true)) {
            ServiceController<?> controller = context.getController().getServiceContainer().getService(deploymentServiceName);
            controller.addListener(new LifecycleListener() {
                @Override
                public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                    if (event == LifecycleEvent.DOWN) {
                        controller.setMode(Mode.ACTIVE);
                        controller.removeListener(this);
                    }
                }
            });
            controller.setMode(Mode.NEVER);
            return;
        }

        ClassLoader oldTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            for (SetupAction action : setupActions) {
                action.setup(null);
            }
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            bootstrapSupplier.get().getBootstrap().startInitialization();
            bootstrapSupplier.get().getBootstrap().deployBeans();
            bootstrapSupplier.get().getBootstrap().validateBeans();
        } finally {

            for (SetupAction action : setupActions) {
                try {
                    action.teardown(null);
                } catch (Exception e) {
                    WeldLogger.DEPLOYMENT_LOGGER.exceptionClearingThreadState(e);
                }
            }
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
        }
    }

    /**
     * Stops the container
     * Executed in WeldStartService to shutdown the runtime before NamingService is closed.
     *
     * @throws IllegalStateException if the container is not running
     */
    @Override
    public void stop(final StopContext context) {
        final WeldBootstrapService bootstrapService = bootstrapSupplier.get();
        if (!bootstrapService.isStarted()) {
            //this happens when the 'runOnce' code in start has been triggered
            return;
        }
        WeldLogger.DEPLOYMENT_LOGGER.stoppingWeldService(bootstrapService.getDeploymentName());
        ClassLoader oldTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(bootstrapService.getDeployment().getModule().getClassLoader());
            WeldProvider.containerShutDown(Container.instance(bootstrapService.getDeploymentName()));
            bootstrapService.getBootstrap().shutdown();
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
            ModuleGroupSingletonProvider.removeClassLoader(bootstrapService.getDeployment().getModule().getClassLoader());
        }
        bootstrapService.startServiceShutdown();
    }

}
