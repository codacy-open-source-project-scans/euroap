/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice.schedule.attribute;

import java.util.SortedSet;

import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType;

/**
 * Represents the value of a minute constructed out of a {@link jakarta.ejb.ScheduleExpression#getMinute()}
 * <p/>
 * <p>
 * A {@link Minute} can hold only {@link Integer} as its value. The only exception to this being the wildcard (*)
 * value. The various ways in which a
 * {@link Minute} value can be represented are:
 * <ul>
 * <li>Wildcard. For example, minute = "*"</li>
 * <li>Range. For example, minute = "0-20"</li>
 * <li>List. For example, minute = "10, 30, 45"</li>
 * <li>Single value. For example, minute = "8"</li>
 * <li>Increment. For example, minute = "10 &#47; 15"</li>
 * </ul>
 * </p>
 *
 * @author Jaikiran Pai
 * @version $Revision: $
 */
public class Minute extends IntegerBasedExpression {

    /**
     * Maximum allowed value for a {@link Minute}
     */
    public static final Integer MAX_MINUTE = 59;

    /**
     * Minimum allowed value for a {@link Minute}
     */
    public static final Integer MIN_MINUTE = 0;

    /**
     * Creates a {@link Minute} by parsing the passed {@link String} <code>value</code>
     * <p>
     * Valid values are of type {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#WILDCARD}, {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#RANGE},
     * {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#LIST} {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#INCREMENT} or
     * {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#SINGLE_VALUE}
     * </p>
     *
     * @param value The value to be parsed
     * @throws IllegalArgumentException If the passed <code>value</code> is neither a {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#WILDCARD},
     *                                  {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#RANGE}, {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#LIST},
     *                                  {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#INCREMENT} nor {@link org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType#SINGLE_VALUE}.
     */
    public Minute(String value) {
        super(value);
    }

    public int getFirst() {
        if (this.scheduleExpressionType == ScheduleExpressionType.WILDCARD) {
            return 0;
        }
        SortedSet<Integer> eligibleMinutes = this.getEligibleMinutes();
        if (eligibleMinutes.isEmpty()) {
            throw EjbLogger.EJB3_TIMER_LOGGER.invalidScheduleValue(Minute.class.getSimpleName(), this.origValue);
        }
        return eligibleMinutes.first();
    }

    /**
     * Returns the maximum allowed value for a {@link Minute}
     *
     * @see Minute#MAX_MINUTE
     */
    @Override
    protected Integer getMaxValue() {
        return MAX_MINUTE;
    }

    /**
     * Returns the minimum allowed value for a {@link Minute}
     *
     * @see Minute#MIN_MINUTE
     */
    @Override
    protected Integer getMinValue() {
        return MIN_MINUTE;
    }

    @Override
    public boolean isRelativeValue(String value) {
        // minute doesn't support relative values, hence
        // return false always
        return false;
    }

    @Override
    protected boolean accepts(ScheduleExpressionType scheduleExprType) {
        switch (scheduleExprType) {
            case RANGE:
            case LIST:
            case SINGLE_VALUE:
            case WILDCARD:
            case INCREMENT:
                return true;
            default:
                return false;
        }
    }

    private SortedSet<Integer> getEligibleMinutes() {
        return this.absoluteValues;
    }

    public Integer getNextMatch(int currentMinute) {
        if (this.scheduleExpressionType == ScheduleExpressionType.WILDCARD) {
            return currentMinute;
        }
        if (this.absoluteValues.isEmpty()) {
            return null;
        }

        for (Integer minute : this.absoluteValues) {
            if (currentMinute == minute) {
                return currentMinute;
            }
            if (minute > currentMinute) {
                return minute;
            }
        }
        return this.absoluteValues.first();
    }

}
