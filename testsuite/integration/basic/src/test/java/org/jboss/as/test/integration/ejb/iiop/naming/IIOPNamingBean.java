/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.iiop.naming;

import jakarta.ejb.RemoteHome;
import jakarta.ejb.Stateless;

/**
 * @author Stuart Douglas
 */
@RemoteHome(IIOPNamingHome.class)
@Stateless
public class IIOPNamingBean {

    public String hello() {
        return "hello";
    }

}
