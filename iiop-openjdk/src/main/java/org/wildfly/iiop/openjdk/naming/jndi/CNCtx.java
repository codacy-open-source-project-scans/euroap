/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.iiop.openjdk.naming.jndi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

import javax.naming.CannotProceedException;
import javax.naming.CompositeName;
import javax.naming.ConfigurationException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.NamingManager;
import javax.naming.spi.ResolveResult;

import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NamingContextHelper;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import org.omg.CosNaming.NamingContextPackage.NotFoundReason;
import org.wildfly.iiop.openjdk.logging.IIOPLogger;
import org.wildfly.iiop.openjdk.service.CorbaORBService;

// Needed for creating default ORB

/**
 * Provides a bridge to the CosNaming server provided by
 * JavaIDL. This class provides the InitialContext from CosNaming.
 *
 * @author Raj Krishnamurthy
 * @author Rosanna Lee
 */

public class CNCtx implements javax.naming.Context {

    ORB _orb;                   // used by ExceptionMapper and RMI/IIOP factory
    public NamingContext _nc;   // public for accessing underlying NamingContext
    private NameComponent[] _name = null;

    Hashtable _env; // used by ExceptionMapper
    static final CNNameParser parser = new CNNameParser();

    private static final String FED_PROP = "com.sun.jndi.cosnaming.federation";
    boolean federation = false;

    /**
     * Create a CNCtx object. Gets the initial naming
     * reference for the COS Naming Service from the ORB.
     * The ORB can be passed in via the java.naming.corba.orb property
     * or be created using properties in the environment properties.
     *
     * @param env Environment properties for initializing name service.
     * @throws NamingException Cannot initialize ORB or naming context.
     */
    CNCtx(Hashtable env) throws NamingException {
        if (env != null) {
            env = (Hashtable) env.clone();
        }
        _env = env;
        federation = "true".equals(env != null ? env.get(FED_PROP) : null);
        initOrbAndRootContext(env);
    }

    private CNCtx() {
    }

    /**
     * This method is used by the iiop and iiopname URL Context factories.
     */
    public static ResolveResult createUsingURL(String url, Hashtable env)
            throws NamingException {
        CNCtx ctx = new CNCtx();
        if (env != null) {
            env = (Hashtable) env.clone();
        }
        ctx._env = env;
        String rest = ctx.initUsingUrl(env != null ? (ORB) env.get("java.naming.corba.orb") : null,
                url, env);

        // rest is the INS name
        // Return the parsed form to prevent subsequent lookup
        // from parsing the string as a composite name
        // The caller should be aware that a toString() of the name
        // will yield its INS syntax, rather than a composite syntax
        return new ResolveResult(ctx, parser.parse(rest));
    }

    /**
     * Creates a CNCtx object which supports the javax.naming
     * apis given a COS Naming Context object.
     *
     * @param orb     The ORB used by this context
     * @param nctx    The COS NamingContext object associated with this context
     * @param name    The name of this context relative to the root
     * @throws ConfigurationException if both the ORB and naming context are null.
     */

    CNCtx(ORB orb, NamingContext nctx, Hashtable env, NameComponent[] name) throws NamingException {
        if (orb == null || nctx == null)
            throw IIOPLogger.ROOT_LOGGER.errorConstructingCNCtx();
        _orb = orb;
        _nc = nctx;
        _env = env;
        _name = name;
        federation = "true".equals(env != null ? env.get(FED_PROP) : null);
    }

    NameComponent[] makeFullName(NameComponent[] child) {
        if (_name == null || _name.length == 0) {
            return child;
        }
        NameComponent[] answer = new NameComponent[_name.length + child.length];

        // parent
        System.arraycopy(_name, 0, answer, 0, _name.length);

        // child
        System.arraycopy(child, 0, answer, _name.length, child.length);
        return answer;
    }


    public String getNameInNamespace() throws NamingException {
        if (_name == null || _name.length == 0) {
            return "";
        }
        return CNNameParser.cosNameToInsString(_name);
    }

    /**
     * These are the URL schemes that need to be processed.
     * IOR and corbaloc URLs can be passed directly to ORB.string_to_object()
     */
    private static boolean isCorbaUrl(String url) {
        return url.startsWith("iiop://") || url.startsWith("iiopname://") || url.startsWith("corbaname:");
    }

