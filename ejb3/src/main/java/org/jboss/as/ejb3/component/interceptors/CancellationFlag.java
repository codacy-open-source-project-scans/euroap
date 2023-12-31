/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.component.interceptors;

import java.util.concurrent.atomic.AtomicInteger;

import org.wildfly.common.Assert;

/**
 * An invocation cancellation flag.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class CancellationFlag {

    private final AtomicInteger stateRef = new AtomicInteger(0);

    private static final int ST_WAITING             = 0;
    private static final int ST_STARTED             = 1;
    private static final int ST_STARTED_FLAG_SET    = 2;
    private static final int ST_CANCELLED           = 3;
    private static final int ST_CANCELLED_FLAG_SET  = 4;

    /**
     * Construct a new instance.
     */
    public CancellationFlag() {
    }

    /**
     * Attempt to cancel the corresponding invocation.
     *
     * @param setFlag {@code true} to set the Jakarta Enterprise Beans context cancellation flag (or equivalent), {@code false} otherwise
     * @return {@code true} if the invocation was definitely cancelled, or {@code false} if it was not cancelled or it could not be determined if it was cancelled
     */
    public boolean cancel(boolean setFlag) {
        final AtomicInteger stateRef = this.stateRef;
        int oldVal, newVal;
        do {
            oldVal = stateRef.get();
            if (oldVal == ST_WAITING) {
                newVal = ST_CANCELLED;
            } else if (oldVal == ST_CANCELLED) {
                if (! setFlag) {
                    return true;
                }
                newVal = ST_CANCELLED_FLAG_SET;
            } else if (oldVal == ST_CANCELLED_FLAG_SET) {
                // do nothing
                return true;
            } else if (oldVal == ST_STARTED) {
                if (! setFlag) {
                    return false;
                }
                newVal = ST_STARTED_FLAG_SET;
            } else {
                assert oldVal == ST_STARTED_FLAG_SET;
                return false;
            }
        } while (! stateRef.compareAndSet(oldVal, newVal));
        return newVal == ST_CANCELLED || newVal == ST_CANCELLED_FLAG_SET;
    }

    /**
     * Attempt to determine whether the invocation should proceed or whether it should be cancelled.  This method should only
     * be called once per flag instance.
     *
     * @return {@code true} if the invocation should proceed, or {@code false} if it was cancelled
     */
    public boolean runIfNotCancelled() {
        final AtomicInteger stateRef = this.stateRef;
        int oldVal;
        do {
            oldVal = stateRef.get();
            if (oldVal == ST_CANCELLED || oldVal == ST_CANCELLED_FLAG_SET) {
                return false;
            } else if (oldVal != ST_WAITING) {
                throw Assert.unreachableCode();
            }
        } while (! stateRef.compareAndSet(oldVal, ST_STARTED));
        return true;
    }

    /**
     * Determine if the cancel flag was set.
     *
     * @return {@code true} if the flag was set, {@code false} otherwise
     */
    public boolean isCancelFlagSet() {
        final int state = stateRef.get();
        return state == ST_STARTED_FLAG_SET || state == ST_CANCELLED_FLAG_SET;
    }

    /**
     * Determine if the request was cancelled before it could complete.
     *
     * @return {@code true} if the request was cancelled, {@code false} otherwise
     */
    public boolean isCancelled() {
        final int state = stateRef.get();
        return state == ST_CANCELLED || state == ST_CANCELLED_FLAG_SET;
    }
}
