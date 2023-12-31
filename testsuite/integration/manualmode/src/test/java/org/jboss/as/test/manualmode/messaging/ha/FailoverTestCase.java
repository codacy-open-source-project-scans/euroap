/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.messaging.ha;


import javax.naming.InitialContext;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.logging.Logger;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 */
public abstract class FailoverTestCase extends AbstractMessagingHATestCase {
    private final Logger log = Logger.getLogger(FailoverTestCase.class);

    protected final String jmsQueueName = "FailoverTestCase-Queue";
    protected final String jmsQueueLookup = "jms/" + jmsQueueName;

    @Test
    public void testBackupActivation() throws Exception {
        ModelControllerClient client2 = createClient2();
        JMSOperations jmsOperations2 = JMSOperationsProvider.getInstance(client2);
        checkJMSQueue(jmsOperations2, jmsQueueName, false);

        InitialContext context1 = createJNDIContextFromServer1();
        sendAndReceiveMessage(context1, jmsQueueLookup);
        // send a message to server1 before it is stopped
        String text = "sent to server1, received from server2 (after failover)";
        sendMessage(context1, jmsQueueLookup, text);
        context1.close();

        testPrimaryInSyncWithReplica(createClient1());
        testSecondaryInSyncWithReplica(client2);
        log.trace("===================");
        log.trace("STOP SERVER1...");
        log.trace("===================");
        container.stop(SERVER1);

        // let some time for the backup to detect the failure
        waitForHornetQServerActivation(jmsOperations2, true);
        checkJMSQueue(jmsOperations2, jmsQueueName, true);
        testSecondaryOutOfSyncWithReplica(client2);

        InitialContext context2 = createJNDIContextFromServer2();
        // receive the message that was sent to server1 before failover occurs
        receiveMessage(context2, jmsQueueLookup, text);
        sendAndReceiveMessage(context2, jmsQueueLookup);
        String text2 = "sent to server2, received from server 1 (after failback)";
        sendMessage(context2, jmsQueueLookup, text2);
        context2.close();
        testSecondaryOutOfSyncWithReplica(client2);

        log.trace("====================");
        log.trace("START SERVER1...");
        log.trace("====================");
        // restart the live server
        container.start(SERVER1);

        // let some time for the backup to detect the live node and failback
        ModelControllerClient client1 = createClient1();
        JMSOperations jmsOperations1 = JMSOperationsProvider.getInstance(client1);
        waitForHornetQServerActivation(jmsOperations1, true);
        checkHornetQServerStartedAndActiveAttributes(jmsOperations1, true, true);

        // let some time for the backup to detect the failure
        waitForHornetQServerActivation(jmsOperations2, false);
        // backup server has been restarted in passive mode
        checkHornetQServerStartedAndActiveAttributes(jmsOperations2, true, false);

        checkJMSQueue(jmsOperations2, jmsQueueName, false);

        context1 = createJNDIContextFromServer1();
        // receive the message that was sent to server2 before failback
        receiveMessage(context1, jmsQueueLookup, text2);
        // send & receive a message from server1
        sendAndReceiveMessage(context1, jmsQueueLookup);
        context1.close();

        testPrimaryInSyncWithReplica(client1);
        testSecondaryInSyncWithReplica(client2);
        log.trace("=============================");
        log.trace("RETURN TO NORMAL OPERATION...");
        log.trace("=============================");

        log.trace("===================");
        log.trace("STOP SERVER2...");
        log.trace("===================");
        container.stop(SERVER2);
        testPrimaryOutOfSyncWithReplica(client1);
    }