    /**
     * Initializes the COS Naming Service.
     * This method initializes the three instance fields:
     * _nc : The root naming context.
     * _orb: The ORB to use for connecting RMI/IIOP stubs and for
     * getting the naming context (_nc) if one was not specified
     * explicitly via PROVIDER_URL.
     * _name: The name of the root naming context.
     * <p/>
     * _orb is obtained from java.naming.corba.orb if it has been set.
     * Otherwise, _orb is created using the host/port from PROVIDER_URL
     * (if it contains an "iiop" or "iiopname" URL), or from initialization
     * properties specified in env.
     * <p/>
     * _nc is obtained from the IOR stored in PROVIDER_URL if it has been
     * set and does not contain an "iiop" or "iiopname" URL. It can be
     * a stringified IOR, "corbaloc" URL, "corbaname" URL,
     * or a URL (such as file/http/ftp) to a location
     * containing a stringified IOR. If PROVIDER_URL has not been
     * set in this way, it is obtained from the result of
     * ORB.resolve_initial_reference("NameService");
     * <p/>
     * _name is obtained from the "iiop", "iiopname", or "corbaname" URL.
     * It is the empty name by default.
     *
     * @param env Environment The possibly null environment.
     * @throws NamingException When an error occurs while initializing the
     *                         ORB or the naming context.
     */
    private void initOrbAndRootContext(Hashtable env) throws NamingException {
        ORB inOrb = null;
        String ncIor = null;

        if (env != null) {
            inOrb = (ORB) env.get("java.naming.corba.orb");
        }

        // Extract PROVIDER_URL from environment
        String provUrl = null;
        if (env != null) {
            provUrl = (String) env.get(javax.naming.Context.PROVIDER_URL);
        }

        if (provUrl != null && !isCorbaUrl(provUrl)) {
            // Initialize the root naming context by using the IOR supplied
            // in the PROVIDER_URL
            ncIor = getStringifiedIor(provUrl);

            if (inOrb == null) {

                // no ORB instance specified; create one using env and defaults
                inOrb = CorbaORBService.getCurrent();
            }
            setOrbAndRootContext(inOrb, ncIor);
        } else if (provUrl != null) {
            // Initialize the root naming context by using the URL supplied
            // in the PROVIDER_URL
            String insName = initUsingUrl(inOrb, provUrl, env);

            // If name supplied in URL, resolve it to a NamingContext
            if (insName.length() > 0) {
                _name = parser.nameToCosName(parser.parse(insName));
                try {
                    org.omg.CORBA.Object obj = _nc.resolve(_name);
                    _nc = NamingContextHelper.narrow(obj);
                    if (_nc == null) {
                        throw IIOPLogger.ROOT_LOGGER.notANamingContext(insName);
                    }
                } catch (BAD_PARAM e) {
                    throw IIOPLogger.ROOT_LOGGER.notANamingContext(insName);
                } catch (Exception e) {
                    throw ExceptionMapper.mapException(e, this, _name);
                }
            }
        } else {
            // No PROVIDER_URL supplied; initialize using defaults
            if (inOrb == null) {

                // No ORB instance specified; create one using env and defaults
                inOrb = CorbaORBService.getCurrent();
                IIOPLogger.ROOT_LOGGER.debugf("Getting default ORB %s", inOrb);
            }
            setOrbAndRootContext(inOrb, (String) null);
        }
    }


    private String initUsingUrl(ORB orb, String url, Hashtable env)
            throws NamingException {
        if (url.startsWith("iiop://") || url.startsWith("iiopname://")) {
            return initUsingIiopUrl(orb, url, env);
        } else {
            return initUsingCorbanameUrl(orb, url, env);
        }
    }

