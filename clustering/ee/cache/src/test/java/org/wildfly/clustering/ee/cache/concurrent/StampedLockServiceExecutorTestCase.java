/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ee.cache.concurrent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.junit.Test;
import org.wildfly.clustering.ee.concurrent.ServiceExecutor;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;

/**
 * @author Paul Ferraro
 */
public class StampedLockServiceExecutorTestCase {

    @Test
    public void testExecuteRunnable() {
        ServiceExecutor executor = new StampedLockServiceExecutor();

        Runnable executeTask = mock(Runnable.class);

        executor.execute(executeTask);

        // Task should run
        verify(executeTask).run();
        reset(executeTask);

        Runnable closeTask = mock(Runnable.class);

        executor.close(closeTask);

        verify(closeTask).run();
        reset(closeTask);

        executor.close(closeTask);

        // Close task should only run once
        verify(closeTask, never()).run();

        executor.execute(executeTask);

        // Task should no longer run since service is closed
        verify(executeTask, never()).run();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteExceptionRunnable() throws Exception {
        ServiceExecutor executor = new StampedLockServiceExecutor();

        ExceptionRunnable<Exception> executeTask = mock(ExceptionRunnable.class);

        executor.execute(executeTask);

        // Task should run
        verify(executeTask).run();
        reset(executeTask);

        doThrow(new Exception()).when(executeTask).run();

        try {
            executor.execute(executeTask);
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertNotNull(e);
        }
        reset(executeTask);

        Runnable closeTask = mock(Runnable.class);

        executor.close(closeTask);

        verify(closeTask).run();
        reset(closeTask);

        executor.close(closeTask);

        // Close task should only run once
        verify(closeTask, never()).run();

        executor.execute(executeTask);

        // Task should no longer run since service is closed
        verify(executeTask, never()).run();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteSupplier() {
        ServiceExecutor executor = new StampedLockServiceExecutor();
        Object expected = new Object();

        Supplier<Object> executeTask = mock(Supplier.class);

        when(executeTask.get()).thenReturn(expected);

        Optional<Object> result = executor.execute(executeTask);

        // Task should run
        assertTrue(result.isPresent());
        assertSame(expected, result.get());
        reset(executeTask);

        Runnable closeTask = mock(Runnable.class);

        executor.close(closeTask);

        verify(closeTask).run();
        reset(closeTask);

        executor.close(closeTask);

        // Close task should only run once
        verify(closeTask, never()).run();

        result = executor.execute(executeTask);

        // Task should no longer run since service is closed
        assertFalse(result.isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteExceptionSupplier() throws Exception {
        ServiceExecutor executor = new StampedLockServiceExecutor();
        Object expected = new Object();

        ExceptionSupplier<Object, Exception> executeTask = mock(ExceptionSupplier.class);

        when(executeTask.get()).thenReturn(expected);

        Optional<Object> result = executor.execute(executeTask);

        // Task should run
        assertTrue(result.isPresent());
        assertSame(expected, result.get());
        reset(executeTask);

        doThrow(new Exception()).when(executeTask).get();

        try {
            executor.execute(executeTask);
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertNotNull(e);
        }
        reset(executeTask);

        Runnable closeTask = mock(Runnable.class);

        executor.close(closeTask);

        verify(closeTask).run();
        reset(closeTask);

        executor.close(closeTask);

        // Close task should only run once
        verify(closeTask, never()).run();

        result = executor.execute(executeTask);

        // Task should no longer run since service is closed
        assertFalse(result.isPresent());
    }

    @Test
    public void concurrent() throws InterruptedException, ExecutionException {
        ServiceExecutor executor = new StampedLockServiceExecutor();

        ExecutorService service = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch executeLatch = new CountDownLatch(1);
            CountDownLatch stopLatch = new CountDownLatch(1);
            Runnable executeTask = () -> {
                try {
                    executeLatch.countDown();
                    stopLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            Future<?> executeFuture = service.submit(() -> executor.execute(executeTask));

            executeLatch.await();

            Runnable closeTask = mock(Runnable.class);

            Future<?> closeFuture = service.submit(() -> executor.close(closeTask));

            Thread.yield();

            // Verify that stop is blocked
            verify(closeTask, never()).run();

            stopLatch.countDown();

            executeFuture.get();
            closeFuture.get();

            // Verify close task was invoked, now that execute task is complete
            verify(closeTask).run();
        } finally {
            service.shutdownNow();
        }
    }
}
