/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.ejb.client.outbound.connection.security;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

/**
 * @author <a href="mailto:mjurc@redhat.com">Michal Jurc</a> (c) 2017 Red Hat, Inc.
 */
@Stateless
public class IntermediateWhoAmI implements WhoAmI {

    @EJB(lookup = "ejb:/inbound-module/ServerWhoAmI!org.jboss.as.test.manualmode.ejb.client.outbound.connection.security.WhoAmI")
    private WhoAmI whoAmIBean;

    public String whoAmI() {
        return whoAmIBean.whoAmI();
    }

    public String whoAmIRestricted() {
        return whoAmIBean.whoAmIRestricted();
    }

}
