/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.resourceadapters;

import static org.jboss.as.connector.subsystems.common.jndi.Constants.JNDI_NAME;
import static org.jboss.as.connector.subsystems.common.jndi.Constants.USE_JAVA_CONTEXT;
import static org.jboss.as.connector.subsystems.common.pool.Constants.BACKGROUNDVALIDATION;
import static org.jboss.as.connector.subsystems.common.pool.Constants.BACKGROUNDVALIDATIONMILLIS;
import static org.jboss.as.connector.subsystems.common.pool.Constants.BLOCKING_TIMEOUT_WAIT_MILLIS;
import static org.jboss.as.connector.subsystems.common.pool.Constants.CAPACITY_DECREMENTER_CLASS;
import static org.jboss.as.connector.subsystems.common.pool.Constants.CAPACITY_DECREMENTER_PROPERTIES;
import static org.jboss.as.connector.subsystems.common.pool.Constants.CAPACITY_INCREMENTER_CLASS;
import static org.jboss.as.connector.subsystems.common.pool.Constants.CAPACITY_INCREMENTER_PROPERTIES;
import static org.jboss.as.connector.subsystems.common.pool.Constants.IDLETIMEOUTMINUTES;
import static org.jboss.as.connector.subsystems.common.pool.Constants.INITIAL_POOL_SIZE;
import static org.jboss.as.connector.subsystems.common.pool.Constants.MAX_POOL_SIZE;
import static org.jboss.as.connector.subsystems.common.pool.Constants.MIN_POOL_SIZE;
import static org.jboss.as.connector.subsystems.common.pool.Constants.POOL_FAIR;
import static org.jboss.as.connector.subsystems.common.pool.Constants.POOL_FLUSH_STRATEGY;
import static org.jboss.as.connector.subsystems.common.pool.Constants.POOL_PREFILL;
import static org.jboss.as.connector.subsystems.common.pool.Constants.POOL_USE_STRICT_MIN;
import static org.jboss.as.connector.subsystems.common.pool.Constants.USE_FAST_FAIL;
import static org.jboss.as.connector.subsystems.common.pool.Constants.VALIDATE_ON_MATCH;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ALLOCATION_RETRY;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ALLOCATION_RETRY_WAIT_MILLIS;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.APPLICATION;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ARCHIVE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.AUTHENTICATION_CONTEXT;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.AUTHENTICATION_CONTEXT_AND_APPLICATION;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.BEANVALIDATION_GROUPS;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.BOOTSTRAP_CONTEXT;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.CLASS_NAME;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.CONNECTABLE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ELYTRON_ENABLED;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ENABLED;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ENLISTMENT;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ENLISTMENT_TRACE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.INTERLEAVING;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.MCP;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.MODULE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.NOTXSEPARATEPOOL;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.NO_RECOVERY;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.PAD_XID;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVER_PLUGIN_CLASSNAME;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVER_PLUGIN_PROPERTIES;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_AUTHENTICATION_CONTEXT;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_CREDENTIAL_REFERENCE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_ELYTRON_ENABLED;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_PASSWORD;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_SECURITY_DOMAIN;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_USERNAME;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.SAME_RM_OVERRIDE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.SECURITY_DOMAIN;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.SECURITY_DOMAIN_AND_APPLICATION;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.SHARABLE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.STATISTICS_ENABLED;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.TRANSACTION_SUPPORT;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.TRACKING;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.USE_CCM;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_ELYTRON_SECURITY_DOMAIN;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_SECURITY;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_SECURITY_DEFAULT_GROUPS;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_SECURITY_DEFAULT_PRINCIPAL;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_SECURITY_DOMAIN;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_SECURITY_MAPPING_GROUPS;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_SECURITY_MAPPING_REQUIRED;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WM_SECURITY_MAPPING_USERS;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.WRAP_XA_RESOURCE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.XA_RESOURCE_TIMEOUT;

