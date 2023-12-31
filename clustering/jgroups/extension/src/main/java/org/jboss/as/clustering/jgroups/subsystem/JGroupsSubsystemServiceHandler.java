/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import static org.jboss.as.clustering.jgroups.logging.JGroupsLogger.ROOT_LOGGER;
import static org.jboss.as.clustering.jgroups.subsystem.JGroupsSubsystemResourceDefinition.CAPABILITIES;
import static org.jboss.as.clustering.jgroups.subsystem.JGroupsSubsystemResourceDefinition.Attribute.DEFAULT_CHANNEL;

import java.util.Map;

import org.jboss.as.clustering.controller.Capability;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.naming.BinderServiceConfigurator;
import org.jboss.as.clustering.naming.JndiNameFactory;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceTarget;
import org.jgroups.Version;
import org.wildfly.clustering.jgroups.spi.JGroupsRequirement;
import org.wildfly.clustering.server.service.ProvidedIdentityGroupServiceConfigurator;
import org.wildfly.clustering.service.IdentityServiceConfigurator;

/**
 * @author Paul Ferraro
 */
public class JGroupsSubsystemServiceHandler implements ResourceServiceHandler {

    @Override
    public void installServices(OperationContext context, ModelNode model) throws OperationFailedException {
        ROOT_LOGGER.activatingSubsystem(Version.printVersion());

        ServiceTarget target = context.getServiceTarget();
        PathAddress address = context.getCurrentAddress();

        // Handle case where JGroups subsystem is added to a running server
        // In this case, the Infinispan subsystem may have already registered default group capabilities
        if (context.getProcessType().isServer() && !context.isBooting()) {
            if (context.readResourceFromRoot(address.getParent(),false).hasChild(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "infinispan"))) {
                // Following restart, default group services will be installed by this handler, rather than the infinispan subsystem handler
                context.addStep((ctx, operation) -> {
                    ctx.reloadRequired();
                    ctx.completeStep(OperationContext.RollbackHandler.REVERT_RELOAD_REQUIRED_ROLLBACK_HANDLER);
                }, OperationContext.Stage.RUNTIME);
                return;
            }
        }

        new ProtocolDefaultsServiceConfigurator().build(target).install();

        String defaultChannel = DEFAULT_CHANNEL.resolveModelAttribute(context, model).asStringOrNull();
        if (defaultChannel != null) {
            for (Map.Entry<JGroupsRequirement, Capability> entry : CAPABILITIES.entrySet()) {
                new IdentityServiceConfigurator<>(entry.getValue().getServiceName(address), entry.getKey().getServiceName(context, defaultChannel)).build(target).install();
            }

            if (!defaultChannel.equals(JndiNameFactory.DEFAULT_LOCAL_NAME)) {
                new BinderServiceConfigurator(JGroupsBindingFactory.createChannelBinding(JndiNameFactory.DEFAULT_LOCAL_NAME), JGroupsRequirement.CHANNEL.getServiceName(context, defaultChannel)).build(target).install();
                new BinderServiceConfigurator(JGroupsBindingFactory.createChannelFactoryBinding(JndiNameFactory.DEFAULT_LOCAL_NAME), JGroupsRequirement.CHANNEL_FACTORY.getServiceName(context, defaultChannel)).build(target).install();
            }

            new ProvidedIdentityGroupServiceConfigurator(null, defaultChannel).configure(context).build(target).install();
        }
    }

    @Override
    public void removeServices(OperationContext context, ModelNode model) throws OperationFailedException {
        PathAddress address = context.getCurrentAddress();
        String defaultChannel = DEFAULT_CHANNEL.resolveModelAttribute(context, model).asStringOrNull();
        if (defaultChannel != null) {
            new ProvidedIdentityGroupServiceConfigurator(null, defaultChannel).remove(context);

            if (!defaultChannel.equals(JndiNameFactory.DEFAULT_LOCAL_NAME)) {
                context.removeService(JGroupsBindingFactory.createChannelFactoryBinding(JndiNameFactory.DEFAULT_LOCAL_NAME).getBinderServiceName());
                context.removeService(JGroupsBindingFactory.createChannelBinding(JndiNameFactory.DEFAULT_LOCAL_NAME).getBinderServiceName());
            }

            for (Capability capability : CAPABILITIES.values()) {
                context.removeService(capability.getServiceName(address));
            }
        }

        context.removeService(ProtocolDefaultsServiceConfigurator.SERVICE_NAME);
    }
}