    /**
     * Handles "iiop" and "iiopname" URLs (INS 98-10-11)
     */
    private String initUsingIiopUrl(ORB defOrb, String url, Hashtable env)
            throws NamingException {
        try {
            IiopUrl parsedUrl = new IiopUrl(url);

            Vector addrs = parsedUrl.getAddresses();
            IiopUrl.Address addr;
            NamingException savedException = null;

            for (int i = 0; i < addrs.size(); i++) {
                addr = (IiopUrl.Address) addrs.elementAt(i);

                try {
                    if (defOrb != null) {
                        try {
                            String tmpUrl = "corbaloc:iiop:" + addr.host + ":" + addr.port + "/NameService";
                            org.omg.CORBA.Object rootCtx = defOrb.string_to_object(tmpUrl);
                            setOrbAndRootContext(defOrb, rootCtx);
                            return parsedUrl.getStringName();
                        } catch (Exception e) {
                        } // keep going
                    }

                    // Get ORB
                    ORB orb = CorbaUtils.getOrb(addr.host, addr.port, env);

                    // Assign to fields
                    setOrbAndRootContext(orb, (String) null);
                    return parsedUrl.getStringName();

                } catch (NamingException ne) {
                    savedException = ne;
                }
            }
            if (savedException != null) {
                throw savedException;
            } else {
                throw IIOPLogger.ROOT_LOGGER.invalidURLOrIOR(url);
            }
        } catch (MalformedURLException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    /**
     * Initializes using "corbaname" URL (INS 99-12-03)
     */
    private String initUsingCorbanameUrl(ORB orb, String url, Hashtable env)
            throws NamingException {
        try {
            CorbanameUrl parsedUrl = new CorbanameUrl(url);

            String corbaloc = parsedUrl.getLocation();
            String cosName = parsedUrl.getStringName();

            if (orb == null) {

                // No ORB instance specified; create one using env and defaults
                orb = CorbaORBService.getCurrent();
            }
            setOrbAndRootContext(orb, corbaloc);

            return parsedUrl.getStringName();
        } catch (MalformedURLException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    private void setOrbAndRootContext(ORB orb, String ncIor)
            throws NamingException {
        _orb = orb;
        try {
            org.omg.CORBA.Object ncRef;
            if (ncIor != null) {
                ncRef = _orb.string_to_object(ncIor);
            } else {
                ncRef = _orb.resolve_initial_references("NameService");
            }
            _nc = NamingContextHelper.narrow(ncRef);
            if (_nc == null) {
                if (ncIor != null) {
                    throw IIOPLogger.ROOT_LOGGER.errorConvertingIORToNamingCtx(ncIor);
                } else {
                    throw IIOPLogger.ROOT_LOGGER.errorResolvingNSInitRef();
                }
            }
        } catch (org.omg.CORBA.ORBPackage.InvalidName in) {
            NamingException ne = IIOPLogger.ROOT_LOGGER.cosNamingNotRegisteredCorrectly();
            ne.setRootCause(in);
            throw ne;
        } catch (org.omg.CORBA.COMM_FAILURE e) {
            NamingException ne = IIOPLogger.ROOT_LOGGER.errorConnectingToORB();
            ne.setRootCause(e);
            throw ne;
        } catch (BAD_PARAM e) {
            NamingException ne = IIOPLogger.ROOT_LOGGER.invalidURLOrIOR(ncIor);
            ne.setRootCause(e);
            throw ne;
        } catch (org.omg.CORBA.INV_OBJREF e) {
            NamingException ne = IIOPLogger.ROOT_LOGGER.invalidObjectReference(ncIor);
            ne.setRootCause(e);
            throw ne;
        }
    }

    private void setOrbAndRootContext(ORB orb, org.omg.CORBA.Object ncRef)
            throws NamingException {
        _orb = orb;
        try {
            _nc = NamingContextHelper.narrow(ncRef);
            if (_nc == null) {
                throw IIOPLogger.ROOT_LOGGER.errorConvertingIORToNamingCtx(ncRef.toString());
            }
        } catch (org.omg.CORBA.COMM_FAILURE e) {
             NamingException ne = IIOPLogger.ROOT_LOGGER.errorConnectingToORB();
            ne.setRootCause(e);
            throw ne;
        }
    }

    private String getStringifiedIor(String url) throws NamingException {
        if (url.startsWith("IOR:") || url.startsWith("corbaloc:")) {
            return url;
        } else {
            InputStream in = null;
            Throwable originalException = null;
            String retValue = null;
            try {
                URL u = new URL(url);
                in = u.openStream();
                if (in != null) {
                    BufferedReader bufin = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
                    String str;
                    while ((str = bufin.readLine()) != null) {
                        if (str.startsWith("IOR:")) {
                            retValue = str;
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                NamingException ne = IIOPLogger.ROOT_LOGGER.invalidURLOrIOR(url);
                ne.setRootCause(e);
                originalException = ne;
            } catch (Throwable ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                originalException = ex;
            } finally {
                Throwable suppressed = originalException; // OK to be null
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        NamingException ne = IIOPLogger.ROOT_LOGGER.invalidURLOrIOR(url);
                        ne.setRootCause(e);
                        if (suppressed != null) {
                            suppressed.addSuppressed(ne);
                        } else {
                            suppressed = ne;
                        }
                    }
                }
                if (suppressed != null) {
                    if (suppressed instanceof RuntimeException) {
                        throw (RuntimeException) suppressed;
                    }
                    if (suppressed instanceof Error) {
                        throw (Error) suppressed;
                    }
                    if (suppressed instanceof NamingException) {
                        throw (NamingException) suppressed;
                    }
                }
            }
            if (retValue != null) {
                return retValue;
            }
        }
        throw IIOPLogger.ROOT_LOGGER.urlDoesNotContainIOR(url);
    }


    /**
     * Does the job of calling the COS Naming API,
     * resolve, and performs the exception mapping. If the resolved
     * object is a COS Naming Context (sub-context), then this function
     * returns a new JNDI naming context object.
     *
     * @param path the NameComponent[] object.
     * @return Resolved object returned by the COS Name Server.
     * @throws NotFound      No objects under the name.
     * @throws org.omg.CosNaming.NamingContextPackage.CannotProceed Unable to obtain a continuation context
     * @throws org.omg.CosNaming.NamingContextPackage.InvalidName   Name not understood.
     */
    Object callResolve(NameComponent[] path)
            throws NamingException {
        try {
            org.omg.CORBA.Object obj = _nc.resolve(path);
            try {
                NamingContext nc = NamingContextHelper.narrow(obj);
                if (nc != null) {
                    return new CNCtx(_orb, nc, _env, makeFullName(path));
                } else {
                    return obj;
                }
            } catch (org.omg.CORBA.SystemException e) {
                return obj;
            }
        } catch (Exception e) {
            throw ExceptionMapper.mapException(e, this, path);
        }
    }

    /**
     * Converts the "String" name into a CompositeName
     * returns the object resolved by the COS Naming api,
     * resolve. Returns the current context if the name is empty.
     * Returns either an org.omg.CORBA.Object or javax.naming.Context object.
     *
     * @param name string used to resolve the object.
     * @return the resolved object
     * @throws NamingException See callResolve.
     */
    public Object lookup(String name) throws NamingException {
        return lookup(new CompositeName(name));
    }

    /**
     * Converts the "Name" name into a NameComponent[] object and
     * returns the object resolved by the COS Naming api,
     * resolve. Returns the current context if the name is empty.
     * Returns either an org.omg.CORBA.Object or javax.naming.Context object.
     *
     * @param name JNDI Name used to resolve the object.
     * @return the resolved object
     * @throws NamingException See callResolve.
     */
    public Object lookup(Name name)
            throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(name.toString());
        if (name.size() == 0)
            return this; // %%% should clone() so that env can be changed
        NameComponent[] path = CNNameParser.nameToCosName(name);

        try {
            Object answer = callResolve(path);

            try {
                return NamingManager.getObjectInstance(answer, name, this, _env);
            } catch (NamingException e) {
                throw e;
            } catch (Exception e) {
                NamingException ne = IIOPLogger.ROOT_LOGGER.errorGeneratingObjectViaFactory();
                ne.setRootCause(e);
                throw ne;
            }
        } catch (CannotProceedException cpe) {
            javax.naming.Context cctx = getContinuationContext(cpe);
            return cctx.lookup(cpe.getRemainingName());
        }
    }

    /**
     * Performs bind or rebind in the context depending on whether the
     * flag rebind is set. The only objects allowed to be bound are of
     * types org.omg.CORBA.Object, org.omg.CosNaming.NamingContext.
     * You can use a state factory to turn other objects (such as
     * Remote) into these acceptable forms.
     * <p/>
     * Uses the COS Naming apis bind/rebind or
     * bind_context/rebind_context.
     *
     * @param pth    NameComponent[] object
     * @param obj    Object to be bound.
     * @param rebind perform rebind ? if true performs a rebind.
     * @throws NotFound      No objects under the name.
     * @throws org.omg.CosNaming.NamingContextPackage.CannotProceed Unable to obtain a continuation context
     * @throws org.omg.CosNaming.NamingContextPackage.AlreadyBound  An object is already bound to this name.
     */
    private void callBindOrRebind(NameComponent[] pth, Name name,
                                  Object obj, boolean rebind) throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(name.toString());
        try {
            // Call state factories to convert
            obj = NamingManager.getStateToBind(obj, name, this, _env);

            if (obj instanceof CNCtx) {
                // Use naming context object reference
                obj = ((CNCtx) obj)._nc;
            }

            if (obj instanceof NamingContext) {
                NamingContext nobj = NamingContextHelper.narrow((org.omg.CORBA.Object) obj);
                if (rebind)
                    _nc.rebind_context(pth, nobj);
                else
                    _nc.bind_context(pth, nobj);

            } else if (obj instanceof org.omg.CORBA.Object) {
                if (rebind)
                    _nc.rebind(pth, (org.omg.CORBA.Object) obj);
                else
                    _nc.bind(pth, (org.omg.CORBA.Object) obj);
            } else
                throw IIOPLogger.ROOT_LOGGER.notACorbaObject();
        } catch (BAD_PARAM e) {
            // probably narrow() failed?
            NamingException ne = new NotContextException(name.toString());
            ne.setRootCause(e);
            throw ne;
        } catch (Exception e) {
            throw ExceptionMapper.mapException(e, this, pth);
        }
    }

    /**
     * Converts the "Name" name into a NameComponent[] object and
     * performs the bind operation. Uses callBindOrRebind. Throws an
     * invalid name exception if the name is empty. We need a name to
     * bind the object even when we work within the current context.
     *
     * @param name JNDI Name object
     * @param obj  Object to be bound.
     * @throws NamingException See callBindOrRebind
     */
    public void bind(Name name, Object obj)
            throws NamingException {
        if (name.size() == 0) {
            throw IIOPLogger.ROOT_LOGGER.invalidEmptyName();
        }

        NameComponent[] path = CNNameParser.nameToCosName(name);

        try {
            callBindOrRebind(path, name, obj, false);
        } catch (CannotProceedException e) {
            javax.naming.Context cctx = getContinuationContext(e);
            cctx.bind(e.getRemainingName(), obj);
        }
    }

    private static javax.naming.Context getContinuationContext(CannotProceedException cpe) throws NamingException {
        try {
            return NamingManager.getContinuationContext(cpe);
        } catch (CannotProceedException e) {
            Object resObj = e.getResolvedObj();
            if (resObj instanceof Reference) {
                Reference ref = (Reference) resObj;
                RefAddr addr = ref.get("nns");
                if (addr.getContent() instanceof javax.naming.Context) {
                    NamingException ne = IIOPLogger.ROOT_LOGGER.noReferenceFound();
                    ne.setRootCause(cpe.getRootCause());
                    throw ne;
                }
            }
            throw e;
        }
    }

    /**
     * Converts the "String" name into a CompositeName object and
     * performs the bind operation. Uses callBindOrRebind. Throws an
     * invalid name exception if the name is empty.
     *
     * @param name string
     * @param obj  Object to be bound.
     * @throws NamingException See callBindOrRebind
     */
    public void bind(String name, Object obj) throws NamingException {
        bind(new CompositeName(name), obj);
    }

    /**
     * Converts the "Name" name into a NameComponent[] object and
     * performs the rebind operation. Uses callBindOrRebind. Throws an
     * invalid name exception if the name is empty. We must have a name
     * to rebind the object to even if we are working within the current
     * context.
     *
     * @param name string
     * @param obj  Object to be bound.
     * @throws NamingException See callBindOrRebind
     */
    public void rebind(Name name, Object obj)
            throws NamingException {
        if (name.size() == 0) {
            throw IIOPLogger.ROOT_LOGGER.invalidEmptyName();
        }
        NameComponent[] path = CNNameParser.nameToCosName(name);
        try {
            callBindOrRebind(path, name, obj, true);
        } catch (CannotProceedException e) {
            javax.naming.Context cctx = getContinuationContext(e);
            cctx.rebind(e.getRemainingName(), obj);
        }
    }

    /**
     * Converts the "String" name into a CompositeName object and
     * performs the rebind operation. Uses callBindOrRebind. Throws an
     * invalid name exception if the name is an empty string.
     *
     * @param name string
     * @param obj  Object to be bound.
     * @throws NamingException See callBindOrRebind
     */
    public void rebind(String name, Object obj)
            throws NamingException {
        rebind(new CompositeName(name), obj);
    }

    /**
     * Calls the unbind api of COS Naming and uses the exception mapper
     * class  to map the exceptions
     *
     * @param path NameComponent[] object
     * @throws NotFound      No objects under the name. If leaf
     *                       is not found, that's OK according to the JNDI spec
     * @throws org.omg.CosNaming.NamingContextPackage.CannotProceed Unable to obtain a continuation context
     * @throws org.omg.CosNaming.NamingContextPackage.InvalidName   Name not understood.
     */
    private void callUnbind(NameComponent[] path) throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(Arrays.toString(path));
        try {
            _nc.unbind(path);
        } catch (NotFound e) {
            // If leaf is the one missing, return success
            // as per JNDI spec

            if (leafNotFound(e, path[path.length - 1])) {
                // do nothing
            } else {
                throw ExceptionMapper.mapException(e, this, path);
            }
        } catch (Exception e) {
            throw ExceptionMapper.mapException(e, this, path);
        }
    }

    private boolean leafNotFound(NotFound e, NameComponent leaf) {

        // This test is not foolproof because some name servers
        // always just return one component in rest_of_name
        // so you might not be able to tell whether that is
        // the leaf (e.g. aa/aa/aa, which one is missing?)

        NameComponent rest;
        return e.why.value() == NotFoundReason._missing_node &&
                e.rest_of_name.length == 1 &&
                (rest = e.rest_of_name[0]).id.equals(leaf.id) &&
                (rest.kind == leaf.kind ||
                        (rest.kind != null && rest.kind.equals(leaf.kind)));
    }

    /**
     * Converts the "String" name into a CompositeName object and
     * performs the unbind operation. Uses callUnbind. If the name is
     * empty, throws an invalid name exception. Do we unbind the
     * current context (JNDI spec says work with the current context if
     * the name is empty) ?
     *
     * @param name string
     * @throws NamingException See callUnbind
     */
    public void unbind(String name) throws NamingException {
        unbind(new CompositeName(name));
    }

    /**
     * Converts the "Name" name into a NameComponent[] object and
     * performs the unbind operation. Uses callUnbind. Throws an
     * invalid name exception if the name is empty.
     *
     * @param name string
     * @throws NamingException See callUnbind
     */
    public void unbind(Name name)
            throws NamingException {
        if (name.size() == 0)
            throw IIOPLogger.ROOT_LOGGER.invalidEmptyName();
        NameComponent[] path = CNNameParser.nameToCosName(name);
        try {
            callUnbind(path);
        } catch (CannotProceedException e) {
            javax.naming.Context cctx = getContinuationContext(e);
            cctx.unbind(e.getRemainingName());
        }
    }

    /**
     * Renames an object. Since COS Naming does not support a rename
     * api, this method unbinds the object with the "oldName" and
     * creates a new binding.
     *
     * @param oldName string, existing name for the binding.
     * @param newName string, name used to replace.
     * @throws NamingException See bind
     */
    public void rename(String oldName, String newName)
            throws NamingException {
        rename(new CompositeName(oldName), new CompositeName(newName));
    }

    /**
     * Renames an object. Since COS Naming does not support a rename
     * api, this method unbinds the object with the "oldName" and
     * creates a new binding.
     *
     * @param oldName JNDI Name, existing name for the binding.
     * @param newName JNDI Name, name used to replace.
     * @throws NamingException See bind
     */
    public void rename(Name oldName, Name newName)
            throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(oldName.toString());
        if (oldName.size() == 0 || newName.size() == 0)
            throw IIOPLogger.ROOT_LOGGER.invalidEmptyName();
        Object obj = lookup(oldName);
        bind(newName, obj);
        unbind(oldName);
    }

    /**
     * Returns a NameClassEnumeration object which has a list of name
     * class pairs. Lists the current context if the name is empty.
     *
     * @param name string
     * @return a list of name-class objects as a NameClassEnumeration.
     * @throws NamingException All exceptions thrown by lookup
     *                         with a non-null argument
     */
    public NamingEnumeration list(String name) throws NamingException {
        return list(new CompositeName(name));
    }

    /**
     * Returns a NameClassEnumeration object which has a list of name
     * class pairs. Lists the current context if the name is empty.
     *
     * @param name JNDI Name
     * @return a list of name-class objects as a NameClassEnumeration.
     * @throws NamingException All exceptions thrown by lookup
     */
    public NamingEnumeration list(Name name)
            throws NamingException {
        return listBindings(name);
    }

    /**
     * Returns a BindingEnumeration object which has a list of name
     * object pairs. Lists the current context if the name is empty.
     *
     * @param name string
     * @return a list of bindings as a BindingEnumeration.
     * @throws NamingException all exceptions returned by lookup
     */
    public NamingEnumeration listBindings(String name)
            throws NamingException {
        return listBindings(new CompositeName(name));
    }

    /**
     * Returns a BindingEnumeration object which has a list of name
     * class pairs. Lists the current context if the name is empty.
     *
     * @param name JNDI Name
     * @return a list of bindings as a BindingEnumeration.
     * @throws NamingException all exceptions returned by lookup.
     */
    public NamingEnumeration listBindings(Name name)
            throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(name.toString());
        if (name.size() > 0) {
            try {
                Object obj = lookup(name);
                if (obj instanceof CNCtx) {
                    return new CNBindingEnumeration(
                            (CNCtx) obj, true, _env);
                } else {
                    throw new NotContextException(name.toString());
                }
            } catch (NamingException ne) {
                throw ne;
            } catch (BAD_PARAM e) {
                NamingException ne =
                        new NotContextException(name.toString());
                ne.setRootCause(e);
                throw ne;
            }
        }
        return new CNBindingEnumeration(this, false, _env);
    }

