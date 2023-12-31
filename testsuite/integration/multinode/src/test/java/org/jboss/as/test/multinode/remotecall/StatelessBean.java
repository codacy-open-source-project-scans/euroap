/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.multinode.remotecall;

import java.util.Properties;

import jakarta.ejb.Local;
import jakarta.ejb.LocalHome;
import jakarta.ejb.Remote;
import jakarta.ejb.RemoteHome;
import jakarta.ejb.Stateless;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:bdecoste@jboss.com">William DeCoste</a>
 */
@Stateless
@Local(StatelessLocal.class)
@LocalHome(StatelessLocalHome.class)
@Remote(StatelessRemote.class)
@RemoteHome(StatelessRemoteHome.class)
public class StatelessBean {
    private static final Logger log = Logger.getLogger(StatelessBean.class);

    private static int methodCount = 0;
    private static int homeMethodCount = 0;

    private InitialContext getInitialContext() throws NamingException {
        final Properties props = new Properties();
        // setup the Jakarta Enterprise Beans: namespace URL factory
        props.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        return new InitialContext(props);
    }

    public void localCall() throws Exception {
        InitialContext jndiContext = getInitialContext();
        log.trace("Calling Local remotely... " + jndiContext.getEnvironment());
        StatefulLocal stateful = (StatefulLocal) jndiContext.lookup("ejb:/" + RemoteLocalCallTestCase.ARCHIVE_NAME_SERVER
                + "//" + StatefulBean.class.getSimpleName() + "!" + StatefulLocal.class.getName());
        stateful.method();
    }

    public void localHomeCall() throws Exception {
        InitialContext jndiContext = getInitialContext();
        log.trace("Calling LocalHome remotely... " + jndiContext.getEnvironment());
        StatefulLocalHome statefulHome = (StatefulLocalHome) jndiContext.lookup("ejb:/" + StatefulBean.class.getSimpleName()
                + "!" + StatefulLocalHome.class.getName());
        StatefulLocal stateful = statefulHome.create();
        stateful.homeMethod();
    }

    public int remoteCall() throws Exception {
        ++methodCount;
        InitialContext jndiContext = getInitialContext();
        log.trace("Calling Remote... " + jndiContext.getEnvironment());
        StatelessRemote stateless = (StatelessRemote) jndiContext.lookup("ejb:/" + RemoteLocalCallTestCase.ARCHIVE_NAME_SERVER
                + "//" + StatelessBean.class.getSimpleName() + "!" + StatelessRemote.class.getName());
        return stateless.method();
    }

    public int remoteHomeCall() throws Exception {
        ++homeMethodCount;
        InitialContext jndiContext = getInitialContext();
        StatelessRemoteHome statelessHome = (StatelessRemoteHome) jndiContext.lookup("ejb:/"
                + RemoteLocalCallTestCase.ARCHIVE_NAME_SERVER + "//" + StatelessBean.class.getSimpleName() + "!"
                + StatelessRemoteHome.class.getName());
        StatelessRemote stateless = statelessHome.create();
        return stateless.homeMethod();
    }

    public int method() throws Exception {
        ++methodCount;
        log.trace("Method called " + methodCount);
        return methodCount;
    }

    public int homeMethod() throws Exception {
        ++homeMethodCount;
        log.trace("HomeMethod called " + homeMethodCount);
        return homeMethodCount;
    }

    public void ejbCreate() throws java.rmi.RemoteException, jakarta.ejb.CreateException {
        log.debug("Creating method for home interface...");
    }
}
