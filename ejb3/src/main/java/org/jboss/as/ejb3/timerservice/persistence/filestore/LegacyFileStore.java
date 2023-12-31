/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.timerservice.persistence.filestore;

import static org.jboss.as.ejb3.logging.EjbLogger.EJB3_TIMER_LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jakarta.ejb.ScheduleExpression;

import org.jboss.as.ejb3.timerservice.CalendarTimer;
import org.jboss.as.ejb3.timerservice.TimerImpl;
import org.jboss.as.ejb3.timerservice.TimerServiceImpl;
import org.jboss.as.ejb3.timerservice.persistence.CalendarTimerEntity;
import org.jboss.as.ejb3.timerservice.persistence.TimerEntity;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.vfs.VFSUtils;

/**
 * Contains the code needed to load timers from the legacy persistent format.
 *
 * The old format was based on jboss marshalling, and as such it was almost impossible to maintain
 * compatibility, as class changes to the serialized classes would result in old persistent timers
 * being unreadable.
 *
 * This class will load old style timers, and then create a marker file to indicate to the system
 * that these timers have already been read.
 *
 * @author Stuart Douglas
 */
public class LegacyFileStore {

    public static final String MIGRATION_MARKER = "migrated-to-xml.marker";

    static Map<String, TimerImpl> loadTimersFromFile(final String timedObjectId, final TimerServiceImpl timerService, String directory, MarshallerFactory factory, MarshallingConfiguration configuration) {
        final Map<String, TimerImpl> timers = new HashMap<String, TimerImpl>();
        Unmarshaller unmarshaller = null;
        try {
            final File file = new File(directory);
            if (!file.exists()) {
                //no timers exist yet
                return timers;
            } else if (!file.isDirectory()) {
                EJB3_TIMER_LOGGER.failToRestoreTimers(file);
                return timers;
            }
            File marker = new File(file, MIGRATION_MARKER);
            if (marker.exists()) {
                return timers;
            }
            unmarshaller = factory.createUnmarshaller(configuration);
            for (File timerFile : file.listFiles()) {
                if(timerFile.getName().endsWith(".xml")) {
                    continue;
                }
                FileInputStream in = null;
                try {
                    in = new FileInputStream(timerFile);
                    unmarshaller.start(new InputStreamByteInput(in));

                    final TimerEntity entity = unmarshaller.readObject(TimerEntity.class);

                    //we load the legacy timer entity class, and turn it into a timer state

                    TimerImpl.Builder builder;
                    if (entity instanceof CalendarTimerEntity) {
                        CalendarTimerEntity c = (CalendarTimerEntity) entity;

                        final ScheduleExpression scheduleExpression = new ScheduleExpression();
                        scheduleExpression.second(c.getSecond())
                                .minute(c.getMinute())
                                .hour(c.getHour())
                                .dayOfWeek(c.getDayOfWeek())
                                .dayOfMonth(c.getDayOfMonth())
                                .month(c.getMonth())
                                .year(c.getYear())
                                .start(c.getStartDate())
                                .end(c.getEndDate())
                                .timezone(c.getTimezone());

                        builder = CalendarTimer.builder()
                                .setScheduleExpression(scheduleExpression)
                                .setAutoTimer(c.isAutoTimer())
                                .setTimeoutMethod(CalendarTimer.getTimeoutMethod(c.getTimeoutMethod(), timerService.getInvoker().getClassLoader()));
                    } else {
                        builder = TimerImpl.builder();
                    }
                    builder.setId(entity.getId())
                            .setTimedObjectId(entity.getTimedObjectId())
                            .setInitialDate(entity.getInitialDate())
                            .setRepeatInterval(entity.getInterval())
                            .setNextDate(entity.getNextDate())
                            .setPreviousRun(entity.getPreviousRun())
                            .setInfo(entity.getInfo())
                            .setTimerState(entity.getTimerState())
                            .setPersistent(true);

                    timers.put(entity.getId(), builder.build(timerService));
                    unmarshaller.finish();
                } catch (Exception e) {
                    EJB3_TIMER_LOGGER.failToRestoreTimersFromFile(timerFile, e);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            EJB3_TIMER_LOGGER.failToCloseFile(e);
                        }
                    }
                }

            }
            if (!timers.isEmpty()) {
                Files.write(marker.toPath(), new Date().toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            EJB3_TIMER_LOGGER.failToRestoreTimersForObjectId(timedObjectId, e);
        } finally {
            VFSUtils.safeClose(unmarshaller);
        }
        return timers;
    }
}
