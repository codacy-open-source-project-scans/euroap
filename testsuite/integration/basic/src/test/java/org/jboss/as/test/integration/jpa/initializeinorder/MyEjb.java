/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jpa.initializeinorder;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * @author Scott Marlow
 */
@Singleton
@Startup
public class MyEjb {

    @PersistenceContext(unitName = "pu1")
    EntityManager em;

    @PersistenceContext(unitName = "pu2")
    EntityManager em2;

    @PostConstruct
    public void postConstruct() {
        TestState.addInitOrder(MyEjb.class.getSimpleName());
    }

    public boolean hasPersistenceContext() {
        return em != null && em2 != null;
    }
}
