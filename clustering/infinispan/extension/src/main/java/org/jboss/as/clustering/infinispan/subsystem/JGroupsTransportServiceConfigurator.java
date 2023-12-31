/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.infinispan.subsystem;

import static org.jboss.as.clustering.infinispan.subsystem.JGroupsTransportResourceDefinition.Attribute.CHANNEL;
import static org.jboss.as.clustering.infinispan.subsystem.JGroupsTransportResourceDefinition.Attribute.LOCK_TIMEOUT;

import java.util.Properties;

import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.TransportConfiguration;
import org.infinispan.configuration.global.TransportConfigurationBuilder;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jboss.as.clustering.infinispan.transport.ChannelConfigurator;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.clustering.jgroups.spi.ChannelFactory;
import org.wildfly.clustering.jgroups.spi.JGroupsRequirement;
import org.wildfly.clustering.jgroups.spi.ProtocolStackConfiguration;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * @author Paul Ferraro
 */
public class JGroupsTransportServiceConfigurator extends GlobalComponentServiceConfigurator<TransportConfiguration> {

    private final String containerName;
    private volatile SupplierDependency<ChannelFactory> factory;
    private volatile SupplierDependency<String> cluster;
    private volatile String channel;
    private volatile long lockTimeout;

    public JGroupsTransportServiceConfigurator(PathAddress address) {
        super(CacheContainerComponent.TRANSPORT, address);
        this.containerName = address.getParent().getLastElement().getValue();
    }

    @Override
    public <T> ServiceBuilder<T> register(ServiceBuilder<T> builder) {
        return super.register(new CompositeDependency(this.factory, this.cluster).register(builder));
    }

    @Override
    public JGroupsTransportServiceConfigurator configure(OperationContext context, ModelNode model) throws OperationFailedException {
        this.lockTimeout = LOCK_TIMEOUT.resolveModelAttribute(context, model).asLong();
        this.channel = CHANNEL.resolveModelAttribute(context, model).asStringOrNull();
        this.factory = new ServiceSupplierDependency<>(JGroupsRequirement.CHANNEL_FACTORY.getServiceName(context, this.channel));
        this.cluster = new ServiceSupplierDependency<>(JGroupsRequirement.CHANNEL_CLUSTER.getServiceName(context, this.channel));
        return this;
    }

    @Override
    public TransportConfiguration get() {
        ChannelFactory factory = this.factory.get();
        Properties properties = new Properties();
        properties.put(JGroupsTransport.CHANNEL_CONFIGURATOR, new ChannelConfigurator(factory, this.containerName));
        ProtocolStackConfiguration stack = factory.getProtocolStackConfiguration();
        org.wildfly.clustering.jgroups.spi.TransportConfiguration.Topology topology = stack.getTransport().getTopology();
        TransportConfigurationBuilder builder = new GlobalConfigurationBuilder().transport()
                .clusterName(this.cluster.get())
                .distributedSyncTimeout(this.lockTimeout)
                .transport(new JGroupsTransport())
                .withProperties(properties)
                ;
        if (topology != null) {
            builder.siteId(topology.getSite()).rackId(topology.getRack()).machineId(topology.getMachine());
        }
        return builder.create();
    }

    String getChannel() {
        return this.channel;
    }
}
