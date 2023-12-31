/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice.persistence;

import java.io.Serializable;
import java.util.Date;

import org.jboss.as.ejb3.timerservice.TimerImpl;
import org.jboss.as.ejb3.timerservice.TimerState;

/**
 *
 * DO NOT MODIFY THIS CLASS
 *
 * Due to a temporary implementation that became permanent, the {@link org.jboss.as.ejb3.timerservice.persistence.filestore.FileTimerPersistence}
 * writes these out directly, modifying this class will break compatibility
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 * @author Stuart Douglas
 */
public class TimerEntity implements Serializable {
    private static final long serialVersionUID = 8229332510659372218L;

    protected final String id;

    protected final String timedObjectId;

    protected final Date initialDate;

    protected final long repeatInterval;

    protected final Date nextDate;

    protected final Date previousRun;

    protected final Serializable info;

    protected final TimerState timerState;

    public TimerEntity(TimerImpl timer) {
        this.id = timer.getId();
        this.initialDate = timer.getInitialExpiration();
        this.repeatInterval = timer.getInterval();
        this.nextDate = timer.getNextExpiration();
        this.previousRun = timer.getPreviousRun();
        this.timedObjectId = timer.getTimedObjectId();
        this.info = timer.getTimerInfo();

        if (timer.getState() == TimerState.CREATED) {
            //a timer that has been persisted cannot be in the created state
            this.timerState = TimerState.ACTIVE;
        } else {
            this.timerState = timer.getState();
        }
    }

    public String getId() {
        return id;
    }

    public String getTimedObjectId() {
        return timedObjectId;
    }

    public Date getInitialDate() {
        return initialDate;
    }

    public long getInterval() {
        return repeatInterval;
    }

    public Serializable getInfo() {
        return this.info;
    }

    public Date getNextDate() {
        return nextDate;
    }

    public Date getPreviousRun() {
        return previousRun;
    }

    public TimerState getTimerState() {
        return timerState;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof TimerEntity)) {
            return false;
        }
        TimerEntity other = (TimerEntity) obj;
        if (this.id == null) {
            return false;
        }
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        if (this.id == null) {
            return super.hashCode();
        }
        return this.id.hashCode();
    }

}
