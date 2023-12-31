/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld;

import org.jboss.as.weld.deployment.BeanDeploymentArchiveImpl;
import org.jboss.as.weld.deployment.WeldDeployment;
import org.jboss.as.weld.logging.WeldLogger;
import org.jboss.as.weld.services.ModuleGroupSingletonProvider;
import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.weld.Container;
import org.jboss.weld.ContainerState;
import org.jboss.weld.bootstrap.WeldBootstrap;
import org.jboss.weld.bootstrap.api.Environment;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.manager.api.ExecutorServices;
import org.jboss.weld.security.spi.SecurityServices;
import org.jboss.weld.transaction.spi.TransactionServices;
import org.wildfly.security.manager.WildFlySecurityManager;

import jakarta.enterprise.inject.spi.BeanManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Provides the initial bootstrap of the Weld container. This does not actually finish starting the container, merely gets it to
 * the point that the bean manager is available.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class WeldBootstrapService implements Service {

    /**
     * The service name that external services depend on
     */
    public static final ServiceName SERVICE_NAME = ServiceName.of("WeldBootstrapService");
    /**
     * The actual service name the bootstrap service is installed under.
     * <p>
     * The reasons for this dual service name setup is kinda complex, and relates to https://issues.redhat.com/browse/JBEAP-18634
     * Because Weld cannot be restarted if an attempt is made to restart the service then we need to bail out and restart
     * the whole deployment.
     * <p>
     * If we just do a check in the start method that looks like:
     * if (restartRequired) {
     * doRestart();
     * return;
     * }
     * Then the service startup will technically complete successfully, and dependent services can still start before
     * the restart actually takes effect (as MSC is directional, it keep starting services that have all their dependencies
     * met before it start to take services down).
     * <p>
     * To get around this we use two different service names, with the service that other services depend on only being
     * installed at the end of the start() method. This means that in the case of a restart this will not be installed,
     * so no dependent services will be started as they are missing their dependency.
     */
    public static final ServiceName INTERNAL_SERVICE_NAME = ServiceName.of("WeldBootstrapServiceInternal");

    private final WeldBootstrap bootstrap;
    private final WeldDeployment deployment;
    private final Environment environment;
    private final Map<String, BeanDeploymentArchive> beanDeploymentArchives;
    private final BeanDeploymentArchiveImpl rootBeanDeploymentArchive;

    private final String deploymentName;
    private final Consumer<WeldBootstrapService> weldBootstrapServiceConsumer;
    private final Supplier<ExecutorServices> executorServicesSupplier;
    private final Supplier<ExecutorService> serverExecutorSupplier;
    private final Supplier<SecurityServices> securityServicesSupplier;
    private final Supplier<TransactionServices> weldTransactionServicesSupplier;
    private final ServiceName deploymentServiceName;
    private final ServiceName weldBootstrapServiceName;
    private volatile boolean started;
    private volatile ServiceController<?> controller;
    private final AtomicBoolean runOnce = new AtomicBoolean();

    public WeldBootstrapService(final WeldDeployment deployment, final Environment environment, final String deploymentName,
                                final Consumer<WeldBootstrapService> weldBootstrapServiceConsumer,
                                final Supplier<ExecutorServices> executorServicesSupplier,
                                final Supplier<ExecutorService> serverExecutorSupplier,
                                final Supplier<SecurityServices> securityServicesSupplier,
                                final Supplier<TransactionServices> weldTransactionServicesSupplier,
                                ServiceName deploymentServiceName, ServiceName weldBootstrapServiceName) {
        this.deployment = deployment;
        this.environment = environment;
        this.deploymentName = deploymentName;
        this.weldBootstrapServiceConsumer = weldBootstrapServiceConsumer;
        this.executorServicesSupplier = executorServicesSupplier;
        this.serverExecutorSupplier = serverExecutorSupplier;
        this.securityServicesSupplier = securityServicesSupplier;
        this.weldTransactionServicesSupplier = weldTransactionServicesSupplier;
        this.deploymentServiceName = deploymentServiceName;
        this.weldBootstrapServiceName = weldBootstrapServiceName;
        this.bootstrap = new WeldBootstrap();
        Map<String, BeanDeploymentArchive> bdas = new HashMap<String, BeanDeploymentArchive>();
        BeanDeploymentArchiveImpl rootBeanDeploymentArchive = null;
        for (BeanDeploymentArchive archive : deployment.getBeanDeploymentArchives()) {
            bdas.put(archive.getId(), archive);
            if (archive instanceof BeanDeploymentArchiveImpl) {
                BeanDeploymentArchiveImpl bda = (BeanDeploymentArchiveImpl) archive;
                if (bda.isRoot()) {
                    rootBeanDeploymentArchive = bda;
                }
            }
        }
        this.rootBeanDeploymentArchive = rootBeanDeploymentArchive;
        this.beanDeploymentArchives = Collections.unmodifiableMap(bdas);
    }

    /**
     * Starts the weld container
     *
     * @throws IllegalStateException if the container is already running
     */
    public synchronized void start(final StartContext context) {

        if (!runOnce.compareAndSet(false, true)) {
            ServiceController<?> controller = context.getController().getServiceContainer().getService(deploymentServiceName);
            controller.addListener(new LifecycleListener() {
                @Override
                public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                    if (event == LifecycleEvent.DOWN) {
                        controller.setMode(ServiceController.Mode.ACTIVE);
                        controller.removeListener(this);
                    }
                }
            });
            controller.setMode(ServiceController.Mode.NEVER);
            return;
        }
        if (started) {
            throw WeldLogger.ROOT_LOGGER.alreadyRunning("WeldContainer");
        }
        started = true;

        WeldLogger.DEPLOYMENT_LOGGER.startingWeldService(deploymentName);
        // set up injected services
        addWeldService(SecurityServices.class, securityServicesSupplier.get());

        TransactionServices transactionServices = weldTransactionServicesSupplier != null ? weldTransactionServicesSupplier.get() : null;
        if (transactionServices != null) {
            addWeldService(TransactionServices.class, transactionServices);
        }

        if (!deployment.getServices().contains(ExecutorServices.class)) {
            addWeldService(ExecutorServices.class, executorServicesSupplier.get());
        }

        ModuleGroupSingletonProvider.addClassLoaders(deployment.getModule().getClassLoader(),
                deployment.getSubDeploymentClassLoaders());

        ClassLoader oldTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(deployment.getModule().getClassLoader());
            bootstrap.startContainer(deploymentName, environment, deployment);
            WeldProvider.containerInitialized(Container.instance(deploymentName), getBeanManager(), deployment);
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
        }
        weldBootstrapServiceConsumer.accept(this);

        //this is the actual service that clients depend on
        //we install it here so that if a restart needs to happen all our dependants won't be able to start
        final ServiceBuilder<?> weldBootstrapServiceBuilder = context.getChildTarget().addService(weldBootstrapServiceName);
        Consumer<WeldBootstrapService> provider = weldBootstrapServiceBuilder.provides(weldBootstrapServiceName);
        weldBootstrapServiceBuilder.setInstance(new Service() {
            @Override
            public void start(StartContext context) throws StartException {
                provider.accept(WeldBootstrapService.this);
            }

            @Override
            public void stop(StopContext context) {
                context.getController().setMode(ServiceController.Mode.REMOVE);
            }
        });
        weldBootstrapServiceBuilder.install();
        controller = context.getController();
    }

    /**
     * This is a no-op if {@link WeldStartService#start(StartContext)} completes normally and the shutdown is performed in
     * {@link WeldStartService#stop(org.jboss.msc.service.StopContext)}.
     */
    public synchronized void stop(final StopContext context) {
        weldBootstrapServiceConsumer.accept(null);
        if (started) {
            // WeldStartService#stop() not completed - attempt to perform the container cleanup
            final Container container = Container.instance(deploymentName);
            if (container != null && !ContainerState.SHUTDOWN.equals(container.getState())) {
                context.asynchronous();
                final ExecutorService executorService = serverExecutorSupplier.get();
                final Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        WeldLogger.DEPLOYMENT_LOGGER.debugf("Weld container cleanup for deployment %s", deploymentName);
                        ClassLoader oldTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
                        try {
                            WildFlySecurityManager
                                    .setCurrentContextClassLoaderPrivileged(deployment.getModule().getClassLoader());
                            WeldProvider.containerShutDown(container);
                            container.setState(ContainerState.SHUTDOWN);
                            container.cleanup();
                            startServiceShutdown();
                        } finally {
                            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
                            ModuleGroupSingletonProvider.removeClassLoader(deployment.getModule().getClassLoader());
                            context.complete();
                        }
                    }
                };
                try {
                    executorService.execute(task);
                } catch (RejectedExecutionException e) {
                    task.run();
                }
            }
        }
    }

    /**
     * Gets the {@link BeanManager} for a given bean deployment archive id.
     *
     * @throws IllegalStateException    if the container is not running
     * @throws IllegalArgumentException if the bean deployment archive id is not found
     */
    public BeanManagerImpl getBeanManager(String beanArchiveId) {
        if (!started) {
            throw WeldLogger.ROOT_LOGGER.notStarted("WeldContainer");
        }
        BeanDeploymentArchive beanDeploymentArchive = beanDeploymentArchives.get(beanArchiveId);
        if (beanDeploymentArchive == null) {
            throw WeldLogger.ROOT_LOGGER.beanDeploymentNotFound(beanArchiveId);
        }
        return bootstrap.getManager(beanDeploymentArchive);
    }

    /**
     * Adds a {@link Service} to the deployment. This method must not be called after the container has started
     */
    public <T extends org.jboss.weld.bootstrap.api.Service> void addWeldService(Class<T> type, T service) {
        deployment.addWeldService(type, service);
    }

    /**
     * Gets the {@link BeanManager} linked to the root bean deployment archive. This BeanManager has access to all beans in a
     * deployment
     *
     * @throws IllegalStateException if the container is not running
     */
    public BeanManagerImpl getBeanManager() {
        if (!started) {
            throw WeldLogger.ROOT_LOGGER.notStarted("WeldContainer");
        }
        return bootstrap.getManager(rootBeanDeploymentArchive);
    }

    /**
     * get all beans deployment archives in the deployment
     */
    public Set<BeanDeploymentArchive> getBeanDeploymentArchives() {
        return new HashSet<BeanDeploymentArchive>(beanDeploymentArchives.values());
    }

    public boolean isStarted() {
        return started;
    }

    void startServiceShutdown() {
        //the start service has been shutdown, which means either we are being shutdown/undeployed
        //or we are going to need to bounce the whole deployment
        this.started = false;
        if (controller.getServiceContainer().isShutdown()) {
            //container is being shutdown, no action required
            return;
        }
        ServiceController<?> deploymentController = controller.getServiceContainer().getService(deploymentServiceName);
        if (deploymentController.getMode() != ServiceController.Mode.ACTIVE) {
            //deployment is not active, no action required
            return;
        }
        //add a listener to tentatively 'bounce' this service
        //if the service does actually restart then this will trigger a full deployment restart
        //we do it this way as we don't have visibility into MSC in the general sense
        //so we don't really know if this service is supposed to go away
        //this 'potential bounce' is hard to do in a non-racey manner
        //we need to add the listener first, but the listener may be invoked before the CAS to never
        CompletableFuture<Boolean> attemptingBounce = new CompletableFuture();

        try {
            CompletableFuture<Boolean> listenerDone = new CompletableFuture<>();
            LifecycleListener listener = new LifecycleListener() {
                @Override
                public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                    try {
                        try {
                            if (controller.getServiceContainer().isShutdown() || !attemptingBounce.get()) {
                                controller.removeListener(this);
                                return;
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                        if (deploymentController.getMode() != ServiceController.Mode.ACTIVE ||
                                controller.getMode() == ServiceController.Mode.REMOVE) {
                            return;
                        }
                        if (event == LifecycleEvent.DOWN) {
                            controller.removeListener(this);
                            do {
                                if (controller.getMode() != ServiceController.Mode.NEVER) {
                                    return;
                                }
                            } while (!controller.compareAndSetMode(ServiceController.Mode.NEVER, ServiceController.Mode.ACTIVE));
                        }
                    } finally {
                        listenerDone.complete(true);
                    }
                }
            };
            //
            controller.getServiceContainer().addService(controller.getName().append("fakeStabilityService")).setInstance(new Service() {
                @Override
                public void start(StartContext context) throws StartException {
                    context.asynchronous();
                    listenerDone.handle(new BiFunction<Boolean, Throwable, Object>() {
                        @Override
                        public Object apply(Boolean aBoolean, Throwable throwable) {
                            context.getController().setMode(ServiceController.Mode.REMOVE);
                            context.complete();
                            return null;
                        }
                    });
                }

                @Override
                public void stop(StopContext context) {

                }
            }).install();
            controller.addListener(listener);
            if (!controller.compareAndSetMode(ServiceController.Mode.ACTIVE, ServiceController.Mode.NEVER)) {
                controller.removeListener(listener);
                attemptingBounce.complete(false);
                listenerDone.complete(false);
            } else {
                attemptingBounce.complete(true);
            }
        } catch (Throwable t) {
            //should never happen
            //but lets be safe
            attemptingBounce.completeExceptionally(t);
            throw new RuntimeException(t);
        }
    }

    WeldDeployment getDeployment() {
        return deployment;
    }

    String getDeploymentName() {
        return deploymentName;
    }

    WeldBootstrap getBootstrap() {
        return bootstrap;
    }
}
