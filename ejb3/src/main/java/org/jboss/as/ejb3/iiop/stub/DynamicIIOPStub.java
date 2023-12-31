/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.iiop.stub;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;
import org.jboss.as.ejb3.logging.EjbLogger;
import javax.rmi.CORBA.Util;
import org.jboss.ejb.iiop.HandleImplIIOP;
import org.jboss.ejb.iiop.HomeHandleImplIIOP;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.portable.ApplicationException;
import org.omg.CORBA.portable.RemarshalException;
import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;
import org.wildfly.iiop.openjdk.rmi.marshal.strategy.StubStrategy;

/**
 * Dynamically generated IIOP stub classes extend this abstract superclass,
 * which extends <code>javax.rmi.CORBA.Stub</code>.
 * <p/>
 * A <code>DynamicIIOPStub</code> is a local proxy of a remote object. It has
 * methods (<code>invoke()</code>, <code>invokeBoolean()</code>,
 * <code>invokeByte()</code>, and so on) that send an IIOP request to the
 * server that implements the remote object, receive the reply from the
 * server, and return the results to the caller. All of these methods take
 * the IDL name of the operation, a <code>StubStrategy</code> instance to
 * be used for marshalling parameters and unmarshalling the result, plus an
 * array of operation parameters.
 *
 * @author <a href="mailto:reverbel@ime.usp.br">Francisco Reverbel</a>
 * @author Stuart Douglas
 */
public abstract class DynamicIIOPStub
        extends javax.rmi.CORBA.Stub {
    /**
     * @since 4.2.0
     */
    static final long serialVersionUID = 3283717238950231589L;


    /**
     * My handle (either a HandleImplIIOP or a HomeHandleImplIIOP).
     */
    private Object handle = null;

    private static void trace(final String msg) {
        if (EjbLogger.EJB3_INVOCATION_LOGGER.isTraceEnabled())
            EjbLogger.EJB3_INVOCATION_LOGGER.trace(msg);
    }

    private static void tracef(final String format, final Object... params) {
        if (EjbLogger.EJB3_INVOCATION_LOGGER.isTraceEnabled())
            EjbLogger.EJB3_INVOCATION_LOGGER.tracef(format, params);
    }

    /**
     * Constructs a <code>DynamicIIOPStub</code>.
     */
    public DynamicIIOPStub() {
        super();
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns an <code>Object</code> result to the caller.
     */
    public Object invoke(String operationName, final StubStrategy stubStrategy, Object[] params) throws Throwable {
        if (operationName.equals("_get_handle")
                && this instanceof jakarta.ejb.EJBObject) {
            if (handle == null) {
                handle = new HandleImplIIOP(this);
            }
            return handle;
        } else if (operationName.equals("_get_homeHandle")
                && this instanceof jakarta.ejb.EJBHome) {
            if (handle == null) {
                handle = new HomeHandleImplIIOP(this);
            }
            return handle;
        } else {
            //FIXME
            // all invocations are now made using remote invocation
            // local invocations between two different applications cause
            // ClassCastException between Stub and Interface
            // (two different modules are loading the classes)
            // problem was unnoticeable with JacORB because it uses
            // remote invocations to all stubs to which interceptors are
            // registered and a result all that JacORB always used
            // remote invocations

            // remote call path

            // To check whether this is a local stub or not we must call
            // org.omg.CORBA.portable.ObjectImpl._is_local(), and _not_
            // javax.rmi.CORBA.Util.isLocal(Stub s), which in Sun's JDK
            // always return false.

            InputStream in = null;
            try {
                try {
                    OutputStream out =
                            (OutputStream) _request(operationName, true);
                    stubStrategy.writeParams(out, params);
                    tracef("sent request: %s", operationName);
                    in = (InputStream) _invoke(out);
                    if (stubStrategy.isNonVoid()) {
                        trace("received reply");
                        final InputStream finalIn = in;
                        return doPrivileged(new PrivilegedAction<Object>() {
                            public Object run() {
                                return stubStrategy.readRetval(finalIn);
                            }
                        });
                    } else {
                        return null;
                    }
                } catch (final ApplicationException ex) {
                    trace("got application exception");
                    in = (InputStream) ex.getInputStream();
                    final InputStream finalIn1 = in;
                    throw doPrivileged(new PrivilegedAction<Exception>() {
                        public Exception run() {
                            return stubStrategy.readException(ex.getId(), finalIn1);
                        }
                    });
                } catch (RemarshalException ex) {
                    trace("got remarshal exception");
                    return invoke(operationName, stubStrategy, params);
                }
            } catch (SystemException ex) {
                if (EjbLogger.EJB3_INVOCATION_LOGGER.isTraceEnabled()) {
                    EjbLogger.EJB3_INVOCATION_LOGGER.trace("CORBA system exception in IIOP stub", ex);
                }
                throw Util.mapSystemException(ex);
            } finally {
                _releaseReply(in);
            }
        }
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns a <code>boolean</code> result to the caller.
     */
    public boolean invokeBoolean(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (Boolean) invoke(operationName,
                stubStrategy, params);
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns a <code>byte</code> result to the caller.
     */
    public byte invokeByte(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (byte) invoke(operationName,
                stubStrategy, params);
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns a <code>char</code> result to the caller.
     */
    public char invokeChar(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (char) invoke(operationName,
                stubStrategy, params);
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns a <code>short</code> result to the caller.
     */
    public short invokeShort(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (short) invoke(operationName,
                stubStrategy, params);
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns an <code>int</code> result to the caller.
     */
    public int invokeInt(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (int) invoke(operationName, stubStrategy, params);
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns a <code>long</code> result to the caller.
     */
    public long invokeLong(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (long) invoke(operationName, stubStrategy, params);
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns a <code>float</code> result to the caller.
     */
    public float invokeFloat(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (float) invoke(operationName, stubStrategy, params);
    }

    /**
     * Sends a request message to the server, receives the reply from the
     * server, and returns a <code>double</code> result to the caller.
     */
    public double invokeDouble(String operationName, StubStrategy stubStrategy, Object[] params) throws Throwable {
        return (double) invoke(operationName,
                stubStrategy, params);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("JBossDynStub[").append(getClass().getName()).append(", ");
        try {
            builder.append(_orb().object_to_string(this));
        } catch (BAD_OPERATION ignored) {
            builder.append("*DISCONNECTED*");
        }
        builder.append("]");
        return builder.toString();
    }
}
