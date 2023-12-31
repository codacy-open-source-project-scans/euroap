/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.iiop.security;

import jakarta.ejb.EJBObject;
import java.rmi.RemoteException;

/**
 * @author Stuart Douglas
 */
public interface IIOPSecurityStatelessRemote extends EJBObject {

    String role1() throws RemoteException;

    String role2() throws RemoteException;

}
