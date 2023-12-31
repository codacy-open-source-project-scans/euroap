/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.timerservice;

import jakarta.ejb.TimerHandle;

import org.jboss.as.ejb3.component.EJBComponent;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.timerservice.spi.ManagedTimer;
import org.jboss.as.server.CurrentServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Serializable handle for an EJB timer.
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 * @author Paul Ferraro
 */
public class TimerHandleImpl implements TimerHandle {
    private static final long serialVersionUID = 1L;

    // Unused, but remains in order to retain serialization compatibility
    // We can use this field to determine if this handle instance was serialized from a legacy handle
    private final String timedObjectId = null;

    private final String serviceName;

    private final String id;

    private transient EJBComponent component;
    private transient ManagedTimer timer;

    /**
     * Creates a {@link TimerHandleImpl}
     *
     * @param timer     The managed timer instance
     * @param component The EJB component associated with the timer
     */
    public TimerHandleImpl(ManagedTimer timer, EJBComponent component) {
        this.timer = timer;
        this.component = component;
        this.id = timer.getId();
        this.serviceName = component.getCreateServiceName().getCanonicalName();
    }

    @SuppressWarnings("deprecation")
    @Override
    public synchronized jakarta.ejb.Timer getTimer() {
        if (this.component == null) {
            ServiceName serviceName = ServiceName.parse(this.serviceName);
            // Is this a legacy timer handle?
            if (this.timedObjectId != null) {
                // If so, figure out the component create service name from the legacy TimerServiceImpl ServiceName
                serviceName = serviceName.getParent().getParent().append("CREATE");
            }
            this.component = (EJBComponent) WildFlySecurityManager.doUnchecked(CurrentServiceContainer.GET_ACTION).getRequiredService(serviceName).getValue();
        }
        if (this.timer == null) {
            this.timer = this.component.getTimerService().findTimer(this.id);
        }
        if ((this.timer == null) || !this.timer.isActive()) {
            throw EjbLogger.EJB3_TIMER_LOGGER.timerHandleIsNotActive(this.id, this.component.getTimerService().getInvoker().getTimedObjectId());
        }
        return this.timer;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof TimerHandleImpl)) return false;
        TimerHandleImpl handle = (TimerHandleImpl) object;
        return this.id.equals(handle.id) && this.serviceName.equals(handle.serviceName);
    }

    @Override
    public String toString() {
        return this.id;
    }
}
