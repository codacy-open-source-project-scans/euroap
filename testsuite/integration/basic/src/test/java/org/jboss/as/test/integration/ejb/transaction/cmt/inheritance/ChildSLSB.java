/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.transaction.cmt.inheritance;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.transaction.Transaction;

import org.jboss.logging.Logger;

/**
 * Bean which shape is based on ejb spec <code>class ABean</code>
 * from chapter <pre>8.3.7.1Specification of Transaction Attributes
 * with Metadata Annotations</pre>
 *
 * @author Ondrej Chaloupka <ochaloup@redhat.com>
 */
@Stateless
public class ChildSLSB extends SuperSLSB {
    private static final Logger log = Logger.getLogger(ChildSLSB.class);

    /**
     * {@link TransactionAttribute} of the method should be REQUIRED.
     */
    @Override
    public Transaction aMethod() {
        log.trace(this.getClass().getName() + ".aMethod called ");
        return getTransaction();
    }

    /**
     * {@link TransactionAttribute} of the method inherited from super class
     * should be SUPPORTS.
     */
    // bMethod() call

    /**
     * {@link TransactionAttribute} of the method should be MANDATORY.
     */
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    @Override
    public Transaction cMethod() {
        log.trace(this.getClass().getName() + ".cMethod called ");
        return getTransaction();
    }

    /**
     * {@link TransactionAttribute} of the method should be REQUIRED.
     */
    @Override
    public Transaction neverMethod() {
        log.trace(this.getClass().getName() + ".neverMethod called ");
        return getTransaction();
    }
}
