/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.parse;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.io.FileNotFoundException;

import org.jboss.as.test.shared.FileUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the ability to parse the config files we ship or have shipped in the past, as well as the ability to marshal
 * them back to xml in a manner such that reparsing them produces a consistent in-memory configuration model.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@Ignore("[WFLY-15178] Rework ParseAndMarshalModelsTestCase.")
public class LegacyParseAndMarshalModelsTestCase extends AbstractParseAndMarshalModelsTestCase {

    private enum Version {
        AS_7_1_3(false, "7-1-3"),
        AS_7_2_0(false, "7-2-0"),

        EAP_6_0_0(true, "6-0-0"),
        EAP_6_1_0(true, "6-1-0"),
        EAP_6_2_0(true, "6-2-0"),
        EAP_6_3_0(true, "6-3-0"),
        EAP_6_4_0(true, "6-4-0"),
        EAP_7_0_0(true, "7-0-0"),
        EAP_7_1_0(true, "7-1-0"),
        EAP_7_2_0(true, "7-2-0"),
        EAP_7_3_0(true, "7-3-0"),
        EAP_7_4_0(true, "7-4-0");

        final boolean eap;
        final String versionQualifier;
        final int major;
        final int minor;
        final int micro;

        Version(boolean eap, String versionQualifier) {
            this.eap = eap;
            this.versionQualifier = versionQualifier;
            final String[] parts = this.versionQualifier.split("-");
            major = Integer.valueOf(parts[0]);
            minor = Integer.valueOf(parts[1]);
            micro = Integer.valueOf(parts[2]);
        }

        boolean is6x() {
            return major == 6;
        }

        boolean is7x() {
            return major == 7;
        }

        boolean isLessThan(int major, int minor) {
            return (this.major == major && this.minor < minor) || this.major < major;
        }
    }


    private static final Version[] EAP_VERSIONS = {
            Version.EAP_6_0_0, Version.EAP_6_1_0, Version.EAP_6_2_0, Version.EAP_6_3_0, Version.EAP_6_4_0,
            Version.EAP_7_0_0, Version.EAP_7_1_0, Version.EAP_7_2_0, Version.EAP_7_3_0, Version.EAP_7_4_0};

    private static final Version[] AS_VERSIONS = {Version.AS_7_1_3, Version.AS_7_2_0};

    private static final boolean altDistTest = "ee-".equals(System.getProperty("testsuite.default.build.project.prefix"));

