/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.xts.wsba.coordinatorcompletion.client;

import static org.jboss.as.test.xts.util.EventLogEvent.CANCEL;
import static org.jboss.as.test.xts.util.EventLogEvent.CLOSE;
import static org.jboss.as.test.xts.util.EventLogEvent.COMPENSATE;
import static org.jboss.as.test.xts.util.EventLogEvent.COMPLETE;
import static org.jboss.as.test.xts.util.EventLogEvent.CONFIRM_COMPLETED;
import static org.jboss.as.test.xts.util.ServiceCommand.APPLICATION_EXCEPTION;
import static org.jboss.as.test.xts.util.ServiceCommand.CANNOT_COMPLETE;
import static org.jboss.as.test.xts.util.ServiceCommand.SYSTEM_EXCEPTION_ON_COMPLETE;

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.xts.base.BaseFunctionalTest;
import org.jboss.as.test.xts.base.TestApplicationException;
import org.jboss.as.test.xts.util.DeploymentHelper;
import org.jboss.as.test.xts.util.EventLog;
import org.jboss.as.test.xts.util.EventLogEvent;
import org.jboss.as.test.xts.wsba.coordinatorcompletion.service.BACoordinatorCompletion;
import org.jboss.as.test.xts.wsba.coordinatorcompletion.service.BACoordinatorCompletionService1;
import org.jboss.as.test.xts.wsba.coordinatorcompletion.service.BACoordinatorCompletionService2;
import org.jboss.as.test.xts.wsba.coordinatorcompletion.service.BACoordinatorCompletionService3;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.arjuna.mw.wst11.UserBusinessActivity;
import com.arjuna.mw.wst11.UserBusinessActivityFactory;

/**
 * XTS business activities - coordinator completition test case
 */
@RunWith(Arquillian.class)
public class BACoordinatorCompletionTestCase extends BaseFunctionalTest {
    private UserBusinessActivity uba;
    private BACoordinatorCompletion client1, client2, client3;

    @Inject
    private EventLog eventLog;

    public static final String ARCHIVE_NAME = "wsba-coordinatorcompletition-test";

    @Deployment
    public static WebArchive createTestArchive() {
        return DeploymentHelper.getInstance().getWebArchiveWithPermissions(ARCHIVE_NAME)
                .addPackage(BACoordinatorCompletion.class.getPackage())
                .addPackage(BACoordinatorCompletionClient.class.getPackage())
                .addPackage(EventLog.class.getPackage())
                .addPackage(BaseFunctionalTest.class.getPackage())
                .addAsResource("context-handlers.xml")
                .addAsManifestResource(new StringAsset("Dependencies: org.jboss.xts,org.jboss.jts\n"), "MANIFEST.MF");
    }

    @Before
    public void setupTest() throws Exception {
        uba = UserBusinessActivityFactory.userBusinessActivity();
        client1 = BACoordinatorCompletionClient.newInstance("BACoordinatorCompletionService1");
        client2 = BACoordinatorCompletionClient.newInstance("BACoordinatorCompletionService2");
        client3 = BACoordinatorCompletionClient.newInstance("BACoordinatorCompletionService3");
    }

    protected EventLog getEventLog() {
        return eventLog;
    }

    @After
    public void teardownTest() throws Exception {
        getEventLog().clear();
        cancelIfActive(uba);
    }

    @Test
    public void testWSBACoordinatorSingle() throws Exception {
        uba.begin();
        client1.saveData();
        uba.close();

        assertEventLogClient1(COMPLETE, CONFIRM_COMPLETED, CLOSE);
    }

    @Test
    public void testWSBACoordinatorSimple() throws Exception {
        uba.begin();
        client1.saveData();
        client2.saveData();
        client3.saveData();
        uba.close();

        assertEventLogClient1(COMPLETE, CONFIRM_COMPLETED, CLOSE);
        assertEventLogClient2(COMPLETE, CONFIRM_COMPLETED, CLOSE);
        assertEventLogClient3(COMPLETE, CONFIRM_COMPLETED, CLOSE);
    }

    @Test
    public void testWSBACoordinatorCannotComplete() throws Exception {
        try {
            uba.begin();
            client1.saveData();
            client2.saveData(CANNOT_COMPLETE);
            client3.saveData();

            Assert.fail("Exception should have been thrown by now");
        } catch (jakarta.xml.ws.soap.SOAPFaultException sfe) {
            // This is OK - exception expected
            // P2 set transaction status to ABORT_ONLY
            // P3 enlist is failed with WrongStateException and can't be done
            // It needs call uba.cancel() to rollback the transaction
            uba.cancel();
        } finally {
            assertEventLogClient1(CANCEL);
            assertEventLogClient2();
            assertEventLogClient3();
        }
    }

    @Test
    public void testWSBACoordinatorClientCancel() throws Exception {
        uba.begin();
        client1.saveData();
        client2.saveData();
        client3.saveData();
        uba.cancel();

        assertEventLogClient1(CANCEL);
        assertEventLogClient2(CANCEL);
        assertEventLogClient3(CANCEL);
    }

    @Test
    public void testWSBACoordinatorApplicationException() throws Exception {
        try {
            uba.begin();
            client1.saveData();
            client2.saveData(APPLICATION_EXCEPTION);
            Assert.fail("Exception should have been thrown by now");
        } catch (TestApplicationException e) {
            // This is OK - exception expected
        } finally {
            client3.saveData();
            // TM can't know this application exception, so it needs cancel.
            uba.cancel();
        }

        assertEventLogClient1(CANCEL);
        assertEventLogClient2(CANCEL);
        assertEventLogClient3(CANCEL);
    }

    @Test
    public void testWSBACoordinatorCompletionFailToComplete() throws Exception {
        try {
            uba.begin();
            client1.saveData();
            client2.saveData(SYSTEM_EXCEPTION_ON_COMPLETE);
            client3.saveData();
            uba.close();
            Assert.fail("Exception should have been thrown by now");
        } catch (com.arjuna.wst.TransactionRolledBackException trbe) {
            // This is OK - exception expected
        } finally {
            assertEventLogClient1(COMPLETE, CONFIRM_COMPLETED, COMPENSATE);
            assertEventLogClient2(COMPLETE, CANCEL);
            assertEventLogClient3(CANCEL);
        }
    }

    // --- assert methods
    // --- they take event log names from the service called by particular client
    private void assertEventLogClient1(EventLogEvent... expectedOrder) {
        assertEventLog(BACoordinatorCompletionService1.SERVICE_EVENTLOG_NAME, expectedOrder);
    }

    private void assertEventLogClient2(EventLogEvent... expectedOrder) {
        assertEventLog(BACoordinatorCompletionService2.SERVICE_EVENTLOG_NAME, expectedOrder);
    }

    private void assertEventLogClient3(EventLogEvent... expectedOrder) {
        assertEventLog(BACoordinatorCompletionService3.SERVICE_EVENTLOG_NAME, expectedOrder);
    }
}
