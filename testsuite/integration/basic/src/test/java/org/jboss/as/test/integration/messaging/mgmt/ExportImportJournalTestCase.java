/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.messaging.mgmt;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.shared.ServerReload.executeReloadAndWaitForCompletion;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ExportImportJournalTestCase {

    protected final String jmsQueueName = "ExportImportJournalTestCase-Queue";
    protected final String jmsQueueLookup = "jms/" + jmsQueueName;

    @ContainerResource
    private Context remoteContext;

    @ContainerResource
    private ManagementClient managementClient;

    protected static  void sendMessage(Context ctx, String destinationLookup, String text) throws NamingException, JMSException {
        ConnectionFactory cf = (ConnectionFactory) ctx.lookup("jms/RemoteConnectionFactory");
        assertNotNull(cf);
        Destination destination = (Destination) ctx.lookup(destinationLookup);
        assertNotNull(destination);

        try (JMSContext context = cf.createContext("guest", "guest")) {
            TextMessage message = context.createTextMessage(text);
            message.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
            context.createProducer().send(destination, message);
        }
    }

    protected static  void receiveMessage(Context ctx, String destinationLookup, boolean expectReceivedMessage, String expectedText) throws NamingException {
        ConnectionFactory cf = (ConnectionFactory) ctx.lookup("jms/RemoteConnectionFactory");
        assertNotNull(cf);
        Destination destination = (Destination) ctx.lookup(destinationLookup);
        assertNotNull(destination);

        try (JMSContext context = cf.createContext("guest", "guest")) {
            JMSConsumer consumer = context.createConsumer(destination);
            String text = consumer.receiveBody(String.class, TimeoutUtil.adjust(5000));
            if (expectReceivedMessage) {
                assertNotNull(text);
                assertEquals(expectedText, text);
            } else {
                assertNull("should not have received any message", text);
            }
        }
    }

    protected static ModelNode execute(ModelControllerClient client, ModelNode operation) throws Exception {
        //System.out.println("operation = " + operation);
        ModelNode response = client.execute(operation);
        //System.out.println("response = " + response);
        boolean success = SUCCESS.equals(response.get(OUTCOME).asString());
        if (success) {
            return response.get(RESULT);
        }
        throw new Exception("Operation failed");
    }

    @Before
    public void setUp() {
        JMSOperations jmsOperations = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
        jmsOperations.createJmsQueue(jmsQueueName, "java:jboss/exported/" + jmsQueueLookup);
        jmsOperations.close();
    }

    @After
    public void tearDown() {
        JMSOperations jmsOperations = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
        jmsOperations.removeJmsQueue(jmsQueueName);
        jmsOperations.close();
    }

    @Test
    public void testExportImportJournal() throws Exception {
        // send a persistent message
        String text = java.util.UUID.randomUUID().toString();
        sendMessage(remoteContext, jmsQueueLookup, text);

        // reload in admin-only mode
        executeReloadAndWaitForCompletion(managementClient, true);

        // export the journal (must be performed in admin-only mode)
        String dumpFilePath = exportJournal();

        // reload in normal mode
        executeReloadAndWaitForCompletion(managementClient, false);

        // remove all messages
        removeAllMessagesFromQueue(jmsQueueName);
        // no message to receive

        receiveMessage(remoteContext, jmsQueueLookup, false, null);

        // import the journal (must be performed in normal mode)
        importJournal(dumpFilePath);

        // check the message is received
        receiveMessage(remoteContext, jmsQueueLookup, true, text);

        // remove the dump file
        File f = new File(dumpFilePath);
        f.delete();


    }

    private void removeAllMessagesFromQueue(String jmsQueueName) throws Exception {
        ModelNode removeAllMessagesOp = new ModelNode();
        removeAllMessagesOp.get(OP_ADDR).add("subsystem", "messaging-activemq");
        removeAllMessagesOp.get(OP_ADDR).add("server", "default");
        removeAllMessagesOp.get(OP_ADDR).add("jms-queue", jmsQueueName);

        removeAllMessagesOp.get(OP).set("remove-messages");
        execute(managementClient.getControllerClient(), removeAllMessagesOp);
    }

    private String exportJournal() throws Exception {
        ModelNode exportJournalOp = new ModelNode();
        exportJournalOp.get(OP_ADDR).add("subsystem", "messaging-activemq");
        exportJournalOp.get(OP_ADDR).add("server", "default");
        exportJournalOp.get(OP).set("export-journal");
        ModelNode result = execute(managementClient.getControllerClient(), exportJournalOp);
        //System.out.println("result = " + result);
        String dumpFilePath = result.asString();
        return dumpFilePath;
    }

    private void importJournal(String dumpFilePath) throws Exception {
        ModelNode importJournalOp = new ModelNode();
        importJournalOp.get(OP_ADDR).add("subsystem", "messaging-activemq");
        importJournalOp.get(OP_ADDR).add("server", "default");
        importJournalOp.get(OP).set("import-journal");
        importJournalOp.get("file").set(dumpFilePath);
        execute(managementClient.getControllerClient(), importJournalOp);
    }


}
