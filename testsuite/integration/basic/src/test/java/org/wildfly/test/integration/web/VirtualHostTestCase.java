/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.web;

import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.SnapshotRestoreSetupTask;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that default-web-module works as it should for scenarios:
 * - default host on of single server
 * - non-default host of single server
 * - non default server
 *
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */

@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(VirtualHostTestCase.VirtualHostSetupTask.class)
//todo this test could probably be done in manual mode test with wildfly runner
public class VirtualHostTestCase {

    @ContainerResource
    private ManagementClient managementClient;

    public static class VirtualHostSetupTask extends SnapshotRestoreSetupTask {
        @Override
        public void doSetup(ManagementClient managementClient, String containerId) throws Exception {
            ModelControllerClient client = managementClient.getControllerClient();
            ModelNode addOp = createOpNode("subsystem=undertow/server=default-server/host=test", "add");
            addOp.get("default-web-module").set("test.war");
            addOp.get("alias").add(TestSuiteEnvironment.getServerAddress()); //either 127.0.0.1 or ::1
            execute(client, addOp);

            addOp = createOpNode("socket-binding-group=standard-sockets/socket-binding=myserver", "add");
            addOp.get("port").set(8181);
            execute(client, addOp);

            addOp = createOpNode("subsystem=undertow/server=myserver", "add");
            addOp.get("default-host").set("another");
            execute(client, addOp);

            addOp = createOpNode("subsystem=undertow/server=myserver/host=another", "add");
            addOp.get("default-web-module").set("another-server.war");
            execute(client, addOp);

            addOp = createOpNode("subsystem=undertow/server=myserver/http-listener=myserver", "add");
            addOp.get("socket-binding").set("myserver");
            execute(client, addOp); //this one is runtime addable
        }

        private void execute(ModelControllerClient client, ModelNode op) throws IOException {
            op.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set(true);
            ModelNode response = client.execute(op);
            if (!SUCCESS.equals(response.get(OUTCOME).asString())) {
                Assert.fail("Could not execute op: '" + op + "', result: " + response);
            }
        }
    }

    private static WebArchive createDeployment(String name) {
        WebArchive war = ShrinkWrap.create(WebArchive.class, name + ".war");
        war.addAsWebResource(new StringAsset(name), "index.html");
        return war;
    }

    @Deployment(name = "ROOT")
    public static Archive<?> getDefaultHostDeployment() {
        return createDeployment("ROOT");
    }

    @Deployment(name = "test")
    public static Archive<?> getAnotherHostDeployment() {
        return createDeployment("test");
    }

    @Deployment(name = "another-server")
    public static Archive<?> getAnotherServerDeployment() {
        return createDeployment("another-server");
    }

    @Deployment(name = "default-common")
    public static Archive<?> getDefaultHostCommonDeployment() {
        return createDeployment("default-common");
    }

    @Deployment(name = "another-common")
    public static Archive<?> getAnotherHostCommonDeployment() {
        WebArchive war = createDeployment("another-common");
        war.addAsWebResource(new StringAsset("<jboss-web><server-instance>myserver</server-instance><virtual-host>another</virtual-host></jboss-web>"), "WEB-INF/jboss-web.xml");
        return war;
    }

    private void callAndTest(String uri, String expectedResult) throws IOException {
        HttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet(uri);
        HttpResponse response = client.execute(get);
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String result = EntityUtils.toString(response.getEntity());
        Assert.assertEquals("Got response from wrong deployment", expectedResult, result);
    }

    private void checkUndertowInfo(String app, String server, String host, String context) throws IOException, MgmtOperationException {
        ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.DEPLOYMENT, app)
                .add(ModelDescriptionConstants.SUBSYSTEM, "undertow");
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        op.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set("true");
        ModelNode result = managementClient.getControllerClient().execute(op);
        Assert.assertTrue("Undertow info OK for " + app, Operations.isSuccessfulOutcome(result));
        Assert.assertEquals("context-root is set to " + context + " for " + app, context, result.get("result").get("context-root").asString());
        Assert.assertEquals("virtual-host is set to " + host + " for " + app, host, result.get("result").get("virtual-host").asString());
        Assert.assertEquals("server is set to " + server + " for " + app, server, result.get("result").get("server").asString());
    }

    @Test
    public void testDefaultHost() throws IOException, MgmtOperationException {
        Assume.assumeTrue("This needs to be localhost, as it is by host mapping",
                InetAddress.getByName(TestSuiteEnvironment.getServerAddress()).isLoopbackAddress());
        callAndTest("http://localhost:8080/", "ROOT");
        checkUndertowInfo("ROOT.war", "default-server", "default-host", "/");
    }

    @Test
    public void testNonDefaultHost() throws IOException, MgmtOperationException {
        callAndTest("http://" + TestSuiteEnvironment.getServerAddress() + ":8080/", "test"); //second host on first server has alias 127.0.0.1 or ::1
        checkUndertowInfo("test.war", "default-server", "test", "/");
    }

    @Test
    public void testAnotherServerHost() throws IOException, MgmtOperationException {
        callAndTest("http://" + TestSuiteEnvironment.getServerAddress() + ":8181/", "another-server");
        checkUndertowInfo("another-server.war", "myserver", "another", "/");
    }

    @Test
    public void testDefaultHostCommonAplication() throws IOException, MgmtOperationException {
        Assume.assumeTrue("This needs to be localhost, as it is by host mapping",
                InetAddress.getByName(TestSuiteEnvironment.getServerAddress()).isLoopbackAddress());
        callAndTest("http://localhost:8080/default-common/", "default-common");
        checkUndertowInfo("default-common.war", "default-server", "default-host", "/default-common");
    }

    @Test
    public void testAnotherHostCommonAplication() throws IOException, MgmtOperationException {
        callAndTest("http://" + TestSuiteEnvironment.getServerAddress() + ":8181/another-common/", "another-common");
        checkUndertowInfo("another-common.war", "myserver", "another", "/another-common");
    }
}
