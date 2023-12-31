/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.messaging.activemq;

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.BYTES;
import static org.jboss.as.controller.client.helpers.MeasurementUnit.MILLISECONDS;
import static org.jboss.dmr.ModelType.BOOLEAN;
import static org.jboss.dmr.ModelType.INT;
import static org.jboss.dmr.ModelType.LONG;
import static org.jboss.dmr.ModelType.STRING;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.MESSAGING_SECURITY_SENSITIVE_TARGET;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.STATIC_CONNECTORS;

import java.util.Arrays;
import java.util.Collection;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.CaseParameterCorrector;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.security.CredentialReferenceWriteAttributeHandler;
import org.jboss.dmr.ModelNode;

/**
 * Bridge resource definition
 *
 * @author <a href="http://jmesnil.net">Jeff Mesnil</a> (c) 2012 Red Hat Inc.
 */
public class BridgeDefinition extends PersistentResourceDefinition {

    public static final PrimitiveListAttributeDefinition CONNECTOR_REFS = new StringListAttributeDefinition.Builder(CommonAttributes.STATIC_CONNECTORS)
            .setRequired(true)
            .setElementValidator(new StringLengthValidator(1))
            .setAttributeParser(AttributeParser.STRING_LIST)
            .setAttributeMarshaller(AttributeMarshaller.STRING_LIST)
            .setAllowExpression(false)
            .setAlternatives(CommonAttributes.DISCOVERY_GROUP)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition DISCOVERY_GROUP_NAME = create(CommonAttributes.DISCOVERY_GROUP, STRING)
            .setRequired(true)
            .setAlternatives(STATIC_CONNECTORS)
            .setRestartAllServices()
            .build();

     /**
     * @see ActiveMQDefaultConfiguration#getDefaultBridgeInitialConnectAttempts
     */
    public static final SimpleAttributeDefinition INITIAL_CONNECT_ATTEMPTS = create("initial-connect-attempts", INT)
            .setRequired(false)
            .setDefaultValue(new ModelNode().set(-1))
            .setAllowExpression(true)
            .setCorrector(InfiniteOrPositiveValidators.NEGATIVE_VALUE_CORRECTOR)
            .setValidator(InfiniteOrPositiveValidators.INT_INSTANCE)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition QUEUE_NAME = create(CommonAttributes.QUEUE_NAME, STRING)
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
     * @see ActiveMQDefaultConfiguration#getDefaultClusterPassword
     */
    public static final SimpleAttributeDefinition PASSWORD = create("password", STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("CHANGE ME!!"))
            .setRestartAllServices()
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .addAccessConstraint(MESSAGING_SECURITY_SENSITIVE_TARGET)
            .setAlternatives(CredentialReference.CREDENTIAL_REFERENCE)
            .build();

     /**
     * @see ActiveMQDefaultConfiguration#getDefaultClusterUser
     */
    public static final SimpleAttributeDefinition USER = create("user", STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("ACTIVEMQ.CLUSTER.ADMIN.USER"))
            .setRestartAllServices()
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .addAccessConstraint(MESSAGING_SECURITY_SENSITIVE_TARGET)
            .build();

    public static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE =
            CredentialReference.getAttributeBuilder(true, false)
                    .setRestartAllServices()
                    .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
                    .addAccessConstraint(MESSAGING_SECURITY_SENSITIVE_TARGET)
                    .setAlternatives(PASSWORD.getName())
                    .build();

