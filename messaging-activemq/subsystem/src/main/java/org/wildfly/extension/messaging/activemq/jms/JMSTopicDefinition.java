/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.messaging.activemq.jms;

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.registry.AttributeAccess.Flag.GAUGE_METRIC;
import static org.jboss.dmr.ModelType.INT;
import static org.jboss.dmr.ModelType.STRING;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.messaging.activemq.CommonAttributes;
import org.wildfly.extension.messaging.activemq.MessagingExtension;


/**
 * Jakarta Messaging Topic resource definition
 *
 * @author <a href="http://jmesnil.net">Jeff Mesnil</a> (c) 2012 Red Hat Inc.
 */
public class JMSTopicDefinition extends PersistentResourceDefinition {

    public static final AttributeDefinition[] ATTRIBUTES = {
            CommonAttributes.DESTINATION_ENTRIES,
            CommonAttributes.LEGACY_ENTRIES
    };

    /**
     * Attributes for deployed Jakarta Messaging topic are stored in runtime
     */
    private static AttributeDefinition[] DEPLOYMENT_ATTRIBUTES = {
            new StringListAttributeDefinition.Builder(CommonAttributes.DESTINATION_ENTRIES)
                    .setStorageRuntime()
                    .build(),
            new StringListAttributeDefinition.Builder(CommonAttributes.LEGACY_ENTRIES)
                    .setStorageRuntime()
                    .build()
    };

    static final AttributeDefinition TOPIC_ADDRESS = create(CommonAttributes.TOPIC_ADDRESS, STRING)
            .setStorageRuntime()
            .build();

    static final AttributeDefinition[] READONLY_ATTRIBUTES = { TOPIC_ADDRESS,
            CommonAttributes.TEMPORARY, CommonAttributes.PAUSED };

    static final AttributeDefinition DURABLE_MESSAGE_COUNT = create(CommonAttributes.DURABLE_MESSAGE_COUNT, INT)
            .setStorageRuntime()
            .setUndefinedMetricValue(ModelNode.ZERO)
            .addFlag(GAUGE_METRIC)
            .build();

    static final AttributeDefinition NON_DURABLE_MESSAGE_COUNT = create(CommonAttributes.NON_DURABLE_MESSAGE_COUNT, INT)
            .setStorageRuntime()
            .setUndefinedMetricValue(ModelNode.ZERO)
            .addFlag(GAUGE_METRIC)
            .build();

    static final AttributeDefinition SUBSCRIPTION_COUNT = create(CommonAttributes.SUBSCRIPTION_COUNT, INT)
            .setStorageRuntime()
            .setUndefinedMetricValue(ModelNode.ZERO)
            .addFlag(GAUGE_METRIC)
            .build();

    static final AttributeDefinition DURABLE_SUBSCRIPTION_COUNT = create(CommonAttributes.DURABLE_SUBSCRIPTION_COUNT, INT)
            .setStorageRuntime()
            .setUndefinedMetricValue(ModelNode.ZERO)
            .addFlag(GAUGE_METRIC)
            .build();

    static final AttributeDefinition NON_DURABLE_SUBSCRIPTION_COUNT = create(CommonAttributes.NON_DURABLE_SUBSCRIPTION_COUNT, INT)
            .setStorageRuntime()
            .setUndefinedMetricValue(ModelNode.ZERO)
            .addFlag(GAUGE_METRIC)
            .build();

    static final AttributeDefinition[] METRICS = { CommonAttributes.DELIVERING_COUNT,
            CommonAttributes.MESSAGES_ADDED,
            CommonAttributes.MESSAGE_COUNT,
            DURABLE_MESSAGE_COUNT,
            NON_DURABLE_MESSAGE_COUNT,
            SUBSCRIPTION_COUNT,
            DURABLE_SUBSCRIPTION_COUNT,
            NON_DURABLE_SUBSCRIPTION_COUNT
    };

    private final boolean deployed;
    private final boolean registerRuntimeOnly;

    public JMSTopicDefinition(final boolean deployed,
                               final boolean registerRuntimeOnly) {
        super(MessagingExtension.JMS_TOPIC_PATH,
                MessagingExtension.getResourceDescriptionResolver(CommonAttributes.JMS_TOPIC),
                deployed ? null : JMSTopicAdd.INSTANCE,
                deployed ? null : JMSTopicRemove.INSTANCE);
        this.deployed = deployed;
        this.registerRuntimeOnly = registerRuntimeOnly;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        if (deployed) {
            return Arrays.asList(DEPLOYMENT_ATTRIBUTES);
        } else {
            return Arrays.asList(ATTRIBUTES);
        }
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registry) {
        ReloadRequiredWriteAttributeHandler handler = new ReloadRequiredWriteAttributeHandler(getAttributes());
        for (AttributeDefinition attr : getAttributes()) {
            if (deployed) {
                registry.registerReadOnlyAttribute(attr, JMSTopicConfigurationRuntimeHandler.INSTANCE);
            } else {
                if (attr == CommonAttributes.DESTINATION_ENTRIES ||
                        attr == CommonAttributes.LEGACY_ENTRIES) {
                    registry.registerReadWriteAttribute(attr, null, handler);
                } else {
                    registry.registerReadOnlyAttribute(attr, null);
                }
            }
        }

        for (AttributeDefinition attr : READONLY_ATTRIBUTES) {
            registry.registerReadOnlyAttribute(attr, JMSTopicReadAttributeHandler.INSTANCE);
        }

        for (AttributeDefinition metric : METRICS) {
            registry.registerMetric(metric, JMSTopicReadAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registry) {
        super.registerOperations(registry);

        if (registerRuntimeOnly) {
            JMSTopicControlHandler.INSTANCE.registerOperations(registry, getResourceDescriptionResolver());

            if (!deployed) {
                JMSTopicUpdateJndiHandler.registerOperations(registry, getResourceDescriptionResolver());
            }
        }
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return Arrays.asList(MessagingExtension.JMS_TOPIC_ACCESS_CONSTRAINT);
    }
}
