/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import static org.jboss.as.clustering.jgroups.subsystem.SocketProtocolResourceDefinition.Attribute.CLIENT_SOCKET_BINDING;
import static org.jboss.as.clustering.jgroups.subsystem.SocketProtocolResourceDefinition.Attribute.SOCKET_BINDING;

import org.jboss.as.clustering.controller.Attribute;
import org.jboss.as.clustering.controller.CommonUnaryRequirement;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jgroups.protocols.TP;
import org.jgroups.stack.Protocol;
import org.wildfly.clustering.jgroups.spi.TransportConfiguration;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SimpleSupplierDependency;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * Configures a service that provides a FD_SOCK protocol.
 * @author Paul Ferraro
 */
public abstract class SocketProtocolConfigurationServiceConfigurator<P extends Protocol> extends ProtocolConfigurationServiceConfigurator<P> {

    private volatile SupplierDependency<SocketBinding> binding;
    private volatile SupplierDependency<SocketBinding> clientBinding;
    private final SupplierDependency<TransportConfiguration<? extends TP>> transport;

    public SocketProtocolConfigurationServiceConfigurator(PathAddress address) {
        super(address);
        this.transport = new ServiceSupplierDependency<>(new SingletonProtocolServiceNameProvider(address.getParent(), TransportResourceDefinition.WILDCARD_PATH));
    }

    @Override
    public <T> ServiceBuilder<T> register(ServiceBuilder<T> builder) {
        return super.register(new CompositeDependency(this.binding, this.clientBinding, this.transport).register(builder));
    }

    @Override
    public ServiceConfigurator configure(OperationContext context, ModelNode model) throws OperationFailedException {
        this.binding = createDependency(context, model, SOCKET_BINDING);
        this.clientBinding = createDependency(context, model, CLIENT_SOCKET_BINDING);
        return super.configure(context, model);
    }

    private static SupplierDependency<SocketBinding> createDependency(OperationContext context, ModelNode model, Attribute attribute) throws OperationFailedException {
        String bindingName = attribute.resolveModelAttribute(context, model).asStringOrNull();
        return (bindingName != null) ? new ServiceSupplierDependency<>(CommonUnaryRequirement.SOCKET_BINDING.getServiceName(context, bindingName)) : new SimpleSupplierDependency<>(null);
    }

    SocketBinding getSocketBinding() {
        return this.binding.get();
    }

    SocketBinding getClientSocketBinding() {
        return this.clientBinding.get();
    }

    TransportConfiguration<? extends TP> getTransport() {
        return this.transport.get();
    }
}
