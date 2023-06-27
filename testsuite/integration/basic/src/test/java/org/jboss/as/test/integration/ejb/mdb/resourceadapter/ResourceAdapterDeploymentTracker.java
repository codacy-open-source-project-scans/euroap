/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.ejb.mdb.resourceadapter;

/**
 * @author Jaikiran Pai
 */
public class ResourceAdapterDeploymentTracker {

    public static final ResourceAdapterDeploymentTracker INSTANCE = new ResourceAdapterDeploymentTracker();

    private boolean endpointActivationCalled;
    private boolean endpointDeactivationCalled;
    private boolean endpointStartCalled;
    private boolean endpointStopCalled;

    private ResourceAdapterDeploymentTracker() {

    }

    void endpointActivationCalled() {
        this.endpointActivationCalled = true;
    }

    public boolean wasEndpointActivationCalled() {
        return this.endpointActivationCalled;
    }

    void endpointDeactivationCalled() {
        this.endpointDeactivationCalled = true;
    }

    public boolean wasEndpointDeactivationCalled() {
        return this.endpointDeactivationCalled;
    }

    void endpointStartCalled() {
        this.endpointStartCalled = true;
    }

    public boolean wasEndpointStartCalled() {
        return this.endpointStartCalled;
    }

    void endpointStopCalled() {
        this.endpointStopCalled = true;
    }

    public boolean wasEndpointStopCalled() {
        return this.endpointStopCalled;
    }
}
