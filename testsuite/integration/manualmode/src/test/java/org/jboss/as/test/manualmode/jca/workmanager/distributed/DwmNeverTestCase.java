/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.jca.workmanager.distributed;

import jakarta.resource.spi.work.Work;
import jakarta.resource.spi.work.WorkException;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Uses policy = NEVER and selector = MAX_FREE_THREADS.
 *
 * InSequence is necessary since testScheduleWork messes with the thread pools for testNeverPolicy. We'd have to wait
 * for the work instances to actually finish and free the thread pools otherwise. The test methods will still run
 * separately without issues.
 */
@RunWith(Arquillian.class)
@RunAsClient
public class DwmNeverTestCase extends AbstractDwmTestCase {

    @Override
    protected Policy getPolicy() {
        return Policy.NEVER;
    }

    @Override
    protected Selector getSelector() {
        return Selector.MAX_FREE_THREADS;
    }

    /**
     * Does a few instances of short work with {@code policy = NEVER} and expects that they will be executed on the node
     * where started.
     */
    @Test
    @InSequence(1)
    public void testNeverPolicy() throws WorkException {
        int doWorkAccepted = server1Proxy.getDoWorkAccepted();

        for (int i = 0; i < 10; i++) {
            server1Proxy.doWork(new ShortWork());
        }

        Assert.assertTrue("Expected doWorkAccepted = " + (doWorkAccepted + 10) + ", actual: " + server1Proxy.getDoWorkAccepted(),
                server1Proxy.getDoWorkAccepted() == doWorkAccepted + 10);
    }

    /**
     * Schedules several (one more than our max threads) long work instances and verifies that
     * {@link org.jboss.jca.core.api.workmanager.DistributedWorkManager#scheduleWork(Work)} executes work on the local
     * node (Policy.NEVER only selects the local node, regardless of the selector).
     */
    @Test
    @InSequence(2)
    public void testScheduleWork() throws WorkException, InterruptedException {
        int scheduleWorkAcceptedServer1 = server1Proxy.getScheduleWorkAccepted();
        int distributedScheduleWorkAccepted = server2Proxy.getDistributedScheduleWorkAccepted();

        for (int i = 0; i < SRT_MAX_THREADS + 1; i++) {
            server1Proxy.scheduleWork(new LongWork());
        }

        Assert.assertTrue("Work did not finish in the expected time on the expected node",
                waitForScheduleWork(server1Proxy, scheduleWorkAcceptedServer1 + SRT_MAX_THREADS + 1, TimeoutUtil.adjust(WORK_FINISH_MAX_TIMEOUT)));
        Assert.assertTrue("Expected distributedScheduleWorkAccepted = " + (distributedScheduleWorkAccepted + SRT_MAX_THREADS + 1) + " but was: " + server2Proxy.getDistributedScheduleWorkAccepted(),
                server2Proxy.getDistributedScheduleWorkAccepted() == distributedScheduleWorkAccepted + SRT_MAX_THREADS + 1);
    }
}
