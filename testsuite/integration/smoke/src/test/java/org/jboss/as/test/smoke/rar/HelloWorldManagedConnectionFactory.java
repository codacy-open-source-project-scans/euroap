/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.smoke.rar;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;
import org.jboss.logging.Logger;

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
 * HelloWorldManagedConnectionFactory
 *
 * @version $Revision: $
 */
@ConnectionDefinition(connectionFactory = HelloWorldConnectionFactory.class,
   connectionFactoryImpl = HelloWorldConnectionFactoryImpl.class,
   connection = HelloWorldConnection.class,
   connectionImpl = HelloWorldConnectionImpl.class)
public class HelloWorldManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {

   private static final long serialVersionUID = 1L;

/** The logger */
   private static Logger log = Logger.getLogger("HelloWorldManagedConnectionFactory");

   /** The resource adapter */
   private ResourceAdapter ra;

   /** The logwriter */
   private PrintWriter logwriter;

   /**
    * default constructor
    */
   public HelloWorldManagedConnectionFactory() {
   }

   /**
    * Creates a Connection Factory instance.
    *
    * @return   EIS-specific Connection Factory instance or jakarta.resource.cci.ConnectionFactory instance
    * @throws   ResourceException Generic exception
    */
   public Object createConnectionFactory() throws ResourceException {
      return createConnectionFactory(new HelloWorldConnectionManager());
   }

   /**
    * Creates a Connection Factory instance.
    *
    * @param    cxManager    ConnectionManager to be associated with created EIS connection factory instance
    * @return   EIS-specific Connection Factory instance or jakarta.resource.cci.ConnectionFactory instance
    * @throws   ResourceException Generic exception
    */
   public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
      return new HelloWorldConnectionFactoryImpl(this, cxManager);
   }

   /**
    * Creates a new physical connection to the underlying EIS resource manager.
    *
    * @param   subject        Caller's security information
    * @param   cxRequestInfo  Additional resource adapter specific connection request information
    * @throws  ResourceException     generic exception
    * @return  ManagedConnection instance
    */
   public ManagedConnection createManagedConnection(Subject subject,
                                                    ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      return new HelloWorldManagedConnection(this);
   }

   /**
    * Returns a matched connection from the candidate set of connections.
    *
    * @param   connectionSet   candidate connection set
    * @param   subject        Caller's security information
    * @param   cxRequestInfo  Additional resource adapter specific connection request information
    * @throws  ResourceException     generic exception
    * @return  ManagedConnection if resource adapter finds an acceptable match otherwise null
    */
   public ManagedConnection matchManagedConnections(Set connectionSet,
                                                    Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      ManagedConnection result = null;

      Iterator it = connectionSet.iterator();
      while (result == null && it.hasNext()) {
         ManagedConnection mc = (ManagedConnection)it.next();
         if (mc instanceof HelloWorldManagedConnection) {
            HelloWorldManagedConnection hwmc = (HelloWorldManagedConnection)mc;
            result = hwmc;
         }
      }

      return result;
   }

   /**
    * Get the log writer for this ManagedConnectionFactory instance.
    *
    * @return  PrintWriter
    * @throws  ResourceException     generic exception
    */
   public PrintWriter getLogWriter() throws ResourceException {
      return logwriter;
   }

   /**
    * Set the log writer for this ManagedConnectionFactory instance.
    *
    * @param   out PrintWriter - an out stream for error logging and tracing
    * @throws  ResourceException     generic exception
    */
   public void setLogWriter(PrintWriter out) throws ResourceException {
      logwriter = out;
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

   /**
    * Returns a hash code value for the object.
    * @return a hash code value for this object.
    */
   @Override
   public int hashCode() {
      int result = 17;
      return result;
   }

   /**
    * Indicates whether some other object is equal to this one.
    * @param   other   the reference object with which to compare.
    * @return true if this object is the same as the obj argument; false otherwise.
    */
   @Override
   public boolean equals(Object other) {
      if (other == null)
         return false;
      if (other == this)
         return true;
      if (!(other instanceof HelloWorldManagedConnectionFactory))
         return false;
      HelloWorldManagedConnectionFactory obj = (HelloWorldManagedConnectionFactory)other;
      boolean result = true;
      return result;
   }


}
