/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.timerservice.expired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.Resource;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.NoMoreTimeoutsException;
import jakarta.ejb.NoSuchObjectLocalException;
import jakarta.ejb.Singleton;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;

import org.jboss.logging.Logger;

/**
 * @author Jaikiran Pai
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class SingletonBean {

    private static final Logger log = Logger.getLogger(SingletonBean.class);
    private static int TIMER_CALL_WAITING_S = 5;

    @Resource
    private TimerService timerService;

    private Timer timer;

    private CountDownLatch timeoutNotifyingLatch;
    private CountDownLatch timeoutWaiter;

    public void createSingleActionTimer(final long delay, final TimerConfig config,
                                        CountDownLatch timeoutNotifyingLatch, CountDownLatch timeoutWaiter) {
        this.timer = this.timerService.createSingleActionTimer(delay, config);
        this.timeoutNotifyingLatch = timeoutNotifyingLatch;
        this.timeoutWaiter = timeoutWaiter;
    }

    @Timeout
    private void onTimeout(final Timer timer) throws InterruptedException {
        log.trace("Timeout invoked for " + this + " on timer " + timer);
        this.timeoutNotifyingLatch.countDown();
        log.debug("Waiting for timer will be permitted to continue");
        this.timeoutWaiter.await(TIMER_CALL_WAITING_S, TimeUnit.SECONDS);
        log.debug("End of onTimeout on singleton");
    }

    public Timer getTimer() {
        return this.timer;
    }

    public void invokeTimeRemaining() throws NoMoreTimeoutsException, NoSuchObjectLocalException {
        this.timer.getTimeRemaining();
    }

    public void invokeGetNext() throws NoMoreTimeoutsException, NoSuchObjectLocalException {
        this.timer.getNextTimeout();
    }


}