     /**
     * @see ActiveMQDefaultConfiguration#isDefaultBridgeDuplicateDetection
     */
    public static final SimpleAttributeDefinition USE_DUPLICATE_DETECTION = create("use-duplicate-detection", BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

     /**
     * @see ActiveMQDefaultConfiguration#getDefaultBridgeReconnectAttempts
     */
    public static final SimpleAttributeDefinition RECONNECT_ATTEMPTS = create("reconnect-attempts", INT)
            .setRequired(false)
            .setDefaultValue(new ModelNode(-1))
            .setAllowExpression(true)
            .setCorrector(InfiniteOrPositiveValidators.NEGATIVE_VALUE_CORRECTOR)
            .setValidator(InfiniteOrPositiveValidators.INT_INSTANCE)
            .setRestartAllServices()
            .build();

     /**
     * @see ActiveMQDefaultConfiguration#getDefaultBridgeConnectSameNode
     */
    public static final SimpleAttributeDefinition RECONNECT_ATTEMPTS_ON_SAME_NODE = create("reconnect-attempts-on-same-node", INT)
            .setRequired(false)
            .setDefaultValue(new ModelNode(10))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition FORWARDING_ADDRESS = create("forwarding-address", STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    /**
     * @see {@link org.apache.activemq.artemis.api.core.client.ActiveMQClient#DEFAULT_CALL_TIMEOUT}
     */
    public static final SimpleAttributeDefinition CALL_TIMEOUT = create("call-timeout", LONG)
            .setDefaultValue(new ModelNode(30000L))
            .setMeasurementUnit(MILLISECONDS)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition ROUTING_TYPE = create("routing-type", STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode("PASS"))
            .setAllowExpression(true)
            .setRestartAllServices()
            .setCorrector(CaseParameterCorrector.TO_UPPER)
            .setValidator(EnumValidator.create(RoutingType.class, RoutingType.values()))
            .build();

    public static final AttributeDefinition[] ATTRIBUTES = {
            QUEUE_NAME, FORWARDING_ADDRESS, CommonAttributes.HA,
            CommonAttributes.FILTER, CommonAttributes.TRANSFORMER_CLASS_NAME,
            CommonAttributes.MIN_LARGE_MESSAGE_SIZE, CommonAttributes.CHECK_PERIOD, CommonAttributes.CONNECTION_TTL,
            CommonAttributes.RETRY_INTERVAL, CommonAttributes.RETRY_INTERVAL_MULTIPLIER, CommonAttributes.MAX_RETRY_INTERVAL,
            INITIAL_CONNECT_ATTEMPTS,
            RECONNECT_ATTEMPTS,
            RECONNECT_ATTEMPTS_ON_SAME_NODE,
            USE_DUPLICATE_DETECTION,
            PRODUCER_WINDOW_SIZE,
            CommonAttributes.BRIDGE_CONFIRMATION_WINDOW_SIZE,
            USER, PASSWORD, CREDENTIAL_REFERENCE,
            CONNECTOR_REFS, DISCOVERY_GROUP_NAME,
            CALL_TIMEOUT, ROUTING_TYPE
    };

    private final boolean registerRuntimeOnly;

    BridgeDefinition(boolean registerRuntimeOnly) {
        super(MessagingExtension.BRIDGE_PATH,
                MessagingExtension.getResourceDescriptionResolver(CommonAttributes.BRIDGE),
                BridgeAdd.INSTANCE,
                BridgeRemove.INSTANCE);
        this.registerRuntimeOnly = registerRuntimeOnly;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registry) {
        ReloadRequiredWriteAttributeHandler reloadRequiredWriteAttributeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        CredentialReferenceWriteAttributeHandler credentialReferenceWriteAttributeHandler = new CredentialReferenceWriteAttributeHandler(CREDENTIAL_REFERENCE);
        for (AttributeDefinition attr : ATTRIBUTES) {
            if (!attr.getFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME)) {
                if (attr.equals(CREDENTIAL_REFERENCE)) {
                    registry.registerReadWriteAttribute(attr, null, credentialReferenceWriteAttributeHandler);
                } else {
                    registry.registerReadWriteAttribute(attr, null, reloadRequiredWriteAttributeHandler);
                }
            }
        }

        BridgeControlHandler.INSTANCE.registerAttributes(registry);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registry) {
        super.registerOperations(registry);
        if (registerRuntimeOnly) {
            BridgeControlHandler.INSTANCE.registerOperations(registry, getResourceDescriptionResolver());
        }
    }

   private enum RoutingType {
       STRIP, PASS;
    }
}