import org.jboss.as.controller.AttributeDefinition;

/**
 * @author @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 */
public class CommonAttributes {

    static final AttributeDefinition[] RESOURCE_ADAPTER_ATTRIBUTE = {
            ARCHIVE,
            MODULE,
            TRANSACTION_SUPPORT,
            BOOTSTRAP_CONTEXT,
            BEANVALIDATION_GROUPS,
            WM_SECURITY,
            WM_SECURITY_MAPPING_REQUIRED,
            WM_SECURITY_DOMAIN,
            WM_ELYTRON_SECURITY_DOMAIN,
            WM_SECURITY_DEFAULT_PRINCIPAL,
            WM_SECURITY_DEFAULT_GROUPS,
            WM_SECURITY_MAPPING_GROUPS,
            WM_SECURITY_MAPPING_USERS,
            STATISTICS_ENABLED
    };
    static final AttributeDefinition[] CONNECTION_DEFINITIONS_NODE_ATTRIBUTE = {
            CLASS_NAME,
            JNDI_NAME,
            USE_JAVA_CONTEXT,
            ENABLED, CONNECTABLE, TRACKING,
            MAX_POOL_SIZE,
            INITIAL_POOL_SIZE,
            MIN_POOL_SIZE,
            POOL_USE_STRICT_MIN,
            POOL_FLUSH_STRATEGY,
            SECURITY_DOMAIN_AND_APPLICATION,
            APPLICATION,
            SECURITY_DOMAIN,
            ELYTRON_ENABLED,
            AUTHENTICATION_CONTEXT,
            AUTHENTICATION_CONTEXT_AND_APPLICATION,
            ALLOCATION_RETRY,
            ALLOCATION_RETRY_WAIT_MILLIS,
            BLOCKING_TIMEOUT_WAIT_MILLIS,
            IDLETIMEOUTMINUTES,
            XA_RESOURCE_TIMEOUT,
            BACKGROUNDVALIDATIONMILLIS,
            BACKGROUNDVALIDATION,
            USE_FAST_FAIL, VALIDATE_ON_MATCH, USE_CCM,
            SHARABLE, ENLISTMENT, ENLISTMENT_TRACE, MCP,
            RECOVER_PLUGIN_CLASSNAME,
            RECOVER_PLUGIN_PROPERTIES,
            RECOVERY_PASSWORD,
            RECOVERY_CREDENTIAL_REFERENCE,
            RECOVERY_SECURITY_DOMAIN,
            RECOVERY_ELYTRON_ENABLED,
            RECOVERY_AUTHENTICATION_CONTEXT,
            RECOVERY_USERNAME,
            NO_RECOVERY,
            WRAP_XA_RESOURCE,
            SAME_RM_OVERRIDE,
            PAD_XID,
            POOL_FAIR,
            POOL_PREFILL,
            INTERLEAVING,
            NOTXSEPARATEPOOL,
            CAPACITY_INCREMENTER_CLASS,
            CAPACITY_INCREMENTER_PROPERTIES,
            CAPACITY_DECREMENTER_CLASS,
            CAPACITY_DECREMENTER_PROPERTIES
    };

    static final AttributeDefinition[] ADMIN_OBJECTS_NODE_ATTRIBUTE = new AttributeDefinition[]{
            CLASS_NAME,
            JNDI_NAME,
            USE_JAVA_CONTEXT,
            ENABLED
    };

    static final AttributeDefinition[] CONNECTION_DEFINITIONS_NODE_ATTRIBUTE_2_0 = new AttributeDefinition[] {INITIAL_POOL_SIZE,
            CAPACITY_INCREMENTER_CLASS, CAPACITY_INCREMENTER_PROPERTIES, CAPACITY_DECREMENTER_CLASS, CAPACITY_DECREMENTER_PROPERTIES};
    public static final String RESOURCE_NAME = CommonAttributes.class.getPackage().getName() + ".LocalDescriptions";
}