    @Test
    public void testBackupFailoverAfterFailback() throws Exception {
        ModelControllerClient client2 = createClient2();
        JMSOperations backupJMSOperations = JMSOperationsProvider.getInstance(client2);
        checkJMSQueue(backupJMSOperations, jmsQueueName, false);

        InitialContext context1 = createJNDIContextFromServer1();
        String text = "sent to server1, received from server2 (after failover)";
        sendMessage(context1, jmsQueueLookup, text);
        context1.close();

        testPrimaryInSyncWithReplica(createClient1());
        testSecondaryInSyncWithReplica(client2);
        log.trace("############## 1 #############");
        //listSharedStoreDir();

        log.trace("===================");
        log.trace("STOP SERVER1...");
        log.trace("===================");
        container.stop(SERVER1);

        log.trace("############## 2 #############");
        //listSharedStoreDir();

        // let some time for the backup to detect the failure
        waitForHornetQServerActivation(backupJMSOperations, true);
        checkJMSQueue(backupJMSOperations, jmsQueueName, true);
        testSecondaryOutOfSyncWithReplica(client2);

        InitialContext context2 = createJNDIContextFromServer2();
        // receive the message that was sent to server1 before failover occurs
        receiveMessage(context2, jmsQueueLookup, text);
        // send a message to server2 before server1 fails back
        String text2 = "sent to server2, received from server 1 (after failback)";
        sendMessage(context2, jmsQueueLookup, text2);
        context2.close();
        testSecondaryOutOfSyncWithReplica(client2);

        log.trace("====================");
        log.trace("START SERVER1...");
        log.trace("====================");
        // restart the live server
        container.start(SERVER1);
        // let some time for the backup to detect the live node and failback
        ModelControllerClient client1 = createClient1();
        JMSOperations liveJMSOperations = JMSOperationsProvider.getInstance(client1);
        waitForHornetQServerActivation(liveJMSOperations, true);
        checkHornetQServerStartedAndActiveAttributes(liveJMSOperations, true, true);

        // let some time for the backup to detect the failure
        waitForHornetQServerActivation(backupJMSOperations, false);
        // backup server has been restarted in passive mode
        checkHornetQServerStartedAndActiveAttributes(backupJMSOperations, true, false);

        checkJMSQueue(backupJMSOperations, jmsQueueName, false);

        context1 = createJNDIContextFromServer1();
        // receive the message that was sent to server2 before failback
        receiveMessage(context1, jmsQueueLookup, text2);
        String text3 = "sent to server1, received from server2 (after 2nd failover)";
        // send a message to server1 before it is stopped a 2nd time
        sendMessage(context1, jmsQueueLookup, text3);
        context1.close();

        testPrimaryInSyncWithReplica(client1);
        testSecondaryInSyncWithReplica(client2);
        log.trace("==============================");
        log.trace("STOP SERVER1 A 2ND TIME...");
        log.trace("==============================");
        // shutdown server1 a 2nd time
        container.stop(SERVER1);

        // let some time for the backup to detect the failure
        waitForHornetQServerActivation(backupJMSOperations, true);
        checkHornetQServerStartedAndActiveAttributes(backupJMSOperations, true, true);
        checkJMSQueue(backupJMSOperations, jmsQueueName, true);

        context2 = createJNDIContextFromServer2();
        // receive the message that was sent to server1 before failover occurs a 2nd time
        receiveMessage(context2, jmsQueueLookup, text3);
        context2.close();

        log.trace("====================");
        log.trace("START SERVER1 A 2ND TIME...");
        log.trace("====================");
        // restart the live server
        container.start(SERVER1);
        // let some time for the backup to detect the live node and failback
        client1 = createClient1();
        liveJMSOperations = JMSOperationsProvider.getInstance(client1);
        waitForHornetQServerActivation(liveJMSOperations, true);
        checkHornetQServerStartedAndActiveAttributes(liveJMSOperations, true, true);

        // let some time for the backup to detect the failure
        waitForHornetQServerActivation(backupJMSOperations, false);
        // backup server has been restarted in passive mode
        checkHornetQServerStartedAndActiveAttributes(backupJMSOperations, true, false);

        checkJMSQueue(backupJMSOperations, jmsQueueName, false);

        context1 = createJNDIContextFromServer1();
        // There should be no message to receive as it was consumed from the backup
        receiveNoMessage(context1, jmsQueueLookup);

    }

    protected void testPrimaryInSyncWithReplica(ModelControllerClient client) throws Exception {}

    protected void testSecondaryInSyncWithReplica(ModelControllerClient client) throws Exception  {}

    protected void testPrimaryOutOfSyncWithReplica(ModelControllerClient client) throws Exception {}

    protected void testSecondaryOutOfSyncWithReplica(ModelControllerClient client) throws Exception  {}
}
