/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.async;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import jakarta.ejb.AsyncResult;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

/**
 * Bean with asynchronous methods.
 *
 * @author Ondrej Chaloupka
 */
@Stateless
@Asynchronous
public class AsyncBeanRemote implements AsyncBeanRemoteInterface {
    @EJB
    AsyncBean asyncBean;

    @Override
    public void asyncMethod() throws InterruptedException {
        AsyncBean.voidMethodCalled = false;
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        asyncBean.asyncMethod(latch, latch2);
        latch.countDown();
        latch2.await();
        if(!AsyncBean.voidMethodCalled) {
            throw new IllegalArgumentException("voidMethodCalled");
        }
    }

    @Override
    public Future<Boolean> futureMethod() throws InterruptedException, ExecutionException {
        AsyncBean.futureMethodCalled = false;
        final CountDownLatch latch = new CountDownLatch(1);
        final Future<Boolean> future = asyncBean.futureMethod(latch);
        latch.countDown();
        return new AsyncResult<Boolean>(future.get());
    }
}
