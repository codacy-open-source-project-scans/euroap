/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.timerservice.expired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.ejb.EJB;
import jakarta.ejb.NoMoreTimeoutsException;
import jakarta.ejb.NoSuchObjectLocalException;
import jakarta.ejb.TimerConfig;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Jaikiran Pai
 */
@RunWith(Arquillian.class)
public class ExpiredTimerTestCase {

    private static final int TIMER_CALL_WAITING_S = 5;
    private static final int TIMER_TIMEOUT_TIME_MS = 300;

    private static final Logger log = Logger.getLogger(ExpiredTimerTestCase.class);

    @EJB(mappedName = "java:module/SingletonBean")
    private SingletonBean bean;

    @Deployment
    public static Archive<?> createDeployment() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "expired-timer-test.jar");
        jar.addPackage(ExpiredTimerTestCase.class.getPackage());
        return jar;
    }

    @Test
    public void testInvocationOnExpiredTimer() throws Exception {

        final CountDownLatch timeoutNotifier = new CountDownLatch(1);
        final CountDownLatch timeoutWaiter = new CountDownLatch(1);
        this.bean.createSingleActionTimer(TIMER_TIMEOUT_TIME_MS, new TimerConfig(null, false), timeoutNotifier, timeoutWaiter);

        // wait for the timeout to be invoked
        final boolean timeoutInvoked = timeoutNotifier.await(TIMER_CALL_WAITING_S, TimeUnit.SECONDS);
        Assert.assertTrue("timeout method was not invoked (within " + TIMER_CALL_WAITING_S + " seconds)", timeoutInvoked);

        // the timer stays in timeout method - checking how the invoke of method getNext and getTimeRemaining behave
        try {
            bean.invokeTimeRemaining();
            Assert.fail("Expecting exception " + NoMoreTimeoutsException.class.getSimpleName());
        } catch (NoMoreTimeoutsException e) {
            log.trace("Expected exception " + e.getClass().getSimpleName() + " was thrown on method getTimeRemaining");
        }
        try {
            bean.invokeGetNext();
            Assert.fail("Expecting exception " + NoMoreTimeoutsException.class.getSimpleName());
        } catch (NoMoreTimeoutsException e) {
            log.trace("Expected exception " + e.getClass().getSimpleName() + " was thrown on method getNextTimeout");
        }

        // the timeout can finish
        timeoutWaiter.countDown();

        // as we can't be exactly sure when the timeout method is finished in this moment
        // we invoke in a loop, can check the exception type.
        int count = 0;
        boolean passed = false;
        while (count < 20 && !passed) {
            try {
                bean.invokeTimeRemaining();
                Assert.fail("Expected to fail on invoking on an expired timer");
            } catch (NoSuchObjectLocalException nsole) {
                // expected
                log.trace("Got the expected exception " + nsole);
                passed = true;
            } catch (NoMoreTimeoutsException e) {
                //this will be thrown if the timer is still active
                Thread.sleep(100);
                count++;
            }
        }
        if(!passed) {
            Assert.fail("Got NoMoreTimeoutsException rather than  NoSuchObjectLocalException");
        }
    }
}

