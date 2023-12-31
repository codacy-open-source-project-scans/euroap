/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.iiop.openjdk;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.iiop.openjdk.logging.IIOPLogger;

import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
public class ConfigValidator {

    private ConfigValidator(){
    }

    public static List<String> validateConfig(final OperationContext context, final ModelNode resourceModel) throws OperationFailedException {
        final List<String> warnings = new LinkedList<>();

        validateSocketBindings(context, resourceModel);

        final boolean supportSSL = IIOPRootDefinition.SUPPORT_SSL.resolveModelAttribute(context, resourceModel).asBoolean();
        final boolean serverRequiresSsl = IIOPRootDefinition.SERVER_REQUIRES_SSL.resolveModelAttribute(context, resourceModel).asBoolean();
        final boolean clientRequiresSsl = IIOPRootDefinition.CLIENT_REQUIRES_SSL.resolveModelAttribute(context, resourceModel).asBoolean();

        final boolean sslConfigured = isSSLConfigured(context, resourceModel);

        validateSSLConfig(supportSSL, sslConfigured, serverRequiresSsl, clientRequiresSsl);
        validateSSLSocketBinding(context, resourceModel, sslConfigured, warnings);
        validateIORTransportConfig(context, resourceModel, supportSSL, serverRequiresSsl, warnings);
        validateORBInitializerConfig(context, resourceModel);

        return warnings;
    }

    private static void validateSocketBindings(final OperationContext context, final ModelNode resourceModel) throws OperationFailedException {
        final ModelNode socketBinding = IIOPRootDefinition.SOCKET_BINDING.resolveModelAttribute(context, resourceModel);
        final ModelNode sslSocketBinding = IIOPRootDefinition.SSL_SOCKET_BINDING.resolveModelAttribute(context, resourceModel);

        if(!socketBinding.isDefined() && !sslSocketBinding.isDefined()){
            throw IIOPLogger.ROOT_LOGGER.noSocketBindingsConfigured();
        }
    }

    private static boolean isSSLConfigured(final OperationContext context, final ModelNode resourceModel) throws OperationFailedException {
        final ModelNode securityDomainNode = IIOPRootDefinition.SECURITY_DOMAIN.resolveModelAttribute(context, resourceModel);
        final ModelNode serverSSLContextNode = IIOPRootDefinition.SERVER_SSL_CONTEXT.resolveModelAttribute(context, resourceModel);
        final ModelNode clientSSLContextNode = IIOPRootDefinition.CLIENT_SSL_CONTEXT.resolveModelAttribute(context, resourceModel);
        if (!securityDomainNode.isDefined() && (!serverSSLContextNode.isDefined() || !clientSSLContextNode.isDefined())){
            return false;
        } else {
            return true;
        }
    }

    private static void validateSSLConfig(final boolean supportSSL, final boolean sslConfigured,
                                          final boolean serverRequiresSsl, final boolean clientRequiresSsl) throws OperationFailedException {
        if (supportSSL) {
            if (!sslConfigured) {
                throw IIOPLogger.ROOT_LOGGER.noSecurityDomainOrSSLContextsSpecified();
            }
        } else if (serverRequiresSsl || clientRequiresSsl) {
            // if either the server or the client requires SSL, then SSL support must have been enabled.
            throw IIOPLogger.ROOT_LOGGER.sslNotConfigured();
        }
    }

    private static void validateSSLSocketBinding(final OperationContext context, final ModelNode resourceModel, final boolean sslConfigured, final List<String> warnings) throws OperationFailedException{
        ModelNode sslSocketBinding = IIOPRootDefinition.SSL_SOCKET_BINDING.resolveModelAttribute(context, resourceModel);
        if(sslSocketBinding.isDefined() && !sslConfigured){
            final String warning = IIOPLogger.ROOT_LOGGER.sslPortWithoutSslConfiguration();
            IIOPLogger.ROOT_LOGGER.warn(warning);
            warnings.add(warning);
        }
    }

    private static void validateIORTransportConfig(final OperationContext context, final ModelNode resourceModel, final boolean sslConfigured,
                                                   final boolean serverRequiresSsl, final List<String> warnings) throws OperationFailedException {
        validateSSLAttribute(context, resourceModel, sslConfigured, serverRequiresSsl, IIOPRootDefinition.INTEGRITY, warnings);
        validateSSLAttribute(context, resourceModel, sslConfigured, serverRequiresSsl, IIOPRootDefinition.CONFIDENTIALITY, warnings);
        validateSSLAttribute(context, resourceModel, sslConfigured, serverRequiresSsl, IIOPRootDefinition.TRUST_IN_CLIENT, warnings);
        validateTrustInTarget(context, resourceModel, sslConfigured, warnings);
        validateSupportedAttribute(context, resourceModel, IIOPRootDefinition.DETECT_MISORDERING, warnings);
        validateSupportedAttribute(context, resourceModel, IIOPRootDefinition.DETECT_REPLAY, warnings);
    }