    @Test
    public void testJBossASStandaloneXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, null));
            validateJsfSubsystem(model, version);
        }
    }

    @Test
    public void testJBossASStandaloneFullHaXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, "full-ha"));
            validateJsfSubsystem(model, version);
        }
    }

    @Test
    public void testJBossASStandaloneFullXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, "full"));
            validateJsfSubsystem(model, version);
        }
    }

    @Test
    public void testJBossASStandaloneHornetQCollocatedXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            standaloneXmlTest(getLegacyConfigFile("standalone", version, "hornetq-colocated"));
        }
    }

    @Test
    public void testJBossASStandaloneJtsXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            standaloneXmlTest(getLegacyConfigFile("standalone", version, "jts"));
        }
    }

    @Test
    public void testJBossASStandaloneMinimalisticXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            standaloneXmlTest(getLegacyConfigFile("standalone", version, "minimalistic"));
        }
    }

    @Test
    public void testJBossASStandaloneXtsXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            standaloneXmlTest(getLegacyConfigFile("standalone", version, "xts"));
        }
    }

    @Test
    public void testEAPStandaloneFullHaXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, "full-ha"));
            validateWebSubsystem(model, version);
            validateJsfSubsystem(model, version);
            validateCmpSubsystem(model, version);
            validateMessagingSubsystem(model, version);
        }
    }

    @Test
    public void testEAPStandaloneFullXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, "full"));
            validateWebSubsystem(model, version);
            validateJsfSubsystem(model, version);
            validateCmpSubsystem(model, version);
            validateMessagingSubsystem(model, version);
        }
    }

    @Test
    public void testEAPStandaloneXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, null));
            validateWebSubsystem(model, version);
            validateJsfSubsystem(model, version);
        }
    }

    @Test
    public void testEAPStandaloneHornetQCollocatedXml() throws Exception {
        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                //Only exists in EAP 6.x
                ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, "hornetq-colocated"));
                validateWebSubsystem(model, version);
                validateJsfSubsystem(model, version);
                validateMessagingSubsystem(model, version);
                validateThreadsSubsystem(model, version);
                validateJacordSubsystem(model, version);
            }
        }
    }

    @Test
    public void testEAPStandaloneJtsXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, "jts"));
            validateWebSubsystem(model, version);
            validateJsfSubsystem(model, version);
            validateThreadsSubsystem(model, version);
            validateJacordSubsystem(model, version);
        }
    }

    @Test
    public void testEAPStandaloneMinimalisticXml() throws Exception {
        for (Version version : EAP_VERSIONS) {
            standaloneXmlTest(getLegacyConfigFile("standalone", version, "minimalistic"));
        }
    }

    @Test
    public void testEAPStandaloneXtsXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            ModelNode model = standaloneXmlTest(getLegacyConfigFile("standalone", version, "xts"));
            validateCmpSubsystem(model, version);
            validateWebSubsystem(model, version);
            validateJsfSubsystem(model, version);
            validateThreadsSubsystem(model, version);
            validateJacordSubsystem(model, version);
            validateXtsSubsystem(model, version);
        }
    }

    @Test
    public void testEAPStandaloneAzureFullHaXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "azure-full-ha"));
            }
        }
    }

    @Test
    public void testEAPStandaloneAzureHaXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "azure-ha"));
            }
        }
    }

    @Test
    public void testEAPStandaloneEc2FullHaXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "ec2-full-ha"));
            }
        }
    }

    @Test
    public void testEAPStandaloneEc2HaXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "ec2-ha"));
            }
        }
    }

    @Test
    public void testEAPStandaloneGenericJmsXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "genericjms"));
            }
        }
    }

    @Test
    public void testEAPStandaloneGossipFullHaXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "gossip-full-ha"));
            }
        }
    }

    @Test
    public void testEAPStandaloneGossipHaXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "gossip-ha"));
            }
        }
    }

    @Test
    public void testEAPStandaloneLoadBalancerXml() throws Exception {
        for (Version version : EAP_VERSIONS) {
            if (version.isLessThan(7, 1)) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "load-balancer"));
            }
        }
    }

    @Test
    public void testEAPStandaloneRtsXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            if (version.is6x()) {
                // didn't exist yet
            } else {
                standaloneXmlTest(getLegacyConfigFile("standalone", version, "rts"));
            }
        }
    }

    @Test
    public void testJBossASHostXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            hostXmlTest(getLegacyConfigFile("host", version, null));
        }
    }

    @Test
    public void testEAPHostXml() throws Exception {
        for (Version version : EAP_VERSIONS) {
            hostXmlTest(getLegacyConfigFile("host", version, null));
        }
    }

    @Test
    public void testJBossASDomainXml() throws Exception {
        for (Version version : AS_VERSIONS) {
            ModelNode model = domainXmlTest(getLegacyConfigFile("domain", version, null));
            validateProfiles(model, version);
        }
    }

    @Test
    public void testEAPDomainXml() throws Exception {

        Assume.assumeFalse(altDistTest);

        for (Version version : EAP_VERSIONS) {
            ModelNode model = domainXmlTest(getLegacyConfigFile("domain", version, null));
            validateProfiles(model, version);
        }
    }

    private static void validateProfiles(ModelNode model, Version version) {
        Assert.assertTrue(model.hasDefined(PROFILE));
        if (version.is6x()) {
            //Only in 6.x
            for (Property prop : model.get(PROFILE).asPropertyList()) {
                validateWebSubsystem(prop.getValue(), version);
                validateJsfSubsystem(prop.getValue(), version);
                validateThreadsSubsystem(prop.getValue(), version);
            }

        } else {
            //Don't validate, although we may decide to validate things in the future
        }
    }

    private static void validateWebSubsystem(ModelNode model, Version version) {
        if (version.is6x()) {
            //Only in 6.x
            validateSubsystem(model, "web", version);
            Assert.assertTrue(model.hasDefined(SUBSYSTEM, "web", "connector", "http"));

        }
    }

    private static void validateJsfSubsystem(ModelNode model, Version version) {
        if (version.is6x()) {
            //Only in 6.x
            validateSubsystem(model, "jsf", version); //we cannot check for it as web subsystem is not present to add jsf one
        }
    }

    private static void validateCmpSubsystem(ModelNode model, Version version) {
        if (version.is6x()) {
            //Only in 6.x
            validateSubsystem(model, "cmp", version); //we cannot check for it as web subsystem is not present to add jsf one
        }
    }

    private static void validateMessagingSubsystem(ModelNode model, Version version) {
        if (version.is6x()) {
            //Only in 6.x
            validateSubsystem(model, "messaging", version);
            Assert.assertTrue(model.hasDefined(SUBSYSTEM, "messaging", "hornetq-server", "default"));
        }
    }

    private static void validateThreadsSubsystem(ModelNode model, Version version) {
        if (version.is6x()) {
            //Only in 6.x
            validateSubsystem(model, "threads", version);
        }
    }

    private static void validateJacordSubsystem(ModelNode model, Version version) {
        if (version.is6x()) {
            //Only in 6.x
            validateSubsystem(model, "jacorb", version);
        }
    }

    private static void validateXtsSubsystem(ModelNode model, Version version) {
        validateSubsystem(model, "xts", version);
        Assert.assertTrue(model.hasDefined(SUBSYSTEM, "xts", "host"));
    }

    private static void validateSubsystem(ModelNode model, String subsystem, Version version) {
        Assert.assertTrue(model.hasDefined(SUBSYSTEM));
        Assert.assertTrue("Missing " + subsystem + " subsystem for " + version.versionQualifier, model.get(SUBSYSTEM)
                .hasDefined(subsystem));
    }

    private File getLegacyConfigFile(String type, Version version, String qualifier) throws FileNotFoundException {
        StringBuilder sb = new StringBuilder();
        if (version.eap) {
            sb.append("eap-");
        }
        sb.append(version.versionQualifier);
        if (qualifier != null) {
            sb.append("-");
            sb.append(qualifier);
        }
        sb.append(".xml");
        String fileName = sb.toString();
        return FileUtils.getFileOrCheckParentsIfNotFound(
                System.getProperty("jbossas.ts.submodule.dir"),
                "src/test/resources/legacy-configs/" + type + File.separator + fileName
        );
    }
}
