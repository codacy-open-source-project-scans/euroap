/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.iiop.security;

import java.rmi.RemoteException;
import jakarta.ejb.Stateless;

/**
 * @author Stuart Douglas
 */
@Stateless
public class ClientEjb {

    private IIOPSecurityStatelessHome statelessHome;


    public String testSuccess() throws RemoteException {
        IIOPSecurityStatelessRemote ejb = statelessHome.create();
        return ejb.role1();
    }

    public String testFailure() throws RemoteException {
        IIOPSecurityStatelessRemote ejb = statelessHome.create();
        return ejb.role2();
    }
}
