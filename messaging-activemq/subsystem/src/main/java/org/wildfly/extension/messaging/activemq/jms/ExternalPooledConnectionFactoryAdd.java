/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.messaging.activemq.jms;

import static org.wildfly.extension.messaging.activemq.CommonAttributes.JGROUPS_CLUSTER;
import static org.wildfly.extension.messaging.activemq.MessagingServices.isSubsystemResource;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttribute.getDefinitions;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_ALLOWLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_BLACKLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_BLOCKLIST;
import static org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common.DESERIALIZATION_WHITELIST;
import static org.wildfly.extension.messaging.activemq.jms.PooledConnectionFactoryAdd.getTxSupport;
import static org.wildfly.extension.messaging.activemq.jms.PooledConnectionFactoryAdd.installStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.deployment.ContextNames.BindInfo;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.messaging.activemq.CommonAttributes;
import org.wildfly.extension.messaging.activemq.DiscoveryGroupDefinition;
import org.wildfly.extension.messaging.activemq.MessagingServices;
import org.wildfly.extension.messaging.activemq.TransportConfigOperationHandlers;
import org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes.Common;

/**
 * Operation Handler to add a Jakarta Messaging external pooled Connection Factory.
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class ExternalPooledConnectionFactoryAdd extends AbstractAddStepHandler {

    public static final ExternalPooledConnectionFactoryAdd INSTANCE = new ExternalPooledConnectionFactoryAdd();

    private ExternalPooledConnectionFactoryAdd() {
        super(getDefinitions(ExternalPooledConnectionFactoryDefinition.ATTRIBUTES));
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
        String jgroupsChannelName = null;
        final PathAddress serverAddress = MessagingServices.getActiveMQServerPathAddress(address);
        if (discoveryGroupName != null) {
            Resource dgResource;
            if(isSubsystemResource(context)) {
                PathAddress dgAddress = address.getParent().append(CommonAttributes.SOCKET_DISCOVERY_GROUP, discoveryGroupName);
                try {
                    dgResource = context.readResourceFromRoot(dgAddress, false);
                } catch(Resource.NoSuchResourceException ex) {
                    dgAddress = address.getParent().append(CommonAttributes.JGROUPS_DISCOVERY_GROUP, discoveryGroupName);
                    dgResource = context.readResourceFromRoot(dgAddress, false);
                }
            } else {
                PathAddress dgAddress = serverAddress.append(CommonAttributes.SOCKET_DISCOVERY_GROUP, discoveryGroupName);
                try {
                    dgResource = context.readResourceFromRoot(dgAddress, false);
                } catch(Resource.NoSuchResourceException ex) {
                    dgAddress = address.getParent().append(CommonAttributes.JGROUPS_DISCOVERY_GROUP, discoveryGroupName);
                    dgResource = context.readResourceFromRoot(dgAddress, false);
                }
            }
            ModelNode dgModel = dgResource.getModel();
            ModelNode jgroupCluster = JGROUPS_CLUSTER.resolveModelAttribute(context, dgModel);
            if(jgroupCluster.isDefined()) {
                jgroupClusterName = jgroupCluster.asString();
                ModelNode channel = DiscoveryGroupDefinition.JGROUPS_CHANNEL.resolveModelAttribute(context, dgModel);
                if(channel.isDefined()) {
                    jgroupsChannelName = channel.asString();
                }
            }
        }

        List<PooledConnectionFactoryConfigProperties> adapterParams = PooledConnectionFactoryAdd.getAdapterParams(resolvedModel, context, ExternalPooledConnectionFactoryDefinition.ATTRIBUTES);
        DiscoveryGroupConfiguration discoveryGroupConfiguration = null;
        if (discoveryGroupName != null) {
            discoveryGroupConfiguration = ExternalConnectionFactoryAdd.getDiscoveryGroup(context, discoveryGroupName);
        }
        Set<String> connectorsSocketBindings = new HashSet<>();
        final Set<String> sslContextNames = new HashSet<>();
        TransportConfiguration[] transportConfigurations = TransportConfigOperationHandlers.processConnectors(context, connectors, connectorsSocketBindings, sslContextNames);
        ExternalPooledConnectionFactoryService.installService(context, name, transportConfigurations, discoveryGroupConfiguration, connectorsSocketBindings, sslContextNames,
                jgroupClusterName, jgroupsChannelName, adapterParams, bindInfo, jndiAliases, txSupport, minPoolSize, maxPoolSize, managedConnectionPoolClassName, enlistmentTrace, model);
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

    static String getDiscoveryGroup(final ModelNode model) {
        if(model.hasDefined(Common.DISCOVERY_GROUP.getName())) {
            return model.get(Common.DISCOVERY_GROUP.getName()).asString();
        }
        return null;
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
}
