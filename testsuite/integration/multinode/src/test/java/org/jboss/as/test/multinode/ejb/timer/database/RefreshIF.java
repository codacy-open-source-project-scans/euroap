/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.multinode.ejb.timer.database;

import java.io.Serializable;
import java.util.List;
import jakarta.ejb.Remote;

@Remote
public interface RefreshIF {
    /**
     * Used to indicate in which node and by which bean the timer is created.
     */
    enum Info {
        /**
         * The timer is created in the client node and by bean 1.
         */
        CLIENT1,

        /**
         * The timer is created in the server node and by bean 1.
         */
        SERVER1,

        /**
         * Indicates that a timer handle should be returned
         */
        RETURN_HANDLE
    }

    /**
     * Creates a timer to expire {@code delay} milliseconds later, with {@code info}
     * as the timer info.
     *
     * @param delay number of milliseconds after which the timer is set to expire
     * @param info timer info
     * @return timer handle for the new timer
     */
    byte[] createTimer(long delay, Serializable info);

    /**
     * Gets all timers after programmatic refresh. Any implementation method
     * should be configured to have an interceptor that enables programmatic
     * timer refresh.
     *
     * @return list of timer info
     */
    List<Serializable> getAllTimerInfoWithRefresh();

    /**
     * Gets all timers after programmatic refresh. Any implementation method
     * should be configured to have an interceptor that enables programmatic
     * timer refresh.
     * <p>
     * This method demonstrates that a bean class can invoke its own business
     * method that enables programmatic timer refresh, without replying on an
     * external client invocation.
     *
     * @return list of timer info
     */
    List<Serializable> getAllTimerInfoWithRefresh2();

    /**
     * Gets all timers without programmatic refresh. Any implementation method
     * should NOT be configured to have an interceptor that enables programmatic
     * timer refresh.
     *
     * @return list of timer info
     */
    List<Serializable> getAllTimerInfoNoRefresh();

    /**
     * Cancels timers of this bean.
     */
    void cancelTimers();

    /**
     * Cancels a timer by its timer handle.
     * @param handle the timer handle for the timer to be cancelled
     */
    void cancelTimer(byte[] handle);
}
