/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.jca;

import static org.jboss.as.connector.logging.ConnectorLogger.ROOT_LOGGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.connector.services.workmanager.NamedDistributedWorkManager;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.connector.util.Injection;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.jca.core.api.connectionmanager.ccm.CachedConnectionManager;
import org.jboss.jca.core.workmanager.policy.Always;
import org.jboss.jca.core.workmanager.policy.Never;
import org.jboss.jca.core.workmanager.policy.WaterMark;
import org.jboss.jca.core.workmanager.selector.FirstAvailable;
import org.jboss.jca.core.workmanager.selector.MaxFreeThreads;
import org.jboss.jca.core.workmanager.selector.PingTime;


public class JcaDistributedWorkManagerWriteHandler extends AbstractWriteAttributeHandler<JcaSubsystemConfiguration> {

    static JcaDistributedWorkManagerWriteHandler INSTANCE = new JcaDistributedWorkManagerWriteHandler();

    private JcaDistributedWorkManagerWriteHandler() {
        super(JcaDistributedWorkManagerDefinition.DWmParameters.getAttributeDefinitions());
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<JcaSubsystemConfiguration> jcaSubsystemConfigurationHandbackHolder) throws OperationFailedException {
        final ModelNode address = operation.require(OP_ADDR);
        final String name = PathAddress.pathAddress(address).getLastElement().getValue();


        Object wm =  context.getServiceRegistry(true).getService(ConnectorServices.WORKMANAGER_SERVICE.append(name)).getValue();

        if (wm == null || ! (wm instanceof NamedDistributedWorkManager)) {
            throw ConnectorLogger.ROOT_LOGGER.failedToFindDistributedWorkManager(name);
        }

        NamedDistributedWorkManager namedDistributedWorkManager = (NamedDistributedWorkManager) wm;

        Injection injector = new Injection();

        if (attributeName.equals(JcaDistributedWorkManagerDefinition.DWmParameters.POLICY.getAttribute().getName())) {
            switch (JcaDistributedWorkManagerDefinition.PolicyValue.valueOf(resolvedValue.asString())) {
                case NEVER: {
                    namedDistributedWorkManager.setPolicy(new Never());
                    break;
                }
                case ALWAYS: {
                    namedDistributedWorkManager.setPolicy(new Always());
                    break;
                }
                case WATERMARK: {
                    namedDistributedWorkManager.setPolicy(new WaterMark());
                    break;
                }
                default: {
                    throw ROOT_LOGGER.unsupportedPolicy(resolvedValue.asString());
                }
            }
        } else if (attributeName.equals(JcaDistributedWorkManagerDefinition.DWmParameters.SELECTOR.getAttribute().getName())) {
            switch (JcaDistributedWorkManagerDefinition.SelectorValue.valueOf(resolvedValue.asString())) {
                case FIRST_AVAILABLE: {
                    namedDistributedWorkManager.setSelector(new FirstAvailable());
                    break;
                }
                case MAX_FREE_THREADS: {
                    namedDistributedWorkManager.setSelector(new MaxFreeThreads());
                    break;
                }
                case PING_TIME: {
                    namedDistributedWorkManager.setSelector(new PingTime());
                    break;
                }
                default: {
                    throw ROOT_LOGGER.unsupportedSelector(resolvedValue.asString());
                }
            }
        } else if (attributeName.equals(JcaDistributedWorkManagerDefinition.DWmParameters.POLICY_OPTIONS.getAttribute().getName()) && namedDistributedWorkManager.getPolicy() != null) {
            for (Property property : resolvedValue.asPropertyList()) {
                try {
                    injector.inject(namedDistributedWorkManager.getPolicy(), property.getName(), property.getValue().asString());
                } catch (Exception e) {
                    ROOT_LOGGER.unsupportedPolicyOption(property.getName());

                }

            }
        } else if (attributeName.equals(JcaDistributedWorkManagerDefinition.DWmParameters.SELECTOR_OPTIONS.getAttribute().getName())) {
            for (Property property : resolvedValue.asPropertyList()) {
                try {
                    injector.inject(namedDistributedWorkManager.getSelector(), property.getName(), property.getValue().asString());
                } catch (Exception e) {
                    ROOT_LOGGER.unsupportedSelectorOption(property.getName());
                }

            }
        }


        return false;

    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, JcaSubsystemConfiguration handback) throws OperationFailedException {

        CachedConnectionManager ccm = (CachedConnectionManager) context.getServiceRegistry(true).getService(ConnectorServices.CCM_SERVICE).getValue();

        if (attributeName.equals(JcaCachedConnectionManagerDefinition.CcmParameters.DEBUG.getAttribute().getName())) {
            ccm.setDebug(valueToRestore.asBoolean());
        } else if (attributeName.equals(JcaCachedConnectionManagerDefinition.CcmParameters.ERROR.getAttribute().getName())) {
            ccm.setError(valueToRestore.asBoolean());
        } else if (attributeName.equals(JcaCachedConnectionManagerDefinition.CcmParameters.IGNORE_UNKNOWN_CONNECTIONS.getAttribute().getName())) {
            ccm.setIgnoreUnknownConnections(valueToRestore.asBoolean());
        }

    }
}
