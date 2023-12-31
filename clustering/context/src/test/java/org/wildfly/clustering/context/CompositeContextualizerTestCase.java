/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.context;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.junit.Test;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;

/**
 * @author Paul Ferraro
 */
public class CompositeContextualizerTestCase {
    @Test
    public void test() {
        Contextualizer contextualizer1 = mock(Contextualizer.class);
        Contextualizer contextualizer2 = mock(Contextualizer.class);
        Contextualizer contextualizer3 = mock(Contextualizer.class);

        Contextualizer contextualizer = new CompositeContextualizer(contextualizer1, contextualizer2, contextualizer3);

        Runnable runner = mock(Runnable.class);
        Runnable contextualRunner1 = mock(Runnable.class);
        Runnable contextualRunner2 = mock(Runnable.class);
        Runnable contextualRunner3 = mock(Runnable.class);

        when(contextualizer1.contextualize(runner)).thenReturn(contextualRunner1);
        when(contextualizer2.contextualize(contextualRunner1)).thenReturn(contextualRunner2);
        when(contextualizer3.contextualize(contextualRunner2)).thenReturn(contextualRunner3);

        assertSame(contextualRunner3, contextualizer.contextualize(runner));

        ExceptionRunnable<Exception> exceptionRunner = mock(ExceptionRunnable.class);
        ExceptionRunnable<Exception> contextualExceptionRunner1 = mock(ExceptionRunnable.class);
        ExceptionRunnable<Exception> contextualExceptionRunner2 = mock(ExceptionRunnable.class);
        ExceptionRunnable<Exception> contextualExceptionRunner3 = mock(ExceptionRunnable.class);

        when(contextualizer1.contextualize(exceptionRunner)).thenReturn(contextualExceptionRunner1);
        when(contextualizer2.contextualize(contextualExceptionRunner1)).thenReturn(contextualExceptionRunner2);
        when(contextualizer3.contextualize(contextualExceptionRunner2)).thenReturn(contextualExceptionRunner3);

        assertSame(contextualExceptionRunner3, contextualizer.contextualize(exceptionRunner));

        Callable<Object> caller = mock(Callable.class);
        Callable<Object> contextualCaller1 = mock(Callable.class);
        Callable<Object> contextualCaller2 = mock(Callable.class);
        Callable<Object> contextualCaller3 = mock(Callable.class);

        when(contextualizer1.contextualize(caller)).thenReturn(contextualCaller1);
        when(contextualizer2.contextualize(contextualCaller1)).thenReturn(contextualCaller2);
        when(contextualizer3.contextualize(contextualCaller2)).thenReturn(contextualCaller3);

        assertSame(contextualCaller3, contextualizer.contextualize(caller));

        Supplier<Object> supplier = mock(Supplier.class);
        Supplier<Object> contextualSupplier1 = mock(Supplier.class);
        Supplier<Object> contextualSupplier2 = mock(Supplier.class);
        Supplier<Object> contextualSupplier3 = mock(Supplier.class);

        when(contextualizer1.contextualize(supplier)).thenReturn(contextualSupplier1);
        when(contextualizer2.contextualize(contextualSupplier1)).thenReturn(contextualSupplier2);
        when(contextualizer3.contextualize(contextualSupplier2)).thenReturn(contextualSupplier3);

        assertSame(contextualSupplier3, contextualizer.contextualize(supplier));

        ExceptionSupplier<Object, Exception> exceptionSupplier = mock(ExceptionSupplier.class);
        ExceptionSupplier<Object, Exception> contextualExceptionSupplier1 = mock(ExceptionSupplier.class);
        ExceptionSupplier<Object, Exception> contextualExceptionSupplier2 = mock(ExceptionSupplier.class);
        ExceptionSupplier<Object, Exception> contextualExceptionSupplier3 = mock(ExceptionSupplier.class);

        when(contextualizer1.contextualize(exceptionSupplier)).thenReturn(contextualExceptionSupplier1);
        when(contextualizer2.contextualize(contextualExceptionSupplier1)).thenReturn(contextualExceptionSupplier2);
        when(contextualizer3.contextualize(contextualExceptionSupplier2)).thenReturn(contextualExceptionSupplier3);

        assertSame(contextualExceptionSupplier3, contextualizer.contextualize(exceptionSupplier));
    }
}
