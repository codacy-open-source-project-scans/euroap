/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.stateful.passivation;

import java.io.Serializable;

import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.interceptor.InvocationContext;

/**
 * @author Stuart Douglas
 */
public class PassivationInterceptor implements Serializable {
    private static final long serialVersionUID = 1L;
    private static volatile Object postActivateTarget, prePassivateTarget;

    @PostActivate
    public void postActivate(final InvocationContext ctx) throws Exception {
        postActivateTarget = ctx.getTarget();
        ctx.proceed();
    }
    @PrePassivate
    public void prePassivate(final InvocationContext ctx) throws Exception {
        prePassivateTarget = ctx.getTarget();
        ctx.proceed();
    }

    public static void reset() {
        postActivateTarget = prePassivateTarget = null;
    }

    public static Object getPostActivateTarget() {
        return postActivateTarget;
    }

    public static Object getPrePassivateTarget() {
        return prePassivateTarget;
    }
}
