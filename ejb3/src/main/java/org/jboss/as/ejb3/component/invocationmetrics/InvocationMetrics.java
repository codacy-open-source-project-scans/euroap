/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.component.invocationmetrics;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class InvocationMetrics {
    public static class Values {
        final long invocations;
        final long executionTime;
        final long waitTime;

        private Values(final long invocations, final long waitTime, final long executionTime) {
            this.invocations = invocations;
            this.executionTime = executionTime;
            this.waitTime = waitTime;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public long getInvocations() {
            return invocations;
        }

        public long getWaitTime() {
            return waitTime;
        }
    }

    private final AtomicReference<Values> values = new AtomicReference<Values>(new Values(0, 0, 0));
    private final AtomicLong concurrent = new AtomicLong(0);
    private final AtomicLong peakConcurrent = new AtomicLong(0);

    private final ConcurrentMap<String, AtomicReference<Values>> methods = new ConcurrentHashMap<String, AtomicReference<Values>>();

    void finishInvocation(final Method method, final long invocationWaitTime, final long invocationExecutionTime) {
        concurrent.decrementAndGet();
        for(;;) {
            final Values oldv = values.get();
            final Values newv = new Values(oldv.invocations + 1, oldv.waitTime + invocationWaitTime, oldv.executionTime + invocationExecutionTime);
            if (values.compareAndSet(oldv, newv))
                break;
        }
        final AtomicReference<Values> methodValues = ref(methods, method.getName());
        for (;;) {
            final Values oldv = methodValues.get();
            final Values newv = new Values(oldv.invocations + 1, oldv.waitTime + invocationWaitTime, oldv.executionTime + invocationExecutionTime);
            if (methodValues.compareAndSet(oldv, newv))
                break;
        }
    }

    private static AtomicReference<Values> ref(final ConcurrentMap<String, AtomicReference<Values>> map, final String key) {
        AtomicReference<Values> ref = map.get(key);
        if (ref == null) {
            ref = new AtomicReference<Values>(new Values(0, 0, 0));
            final AtomicReference<Values> prevRef = map.putIfAbsent(key, ref);
            if (prevRef != null)
                ref = prevRef;
        }
        return ref;
    }

    public long getConcurrent() {
        return concurrent.get();
    }

    public long getExecutionTime() {
        return values.get().executionTime;
    }

    public long getInvocations() {
        return values.get().invocations;
    }

    public Map<String, Values> getMethods() {
        return new AbstractMap<String, Values>() {
            @Override
            public Set<Entry<String, Values>> entrySet() {
                return new AbstractSet<Entry<String, Values>>() {
                    @Override
                    public Iterator<Entry<String, Values>> iterator() {
                        final Iterator<Entry<String, AtomicReference<Values>>> delegate = methods.entrySet().iterator();
                        return new Iterator<Entry<String, Values>>() {
                            @Override
                            public boolean hasNext() {
                                return delegate.hasNext();
                            }

                            @Override
                            public Entry<String, Values> next() {
                                final Entry<String, AtomicReference<Values>> next = delegate.next();
                                return new Entry<String, Values>() {
                                    @Override
                                    public String getKey() {
                                        return next.getKey();
                                    }

                                    @Override
                                    public Values getValue() {
                                        return next.getValue().get();
                                    }

                                    @Override
                                    public Values setValue(final Values value) {
                                        throw new UnsupportedOperationException("NYI");
                                    }
                                };
                            }

                            @Override
                            public void remove() {
                                throw new UnsupportedOperationException("NYI");
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return methods.size();
                    }
                };
            }
        };
    }

    public long getPeakConcurrent() {
        return peakConcurrent.get();
    }

    public long getWaitTime() {
        return values.get().waitTime;
    }

    void startInvocation() {
        final long v = concurrent.incrementAndGet();
        // concurrent might decrement here, but we take that missing peak for granted.
        if (peakConcurrent.get() < v)
            peakConcurrent.incrementAndGet();
    }
}
