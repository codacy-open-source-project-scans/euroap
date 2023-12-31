/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.messaging.activemq;

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.BYTES;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.MILLISECONDS;
import static org.jboss.dmr.ModelType.BOOLEAN;
import static org.jboss.dmr.ModelType.DOUBLE;
import static org.jboss.dmr.ModelType.INT;
import static org.jboss.dmr.ModelType.LONG;
import static org.jboss.dmr.ModelType.OBJECT;
import static org.jboss.dmr.ModelType.STRING;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.STATIC_CONNECTORS;

import java.util.Arrays;
import java.util.Collection;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;


/**
 * Cluster connection resource definition
 *
 * @author <a href="http://jmesnil.net">Jeff Mesnil</a> (c) 2012 Red Hat Inc.
 */
public class ClusterConnectionDefinition extends PersistentResourceDefinition {

    public static final String GET_NODES = "get-nodes";

    public static final SimpleAttributeDefinition ADDRESS = create("cluster-connection-address", STRING)
            .setXmlName(CommonAttributes.ADDRESS)
            .setDefaultValue(null)
            // empty string is allowed to route *any* address to the cluster
            .setValidator(new StringLengthValidator(0))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition ALLOW_DIRECT_CONNECTIONS_ONLY = create("allow-direct-connections-only", BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setAllowExpression(true)
            .setRequires(STATIC_CONNECTORS)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition CALL_FAILOVER_TIMEOUT = create(CommonAttributes.CALL_FAILOVER_TIMEOUT)
            // cluster connection will wait forever during failover for a non-blocking call
            .setDefaultValue(new ModelNode(-1L))
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterFailureCheckPeriod
     */
    public static final SimpleAttributeDefinition CHECK_PERIOD = create("check-period", LONG)
            .setDefaultValue(new ModelNode(30000L))
            .setRequired(false)
            .setAllowExpression(true)
            .setMeasurementUnit(MILLISECONDS)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterConnectionTtl
     */
    public static final SimpleAttributeDefinition CONNECTION_TTL = create("connection-ttl", LONG)
            .setDefaultValue(new ModelNode(60000L))
            .setMeasurementUnit(MILLISECONDS)
            .setRequired(false)
            .setAllowExpression(true)
            .setValidator(InfiniteOrPositiveValidators.LONG_INSTANCE)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition CONNECTOR_NAME = create("connector-name", STRING)
            .setRestartAllServices()
            .build();

    public static final PrimitiveListAttributeDefinition CONNECTOR_REFS = new StringListAttributeDefinition.Builder(STATIC_CONNECTORS)
            .setRequired(true)
            .setElementValidator(new StringLengthValidator(1))
            .setAttributeParser(AttributeParser.STRING_LIST)
            .setAttributeMarshaller(AttributeMarshaller.STRING_LIST)
            .setAlternatives(CommonAttributes.DISCOVERY_GROUP)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition DISCOVERY_GROUP_NAME = create(CommonAttributes.DISCOVERY_GROUP, STRING)
            .setRequired(true)
            .setAlternatives(CONNECTOR_REFS.getName())
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterMessageLoadBalancingType
     */
    // FIXME WFLY-4587 forward-when-no-consumers == true ? STRICT : ON_DEMAND
    public static final SimpleAttributeDefinition MESSAGE_LOAD_BALANCING_TYPE = create("message-load-balancing-type", STRING)
            .setDefaultValue(new ModelNode(MessageLoadBalancingType.ON_DEMAND.toString()))
            .setValidator(EnumValidator.create(MessageLoadBalancingType.class))
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterInitialConnectAttempts
     */
    public static final SimpleAttributeDefinition INITIAL_CONNECT_ATTEMPTS = create("initial-connect-attempts", INT)
            .setDefaultValue(new ModelNode(-1))
            .setRequired(false)
            .setAllowExpression(true)
            .setCorrector(InfiniteOrPositiveValidators.NEGATIVE_VALUE_CORRECTOR)
            .setValidator(InfiniteOrPositiveValidators.INT_INSTANCE)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterMaxHops
     */
    public static final SimpleAttributeDefinition MAX_HOPS = create("max-hops", INT)
            .setDefaultValue(new ModelNode(1))
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterMaxRetryInterval
     */
    public static final SimpleAttributeDefinition MAX_RETRY_INTERVAL = create("max-retry-interval", LONG)
            .setDefaultValue(new ModelNode(2000L))
            .setRequired(false)
            .setAllowExpression(true)
            .setMeasurementUnit(MILLISECONDS)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterNotificationAttempts
     */
    public static final SimpleAttributeDefinition NOTIFICATION_ATTEMPTS = create("notification-attempts",INT)
            .setDefaultValue(new ModelNode(2))
            .setMeasurementUnit(MILLISECONDS)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterNotificationInterval
     */
    public static final SimpleAttributeDefinition NOTIFICATION_INTERVAL = create("notification-interval",LONG)
            .setDefaultValue(new ModelNode(1000L))
            .setMeasurementUnit(MILLISECONDS)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultBridgeProducerWindowSize
     */
    public static final SimpleAttributeDefinition PRODUCER_WINDOW_SIZE = create("producer-window-size", INT)
            .setDefaultValue(new ModelNode(-1))
            .setMeasurementUnit(BYTES)
            .setRequired(false)
            .setAllowExpression(true)
            .setCorrector(InfiniteOrPositiveValidators.NEGATIVE_VALUE_CORRECTOR)
            .setValidator(InfiniteOrPositiveValidators.INT_INSTANCE)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterRetryInterval
     */
    public static final SimpleAttributeDefinition RETRY_INTERVAL = create("retry-interval", LONG)
            .setDefaultValue(new ModelNode(500L))
            .setMeasurementUnit(MILLISECONDS)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterReconnectAttempts
     */
    public static final SimpleAttributeDefinition RECONNECT_ATTEMPTS = create("reconnect-attempts", INT)
            .setDefaultValue(new ModelNode(-1))
            .setRequired(false)
            .setAllowExpression(true)
            .setCorrector(InfiniteOrPositiveValidators.NEGATIVE_VALUE_CORRECTOR)
            .setValidator(InfiniteOrPositiveValidators.INT_INSTANCE)
            .setRestartAllServices()
            .build();

    /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterRetryIntervalMultiplier
     */
    public static final SimpleAttributeDefinition RETRY_INTERVAL_MULTIPLIER = create("retry-interval-multiplier", DOUBLE)
            .setDefaultValue(new ModelNode(1.0d))
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition USE_DUPLICATE_DETECTION = create("use-duplicate-detection", BOOLEAN)
            .setDefaultValue(ModelNode.TRUE)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final AttributeDefinition[] ATTRIBUTES = {
            ADDRESS, CONNECTOR_NAME,
            CHECK_PERIOD,
            CONNECTION_TTL,
            CommonAttributes.MIN_LARGE_MESSAGE_SIZE,
            CommonAttributes.CALL_TIMEOUT,
            CALL_FAILOVER_TIMEOUT,
            RETRY_INTERVAL, RETRY_INTERVAL_MULTIPLIER, MAX_RETRY_INTERVAL,
            INITIAL_CONNECT_ATTEMPTS,
            RECONNECT_ATTEMPTS, USE_DUPLICATE_DETECTION,
            MESSAGE_LOAD_BALANCING_TYPE, MAX_HOPS,
            CommonAttributes.BRIDGE_CONFIRMATION_WINDOW_SIZE,
            PRODUCER_WINDOW_SIZE,
            NOTIFICATION_ATTEMPTS,
            NOTIFICATION_INTERVAL,
            CONNECTOR_REFS,
            ALLOW_DIRECT_CONNECTIONS_ONLY,
            DISCOVERY_GROUP_NAME,
    };

    public static final SimpleAttributeDefinition NODE_ID = create("node-id", STRING)
            .setStorageRuntime()
            .build();

    public static final SimpleAttributeDefinition TOPOLOGY = create("topology", STRING)
            .setStorageRuntime()
            .build();

    public static final AttributeDefinition[] READONLY_ATTRIBUTES = {
            TOPOLOGY,
            NODE_ID
    };

    private final boolean registerRuntimeOnly;

    ClusterConnectionDefinition(boolean registerRuntimeOnly) {
        super(MessagingExtension.CLUSTER_CONNECTION_PATH,
                MessagingExtension.getResourceDescriptionResolver(CommonAttributes.CLUSTER_CONNECTION),
                ClusterConnectionAdd.INSTANCE,
                ClusterConnectionRemove.INSTANCE);
        this.registerRuntimeOnly = registerRuntimeOnly;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registry) {
        ReloadRequiredWriteAttributeHandler reloadRequiredWriteAttributeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            if (!attr.getFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME)) {
                registry.registerReadWriteAttribute(attr, null, reloadRequiredWriteAttributeHandler);
            }
        }

        ClusterConnectionControlHandler.INSTANCE.registerAttributes(registry);

        for (AttributeDefinition attr : READONLY_ATTRIBUTES) {
            registry.registerReadOnlyAttribute(attr, ClusterConnectionControlHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registry) {

        super.registerOperations(registry);

        if (registerRuntimeOnly) {
            ClusterConnectionControlHandler.INSTANCE.registerOperations(registry, getResourceDescriptionResolver());

            SimpleOperationDefinition getNodesDef = new SimpleOperationDefinitionBuilder(ClusterConnectionDefinition.GET_NODES, getResourceDescriptionResolver())
                    .setReadOnly()
                    .setRuntimeOnly()
                    .setReplyType(OBJECT)
                    .setReplyValueType(STRING)
                    .build();
            registry.registerOperationHandler(getNodesDef, ClusterConnectionControlHandler.INSTANCE);
        }
    }

    private enum MessageLoadBalancingType {
        OFF, STRICT, ON_DEMAND;
    }

}
