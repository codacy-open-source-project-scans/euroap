/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ee.injection.resource.enventry;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.MapMessage;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import javax.naming.InitialContext;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Migration test from EJB Testsuite (ejbthree-985 + enventry) to AS7 [JIRA
 * JBQA-5483]. Test to see if optional env-entry-value works (16.4.1.3). Testing
 * of behaviour of environment variables in ejb-jar.xml.
 *
 * @author Carlo de Wolf, William DeCoste, Ondrej Chaloupka
 */
@RunWith(Arquillian.class)
@ServerSetup({EnvEntryTestCase.JmsQueueSetup.class})
public class EnvEntryTestCase {

    @ArquillianResource
    InitialContext ctx;

    static class JmsQueueSetup implements ServerSetupTask {

        private JMSOperations jmsAdminOperations;

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            jmsAdminOperations = JMSOperationsProvider.getInstance(managementClient);
            jmsAdminOperations.createJmsQueue("queue/testEnvEntry", "java:jboss/queue/testEnvEntry");
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            if (jmsAdminOperations != null) {
                jmsAdminOperations.removeJmsQueue("queue/testEnvEntry");
                jmsAdminOperations.close();
            }
        }
    }

    @Deployment
    public static Archive<?> deploymentOptional() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "env-entry-test.jar")
                .addClasses(
                        ExtendedTestEnvEntryBean.class,
                        OptionalEnvEntry.class,
                        OptionalEnvEntryBean.class,
                        TestEnvEntry.class,
                        TestEnvEntryBean.class,
                        TestEnvEntryBeanBase.class,
                        TestEnvEntryMDBean.class,
                        EnvEntryTestCase.class,
                        JmsQueueSetup.class)
                .addPackage(JMSOperations.class.getPackage())
                .addAsManifestResource(EnvEntryTestCase.class.getPackage(), "ejb-jar.xml", "ejb-jar.xml");
        return jar;
    }

    // Deployment optional
    private OptionalEnvEntry lookupEnvEntryBean() throws Exception {
        return (OptionalEnvEntry) ctx.lookup("java:module/OptionalEnvEntryBean");
    }

    @Test
    public void test() throws Exception {
        OptionalEnvEntry bean = lookupEnvEntryBean();
        Double actual = bean.getEntry();
        // 1.1 is defined in OptionalEnvEntryBean
        Assert.assertEquals(new Double(1.1), actual);
    }

    @Test
    public void testLookup() throws Exception {
        OptionalEnvEntry bean = lookupEnvEntryBean();
        bean.checkLookup();
    }

    // Deployment enventry
    @Test
    public void testEnvEntries() throws Exception {
        TestEnvEntry test = (TestEnvEntry) ctx.lookup("java:module/TestEnvEntry!" + TestEnvEntry.class.getName());
        Assert.assertNotNull(test);

        int maxExceptions = test.getMaxExceptions();
        Assert.assertEquals(15, maxExceptions);

        int minExceptions = test.getMinExceptions();
        Assert.assertEquals(5, minExceptions);

        int numExceptions = test.getNumExceptions();
        Assert.assertEquals(10, numExceptions);

        TestEnvEntry etest = (TestEnvEntry) ctx.lookup("java:module/ExtendedTestEnvEntry!" + TestEnvEntry.class.getName());
        Assert.assertNotNull(etest);

        maxExceptions = etest.getMaxExceptions();
        Assert.assertEquals(14, maxExceptions);

        minExceptions = etest.getMinExceptions();
        Assert.assertEquals(6, minExceptions);

        numExceptions = etest.getNumExceptions();
        Assert.assertEquals(11, numExceptions);
    }

    @Test
    public void testEnvEntriesMDB() throws Exception {
        ConnectionFactory factory = (ConnectionFactory) ctx.lookup("ConnectionFactory");
        Connection con = factory.createConnection();
        try {
            Destination dest = (Destination) ctx.lookup("java:jboss/queue/testEnvEntry");

            Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(dest);

            Queue replyQueue = session.createTemporaryQueue();
            MessageConsumer consumer = session.createConsumer(replyQueue);

            con.start();

            TextMessage msg = session.createTextMessage();
            msg.setJMSReplyTo(replyQueue);
            msg.setText("This is message one");
            producer.send(msg);

            MapMessage replyMsg = (MapMessage) consumer.receive(5000);
            Assert.assertNotNull(replyMsg);
            Assert.assertEquals(16, replyMsg.getInt("maxExceptions"));
            Assert.assertEquals(12, replyMsg.getInt("numExceptions"));
            Assert.assertEquals(7, replyMsg.getInt("minExceptions"));
        } finally {
            con.close();
        }
    }

    @Test
    public void testJNDI() throws Exception {
        TestEnvEntry test = (TestEnvEntry) ctx.lookup("java:module/TestEnvEntry!" + TestEnvEntry.class.getName());
        Assert.assertNotNull(test);

        Assert.assertEquals(15, test.checkJNDI());
    }
}
