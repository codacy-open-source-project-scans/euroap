/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.messaging.jms.external;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.SocketPermission;
import jakarta.annotation.Resource;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.ServerSnapshot;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Basic JMS test using a customly created JMS topic
 *
 * @author <a href="jmartisk@redhat.com">Jan Martiska</a>
 */
@RunWith(Arquillian.class)
@ServerSetup(SendToExternalJMSQueueTestCase.SetupTask.class)
public class SendToExternalJMSQueueTestCase {

    public static final String QUEUE_LOOKUP = "java:jboss/exported/queue/myAwesomeClientQueue";

    static class SetupTask implements ServerSetupTask {

        private static final Logger logger = Logger.getLogger(SendToExternalJMSQueueTestCase.SetupTask.class);
        private AutoCloseable snapshot = null;

        @Override
        public void setup(org.jboss.as.arquillian.container.ManagementClient managementClient, String s) throws Exception {
            snapshot = ServerSnapshot.takeSnapshot(managementClient);
            ServerReload.executeReloadAndWaitForCompletion(managementClient, true);
            JMSOperations ops = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
            boolean needRemoteConnector = ops.isRemoteBroker();
            if (needRemoteConnector) {
                ops.addExternalRemoteConnector("remote-broker-connector", "messaging-activemq");
            } else {
                ops.addExternalHttpConnector("http-test-connector", "http", "http-acceptor");
                ops.createJmsQueue("myAwesomeQueue", "/queue/myAwesomeQueue");
            }
            ModelNode op = Operations.createRemoveOperation(getInitialPooledConnectionFactoryAddress(ops.getServerAddress()));
            execute(managementClient, op, true);
            op = Operations.createAddOperation(getPooledConnectionFactoryAddress());
            op.get("transaction").set("xa");
            op.get("entries").add("java:/JmsXA java:jboss/DefaultJMSConnectionFactory");
            if(needRemoteConnector) {
                op.get("connectors").add("remote-broker-connector");
            } else {
                op.get("connectors").add("http-test-connector");
            }
            execute(managementClient, op, true);
            op = Operations.createAddOperation(getClientQueueAddress());
            op.get("entries").add(QUEUE_LOOKUP);
            op.get("entries").add("/queue/myAwesomeClientQueue");
            execute(managementClient, op, true);
            ServerReload.executeReloadAndWaitForCompletion(managementClient);
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            JMSOperations ops = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
            ops.removeJmsQueue("myAwesomeQueue");
            if (snapshot != null) {
                snapshot.close();
            }
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


        ModelNode getClientQueueAddress() {
            ModelNode address = new ModelNode();
            address.add("subsystem", "messaging-activemq");
            address.add("external-jms-queue", "myAwesomeQueue");
            return address;
        }

        ModelNode getInitialPooledConnectionFactoryAddress(ModelNode serverAddress) {
            ModelNode address = serverAddress.clone();
            address.add("pooled-connection-factory", "activemq-ra");
            return address;
        }
    }

    private static final Logger logger = Logger.getLogger(SendToExternalJMSQueueTestCase.class);

    @Resource(lookup = QUEUE_LOOKUP)
    private Queue queue;

    @Resource(lookup = "java:/JmsXA")
    private ConnectionFactory factory;

    @Deployment
    public static JavaArchive createTestArchive() {
        return ShrinkWrap.create(JavaArchive.class, "test.jar")
                .addPackage(JMSOperations.class.getPackage())
                .addClass(SendToExternalJMSQueueTestCase.SetupTask.class)
                .addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                        RemotingPermission.CREATE_ENDPOINT,
                        RemotingPermission.CONNECT,
                        new SocketPermission("localhost", "resolve")), "permissions.xml")
                .addAsManifestResource(
                        EmptyAsset.INSTANCE,
                        ArchivePaths.create("beans.xml"));
    }

    @Test
    public void sendMessage() throws Exception {
        Connection senderConnection = null;
        Connection consumerConnection = null;
        Session senderSession = null;
        Session consumerSession = null;
        MessageConsumer consumer = null;
        try {
            // CREATE SUBSCRIBER
            logger.trace("******* Creating connection for consumer");
            consumerConnection = factory.createConnection("guest", "guest");
            logger.trace("Creating session for consumer");
            consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            logger.trace("Creating consumer");
            consumer = consumerSession.createConsumer(queue);
            logger.trace("Start session");
            consumerConnection.start();

            // SEND A MESSAGE
            logger.trace("***** Start - sending message to topic");
            senderConnection = factory.createConnection("guest", "guest");
            logger.trace("Creating session..");
            senderSession = senderConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = senderSession.createProducer(queue);
            TextMessage message = senderSession.createTextMessage("Hello world!");
            logger.trace("Sending..");
            producer.send(message);
            logger.trace("Message sent");
            senderConnection.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            logger.trace("Closing connections and sessions");
            if (senderSession != null) {
                senderSession.close();
            }
            if (senderConnection != null) {
                senderConnection.close();
            }
        }

        Message receivedMessage = null;
        try {
            logger.trace("Receiving");
            receivedMessage = consumer.receive(5000);
            logger.trace("Received: " + ((TextMessage) receivedMessage).getText());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        } finally {
            if (receivedMessage == null) {
                Assert.fail("received null instead of a TextMessage");
            }
            if (consumerSession != null) {
                consumerSession.close();
            }
            if (consumerConnection != null) {
                consumerConnection.close();
            }
        }

        Assert.assertTrue("received a " + receivedMessage.getClass().getName() + " instead of a TextMessage", receivedMessage instanceof TextMessage);
        Assert.assertEquals("Hello world!", ((TextMessage) receivedMessage).getText());
    }


}
