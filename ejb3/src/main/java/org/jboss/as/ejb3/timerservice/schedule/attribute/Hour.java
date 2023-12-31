/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice.schedule.attribute;

import org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType;

/**
 * Represents the value of a hour constructed out of a {@link jakarta.ejb.ScheduleExpression#getHour()}
 * <p/>
 * <p>
 * A {@link Hour} can hold only {@link Integer} as its value. The only exception to this being the wildcard (*)
 * value. The various ways in which a
 * {@link Hour} value can be represented are:
 * <ul>
 * <li>Wildcard. For example, hour = "*"</li>
 * <li>Range. For example, hour = "0-23"</li>
 * <li>List. For example, hour = "1, 12, 20"</li>
 * <li>Single value. For example, hour = "5"</li>
 * <li>Increment. For example, hour = "0 &#47; 3"</li>
 * </ul>
 * </p>
 *
 * @author Jaikiran Pai
 * @version $Revision: $
 */
public class Hour extends IntegerBasedExpression {
    /**
     * Maximum allowed value for a {@link Hour}
     */
    public static final Integer MAX_HOUR = 23;

    /**
     * Minimum allowed value for a {@link Hour}
     */
    public static final Integer MIN_HOUR = 0;

    /**
     * Creates a {@link Hour} by parsing the passed {@link String} <code>value</code>
     * <p>
     * Valid values are of type {@link ScheduleExpressionType#WILDCARD}, {@link ScheduleExpressionType#RANGE},
     * {@link ScheduleExpressionType#LIST} {@link ScheduleExpressionType#INCREMENT} or
     * {@link ScheduleExpressionType#SINGLE_VALUE}
     * </p>
     *
     * @param value The value to be parsed
     * @throws IllegalArgumentException If the passed <code>value</code> is neither a {@link ScheduleExpressionType#WILDCARD},
     *                                  {@link ScheduleExpressionType#RANGE}, {@link ScheduleExpressionType#LIST},
     *                                  {@link ScheduleExpressionType#INCREMENT} nor {@link ScheduleExpressionType#SINGLE_VALUE}.
     */
    public Hour(String value) {
        super(value);
    }

    public int getFirst() {
        if (this.scheduleExpressionType == ScheduleExpressionType.WILDCARD) {
            return 0;
        }
        return this.absoluteValues.first();
    }

    /**
     * Returns the maximum allowed value for a {@link Hour}
     *
     * @see Hour#MAX_HOUR
     */
    @Override
    protected Integer getMaxValue() {
        return MAX_HOUR;
    }

    /**
     * Returns the minimum allowed value for a {@link Hour}
     *
     * @see Hour#MIN_HOUR
     */
    @Override
    protected Integer getMinValue() {
        return MIN_HOUR;
    }

    @Override
    public boolean isRelativeValue(String value) {
        // hour doesn't support relative values
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

    public Integer getNextMatch(int currentHour) {
        if (this.scheduleExpressionType == ScheduleExpressionType.WILDCARD) {
            return currentHour;
        }
        if (this.absoluteValues.isEmpty()) {
            return null;
        }
        for (Integer hour : this.absoluteValues) {
            if (currentHour == hour) {
                return currentHour;
            }
            if (hour > currentHour) {
                return hour;
            }
        }
        return this.absoluteValues.first();
    }

}
