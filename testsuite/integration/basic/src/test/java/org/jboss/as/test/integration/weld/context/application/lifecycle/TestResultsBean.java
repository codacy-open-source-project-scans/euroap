/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.weld.context.application.lifecycle;

import org.jboss.as.test.shared.TimeoutUtil;

import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author emmartins
 */
@Startup
@Singleton
public class TestResultsBean implements TestResults {

    private CountDownLatch latch;

    private boolean cdiBeanInitialized;
    private boolean cdiBeanBeforeDestroyed;
    private boolean cdiBeanDestroyed;

    private boolean ejbBeanInitialized;
    private boolean ejbBeanBeforeDestroyed;
    private boolean ejbBeanDestroyed;

    private boolean servletInitialized;
    private boolean servletBeforeDestroyed;
    private boolean servletDestroyed;

    public boolean isCdiBeanInitialized() {
        return cdiBeanInitialized;
    }

    public void setCdiBeanInitialized(boolean cdiBeanInitialized) {
        this.cdiBeanInitialized = cdiBeanInitialized;
        latch.countDown();
    }

    public boolean isEjbBeanInitialized() {
        return ejbBeanInitialized;
    }

    public void setEjbBeanInitialized(boolean ejbBeanInitialized) {
        this.ejbBeanInitialized = ejbBeanInitialized;
        latch.countDown();
    }

    public boolean isServletInitialized() {
        return servletInitialized;
    }

    public void setServletInitialized(boolean servletInitialized) {
        this.servletInitialized = servletInitialized;
        latch.countDown();
    }

    // ---

    public boolean isCdiBeanBeforeDestroyed() {
        return cdiBeanBeforeDestroyed;
    }

    public void setCdiBeanBeforeDestroyed(boolean cdiBeanBeforeDestroyed) {
        this.cdiBeanBeforeDestroyed = cdiBeanBeforeDestroyed;
        latch.countDown();
    }

    public boolean isEjbBeanBeforeDestroyed() {
        return ejbBeanBeforeDestroyed;
    }

    public void setEjbBeanBeforeDestroyed(boolean ejbBeanBeforeDestroyed) {
        this.ejbBeanBeforeDestroyed = ejbBeanBeforeDestroyed;
        latch.countDown();
    }

    public boolean isServletBeforeDestroyed() {
        return servletBeforeDestroyed;
    }

    public void setServletBeforeDestroyed(boolean servletBeforeDestroyed) {
        this.servletBeforeDestroyed = servletBeforeDestroyed;
        latch.countDown();
    }

    // ---

    public boolean isCdiBeanDestroyed() {
        return cdiBeanDestroyed;
    }

    public void setCdiBeanDestroyed(boolean cdiBeanDestroyed) {
        this.cdiBeanDestroyed = cdiBeanDestroyed;
        latch.countDown();
    }

    public boolean isEjbBeanDestroyed() {
        return ejbBeanDestroyed;
    }

    public void setEjbBeanDestroyed(boolean ejbBeanDestroyed) {
        this.ejbBeanDestroyed = ejbBeanDestroyed;
        latch.countDown();
    }

    public boolean isServletDestroyed() {
        return servletDestroyed;
    }

    public void setServletDestroyed(boolean servletDestroyed) {
        this.servletDestroyed = servletDestroyed;
        latch.countDown();
    }

    // --

    public void setup(int latchCount) {
        if (latch != null) {
            throw new IllegalStateException();
        }
        latch = new CountDownLatch(latchCount);
    }

    public void await(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (latch == null) {
            throw new IllegalStateException();
        }
        try {
            latch.await(TimeoutUtil.adjust(Long.valueOf(timeout).intValue()), timeUnit);
        } finally {
            latch = null;
        }
    }
}
