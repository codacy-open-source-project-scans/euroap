/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.security;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.AdditionalInitialization.ManagementAdditionalInitialization;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Security subsystem tests for the version 2.0 of the subsystem schema.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class SecurityDomainModelv20UnitTestCase extends AbstractSubsystemBaseTest {

    public SecurityDomainModelv20UnitTestCase() {
        super(SecurityExtension.SUBSYSTEM_NAME, new SecurityExtension());
    }

    private static String oldConfig;


    @BeforeClass
    public static void beforeClass() {
        try {
            File target = new File(SecurityDomainModelv20UnitTestCase.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            File config = new File(target, "config");
            config.mkdir();
            oldConfig = System.setProperty("jboss.server.config.dir", config.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterClass
    public static void afterClass() {
        if (oldConfig != null) {
            System.setProperty("jboss.server.config.dir", oldConfig);
        } else {
            System.clearProperty("jboss.server.config.dir");
        }
    }


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("securitysubsystemv20.xml");
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/wildfly-security_2_0.xsd";
    }

    @Override
    protected Properties getResolvedProperties() {
        Properties properties = new Properties();
        properties.put("jboss.server.config.dir", System.getProperty("java.io.tmpdir"));
        return properties;
    }


    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        String[] capabilities = new String[] { "org.wildfly.clustering.infinispan.default-cache-configuration.security" };
        return new ManagementAdditionalInitialization() {

            @Override
            protected ProcessType getProcessType() {
                return ProcessType.HOST_CONTROLLER;
            }

            @Override
            protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource,
                    ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
                super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
                registerCapabilities(capabilityRegistry, capabilities);
            }
        };

    }

}
