/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.iiop.naming;

import java.rmi.RemoteException;

import jakarta.ejb.RemoteHome;
import jakarta.ejb.Stateful;

/**
 * @author Stuart Douglas
 */
@RemoteHome(IIOPStatefulNamingHome.class)
@Stateful
public class IIOPStatefulNamingBean {

    int count = 0;

    public void ejbCreate(int start) {
        count = start;
    }

    public int increment() throws RemoteException {
        return ++count;
    }

}