    private static void validateSSLAttribute(final OperationContext context, final ModelNode resourceModel, final boolean sslConfigured, final boolean serverRequiresSsl, final AttributeDefinition attributeDefinition, final List<String> warnings) throws OperationFailedException {
        final ModelNode attributeNode = attributeDefinition.resolveModelAttribute(context, resourceModel);
        if(attributeNode.isDefined()){
            final String attribute = attributeNode.asString();
            if(sslConfigured) {
                if(attribute.equals(Constants.IOR_NONE)){
                    final String warning = IIOPLogger.ROOT_LOGGER.inconsistentSupportedTransportConfig(attributeDefinition.getName(), serverRequiresSsl ? Constants.IOR_REQUIRED : Constants.IOR_SUPPORTED);
                    IIOPLogger.ROOT_LOGGER.warn(warning);
                    warnings.add(warning);
                }
                if (serverRequiresSsl && attribute.equals(Constants.IOR_SUPPORTED)) {
                    final String warning = IIOPLogger.ROOT_LOGGER.inconsistentRequiredTransportConfig(Constants.SECURITY_SERVER_REQUIRES_SSL, attributeDefinition.getName());
                    IIOPLogger.ROOT_LOGGER.warn(warning);
                    warnings.add(warning);
                }
            } else {
                if(!attribute.equals(Constants.IOR_NONE)){
                    final String warning = IIOPLogger.ROOT_LOGGER.inconsistentUnsupportedTransportConfig(attributeDefinition.getName());
                    IIOPLogger.ROOT_LOGGER.warn(warning);
                    warnings.add(warning);
                }
            }
        }
    }

    private static void validateTrustInTarget(final OperationContext context, final ModelNode resourceModel, final boolean sslConfigured, final List<String> warnings) throws OperationFailedException {
        final ModelNode establishTrustInTargetNode = IIOPRootDefinition.TRUST_IN_TARGET.resolveModelAttribute(context, resourceModel);
        if(establishTrustInTargetNode.isDefined()){
            final String establishTrustInTarget = establishTrustInTargetNode.asString();
            if(sslConfigured && establishTrustInTarget.equals(Constants.IOR_NONE)){
                final String warning = IIOPLogger.ROOT_LOGGER.inconsistentSupportedTransportConfig(Constants.IOR_TRANSPORT_TRUST_IN_TARGET, Constants.IOR_SUPPORTED);
                IIOPLogger.ROOT_LOGGER.warn(warning);
                warnings.add(warning);
            }
        }
    }

    private static void validateSupportedAttribute(final OperationContext context, final ModelNode resourceModel, final AttributeDefinition attributeDefinition, final List<String> warnings) throws OperationFailedException{
        final ModelNode attributeNode = attributeDefinition.resolveModelAttribute(context, resourceModel);
        if(attributeNode.isDefined() && !attributeNode.asString().equals(Constants.IOR_SUPPORTED)) {
            final String warning = IIOPLogger.ROOT_LOGGER.inconsistentSupportedTransportConfig(attributeDefinition.getName(), Constants.IOR_SUPPORTED);
            IIOPLogger.ROOT_LOGGER.warn(warning);
            warnings.add(warning);
        }
    }

    private static void validateORBInitializerConfig(final OperationContext context, final ModelNode resourceModel) throws OperationFailedException {
        // validate the elytron initializer configuration: it requires an authentication-context name.
        final ModelNode securityInitializerNode = IIOPRootDefinition.SECURITY.resolveModelAttribute(context, resourceModel);
        final ModelNode authContextNode = IIOPRootDefinition.AUTHENTICATION_CONTEXT.resolveModelAttribute(context, resourceModel);
        if ((!securityInitializerNode.isDefined()
                || !securityInitializerNode.asString().equalsIgnoreCase(Constants.ELYTRON))
                && authContextNode.isDefined()) {
            // authentication-context has been specified but is ineffective because the security initializer is not set to
            // 'elytron'.
            throw IIOPLogger.ROOT_LOGGER.ineffectiveAuthenticationContextConfiguration();
        }
    }
}
