/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jca.annorar;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionEvent;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.LocalTransaction;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionMetaData;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;

/**
 * AnnoManagedConnection
 *
 * @version $Revision: $
 */
public class AnnoManagedConnection implements ManagedConnection {

    /**
     * The logger
     */
    private static Logger log = Logger.getLogger("AnnoManagedConnection");

    /**
     * The logwriter
     */
    private PrintWriter logwriter;

    /**
     * ManagedConnectionFactory
     */
    private AnnoManagedConnectionFactory mcf;

    /**
     * Listeners
     */
    private List<ConnectionEventListener> listeners;

    /**
     * Connection
     */
    private AnnoConnectionImpl connection;

    /**
     * Default constructor
     *
     * @param mcf mcf
     */
    public AnnoManagedConnection(AnnoManagedConnectionFactory mcf) {
        this.mcf = mcf;
        this.logwriter = null;
        this.listeners = Collections
                .synchronizedList(new ArrayList<ConnectionEventListener>(1));
        this.connection = null;
    }

    /**
     * Creates a new connection handle for the underlying physical connection
     * represented by the ManagedConnection instance.
     *
     * @param subject       Security context as JAAS subject
     * @param cxRequestInfo ConnectionRequestInfo instance
     * @return generic Object instance representing the connection handle.
     * @throws ResourceException generic exception if operation fails
     */
    public Object getConnection(Subject subject,
                                ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        log.trace("getConnection()");
        connection = new AnnoConnectionImpl(this, mcf);
        return connection;
    }

    /**
     * Used by the container to change the association of an application-level
     * connection handle with a ManagedConneciton instance.
     *
     * @param connection Application-level connection handle
     * @throws ResourceException generic exception if operation fails
     */
    public void associateConnection(Object connection) throws ResourceException {
        log.trace("associateConnection()");

        if (connection == null) { throw new ResourceException("Null connection handle"); }

        if (!(connection instanceof AnnoConnectionImpl)) { throw new ResourceException("Wrong connection handle"); }

        this.connection = (AnnoConnectionImpl) connection;
    }

    /**
     * Application server calls this method to force any cleanup on the
     * ManagedConnection instance.
     *
     * @throws ResourceException generic exception if operation fails
     */
    public void cleanup() throws ResourceException {
        log.trace("cleanup()");
    }

    /**
     * Destroys the physical connection to the underlying resource manager.
     *
     * @throws ResourceException generic exception if operation fails
     */
    public void destroy() throws ResourceException {
        log.trace("destroy()");
    }

    /**
     * Adds a connection event listener to the ManagedConnection instance.
     *
     * @param listener A new ConnectionEventListener to be registered
     */
    public void addConnectionEventListener(ConnectionEventListener listener) {
        log.trace("addConnectionEventListener()");
        checkNotNullParam("listener", listener);
        listeners.add(listener);
    }

    /**
     * Removes an already registered connection event listener from the
     * ManagedConnection instance.
     *
     * @param listener already registered connection event listener to be removed
     */
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        log.trace("removeConnectionEventListener()");
        checkNotNullParam("listener", listener);
        listeners.remove(listener);
    }

    /**
     * Close handle
     *
     * @param handle The handle
     */
    void closeHandle(AnnoConnection handle) {
        ConnectionEvent event = new ConnectionEvent(this,
                ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle(handle);
        for (ConnectionEventListener cel : listeners) {
            cel.connectionClosed(event);
        }

    }

    /**
     * Gets the log writer for this ManagedConnection instance.
     *
     * @return Character output stream associated with this Managed-Connection
     * instance
     * @throws ResourceException generic exception if operation fails
     */
    public PrintWriter getLogWriter() throws ResourceException {
        log.trace("getLogWriter()");
        return logwriter;
    }

    /**
     * Sets the log writer for this ManagedConnection instance.
     *
     * @param out Character Output stream to be associated
     * @throws ResourceException generic exception if operation fails
     */
    public void setLogWriter(PrintWriter out) throws ResourceException {
        log.trace("setLogWriter()");
        logwriter = out;
    }

    /**
     * Returns an <code>jakarta.resource.spi.LocalTransaction</code> instance.
     *
     * @return LocalTransaction instance
     * @throws ResourceException generic exception if operation fails
     */
    public LocalTransaction getLocalTransaction() throws ResourceException {
        log.trace("getLocalTransaction()");
        return null;
    }

    /**
     * Returns an <code>javax.transaction.xa.XAresource</code> instance.
     *
     * @return XAResource instance
     * @throws ResourceException generic exception if operation fails
     */
    public XAResource getXAResource() throws ResourceException {
        log.trace("getXAResource()");
        return null;
    }

    /**
     * Gets the metadata information for this connection's underlying EIS
     * resource manager instance.
     *
     * @return ManagedConnectionMetaData instance
     * @throws ResourceException generic exception if operation fails
     */
    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        log.trace("getMetaData()");
        return new AnnoManagedConnectionMetaData();
    }

    /**
     * Call me
     */
    void callMe() {
        log.trace("callMe()");
    }

}
