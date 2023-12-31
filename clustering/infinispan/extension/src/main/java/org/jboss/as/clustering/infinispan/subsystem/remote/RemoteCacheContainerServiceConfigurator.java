/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem.remote;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.jboss.as.clustering.controller.CapabilityServiceNameProvider;
import org.jboss.as.clustering.controller.ResourceServiceConfigurator;
import org.jboss.as.clustering.infinispan.client.ManagedRemoteCacheContainer;
import org.jboss.as.clustering.infinispan.logging.InfinispanLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.Registrar;
import org.wildfly.clustering.infinispan.client.RemoteCacheContainer;
import org.wildfly.clustering.infinispan.client.service.InfinispanClientRequirement;
import org.wildfly.clustering.service.AsyncServiceConfigurator;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.FunctionalService;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * Configures a service providing a {@link RemoteCacheContainer}.
 * @author Radoslav Husar
 * @author Paul Ferraro
 */
public class RemoteCacheContainerServiceConfigurator extends CapabilityServiceNameProvider implements ResourceServiceConfigurator, Function<RemoteCacheManager, RemoteCacheContainer>, Supplier<RemoteCacheManager>, Consumer<RemoteCacheManager> {

    private final String name;
    private final SupplierDependency<ModuleLoader> loader;

    private volatile SupplierDependency<Configuration> configuration;
    private volatile Registrar<String> registrar;

    public RemoteCacheContainerServiceConfigurator(PathAddress address) {
        super(RemoteCacheContainerResourceDefinition.Capability.CONTAINER, address);
        this.name = address.getLastElement().getValue();
        this.loader = new ServiceSupplierDependency<>(Services.JBOSS_SERVICE_MODULE_LOADER);
    }

    @Override
    public ServiceConfigurator configure(OperationContext context, ModelNode model) throws OperationFailedException {
        this.configuration = new ServiceSupplierDependency<>(InfinispanClientRequirement.REMOTE_CONTAINER_CONFIGURATION.getServiceName(context, this.name));
        this.registrar = (RemoteCacheContainerResource) context.readResource(PathAddress.EMPTY_ADDRESS);
        return this;
    }

    @Override
    public ServiceBuilder<?> build(ServiceTarget target) {
        ServiceBuilder<?> builder = new AsyncServiceConfigurator(this.getServiceName()).build(target);
        Consumer<RemoteCacheContainer> container = new CompositeDependency(this.configuration, this.loader).register(builder).provides(this.getServiceName());
        Service service = new FunctionalService<>(container, this, this, this);
        return builder.setInstance(service).setInitialMode(ServiceController.Mode.ON_DEMAND);
    }

    @Override
    public RemoteCacheManager get() {
        Configuration configuration = this.configuration.get();
        RemoteCacheManager manager = new RemoteCacheManager(configuration);
        manager.start();
        InfinispanLogger.ROOT_LOGGER.remoteCacheContainerStarted(this.name);
        return manager;
    }

    @Override
    public void accept(RemoteCacheManager manager) {
        manager.stop();
        InfinispanLogger.ROOT_LOGGER.remoteCacheContainerStopped(this.name);
    }

    @Override
    public RemoteCacheContainer apply(RemoteCacheManager container) {
        return new ManagedRemoteCacheContainer(container, this.name, this.loader.get(), this.registrar);
    }
}
