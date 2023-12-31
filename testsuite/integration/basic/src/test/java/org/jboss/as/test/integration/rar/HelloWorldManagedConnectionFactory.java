/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.rar;

import java.io.PrintWriter;
import java.util.Set;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionDefinition;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterAssociation;
import javax.security.auth.Subject;

/**
 * User: jpai
 */
@ConnectionDefinition(connectionFactory = HelloWorldConnectionFactory.class,
        connectionFactoryImpl = HelloWorldConnectionFactoryImpl.class,
        connection = HelloWorldConnection.class,
        connectionImpl = HelloWorldConnectionImpl.class)
public class HelloWorldManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {


    /**
     * The serialVersionUID
     */

    private static final long serialVersionUID = 1L;


    /**
     * The resource adapter
     */
    private ResourceAdapter ra;

    private PrintWriter writer;

    /**
     * Default constructor
     */

    public HelloWorldManagedConnectionFactory() {


    }


    /**
     * Creates a Connection Factory instance.
     *
     * @return EIS-specific Connection Factory instance or
     *         <p/>
     *         jakarta.resource.cci.ConnectionFactory instance
     * @throws ResourceException Generic exception
     */

    public Object createConnectionFactory() throws ResourceException {

        throw new ResourceException("This resource adapter doesn't support non-managed environments");

    }


    /**
     * Creates a Connection Factory instance.
     *
     * @param cxManager ConnectionManager to be associated with created EIS
     *                  <p/>
     *                  connection factory instance
     * @return EIS-specific Connection Factory instance or
     *         <p/>
     *         jakarta.resource.cci.ConnectionFactory instance
     * @throws ResourceException Generic exception
     */

    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {

        return new HelloWorldConnectionFactoryImpl(this, cxManager);

    }


    /**
     * Creates a new physical connection to the underlying EIS resource manager.
     *
     * @param subject       Caller's security information
     * @param cxRequestInfo Additional resource adapter specific connection
     *                      <p/>
     *                      request information
     * @return ManagedConnection instance
     * @throws ResourceException generic exception
     */

    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {

        return new HelloWorldManagedConnection(this);

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

    public ManagedConnection matchManagedConnections(Set connectionSet, Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {

        return null;

    }

    @Override
    public void setLogWriter(PrintWriter printWriter) throws ResourceException {
        this.writer = printWriter;
    }

    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        return this.writer;
    }

    /**
     * Get the resource adapter
     *
     * @return The handle
     */

    public ResourceAdapter getResourceAdapter() {

        return ra;

    }


    /**
     * Set the resource adapter
     *
     * @param ra The handle
     */

    public void setResourceAdapter(ResourceAdapter ra) {

        this.ra = ra;

    }

    public boolean equals(Object o) {
        return super.equals(o);
    }

    public int hashCode() {
        return super.hashCode();
    }
}
