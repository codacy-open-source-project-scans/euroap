/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice.schedule;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import jakarta.ejb.ScheduleExpression;

import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * CalendarBasedTimeoutTestCase
 *
 * @author Brad Maxwell
 * @version $Revision: $
 */
public class CalendarBasedTimeoutWithDifferentTimeZoneTestCase {

    /**
     * Logger
     */
    private static Logger logger = Logger.getLogger(CalendarBasedTimeoutWithDifferentTimeZoneTestCase.class);

    /**
     * Construct a testcase for the timezone
     */
    public CalendarBasedTimeoutWithDifferentTimeZoneTestCase() {
    }

    @Test
    public void testScheduledTimezoneDifferentThanCurrentSystem() {

        // This tests replicates an automatic timer below being deployed in a system whose default timezone is America/Chicago
        // @Schedule(persistent = false, timezone = "America/New_York", dayOfMonth = "*", dayOfWeek = "*", month = "*", hour =
        // "2", minute = "*", second = "0", year = "*")

        // The schedule for the timer is to fire every minute where the hour is 2 in the America/New_York timezone
        ScheduleExpression sch = new ScheduleExpression();
        sch.timezone("America/New_York");
        sch.dayOfMonth("*");
        sch.dayOfWeek("*");
        sch.month("*");
        sch.hour("2");
        sch.minute("*");
        sch.second("0");
        sch.year("*");

        CalendarBasedTimeout calendarTimeout = new CalendarBasedTimeout(sch);

        Calendar firstTimeout = calendarTimeout.getFirstTimeout();
        Assert.assertNotNull("first timeout is null", firstTimeout);
        logger.info("First timeout is " + firstTimeout.getTime());

        // currentCal sets up a dummy time in the future, the timezone is America/Chicago in which this imaginary system is
        // running
        TimeZone currentTimezone = TimeZone.getTimeZone("America/Chicago");
        Calendar currentCal = new GregorianCalendar(currentTimezone);
        currentCal.set(Calendar.YEAR, 2014);
        currentCal.set(Calendar.MONTH, 1);
        currentCal.set(Calendar.DATE, 8);
        currentCal.set(Calendar.HOUR_OF_DAY, 1);
        currentCal.set(Calendar.MINUTE, 1);
        currentCal.set(Calendar.SECOND, 1);
        currentCal.set(Calendar.MILLISECOND, 0);

        // https://issues.jboss.org/browse/WFLY-2840 - @Schedule EJB Timer not using timezone when calcualting next timeout
        // Next test WFLY-2840, by calling getNextTimeout with the dummy time above, the expected result is for the timer to
        // fire at 1:02:00
        // If the bug is not fixed it will return 2:00:00

        Calendar nextTimeout = calendarTimeout.getNextTimeout(currentCal);
        logger.info("Next timeout is " + nextTimeout.getTime());

        Calendar expectedCal = new GregorianCalendar(currentTimezone);
        expectedCal.set(Calendar.YEAR, 2014);
        expectedCal.set(Calendar.MONTH, 1);
        expectedCal.set(Calendar.DATE, 8);
        expectedCal.set(Calendar.HOUR_OF_DAY, 1);
        expectedCal.set(Calendar.MINUTE, 2);
        expectedCal.set(Calendar.SECOND, 0);
        expectedCal.set(Calendar.MILLISECOND, 0);

        Assert.assertEquals("[WFLY-2840] Next timeout should be: " + expectedCal.getTime(), expectedCal.getTime(),
                nextTimeout.getTime());
    }

}