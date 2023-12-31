/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.client.HttpClients;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.junit.Assert;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlanResult;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReadEnvironmentVariablesTestCase {
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ReadEnvironmentVariablesTestCase.class.getSimpleName());

        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainTestSuite.stopSupport();
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
    }

    @Test
    public void testReadEnvironmentVariablesForServers() throws Exception {
        DomainClient client = domainPrimaryLifecycleUtil.createDomainClient();
        DomainDeploymentManager manager = client.getDeploymentManager();

        try {
            //Deploy the archive
            String archiveName = "env-test.war";
            WebArchive archive = ShrinkWrap.create(WebArchive.class, archiveName).addClass(EnvironmentTestServlet.class);
            archive.addAsResource(new StringAsset("Manifest-Version: 1.0\nDependencies: org.jboss.dmr \n"),"META-INF/MANIFEST.MF");
            archive.addAsManifestResource(createPermissionsXmlAsset(new RuntimePermission("getenv.*")), "permissions.xml");

            final InputStream contents = archive.as(ZipExporter.class).exportAsInputStream();
            try {
                DeploymentPlan plan = manager.newDeploymentPlan()
                                          .add("env-test.war", contents)
                                          .deploy("env-test.war")
                                          .toServerGroup("main-server-group")
                                          .toServerGroup("other-server-group")
                                          .build();
                DeploymentPlanResult result = manager.execute(plan).get();
                Assert.assertTrue(result.isValid());
            } finally {
                IoUtils.safeClose(contents);
            }

            Map<String, String> env = getEnvironmentVariables(client, "primary", "main-one", "standard-sockets");
            checkEnvironmentVariable(env, "DOMAIN_TEST_MAIN_GROUP", "main_group");
            checkEnvironmentVariable(env, "DOMAIN_TEST_SERVER", "server");
            checkEnvironmentVariable(env, "DOMAIN_TEST_JVM", "jvm");

            env = getEnvironmentVariables(client, "secondary", "main-three", "standard-sockets");
            checkEnvironmentVariable(env, "DOMAIN_TEST_MAIN_GROUP", "main_group");
            Assert.assertFalse(env.containsKey("DOMAIN_TEST_SERVER"));
            Assert.assertFalse(env.containsKey("DOMAIN_TEST_JVM"));

            env = getEnvironmentVariables(client, "secondary", "other-two", "other-sockets");
            Assert.assertFalse(env.containsKey("DOMAIN_TEST_MAIN_GROUP"));
            Assert.assertFalse(env.containsKey("DOMAIN_TEST_SERVER"));
            Assert.assertFalse(env.containsKey("DOMAIN_TEST_JVM"));

        } finally {
            DeploymentPlanResult result = manager.execute(manager.newDeploymentPlan().undeploy("env-test.war").build()).get();
            Assert.assertTrue(result.isValid());
            IoUtils.safeClose(client);
        }
    }

    private void checkEnvironmentVariable(Map<String, String> env, String name, String expected) {
        Assert.assertTrue(env.containsKey(name));
        Assert.assertEquals(expected, env.get(name));
    }

    private Map<String, String> getEnvironmentVariables(DomainClient client, String host, String server, String socketBindingGroup) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_RESOURCE_OPERATION);
        op.get(OP_ADDR).add(HOST, host).add(SERVER, server).add(SOCKET_BINDING_GROUP, socketBindingGroup).add(SOCKET_BINDING, "http");
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode socketBinding = validateResponse(client.execute(op));

        URL url = new URL("http",
                TestSuiteEnvironment.formatPossibleIpv6Address(socketBinding.get("bound-address").asString()),
                socketBinding.get("bound-port").asInt(),
                "/env-test/env");
        HttpGet get = new HttpGet(url.toURI());
        HttpClient httpClient = HttpClients.createDefault();
        HttpResponse response = httpClient.execute(get);
        ModelNode env = ModelNode.fromJSONStream(response.getEntity().getContent());
        Map<String, String> environment = new HashMap<String, String>();
        for (Property property : env.asPropertyList()) {
            environment.put(property.getName(), property.getValue().asProperty().getName());
        }
        return environment;
    }

}