    /**
     * Calls the destroy on the COS Naming Server
     *
     * @param nc The NamingContext object to use.
     * @throws org.omg.CosNaming.NamingContextPackage.NotEmpty when the context is not empty and cannot be destroyed.
     */
    private void callDestroy(NamingContext nc)
            throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(nc.toString());
        try {
            nc.destroy();
        } catch (Exception e) {
            throw ExceptionMapper.mapException(e, this, null);
        }
    }

    /**
     * Uses the callDestroy function to destroy the context. If name is
     * empty destroys the current context.
     *
     * @param name string
     * @throws javax.naming.OperationNotSupportedException when list is invoked
     *                                        with a non-null argument
     */
    public void destroySubcontext(String name) throws NamingException {
        destroySubcontext(new CompositeName(name));
    }

    /**
     * Uses the callDestroy function to destroy the context. Destroys
     * the current context if name is empty.
     *
     * @param name JNDI Name
     * @throws javax.naming.OperationNotSupportedException when list is invoked
     *                                        with a non-null argument
     */
    public void destroySubcontext(Name name)
            throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(name.toString());
        NamingContext the_nc = _nc;
        NameComponent[] path = CNNameParser.nameToCosName(name);
        if (name.size() > 0) {
            try {
                javax.naming.Context ctx =
                        (javax.naming.Context) callResolve(path);
                CNCtx cnc = (CNCtx) ctx;
                the_nc = cnc._nc;
                cnc.close(); //remove the reference to the context
            } catch (ClassCastException e) {
                throw new NotContextException(name.toString());
            } catch (CannotProceedException e) {
                javax.naming.Context cctx = getContinuationContext(e);
                cctx.destroySubcontext(e.getRemainingName());
                return;
            } catch (NameNotFoundException e) {
                // If leaf is the one missing, return success
                // as per JNDI spec

                if (e.getRootCause() instanceof NotFound &&
                        leafNotFound((NotFound) e.getRootCause(),
                                path[path.length - 1])) {
                    return; // leaf missing OK
                }
                throw e;
            } catch (NamingException e) {
                throw e;
            }
        }
        callDestroy(the_nc);
        callUnbind(path);
    }

    /**
     * Calls the bind_new_context COS naming api to create a new subcontext.
     *
     * @param path NameComponent[] object
     * @return the new context object.
     * @throws NotFound      No objects under the name.
     * @throws org.omg.CosNaming.NamingContextPackage.CannotProceed Unable to obtain a continuation context
     * @throws org.omg.CosNaming.NamingContextPackage.InvalidName   Name not understood.
     * @throws org.omg.CosNaming.NamingContextPackage.AlreadyBound  An object is already bound to this name.
     */
    private javax.naming.Context callBindNewContext(NameComponent[] path)
            throws NamingException {
        if (_nc == null)
            throw IIOPLogger.ROOT_LOGGER.notANamingContext(Arrays.toString(path));
        try {
            NamingContext nctx = _nc.bind_new_context(path);
            return new CNCtx(_orb, nctx, _env, makeFullName(path));
        } catch (Exception e) {
            throw ExceptionMapper.mapException(e, this, path);
        }
    }

    /**
     * Uses the callBindNewContext convenience function to create a new
     * context. Throws an invalid name exception if the name is empty.
     *
     * @param name string
     * @return the new context object.
     * @throws NamingException See callBindNewContext
     */
    public javax.naming.Context createSubcontext(String name)
            throws NamingException {
        return createSubcontext(new CompositeName(name));
    }

    /**
     * Uses the callBindNewContext convenience function to create a new
     * context. Throws an invalid name exception if the name is empty.
     *
     * @param name string
     * @return the new context object.
     * @throws NamingException See callBindNewContext
     */
    public javax.naming.Context createSubcontext(Name name)
            throws NamingException {
        if (name.size() == 0)
            throw IIOPLogger.ROOT_LOGGER.invalidEmptyName();
        NameComponent[] path = CNNameParser.nameToCosName(name);
        try {
            return callBindNewContext(path);
        } catch (CannotProceedException e) {
            javax.naming.Context cctx = getContinuationContext(e);
            return cctx.createSubcontext(e.getRemainingName());
        }
    }

    /**
     * Is mapped to resolve in the COS Naming api.
     *
     * @param name string
     * @return the resolved object.
     * @throws NamingException See lookup.
     */
    public Object lookupLink(String name) throws NamingException {
        return lookupLink(new CompositeName(name));
    }

    /**
     * Is mapped to resolve in the COS Naming api.
     *
     * @param name string
     * @return the resolved object.
     * @throws NamingException See lookup.
     */
    public Object lookupLink(Name name) throws NamingException {
        return lookup(name);
    }

    /**
     * Allow access to the name parser object.
     *
     * @param name, is ignored since there is only one Name
     *               Parser object.
     * @return NameParser object
     * @throws NamingException --
     */
    public NameParser getNameParser(String name) throws NamingException {
        return parser;
    }

    /**
     * Allow access to the name parser object.
     *
     * @param name JNDI name, is ignored since there is only one Name
     *             Parser object.
     * @return NameParser object
     * @throws NamingException --
     */
    public NameParser getNameParser(Name name) throws NamingException {
        return parser;
    }

    /**
     * Returns the current environment.
     *
     * @return Environment.
     */
    public Hashtable getEnvironment() throws NamingException {
        if (_env == null) {
            return new Hashtable(5, 0.75f);
        } else {
            return (Hashtable) _env.clone();
        }
    }

    public String composeName(String name, String prefix) throws NamingException {
        return composeName(new CompositeName(name),
                new CompositeName(prefix)).toString();
    }

    public Name composeName(Name name, Name prefix) throws NamingException {
        Name result = (Name) prefix.clone();
        return result.addAll(name);
    }

    /**
     * Adds to the environment for the current context.
     * Record change but do not reinitialize ORB.
     *
     * @param propName The property name.
     * @param propValue  The ORB.
     * @return the previous value of this property if any.
     */
    public Object addToEnvironment(String propName,
                                             Object propValue)
            throws NamingException {
        if (_env == null) {
            _env = new Hashtable(7, 0.75f);
        } else {
            // copy-on-write
            _env = (Hashtable) _env.clone();
        }

        return _env.put(propName, propValue);
    }

    // Record change but do not reinitialize ORB
    public Object removeFromEnvironment(String propName)
            throws NamingException {
        if (_env != null && _env.get(propName) != null) {
            // copy-on-write
            _env = (Hashtable) _env.clone();
            return _env.remove(propName);
        }
        return null;
    }

    public synchronized void close() throws NamingException {
    }

    protected void finalize() {
        try {
            close();
        } catch (NamingException e) {
            // ignore failures
        }
    }
}
