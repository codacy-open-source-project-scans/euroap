/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.multinode.ejb.timer.database;

import jakarta.ejb.Remote;
import java.util.List;

/**
 * @author Stuart Douglas
 */
@Remote
public interface Collector {

    void timerRun(final String nodeName, String info);

    List<TimerData> collect(final int expectedCount);

}
