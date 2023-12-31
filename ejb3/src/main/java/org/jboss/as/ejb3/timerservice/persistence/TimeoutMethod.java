/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice.persistence;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * TimeoutMethod
 *
 * @author Jaikiran Pai
 * @author Stuart Douglas
 */
public class TimeoutMethod implements Serializable {
    private static final long serialVersionUID = 3306711742026221772L;

    /**
     * Constant string value to indicate that the timeout method has 1 parameter, which must be jakarta.ejb.Timer
     */
    public static final String TIMER_PARAM_1 = "1";

    /**
     * Constant string array value to indicate that the timeout method has 1
     * parameter, which must be jakarta.ejb.Timer
     */
    public static final String[] TIMER_PARAM_1_ARRAY = new String[]{TIMER_PARAM_1};

    /**
     * Internal immutable string list to indicate that the timeout method has 1
     * parameter, which must be jakarta.ejb.Timer
     */
    private static final List<String> TIMER_PARAM_1_LIST = Collections.singletonList("jakarta.ejb.Timer");

    private String declaringClass;

    private String methodName;

    private List<String> methodParams;

    public TimeoutMethod(String declaringClass, String methodName, String[] methodParams) {
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        if (methodParams == null || methodParams.length == 0) {
            this.methodParams = Collections.emptyList();
        } else {
            this.methodParams = TIMER_PARAM_1_LIST;
        }
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean hasTimerParameter() {
        return methodParams == TIMER_PARAM_1_LIST;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.declaringClass);
        sb.append(".");
        sb.append(this.methodName);
        sb.append("(");
        if (!this.methodParams.isEmpty()) {
            sb.append(this.methodParams.get(0));
        }
        sb.append(")");
        return sb.toString();
    }
}
