/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.webservices.dmr;

import static org.jboss.as.webservices.dmr.Constants.MODIFY_WSDL_ADDRESS;
import static org.jboss.as.webservices.dmr.Constants.STATISTICS_ENABLED;
import static org.jboss.as.webservices.dmr.Constants.WSDL_HOST;
import static org.jboss.as.webservices.dmr.Constants.WSDL_PORT;
import static org.jboss.as.webservices.dmr.Constants.WSDL_SECURE_PORT;
import static org.jboss.as.webservices.dmr.Constants.WSDL_PATH_REWRITE_RULE;
import static org.jboss.as.webservices.dmr.Constants.WSDL_URI_SCHEME;
import java.net.UnknownHostException;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.webservices.config.DisabledOperationException;
import org.jboss.as.webservices.config.ServerConfigFactoryImpl;
import org.jboss.as.webservices.config.ServerConfigImpl;
import org.jboss.dmr.ModelNode;

/**
 * An AbstractWriteAttributeHandler extension for updating basic WS server config attributes
 *
 * @author <a href="mailto:alessio.soldano@jboss.com">Alessio Soldano</a>
 * @author <a href="mailto:ema@redhat.com">Jim Ma</a>
 */
final class WSServerConfigAttributeHandler extends AbstractWriteAttributeHandler<WSServerConfigAttributeHandler.RollbackInfo> {

    public WSServerConfigAttributeHandler(final AttributeDefinition... definitions) {
        super(definitions);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<WSServerConfigAttributeHandler.RollbackInfo> handbackHolder)
            throws OperationFailedException {

        //if the server is booting or the required value is the current one,
        //we do not need to do anything and reload is not required
        if (isSameValue(context, resolvedValue, currentValue, attributeName) || context.isBooting()) {
            return false;
        }

        final String value = resolvedValue.isDefined() ? resolvedValue.asString() : null;
        boolean done = updateServerConfig(attributeName, value, false);
        handbackHolder.setHandback(new RollbackInfo(done));
        return !done; //reload required if runtime has not been updated
    }

    private boolean isSameValue(OperationContext context, ModelNode resolvedValue, ModelNode currentValue, String attributeName)
            throws OperationFailedException {
        if (resolvedValue.equals(getAttributeDefinition(attributeName).resolveValue(context, currentValue))) {
            return true;
        }
        if (!currentValue.isDefined()) {
            return resolvedValue.equals(getAttributeDefinition(attributeName).getDefaultValue());
        }
        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode valueToRestore, ModelNode valueToRevert, WSServerConfigAttributeHandler.RollbackInfo handback) throws OperationFailedException {
        if (handback != null && handback.isRuntimeUpdated()) { //nothing to do if the runtime was not updated
            final String value = valueToRestore.isDefined() ? valueToRestore.asString() : null;
            try {
                updateServerConfig(attributeName, value, true);
            } catch (DisabledOperationException e) { //revert rejected by WS stack
                throw new OperationFailedException(e);
            }
        }
    }

    /**
     * Returns true if the update operation succeeds in modifying the runtime, false otherwise.
     *
     * @param attributeName
     * @param value
     * @return
     * @throws OperationFailedException
     * @throws DisabledOperationException
     */
    private boolean updateServerConfig(String attributeName, String value, boolean isRevert) throws OperationFailedException, DisabledOperationException {
        final ServerConfigImpl config = (ServerConfigImpl) ServerConfigFactoryImpl.getConfig();
        try {
            if (MODIFY_WSDL_ADDRESS.equals(attributeName)) {
                final boolean modifyWSDLAddress = value != null && Boolean.parseBoolean(value);
                config.setModifySOAPAddress(modifyWSDLAddress, isRevert);
            } else if (WSDL_HOST.equals(attributeName)) {
                final String host = value != null ? value : null;
                try {
                    config.setWebServiceHost(host, isRevert);
                } catch (final UnknownHostException e) {
                    throw new OperationFailedException(e.getMessage(), e);
                }
            } else if (WSDL_PORT.equals(attributeName)) {
                final int port = value != null ? Integer.parseInt(value) : -1;
                config.setWebServicePort(port, isRevert);
            } else if (WSDL_SECURE_PORT.equals(attributeName)) {
                final int securePort = value != null ? Integer.parseInt(value) : -1;
                config.setWebServiceSecurePort(securePort, isRevert);
            } else if (WSDL_PATH_REWRITE_RULE.equals(attributeName)) {
                final String path = value != null ? value : null;
                config.setWebServicePathRewriteRule(path, isRevert);
            } else if (WSDL_URI_SCHEME.equals(attributeName)) {
                if (value == null || value.equals("http") || value.equals("https")) {
                    config.setWebServiceUriScheme(value, isRevert);
                } else {
                    throw new IllegalArgumentException(attributeName + " = " + value);
                }
            } else if (STATISTICS_ENABLED.equals(attributeName)) {
                final boolean enabled = value != null ? Boolean.parseBoolean(value) : false;
                config.setStatisticsEnabled(enabled);
            } else {
                throw new IllegalArgumentException(attributeName);
            }
        } catch (DisabledOperationException doe) {
            // the WS stack rejected the runtime update
            if (!isRevert) {
                return false;
            } else {
                throw doe;
            }
        }
        return true;
    }

    static class RollbackInfo {
        private final boolean runtimeUpdated;

        public RollbackInfo(boolean runtimeUpdated) {
            this.runtimeUpdated = runtimeUpdated;
        }

        public boolean isRuntimeUpdated() {
            return this.runtimeUpdated;
        }
    }
}
