/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.keycloak.subsystem.adapter.extension;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines attributes that can be present in both a realm and an application (secure-deployment).
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2013 Red Hat Inc.
 */
public class SharedAttributeDefinitons {

    protected static final SimpleAttributeDefinition REALM_PUBLIC_KEY =
            new SimpleAttributeDefinitionBuilder("realm-public-key", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();
    protected static final SimpleAttributeDefinition AUTH_SERVER_URL =
            new SimpleAttributeDefinitionBuilder("auth-server-url", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();
    protected static final SimpleAttributeDefinition SSL_REQUIRED =
            new SimpleAttributeDefinitionBuilder("ssl-required", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode("external"))
                    .build();
    protected static final SimpleAttributeDefinition ALLOW_ANY_HOSTNAME =
            new SimpleAttributeDefinitionBuilder("allow-any-hostname", ModelType.BOOLEAN, true)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    protected static final SimpleAttributeDefinition DISABLE_TRUST_MANAGER =
            new SimpleAttributeDefinitionBuilder("disable-trust-manager", ModelType.BOOLEAN, true)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    protected static final SimpleAttributeDefinition TRUSTSTORE =
            new SimpleAttributeDefinitionBuilder("truststore", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();
    protected static final SimpleAttributeDefinition TRUSTSTORE_PASSWORD =
            new SimpleAttributeDefinitionBuilder("truststore-password", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();
    protected static final SimpleAttributeDefinition CONNECTION_POOL_SIZE =
            new SimpleAttributeDefinitionBuilder("connection-pool-size", ModelType.INT, true)
                    .setAllowExpression(true)
                    .setValidator(new IntRangeValidator(0, true))
                    .build();
    protected static final SimpleAttributeDefinition SOCKET_TIMEOUT =
            new SimpleAttributeDefinitionBuilder("socket-timeout-millis", ModelType.LONG, true)
                    .setAllowExpression(true)
                    .setValidator(new LongRangeValidator(-1L, true))
                    .build();
    protected static final SimpleAttributeDefinition CONNECTION_TTL =
            new SimpleAttributeDefinitionBuilder("connection-ttl-millis", ModelType.LONG, true)
                    .setAllowExpression(true)
                    .setValidator(new LongRangeValidator(-1L, true))
                    .build();
    protected static final SimpleAttributeDefinition CONNECTION_TIMEOUT =
            new SimpleAttributeDefinitionBuilder("connection-timeout-millis", ModelType.LONG, true)
                    .setAllowExpression(true)
                    .setValidator(new LongRangeValidator(-1L, true))
                    .build();

    protected static final SimpleAttributeDefinition ENABLE_CORS =
            new SimpleAttributeDefinitionBuilder("enable-cors", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();
    protected static final SimpleAttributeDefinition CLIENT_KEYSTORE =
            new SimpleAttributeDefinitionBuilder("client-keystore", ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .build();
    protected static final SimpleAttributeDefinition CLIENT_KEYSTORE_PASSWORD =
            new SimpleAttributeDefinitionBuilder("client-keystore-password", ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .build();
    protected static final SimpleAttributeDefinition CLIENT_KEY_PASSWORD =
            new SimpleAttributeDefinitionBuilder("client-key-password", ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .build();
    protected static final SimpleAttributeDefinition CORS_MAX_AGE =
            new SimpleAttributeDefinitionBuilder("cors-max-age", ModelType.INT, true)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(-1, true))
            .build();
    protected static final SimpleAttributeDefinition CORS_ALLOWED_HEADERS =
            new SimpleAttributeDefinitionBuilder("cors-allowed-headers", ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .build();
    protected static final SimpleAttributeDefinition CORS_ALLOWED_METHODS =
            new SimpleAttributeDefinitionBuilder("cors-allowed-methods", ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .build();
    protected static final SimpleAttributeDefinition CORS_EXPOSED_HEADERS =
            new SimpleAttributeDefinitionBuilder("cors-exposed-headers", ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .build();
    protected static final SimpleAttributeDefinition EXPOSE_TOKEN =
            new SimpleAttributeDefinitionBuilder("expose-token", ModelType.BOOLEAN, true)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    protected static final SimpleAttributeDefinition AUTH_SERVER_URL_FOR_BACKEND_REQUESTS =
            new SimpleAttributeDefinitionBuilder("auth-server-url-for-backend-requests", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();
    protected static final SimpleAttributeDefinition ALWAYS_REFRESH_TOKEN =
            new SimpleAttributeDefinitionBuilder("always-refresh-token", ModelType.BOOLEAN, true)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    protected static final SimpleAttributeDefinition REGISTER_NODE_AT_STARTUP =
            new SimpleAttributeDefinitionBuilder("register-node-at-startup", ModelType.BOOLEAN, true)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();
    protected static final SimpleAttributeDefinition REGISTER_NODE_PERIOD =
            new SimpleAttributeDefinitionBuilder("register-node-period", ModelType.INT, true)
                    .setAllowExpression(true)
                    .setValidator(new IntRangeValidator(-1, true))
                    .build();
    protected static final SimpleAttributeDefinition TOKEN_STORE =
            new SimpleAttributeDefinitionBuilder("token-store", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();
    protected static final SimpleAttributeDefinition PRINCIPAL_ATTRIBUTE =
            new SimpleAttributeDefinitionBuilder("principal-attribute", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();
    protected static final SimpleAttributeDefinition AUTODETECT_BEARER_ONLY =
            new SimpleAttributeDefinitionBuilder("autodetect-bearer-only", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    protected static final SimpleAttributeDefinition IGNORE_OAUTH_QUERY_PARAMETER =
            new SimpleAttributeDefinitionBuilder("ignore-oauth-query-parameter", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    protected static final SimpleAttributeDefinition CONFIDENTIAL_PORT =
            new SimpleAttributeDefinitionBuilder("confidential-port", ModelType.INT, true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode(8443))
                    .build();

    protected static final SimpleAttributeDefinition PROXY_URL =
            new SimpleAttributeDefinitionBuilder("proxy-url", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .build();

    protected static final SimpleAttributeDefinition VERIFY_TOKEN_AUDIENCE =
            new SimpleAttributeDefinitionBuilder("verify-token-audience", ModelType.BOOLEAN, true)
                    .setAllowExpression(true)
                    .setDefaultValue(ModelNode.FALSE)
                    .build();


    protected static final List<SimpleAttributeDefinition> ATTRIBUTES = new ArrayList<SimpleAttributeDefinition>();
    static {
        ATTRIBUTES.add(REALM_PUBLIC_KEY);
        ATTRIBUTES.add(AUTH_SERVER_URL);
        ATTRIBUTES.add(TRUSTSTORE);
        ATTRIBUTES.add(TRUSTSTORE_PASSWORD);
        ATTRIBUTES.add(SSL_REQUIRED);
        ATTRIBUTES.add(CONFIDENTIAL_PORT);
        ATTRIBUTES.add(ALLOW_ANY_HOSTNAME);
        ATTRIBUTES.add(DISABLE_TRUST_MANAGER);
        ATTRIBUTES.add(CONNECTION_POOL_SIZE);
        ATTRIBUTES.add(SOCKET_TIMEOUT);
        ATTRIBUTES.add(CONNECTION_TTL);
        ATTRIBUTES.add(CONNECTION_TIMEOUT);
        ATTRIBUTES.add(ENABLE_CORS);
        ATTRIBUTES.add(CLIENT_KEYSTORE);
        ATTRIBUTES.add(CLIENT_KEYSTORE_PASSWORD);
        ATTRIBUTES.add(CLIENT_KEY_PASSWORD);
        ATTRIBUTES.add(CORS_MAX_AGE);
        ATTRIBUTES.add(CORS_ALLOWED_HEADERS);
        ATTRIBUTES.add(CORS_ALLOWED_METHODS);
        ATTRIBUTES.add(CORS_EXPOSED_HEADERS);
        ATTRIBUTES.add(EXPOSE_TOKEN);
        ATTRIBUTES.add(AUTH_SERVER_URL_FOR_BACKEND_REQUESTS);
        ATTRIBUTES.add(ALWAYS_REFRESH_TOKEN);
        ATTRIBUTES.add(REGISTER_NODE_AT_STARTUP);
        ATTRIBUTES.add(REGISTER_NODE_PERIOD);
        ATTRIBUTES.add(TOKEN_STORE);
        ATTRIBUTES.add(PRINCIPAL_ATTRIBUTE);
        ATTRIBUTES.add(AUTODETECT_BEARER_ONLY);
        ATTRIBUTES.add(IGNORE_OAUTH_QUERY_PARAMETER);
        ATTRIBUTES.add(PROXY_URL);
        ATTRIBUTES.add(VERIFY_TOKEN_AUDIENCE);
    }

    private static boolean isSet(ModelNode attributes, SimpleAttributeDefinition def) {
        ModelNode attribute = attributes.get(def.getName());

        if (def.getType() == ModelType.BOOLEAN) {
            return attribute.isDefined() && attribute.asBoolean();
        }

        return attribute.isDefined() && !attribute.asString().isEmpty();
    }


}
