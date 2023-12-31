/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice.schedule.attribute;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jboss.as.ejb3.timerservice.schedule.value.ScheduleExpressionType;

/**
 * DayOfWeek
 *
 * @author Jaikiran Pai
 * @version $Revision: $
 */
public class DayOfWeek extends IntegerBasedExpression {

    public static final Integer MAX_DAY_OF_WEEK = 7;

    public static final Integer MIN_DAY_OF_WEEK = 0;

    static final Map<String, Integer> DAY_OF_WEEK_ALIAS = new HashMap<>();

    static {
        DAY_OF_WEEK_ALIAS.put("sun", 0);
        DAY_OF_WEEK_ALIAS.put("mon", 1);
        DAY_OF_WEEK_ALIAS.put("tue", 2);
        DAY_OF_WEEK_ALIAS.put("wed", 3);
        DAY_OF_WEEK_ALIAS.put("thu", 4);
        DAY_OF_WEEK_ALIAS.put("fri", 5);
        DAY_OF_WEEK_ALIAS.put("sat", 6);
    }

    private static final int OFFSET = DAY_OF_WEEK_ALIAS.get("sun") - Calendar.SUNDAY;

    private SortedSet<Integer> offsetAdjustedDaysOfWeek = new TreeSet<>();

    public DayOfWeek(String value) {
        super(value);
        for (Integer dayOfWeek : this.absoluteValues) {
            if (dayOfWeek == 7) {
                this.absoluteValues.remove(dayOfWeek);
                this.absoluteValues.add(0);
            }
        }
        if (OFFSET != 0) {
            for (Integer dayOfWeek : this.absoluteValues) {
                this.offsetAdjustedDaysOfWeek.add(dayOfWeek - OFFSET);
            }
        } else {
            this.offsetAdjustedDaysOfWeek = this.absoluteValues;
        }
    }


    @Override
    protected Integer getMaxValue() {
        return MAX_DAY_OF_WEEK;
    }

    @Override
    protected Integer getMinValue() {
        return MIN_DAY_OF_WEEK;
    }


    public int getFirst() {
        if (this.scheduleExpressionType == ScheduleExpressionType.WILDCARD) {
            return Calendar.SUNDAY;
        }
        return this.offsetAdjustedDaysOfWeek.first();
    }

    @Override
    protected boolean accepts(ScheduleExpressionType scheduleExprType) {
        switch (scheduleExprType) {
            case RANGE:
            case LIST:
            case SINGLE_VALUE:
            case WILDCARD:
                return true;
            // day-of-week doesn't support increment
            case INCREMENT:
            default:
                return false;
        }
    }

    @Override
    public boolean isRelativeValue(String value) {
        // day-of-week doesn't support relative values
        return false;
    }

    @Override
    protected Integer parseInt(String alias) {
        try {
            return super.parseInt(alias);
        } catch (NumberFormatException nfe) {
            String lowerCaseAlias = alias.toLowerCase(Locale.ENGLISH);
            return DAY_OF_WEEK_ALIAS.get(lowerCaseAlias);
        }
    }

    public Integer getNextMatch(Calendar currentCal) {
        if (this.scheduleExpressionType == ScheduleExpressionType.WILDCARD) {
            return currentCal.get(Calendar.DAY_OF_WEEK);
        }
        int currentDayOfWeek = currentCal.get(Calendar.DAY_OF_WEEK);
        for (Integer dayOfWeek : this.offsetAdjustedDaysOfWeek) {
            if (currentDayOfWeek == dayOfWeek) {
                return currentDayOfWeek;
            }
            if (dayOfWeek > currentDayOfWeek) {
                return dayOfWeek;
            }
        }
        return this.offsetAdjustedDaysOfWeek.first();
    }
}
