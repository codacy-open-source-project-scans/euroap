/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import java.util.function.UnaryOperator;

import org.jboss.as.clustering.controller.ChildResourceDefinition;
import org.jboss.as.clustering.controller.PropertiesAttributeDefinition;
import org.jboss.as.clustering.controller.ResourceDescriptor;
import org.jboss.as.clustering.controller.ResourceServiceConfiguratorFactory;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.controller.RestartParentResourceRegistrar;
import org.jboss.as.clustering.controller.SimpleResourceServiceHandler;
import org.jboss.as.clustering.controller.validation.ModuleIdentifierValidatorBuilder;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Resource description for /subsystem=jgroups/stack=X/protocol=Y
 *
 * @author Richard Achmatowicz (c) 2011 Red Hat Inc.
 * @author Paul Ferraro
 */
public class AbstractProtocolResourceDefinition extends ChildResourceDefinition<ManagementResourceRegistration> {

    enum Attribute implements org.jboss.as.clustering.controller.Attribute, UnaryOperator<SimpleAttributeDefinitionBuilder> {
        MODULE(ModelDescriptionConstants.MODULE, ModelType.STRING) {
            @Override
            public SimpleAttributeDefinitionBuilder apply(SimpleAttributeDefinitionBuilder builder) {
                return builder.setDefaultValue(new ModelNode("org.jgroups"))
                        .setValidator(new ModuleIdentifierValidatorBuilder().configure(builder).build())
                        ;
            }
        },
        PROPERTIES(ModelDescriptionConstants.PROPERTIES),
        STATISTICS_ENABLED(ModelDescriptionConstants.STATISTICS_ENABLED, ModelType.BOOLEAN),
        ;
        private final AttributeDefinition definition;

        Attribute(String name, ModelType type) {
            this.definition = this.apply(new SimpleAttributeDefinitionBuilder(name, type)
                    .setAllowExpression(true)
                    .setRequired(false)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    ).build();
        }

        Attribute(String name) {
            this.definition = new PropertiesAttributeDefinition.Builder(name)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }

        @Override
        public SimpleAttributeDefinitionBuilder apply(SimpleAttributeDefinitionBuilder builder) {
            return builder;
        }
    }

    private final UnaryOperator<ResourceDescriptor> configurator;
    private final ResourceServiceHandler handler;
    private final ResourceServiceConfiguratorFactory parentServiceConfiguratorFactory;

    AbstractProtocolResourceDefinition(Parameters parameters, UnaryOperator<ResourceDescriptor> configurator, ResourceServiceConfiguratorFactory serviceConfiguratorFactory, ResourceServiceConfiguratorFactory parentServiceConfiguratorFactory) {
        super(parameters);
        this.configurator = configurator;
        this.handler = new SimpleResourceServiceHandler(serviceConfiguratorFactory);
        this.parentServiceConfiguratorFactory = parentServiceConfiguratorFactory;
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent) {
        ManagementResourceRegistration registration = parent.registerSubModel(this);

        ResourceDescriptor descriptor = this.configurator.apply(new ResourceDescriptor(this.getResourceDescriptionResolver()))
                .addAttributes(Attribute.class)
                ;
        new RestartParentResourceRegistrar(this.parentServiceConfiguratorFactory, descriptor, this.handler).register(registration);

        return registration;
    }
}
