/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.subsystem;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of elements used in the EJB3 subsystem
 *
 * @author Jaikiran Pai
 */
public enum EJB3SubsystemXMLElement {

    // must be first
    UNKNOWN(null),

    ASYNC("async"),
    ALLOW_EJB_NAME_REGEX("allow-ejb-name-regex"),

    BEAN_INSTANCE_POOLS("bean-instance-pools"),
    BEAN_INSTANCE_POOL_REF("bean-instance-pool-ref"),

    ENTITY_BEAN("entity-bean"),

    DATA_STORE("data-store"),
    DATA_STORES("data-stores"),
    DEFAULT_DISTINCT_NAME("default-distinct-name"),
    DEFAULT_SECURITY_DOMAIN("default-security-domain"),
    DEFAULT_MISSING_METHOD_PERMISSIONS_DENY_ACCESS(EJB3SubsystemModel.DEFAULT_MISSING_METHOD_PERMISSIONS_DENY_ACCESS),
    DISABLE_DEFAULT_EJB_PERMISSIONS(EJB3SubsystemModel.DISABLE_DEFAULT_EJB_PERMISSIONS),
    DISTRIBUTABLE_CACHE(EJB3SubsystemModel.DISTRIBUTABLE_CACHE),
    ENABLE_GRACEFUL_TXN_SHUTDOWN(EJB3SubsystemModel.ENABLE_GRACEFUL_TXN_SHUTDOWN),

    FILE_DATA_STORE("file-data-store"),

    IIOP("iiop"),
    IN_VM_REMOTE_INTERFACE_INVOCATION("in-vm-remote-interface-invocation"),

    MDB("mdb"),

    POOLS("pools"),

    @Deprecated CACHE("cache"),
    CACHES("caches"),
    CHANNEL_CREATION_OPTIONS("channel-creation-options"),

    DATABASE_DATA_STORE("database-data-store"),

    OPTIMISTIC_LOCKING("optimistic-locking"),
    OPTION("option"),
    OUTBOUND_CONNECTION_REF("outbound-connection-ref"),

    @Deprecated PASSIVATION_STORE("passivation-store"),
    @Deprecated PASSIVATION_STORES("passivation-stores"),
    PROFILE("profile"),
    PROFILES("profiles"),
    PROPERTY("property"),
    @Deprecated CLUSTER_PASSIVATION_STORE("cluster-passivation-store"),
    @Deprecated FILE_PASSIVATION_STORE("file-passivation-store"),

    REMOTE("remote"),
    REMOTING_EJB_RECEIVER("remoting-ejb-receiver"),
    REMOTE_HTTP_CONNECTION("remote-http-connection"),
    RESOURCE_ADAPTER_NAME("resource-adapter-name"),
    RESOURCE_ADAPTER_REF("resource-adapter-ref"),

    SESSION_BEAN("session-bean"),
    SIMPLE_CACHE("simple-cache"),
    SINGLETON("singleton"),
    STATEFUL("stateful"),
    STATELESS("stateless"),
    STATISTICS("statistics"),
    STRICT_MAX_POOL("strict-max-pool"),

    CONNECTIONS("connections"),

    THREAD_POOL("thread-pool"),
    THREAD_POOLS("thread-pools"),
    TIMER_SERVICE("timer-service"),
    LOG_SYSTEM_EXCEPTIONS(EJB3SubsystemModel.LOG_SYSTEM_EXCEPTIONS),
    DELIVERY_GROUPS("delivery-groups"),
    DELIVERY_GROUP("delivery-group"),

    // Elytron integration
    APPLICATION_SECURITY_DOMAIN("application-security-domain"),
    APPLICATION_SECURITY_DOMAINS("application-security-domains"),
    IDENTITY("identity"),

    STATIC_EJB_DISCOVERY("static-ejb-discovery"),
    MODULES("modules"),
    MODULE("module"),

    //server interceptors
    SERVER_INTERCEPTORS("server-interceptors"),
    CLIENT_INTERCEPTORS("client-interceptors"),
    INTERCEPTOR("interceptor")
    ;

    private final String name;

    EJB3SubsystemXMLElement(final String name) {
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

    private static final Map<String, EJB3SubsystemXMLElement> MAP;

    static {
        final Map<String, EJB3SubsystemXMLElement> map = new HashMap<String, EJB3SubsystemXMLElement>();
        for (EJB3SubsystemXMLElement element : values()) {
            final String name = element.getLocalName();
            if (name != null) map.put(name, element);
        }
        MAP = map;
    }

    public static EJB3SubsystemXMLElement forName(String localName) {
        final EJB3SubsystemXMLElement element = MAP.get(localName);
        return element == null ? UNKNOWN : element;
    }
}
