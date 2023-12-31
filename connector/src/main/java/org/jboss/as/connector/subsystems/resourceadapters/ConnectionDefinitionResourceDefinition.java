/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.connector.subsystems.resourceadapters;

import static org.jboss.as.connector.subsystems.resourceadapters.Constants.CONNECTIONDEFINITIONS_NAME;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ENLISTMENT_TRACE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_CREDENTIAL_REFERENCE;

import org.jboss.as.connector.subsystems.common.pool.PoolConfigurationRWHandler;
import org.jboss.as.connector.subsystems.common.pool.PoolOperations;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.security.CredentialReferenceWriteAttributeHandler;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ConnectionDefinitionResourceDefinition extends SimpleResourceDefinition {

    static final PathElement PATH = PathElement.pathElement(CONNECTIONDEFINITIONS_NAME);
    private static final ResourceDescriptionResolver RESOLVER = ResourceAdaptersExtension.getResourceDescriptionResolver(CONNECTIONDEFINITIONS_NAME);
    private static final OperationDefinition FLUSH__IDLE_DEFINITION = new SimpleOperationDefinitionBuilder(Constants.FLUSH_IDLE_CONNECTION_IN_POOL, RESOLVER)
            .setRuntimeOnly()
            .build();
    private static final OperationDefinition FLUSH_ALL_DEFINITION = new SimpleOperationDefinitionBuilder(Constants.FLUSH_ALL_CONNECTION_IN_POOL, RESOLVER)
            .setRuntimeOnly()
            .build();
    private static final SimpleOperationDefinition DUMP_QUEUED_THREADS = new SimpleOperationDefinitionBuilder("dump-queued-threads-in-pool", RESOLVER)
            .setReadOnly()
            .setRuntimeOnly()
            .build();

    private static final OperationDefinition FLUSH_INVALID_DEFINITION = new SimpleOperationDefinitionBuilder(Constants.FLUSH_INVALID_CONNECTION_IN_POOL, RESOLVER)
                .setRuntimeOnly()
                .build();
    private static final OperationDefinition FLUSH_GRACEFULY_DEFINITION = new SimpleOperationDefinitionBuilder(Constants.FLUSH_GRACEFULLY_CONNECTION_IN_POOL, RESOLVER)
                .setRuntimeOnly()
                .build();
    private static final OperationDefinition TEST_DEFINITION = new SimpleOperationDefinitionBuilder(Constants.TEST_CONNECTION_IN_POOL, RESOLVER)
            .setRuntimeOnly()
            .build();

    private final boolean readOnly;
    private final boolean runtimeOnlyRegistrationValid;

    public ConnectionDefinitionResourceDefinition(final boolean readOnly, final boolean runtimeOnlyRegistrationValid) {
        super(PATH, ResourceAdaptersExtension.getResourceDescriptionResolver(CONNECTIONDEFINITIONS_NAME),
                readOnly ? null : ConnectionDefinitionAdd.INSTANCE, readOnly ? null : ReloadRequiredRemoveStepHandler.INSTANCE);
        this.readOnly = readOnly;
        this.runtimeOnlyRegistrationValid = runtimeOnlyRegistrationValid;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (final AttributeDefinition attribute : CommonAttributes.CONNECTION_DEFINITIONS_NODE_ATTRIBUTE) {
            if (readOnly) {
                resourceRegistration.registerReadOnlyAttribute(attribute, null);
            } else {
                if (PoolConfigurationRWHandler.ATTRIBUTES.contains(attribute.getName())) {
                    resourceRegistration.registerReadWriteAttribute(attribute, null, PoolConfigurationRWHandler.RaPoolConfigurationWriteHandler.INSTANCE);
                } else if (attribute.equals(ENLISTMENT_TRACE)) {
                    resourceRegistration.registerReadWriteAttribute(attribute, null, new EnlistmentTraceAttributeWriteHandler());
                } else if (attribute.equals(RECOVERY_CREDENTIAL_REFERENCE)){
                    resourceRegistration.registerReadWriteAttribute(attribute, null, new CredentialReferenceWriteAttributeHandler(attribute));
                } else {
                    resourceRegistration.registerReadWriteAttribute(attribute, null, new ReloadRequiredWriteAttributeHandler(attribute));
                }
            }
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        if (runtimeOnlyRegistrationValid) {
            resourceRegistration.registerOperationHandler(FLUSH__IDLE_DEFINITION, PoolOperations.FlushIdleConnectionInPool.RA_INSTANCE);
            resourceRegistration.registerOperationHandler(FLUSH_ALL_DEFINITION, PoolOperations.FlushAllConnectionInPool.RA_INSTANCE);
            resourceRegistration.registerOperationHandler(DUMP_QUEUED_THREADS, PoolOperations.DumpQueuedThreadInPool.RA_INSTANCE);
            resourceRegistration.registerOperationHandler(FLUSH_INVALID_DEFINITION, PoolOperations.FlushInvalidConnectionInPool.RA_INSTANCE);
            resourceRegistration.registerOperationHandler(FLUSH_GRACEFULY_DEFINITION, PoolOperations.FlushGracefullyConnectionInPool.RA_INSTANCE);
            resourceRegistration.registerOperationHandler(TEST_DEFINITION, PoolOperations.TestConnectionInPool.RA_INSTANCE);
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new ConfigPropertyResourceDefinition(readOnly ? null : CDConfigPropertyAdd.INSTANCE, readOnly ? null : ReloadRequiredRemoveStepHandler.INSTANCE));
    }
}
