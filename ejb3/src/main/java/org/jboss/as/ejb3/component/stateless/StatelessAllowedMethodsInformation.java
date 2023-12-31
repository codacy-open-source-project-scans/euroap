/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.component.stateless;

import java.util.Set;

import org.jboss.as.ee.component.interceptors.InvocationType;
import org.jboss.as.ejb3.component.allowedmethods.DeniedMethodKey;
import org.jboss.as.ejb3.component.allowedmethods.MethodType;
import org.jboss.as.ejb3.component.session.SessionBeanAllowedMethodsInformation;

/**
 * @author Stuart Douglas
 */
public class StatelessAllowedMethodsInformation extends SessionBeanAllowedMethodsInformation {

    public static final StatelessAllowedMethodsInformation INSTANCE_BMT = new StatelessAllowedMethodsInformation(true);
    public static final StatelessAllowedMethodsInformation INSTANCE_CMT = new StatelessAllowedMethodsInformation(false);

    protected StatelessAllowedMethodsInformation(boolean beanManagedTransaction) {
        super(beanManagedTransaction);
    }

    @Override
    protected void setup(Set<DeniedMethodKey> denied) {
        super.setup(denied);
        add(denied, InvocationType.POST_CONSTRUCT, MethodType.GET_CALLER_PRINCIPLE);
        add(denied, InvocationType.PRE_DESTROY, MethodType.GET_CALLER_PRINCIPLE);
        add(denied, InvocationType.POST_CONSTRUCT, MethodType.IS_CALLER_IN_ROLE);
        add(denied, InvocationType.PRE_DESTROY, MethodType.IS_CALLER_IN_ROLE);
    }
}
