/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jca.rar;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;
import org.jboss.logging.Logger;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterAssociation;
import javax.security.auth.Subject;

/**
 * MultipleManagedConnectionFactory2
 *
 * @version $Revision: $
 */
public class MultipleManagedConnectionFactory2 implements ManagedConnectionFactory, ResourceAdapterAssociation {

    /**
     * The serial version UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * The logger
     */
    private static Logger log = Logger.getLogger("MultipleManagedConnectionFactory2");

    /**
     * The resource adapter
     */
    private ResourceAdapter ra;

    /**
     * The logwriter
     */
    private PrintWriter logwriter;

    /**
     * Name
     */
    private String name;

    /**
     * Default constructor
     */
    public MultipleManagedConnectionFactory2() {

    }

    /**
     * Set name
     *
     * @param name The value
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get name
     *
     * @return The value
     */
    public String getName() {
        return name;
    }

    /**
     * Creates a Connection Factory instance.
     *
     * @param cxManager ConnectionManager to be associated with created EIS connection factory instance
     * @return EIS-specific Connection Factory instance or jakarta.resource.cci.ConnectionFactory instance
     * @throws ResourceException Generic exception
     */
    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        log.trace("createConnectionFactory()");
        return new MultipleConnectionFactory2Impl(this, cxManager);
    }

    /**
     * Creates a Connection Factory instance.
     *
     * @return EIS-specific Connection Factory instance or jakarta.resource.cci.ConnectionFactory instance
     * @throws ResourceException Generic exception
     */
    public Object createConnectionFactory() throws ResourceException {
        throw new ResourceException("This resource adapter doesn't support non-managed environments");
    }

    /**
     * Creates a new physical connection to the underlying EIS resource manager.
     *
     * @param subject       Caller's security information
     * @param cxRequestInfo Additional resource adapter specific connection request information
     * @return ManagedConnection instance
     * @throws ResourceException generic exception
     */
    public ManagedConnection createManagedConnection(Subject subject,
                                                     ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        log.trace("createManagedConnection()");
        return new MultipleManagedConnection2(this);
    }

    /**
     * Returns a matched connection from the candidate set of connections.
     *
     * @param connectionSet Candidate connection set
     * @param subject       Caller's security information
     * @param cxRequestInfo Additional resource adapter specific connection request information
     * @return ManagedConnection if resource adapter finds an acceptable match otherwise null
     * @throws ResourceException generic exception
     */
    public ManagedConnection matchManagedConnections(Set connectionSet,
                                                     Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        log.trace("matchManagedConnections()");
        ManagedConnection result = null;
        Iterator it = connectionSet.iterator();
        while (result == null && it.hasNext()) {
            ManagedConnection mc = (ManagedConnection) it.next();
            if (mc instanceof MultipleManagedConnection2) {
                result = mc;
            }

        }
        return result;
    }

    /**
     * Get the log writer for this ManagedConnectionFactory instance.
     *
     * @return PrintWriter
     * @throws ResourceException generic exception
     */
    public PrintWriter getLogWriter() throws ResourceException {
        log.trace("getLogWriter()");
        return logwriter;
    }

    /**
     * Set the log writer for this ManagedConnectionFactory instance.
     *
     * @param out PrintWriter - an out stream for error logging and tracing
     * @throws ResourceException generic exception
     */
    public void setLogWriter(PrintWriter out) throws ResourceException {
        log.trace("setLogWriter()");
        logwriter = out;
    }

    /**
     * Get the resource adapter
     *
     * @return The handle
     */
    public ResourceAdapter getResourceAdapter() {
        log.trace("getResourceAdapter()");
        return ra;
    }

    /**
     * Set the resource adapter
     *
     * @param ra The handle
     */
    public void setResourceAdapter(ResourceAdapter ra) {
        log.trace("setResourceAdapter()");
        this.ra = ra;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        int result = 17;
        if (name != null) { result += 31 * result + 7 * name.hashCode(); } else { result += 31 * result + 7; }
        return result;
    }

    /**
     * Indicates whether some other object is equal to this one.
     *
     * @param other The reference object with which to compare.
     * @return true if this object is the same as the obj argument, false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) { return false; }
        if (other == this) { return true; }
        if (!(other instanceof MultipleManagedConnectionFactory2)) { return false; }
        MultipleManagedConnectionFactory2 obj = (MultipleManagedConnectionFactory2) other;
        boolean result = true;
        if (result) {
            if (name == null) { result = obj.getName() == null; } else { result = name.equals(obj.getName()); }
        }
        return result;
    }


}
