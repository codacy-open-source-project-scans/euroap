/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.smoke.jms;

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
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.jms.auxiliary.CreateQueueSetupTask;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Basic Jakarta Messaging test using a customly created Jakarta Messaging queue
 *
 * @author <a href="jmartisk@redhat.com">Jan Martiska</a>
 */
@RunWith(Arquillian.class)
@ServerSetup(CreateQueueSetupTask.class)
public class SendToJMSQueueTest {

    private static final Logger logger = Logger.getLogger(SendToJMSQueueTest.class);

    private static final String MESSAGE_TEXT = "Hello world!";

    @Resource(mappedName = "/queue/myAwesomeQueue")
    private Queue queue;

    @Resource(mappedName = "/queue/myAwesomeQueue2")
    private Queue queue2;

    @Resource(mappedName = "/queue/myAwesomeQueue3")
    private Queue queue3;

    @Resource(mappedName = "/ConnectionFactory")
    private ConnectionFactory factory;

    @Deployment
    public static JavaArchive createTestArchive() {
        return ShrinkWrap.create(JavaArchive.class, "test.jar")
                .addPackage(JMSOperations.class.getPackage())
                .addClass(CreateQueueSetupTask.class)
                .addAsManifestResource(
                        EmptyAsset.INSTANCE,
                        ArchivePaths.create("beans.xml"));
    }

    @Test
    public void sendAndReceiveMessage() throws Exception {
        Connection connection = null;
        Session session = null;
        Message receivedMessage = null;

        try {
            // SEND A MESSAGE
            connection = factory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(queue);
            Message message = session.createTextMessage(MESSAGE_TEXT);
            producer.send(message);
            connection.start();
            session.close();
            connection.close();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
            }


            // RECEIVE THE MESSAGE BACK
            connection = factory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(queue);
            connection.start();
            receivedMessage = consumer.receive(5000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // CLEANUP
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
        }

        // ASSERTIONS
        Assert.assertTrue(receivedMessage instanceof TextMessage);
        Assert.assertTrue(((TextMessage) receivedMessage).getText().equals(MESSAGE_TEXT));
    }

    @Test
    public void sendMessageWithClientAcknowledge() throws Exception {
        Connection senderConnection = null;
        Connection consumerConnection = null;
        Session senderSession = null;
        Session consumerSession = null;
        MessageConsumer consumer = null;
        try {
            // CREATE CONSUMER
            logger.trace("******* Creating connection for consumer");
            consumerConnection = factory.createConnection();
            logger.trace("Creating session for consumer");
            consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            logger.trace("Creating consumer");
            consumer = consumerSession.createConsumer(queue2);
            logger.trace("Start session");
            consumerConnection.start();

            // SEND A MESSAGE
            logger.trace("***** Start - sending message to topic");
            senderConnection = factory.createConnection();
            logger.trace("Creating session..");
            senderSession = senderConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            MessageProducer producer = senderSession.createProducer(queue2);
            TextMessage message = senderSession.createTextMessage("Hahaha!");

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
        Message receivedMessage2 = null;
        try {
            logger.trace("Receiving");
            receivedMessage = consumer.receive(5000);
            logger.trace("Received a message");
            receivedMessage.acknowledge();
            consumerSession.recover();
            receivedMessage2 = consumer.receive(5000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (consumerSession != null) {
                consumerSession.close();
            }
            if (consumerConnection != null) {
                consumerConnection.close();
            }
        }

        if (receivedMessage == null) {
            Assert.fail("received null instead of a TextMessage");
        }
        Assert.assertTrue("received a " + receivedMessage.getClass().getName() + " instead of a TextMessage", receivedMessage instanceof TextMessage);
        Assert.assertEquals("Hahaha!", ((TextMessage) receivedMessage).getText());

        if (receivedMessage2 != null) {
            Assert.fail("redelivered=" + String.valueOf(receivedMessage2.getJMSRedelivered()) + ", text=" + ((TextMessage) receivedMessage).getText());
        }
        Assert.assertNull("Message should not have been re-delivered", receivedMessage2);
    }

    @Test
    public void sendMessageWithMissingClientAcknowledge() throws Exception {
        Connection senderConnection = null;
        Connection consumerConnection = null;
        Session senderSession = null;
        Session consumerSession = null;
        MessageConsumer consumer = null;
        try {
            // CREATE SUBSCRIBER
            logger.trace("******* Creating connection for consumer");
            consumerConnection = factory.createConnection();
            logger.trace("Creating session for consumer");
            consumerSession = consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            logger.trace("Creating consumer");
            consumer = consumerSession.createConsumer(queue3);
            logger.trace("Start session");
            consumerConnection.start();

            // SEND A MESSAGE
            logger.trace("***** Start - sending message to topic");
            senderConnection = factory.createConnection();
            logger.trace("Creating session..");
            senderSession = senderConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            MessageProducer producer = senderSession.createProducer(queue3);
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
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
            consumerSession.recover();
            receivedMessage = consumer.receive(5000);
            receivedMessage.acknowledge();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        } finally {
            if (consumerSession != null) {
                consumerSession.close();
            }
            if (consumerConnection != null) {
                consumerConnection.close();
            }
        }

        if (receivedMessage == null) {
            Assert.fail("Message should have been re-delivered, but subsequent attempt to receive it returned null");
        }
        Assert.assertTrue("received a " + receivedMessage.getClass().getName() + " instead of a TextMessage", receivedMessage instanceof TextMessage);
        Assert.assertEquals("Hello world!", ((TextMessage) receivedMessage).getText());
        Assert.assertTrue("Redelivered header should have been set to true", receivedMessage.getJMSRedelivered());
    }

}
