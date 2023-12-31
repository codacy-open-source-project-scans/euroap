/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.infinispan.remote;

import java.util.List;
import java.util.function.Consumer;

import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ClusteringConfiguration;
import org.infinispan.configuration.cache.ClusteringConfigurationBuilder;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StateTransferConfiguration;
import org.infinispan.configuration.cache.StateTransferConfigurationBuilder;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.eviction.EvictionStrategy;
import org.jboss.as.clustering.controller.CapabilityServiceConfigurator;
import org.jboss.as.controller.ServiceNameFactory;
import org.jboss.as.network.ClientMapping;
import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.ejb.infinispan.network.ClientMappingsRegistryEntryServiceConfigurator;
import org.wildfly.clustering.ejb.remote.ClientMappingsRegistryProvider;
import org.wildfly.clustering.infinispan.configuration.ConfigurationBuilderAttributesAccessor;
import org.wildfly.clustering.infinispan.container.DataContainerConfigurationBuilder;
import org.wildfly.clustering.infinispan.service.CacheServiceConfigurator;
import org.wildfly.clustering.infinispan.service.InfinispanCacheRequirement;
import org.wildfly.clustering.infinispan.service.TemplateConfigurationServiceConfigurator;
import org.wildfly.clustering.server.service.ProvidedCacheServiceConfigurator;
import org.wildfly.clustering.server.service.group.DistributedCacheGroupServiceConfiguratorProvider;
import org.wildfly.clustering.server.service.registry.DistributedRegistryServiceConfiguratorProvider;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * The legacy version of the client mappings registry provider, used when no distributable-ejb subsystem is present.
 *
 * @author Paul Ferraro
 */
@Deprecated
@MetaInfServices(ClientMappingsRegistryProvider.class)
public class LegacyInfinispanClientMappingsRegistryProvider implements ClientMappingsRegistryProvider, Consumer<ConfigurationBuilder> {

    private final String containerName;

    public LegacyInfinispanClientMappingsRegistryProvider(final String containerName) {
        this.containerName = containerName;
    }

    @Override
    public Iterable<CapabilityServiceConfigurator> getServiceConfigurators(String connectorName, SupplierDependency<List<ClientMapping>> clientMappings) {
        CapabilityServiceConfigurator configurationConfigurator = new TemplateConfigurationServiceConfigurator(ServiceNameFactory.parseServiceName(InfinispanCacheRequirement.CONFIGURATION.getName()).append(this.containerName, connectorName), this.containerName, connectorName, null, this);
        CapabilityServiceConfigurator cacheConfigurator = new CacheServiceConfigurator<>(ServiceNameFactory.parseServiceName(InfinispanCacheRequirement.CACHE.getName()).append(this.containerName, connectorName), this.containerName, connectorName);
        CapabilityServiceConfigurator registryEntryConfigurator = new ClientMappingsRegistryEntryServiceConfigurator(this.containerName, connectorName, clientMappings);
        CapabilityServiceConfigurator registryConfigurator = new ProvidedCacheServiceConfigurator<>(DistributedRegistryServiceConfiguratorProvider.class, this.containerName, connectorName);
        CapabilityServiceConfigurator groupConfigurator = new ProvidedCacheServiceConfigurator<>(DistributedCacheGroupServiceConfiguratorProvider.class, this.containerName, connectorName);
        return List.of(configurationConfigurator, cacheConfigurator, registryEntryConfigurator, registryConfigurator, groupConfigurator);
    }

    @Override
    public void accept(ConfigurationBuilder builder) {
        ClusteringConfigurationBuilder clustering = builder.clustering();
        CacheMode mode = clustering.cacheMode();
        clustering.cacheMode(mode.needsStateTransfer() ? CacheMode.REPL_SYNC : CacheMode.LOCAL);
        clustering.l1().disable();
        // Workaround for ISPN-8722
        AttributeSet attributes = ConfigurationBuilderAttributesAccessor.INSTANCE.apply(clustering);
        attributes.attribute(ClusteringConfiguration.BIAS_ACQUISITION).reset();
        attributes.attribute(ClusteringConfiguration.BIAS_LIFESPAN).reset();
        attributes.attribute(ClusteringConfiguration.INVALIDATION_BATCH_SIZE).reset();
        // Ensure we use the default data container
        builder.addModule(DataContainerConfigurationBuilder.class).evictable(null);
        // Disable expiration
        builder.expiration().lifespan(-1).maxIdle(-1);
        // Disable eviction
        builder.memory().storage(StorageType.HEAP).maxCount(-1).whenFull(EvictionStrategy.NONE);
        builder.persistence().clearStores();
        StateTransferConfigurationBuilder stateTransfer = clustering.stateTransfer().fetchInMemoryState(mode.needsStateTransfer());
        attributes = ConfigurationBuilderAttributesAccessor.INSTANCE.apply(stateTransfer);
        attributes.attribute(StateTransferConfiguration.AWAIT_INITIAL_TRANSFER).reset();
        attributes.attribute(StateTransferConfiguration.TIMEOUT).reset();
    }
}
