/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.messaging.activemq.jms;

import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.JGROUPS_CLUSTER;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.LOCAL;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.LOCAL_TX;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.NONE;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.NO_TX;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.XA_TX;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttribute.getDefinitions;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_ALLOWLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_BLACKLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_BLOCKLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_WHITELIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Pooled.CREDENTIAL_REFERENCE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.activemq.artemis.api.core.TransportConfiguration;

import org.jboss.as.connector.metadata.deployment.ResourceAdapterDeployment;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.deployment.ContextNames.BindInfo;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.extension.messaging.activemq.CommonAttributes;
import org.wildfly.extension.messaging.activemq.MessagingServices;
import org.wildfly.extension.messaging.activemq.TransportConfigOperationHandlers;
import org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common;

/**
 * @author <a href="mailto:andy.taylor@jboss.com">Andy Taylor</a>
 *         Date: 5/13/11
 *         Time: 1:42 PM
 */
public class PooledConnectionFactoryAdd extends AbstractAddStepHandler {

    public static final PooledConnectionFactoryAdd INSTANCE = new PooledConnectionFactoryAdd();

    private PooledConnectionFactoryAdd() {
        super(getDefinitions(PooledConnectionFactoryDefinition.ATTRIBUTES));
    }

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
        super.populateModel(context, operation, resource);
        handleCredentialReferenceUpdate(context, resource.getModel());
    }

    @Override
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attr : attributes) {
            if (DESERIALIZATION_BLACKLIST.equals(attr)) {
                if (operation.hasDefined(DESERIALIZATION_BLACKLIST.getName())) {
                    DESERIALIZATION_BLOCKLIST.validateAndSet(operation, model);
                }
            } else if (DESERIALIZATION_WHITELIST.equals(attr)) {
                if (operation.hasDefined(DESERIALIZATION_WHITELIST.getName())) {
                    DESERIALIZATION_ALLOWLIST.validateAndSet(operation, model);
                }
            } else {
                attr.validateAndSet(operation, model);
            }
        }
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

        ModelNode model = resource.getModel();
        PathAddress address = context.getCurrentAddress();
        final String name = context.getCurrentAddressValue();

        final ModelNode resolvedModel = model.clone();
        for(final AttributeDefinition attribute : attributes) {
            resolvedModel.get(attribute.getName()).set(attribute.resolveModelAttribute(context, resolvedModel ));
        }

        // We validated that jndiName part of the model in populateModel
        final List<String> jndiNames = new ArrayList<>();
        for (ModelNode node : resolvedModel.get(Common.ENTRIES.getName()).asList()) {
            jndiNames.add(node.asString());
        }
        final BindInfo bindInfo = ContextNames.bindInfoFor(jndiNames.get(0));
        List<String> jndiAliases;
        if(jndiNames.size() > 1) {
            jndiAliases = new ArrayList<>(jndiNames.subList(1, jndiNames.size()));
        } else {
            jndiAliases = Collections.emptyList();
        }
        String managedConnectionPoolClassName = resolvedModel.get(ConnectionFactoryAttributes.Pooled.MANAGED_CONNECTION_POOL.getName()).asStringOrNull();
        final int minPoolSize = resolvedModel.get(ConnectionFactoryAttributes.Pooled.MIN_POOL_SIZE.getName()).asInt();
        final int maxPoolSize = resolvedModel.get(ConnectionFactoryAttributes.Pooled.MAX_POOL_SIZE.getName()).asInt();
        Boolean enlistmentTrace = resolvedModel.get(ConnectionFactoryAttributes.Pooled.ENLISTMENT_TRACE.getName()).asBooleanOrNull();

        String txSupport = getTxSupport(resolvedModel);

        List<String> connectors = Common.CONNECTORS.unwrap(context, model);
        String discoveryGroupName = getDiscoveryGroup(resolvedModel);
        String jgroupClusterName = null;
        final PathAddress serverAddress = MessagingServices.getActiveMQServerPathAddress(address);
        if (discoveryGroupName != null) {
            Resource dgResource;
            try {
                dgResource = context.readResourceFromRoot(serverAddress.append(CommonAttributes.SOCKET_DISCOVERY_GROUP, discoveryGroupName), false);
            } catch (Resource.NoSuchResourceException ex) {
                dgResource = context.readResourceFromRoot(serverAddress.append(CommonAttributes.JGROUPS_DISCOVERY_GROUP, discoveryGroupName), false);
            }
            ModelNode dgModel = dgResource.getModel();
            ModelNode jgroupCluster = JGROUPS_CLUSTER.resolveModelAttribute(context, dgModel);
            if(jgroupCluster.isDefined()) {
                jgroupClusterName = jgroupCluster.asString();
            }
        }

        List<PooledConnectionFactoryConfigProperties> adapterParams = getAdapterParams(resolvedModel, context, PooledConnectionFactoryDefinition.ATTRIBUTES);
        final Set<String> connectorsSocketBindings = new HashSet<>();
        final Set<String> sslContextNames = new HashSet<>();
        TransportConfiguration[] transportConfigurations = TransportConfigOperationHandlers.processConnectors(context, connectors, connectorsSocketBindings, sslContextNames);
        String serverName = serverAddress.getLastElement().getValue();
        PooledConnectionFactoryService.installService(context,
                name, serverName, connectors, discoveryGroupName, jgroupClusterName,
                adapterParams, bindInfo, jndiAliases, txSupport, minPoolSize, maxPoolSize, managedConnectionPoolClassName, enlistmentTrace, model);
        boolean statsEnabled = ConnectionFactoryAttributes.Pooled.STATISTICS_ENABLED.resolveModelAttribute(context, model).asBoolean();

        if (statsEnabled) {
            // Add the stats resource. This is kind of a hack as we are modifying the resource
            // in runtime, but oh well. We don't use readResourceForUpdate for this reason.
            // This only runs in this add op anyway, and because it's an add we know readResource
            // is going to be returning the current write snapshot of the model, i.e. the one we want
            PooledConnectionFactoryStatisticsService.registerStatisticsResources(resource);

            installStatistics(context, name);
        }
    }

    @Override
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
        rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
    }

    static String getTxSupport(final ModelNode resolvedModel) {
        String txType = resolvedModel.get(ConnectionFactoryAttributes.Pooled.TRANSACTION.getName()).asStringOrNull();
        switch (txType) {
            case LOCAL:
                return LOCAL_TX;
            case NONE:
                return NO_TX;
            default:
                return XA_TX;
        }
    }

    static String getDiscoveryGroup(final ModelNode model) {
        if(model.hasDefined(Common.DISCOVERY_GROUP.getName())) {
            return model.get(Common.DISCOVERY_GROUP.getName()).asString();
        }
        return null;
    }

    static List<PooledConnectionFactoryConfigProperties> getAdapterParams(ModelNode model, OperationContext context, ConnectionFactoryAttribute[] attributes) throws OperationFailedException {
        List<PooledConnectionFactoryConfigProperties> configs = new ArrayList<PooledConnectionFactoryConfigProperties>();
        for (ConnectionFactoryAttribute nodeAttribute : attributes)
        {
            if (!nodeAttribute.isResourceAdapterProperty())
                continue;

            AttributeDefinition definition = nodeAttribute.getDefinition();
            ModelNode node = definition.resolveModelAttribute(context, model);
            if (node.isDefined()) {
                String attributeName = definition.getName();
                final String value;
                if (attributeName.equals(Common.DESERIALIZATION_BLOCKLIST.getName())) {
                    value = String.join(",", Common.DESERIALIZATION_BLOCKLIST.unwrap(context, model));
                } else if (attributeName.equals(Common.DESERIALIZATION_ALLOWLIST.getName())) {
                    value = String.join(",", Common.DESERIALIZATION_ALLOWLIST.unwrap(context, model));
                } else {
                    value = node.asString();
                }
                configs.add(new PooledConnectionFactoryConfigProperties(nodeAttribute.getPropertyName(), value, nodeAttribute.getClassType(), nodeAttribute.getConfigType()));
            }
        }
        return configs;
    }

    static void installStatistics(OperationContext context, String name) {
        ServiceName raActivatorsServiceName = PooledConnectionFactoryService.getResourceAdapterActivatorsServiceName(name);
        PooledConnectionFactoryStatisticsService statsService = new PooledConnectionFactoryStatisticsService(context.getResourceRegistrationForUpdate(), true);
        context.getServiceTarget().addService(raActivatorsServiceName.append("statistics"), statsService)
                .addDependency(raActivatorsServiceName, ResourceAdapterDeployment.class, statsService.getRADeploymentInjector())
                .setInitialMode(ServiceController.Mode.PASSIVE)
                .install();
    }
}
