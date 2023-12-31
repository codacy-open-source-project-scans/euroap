/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.security;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum for the Security container attributes
 *
 * @author <a href="mailto:mmoyses@redhat.com">Marcus Moyses</a>
 */
enum Attribute {
    // must be first
    UNKNOWN(null),

    AUDIT_MANAGER_CLASS_NAME("audit-manager-class-name"),
    AUTHENTICATION_MANAGER_CLASS_NAME("authentication-manager-class-name"),
    AUTHORIZATION_MANAGER_CLASS_NAME("authorization-manager-class-name"),
    CACHE_TYPE("cache-type"),
    CIPHER_SUITES("cipher-suites"),
    CLIENT_ALIAS("client-alias"),
    CLIENT_AUTH("client-auth"),
    CODE("code"),
    DEEP_COPY_SUBJECT_MODE("deep-copy-subject-mode"),
    DEFAULT_CALLBACK_HANDLER_CLASS_NAME("default-callback-handler-class-name"),
    EXTENDS("extends"),
    FLAG("flag"),
    IDENTITY_TRUST_MANAGER_CLASS_NAME("identity-trust-manager-class-name"),
    INITIALIZE_JACC("initialize-jacc"),
    KEY_MANAGER_FACTORY_ALGORITHM("key-manager-factory-algorithm"),
    KEY_MANAGER_FACTORY_PROVIDER("key-manager-factory-provider"),
    KEYSTORE_PASSWORD("keystore-password"),
    KEYSTORE_PROVIDER("keystore-provider"),
    KEYSTORE_PROVIDER_ARGUMENT("keystore-provider-argument"),
    KEYSTORE_TYPE("keystore-type"),
    KEYSTORE_URL("keystore-url"),
    LOGIN_MODULE_STACK_REF("login-module-stack-ref"),
    MAPPING_MANAGER_CLASS_NAME("mapping-manager-class-name"),
    MODULE("module"),
    NAME("name"),
    PROTOCOLS("protocols"),
    SERVER_ALIAS("server-alias"),
    SERVICE_AUTH_TOKEN("service-auth-token"),
    SUBJECT_FACTORY_CLASS_NAME("subject-factory-class-name"),
    TRUST_MANAGER_FACTORY_ALGORITHM("trust-manager-factory-algorithm"),
    TRUST_MANAGER_FACTORY_PROVIDER("trust-manager-factory-provider"),
    TRUSTSTORE_PASSWORD("truststore-password"),
    TRUSTSTORE_PROVIDER("truststore-provider"),
    TRUSTSTORE_PROVIDER_ARGUMENT("truststore-provider-argument"),
    TRUSTSTORE_TYPE("truststore-type"),
    TRUSTSTORE_URL("truststore-url"),
    TYPE("type"),
    VALUE("value"),
    // ELYTRON INTEGRATION ATTRIBUTES
    LEGACY_JAAS_CONFIG(Constants.LEGACY_JAAS_CONFIG),
    LEGACY_JSSE_CONFIG(Constants.LEGACY_JSSE_CONFIG),
    APPLY_ROLE_MAPPERS(Constants.APPLY_ROLE_MAPPERS);

    private final String name;

    Attribute(final String name) {
        this.name = name;
    }

    /**
     * Get the local name of this element.
     *
     * @return the local name
     */
    public String getLocalName() {
        return name;
    }

    private static final Map<String, Attribute> MAP;

    static {
        final Map<String, Attribute> map = new HashMap<>();
        for (Attribute element : values()) {
            final String name = element.getLocalName();
            if (name != null)
                map.put(name, element);
        }
        MAP = map;
    }

    public static Attribute forName(String localName) {
        final Attribute element = MAP.get(localName);
        return element == null ? UNKNOWN : element;
    }

    @Override
    public String toString() {
        return getLocalName();
    }

}
