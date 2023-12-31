/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.iiop.basic;

import jakarta.ejb.EJBHome;
import java.rmi.RemoteException;

/**
 * @author Stuart Douglas
 */
public interface IIOPBasicStatefulHome extends EJBHome {

    IIOPBasicStatefulRemote create(int state) throws RemoteException;

}
