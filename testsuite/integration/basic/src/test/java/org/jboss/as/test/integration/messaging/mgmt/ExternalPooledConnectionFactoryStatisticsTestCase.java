/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.messaging.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STATISTICS_ENABLED;
import static org.jboss.shrinkwrap.api.ArchivePaths.create;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.SocketPermission;

import javax.naming.Context;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.SnapshotRestoreSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the statistics for pooled-connection-factory.
 */
@RunAsClient
@RunWith(Arquillian.class)
@ServerSetup({ExternalPooledConnectionFactoryStatisticsTestCase.SetupTask.class})
public class ExternalPooledConnectionFactoryStatisticsTestCase {

    static class SetupTask extends SnapshotRestoreSetupTask {

        private static final Logger logger = Logger.getLogger(ExternalPooledConnectionFactoryStatisticsTestCase.SetupTask.class);

        @Override
        public void doSetup(org.jboss.as.arquillian.container.ManagementClient managementClient, String s) throws Exception {
            ServerReload.executeReloadAndWaitForCompletion(managementClient, true);
            JMSOperations ops = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
            ops.addExternalHttpConnector("http-test-connector", "http", "http-acceptor");
            ModelNode attr = new ModelNode();
            attr.get("connectors").add("http-test-connector");
            ModelNode op = Operations.createRemoveOperation(getInitialPooledConnectionFactoryAddress());
            execute(managementClient, op, true);
            op = Operations.createAddOperation(getPooledConnectionFactoryAddress());
            op.get("transaction").set("xa");
            op.get("entries").add("java:/JmsXA java:jboss/DefaultJMSConnectionFactory");
            op.get("connectors").add("http-test-connector");
            execute(managementClient, op, true);
            ServerReload.executeReloadAndWaitForCompletion(managementClient);
        }

        private ModelNode execute(final org.jboss.as.arquillian.container.ManagementClient managementClient, final ModelNode op, final boolean expectSuccess) throws IOException {
            ModelNode response = managementClient.getControllerClient().execute(op);
            final String outcome = response.get("outcome").asString();
            if (expectSuccess) {
                assertEquals(response.toString(), "success", outcome);
                return response.get("result");
            } else {
                assertEquals("failed", outcome);
                return response.get("failure-description");
            }
        }

        ModelNode getPooledConnectionFactoryAddress() {
            ModelNode address = new ModelNode();
            address.add("subsystem", "messaging-activemq");
            address.add("pooled-connection-factory", "activemq-ra");
            return address;
        }

        ModelNode getInitialPooledConnectionFactoryAddress() {
            ModelNode address = new ModelNode();
            address.add("subsystem", "messaging-activemq");
            address.add("server", "default");
            address.add("pooled-connection-factory", "activemq-ra");
            return address;
        }
    }

    @ContainerResource
    private ManagementClient managementClient;

    @ContainerResource
    private Context context;

    @Deployment
    public static JavaArchive deploy() {
        return ShrinkWrap.create(JavaArchive.class, "PooledConnectionFactoryStatisticsTestCase.jar")
                .addClass(ConnectionHoldingBean.class)
                .addClass(RemoteConnectionHolding.class)
                .addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                        new SocketPermission("localhost", "resolve")), "permissions.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, create("beans.xml"));
    }

    @Test
    public void testStatistics() throws Exception {
            checkStatisticsAreDisabled();

            enableStatistics();
            assertEquals(0, readStatistic("InUseCount"));

            RemoteConnectionHolding bean = (RemoteConnectionHolding) context.lookup("PooledConnectionFactoryStatisticsTestCase/ConnectionHoldingBean!org.jboss.as.test.integration.messaging.mgmt.RemoteConnectionHolding");
            bean.createConnection();
            assertEquals(1, readStatistic("InUseCount"));

            bean.closeConnection();
            assertEquals(0, readStatistic("InUseCount"));
    }

    ModelNode getPooledConnectionFactoryAddress() {
        ModelNode address = new ModelNode();
        address.add("subsystem", "messaging-activemq");
        address.add("pooled-connection-factory", "activemq-ra");
        return address;
    }


    ModelNode getStatisticsAddress() {
        return getPooledConnectionFactoryAddress().add("statistics", "pool");
    }

    private void checkStatisticsAreDisabled() throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP_ADDR).set(getStatisticsAddress());
        op.get(OP).set(READ_RESOURCE_OPERATION);
        execute(op, false);
    }

    private void enableStatistics() throws IOException {
        ModelNode op = Operations.createWriteAttributeOperation(getPooledConnectionFactoryAddress(), STATISTICS_ENABLED, true);
        execute(op, true);
        ServerReload.executeReloadAndWaitForCompletion(managementClient);
    }

    private int readStatistic(String name) throws IOException {
        ModelNode op = Operations.createReadAttributeOperation(getStatisticsAddress(), name);
        ModelNode result = execute(op, true);
        return result.asInt();
    }

    private ModelNode execute(final ModelNode op, final boolean expectSuccess) throws IOException {
        ModelNode response = managementClient.getControllerClient().execute(op);
        final String outcome = response.get("outcome").asString();
        if (expectSuccess) {
            assertEquals("success", outcome);
            return response.get("result");
        } else {
            assertEquals("failed", outcome);
            return response.get("failure-description");
        }
    }
}
