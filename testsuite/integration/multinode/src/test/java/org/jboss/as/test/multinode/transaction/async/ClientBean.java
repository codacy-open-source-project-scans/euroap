/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.multinode.transaction.async;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Hashtable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import javax.naming.Context;
import javax.naming.NamingException;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

/**
 * Client bean which lookups for a remote bean on other server and do the call.
 *
 * @author Ondrej Chaloupka
 */
@Stateless
@TransactionManagement(TransactionManagementType.BEAN)
public class ClientBean {

    @Resource
    private UserTransaction userTransaction;

    private Context namingContext;

    @PostConstruct
    private void postConstruct() {
        final Hashtable<String,String> props = new Hashtable<>();
        props.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        try {
            namingContext = new javax.naming.InitialContext(props);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    private void preDestroy() {
        if (namingContext != null) {
            try {
                namingContext.close();
            } catch (NamingException e) {
                //ignore
            }
        }
    }

    private TransactionalRemote getRemote(Class<?> beanClass) throws NamingException {
        return (TransactionalRemote) namingContext.lookup(String.format("ejb:/%s//%s!%s",
          TransactionPropagationTestCase.SERVER_DEPLOYMENT, beanClass.getSimpleName(), TransactionalRemote.class.getName()));
    }

    public void callToMandatory() throws Exception {
        final TransactionalRemote remote = getRemote(TransactionalMandatory.class);
        userTransaction.begin();
        try {
            remote.transactionStatus().get();
            fail("Expecting exception being thrown as async call does not provide transaction context");
        } catch (java.util.concurrent.ExecutionException ee) {
            // ignored - bean with transaction attribute mandatory
            // but async call does not provide transactional context thus the exception is expected
        } finally {
            userTransaction.rollback();
        }
    }

    public void callToStatusByRegistry() throws Exception {
        final TransactionalRemote remote = getRemote(TransactionalStatusByRegistry.class);
        userTransaction.begin();
        try {
            assertEquals("No transaction expected as async call does not pass txn context",
                (Integer) Status.STATUS_NO_TRANSACTION, remote.transactionStatus().get());
        } finally {
            userTransaction.rollback();
        }
    }

    public void callToStatusByTransactionmanager() throws Exception {
        final TransactionalRemote remote = getRemote(TransactionalStatusByManager.class);
        userTransaction.begin();
        try {
            assertEquals("No transaction expected as async call does not pass txn context",
                    (Integer) Status.STATUS_NO_TRANSACTION, remote.transactionStatus().get());
        } finally {
            userTransaction.rollback();
        }
    }

    /**
     * Verifies async method invocation with REQUIRED transaction attribute.
     * The client transaction context should not be propagated to the invoked async method.
     * The invoked async method should execute in a new, separate transaction context.
     */
    public void callToRequired() throws Exception {
        final TransactionalRemote remote = getRemote(TransactionalStatusByManager.class);
        userTransaction.begin();

        // asyncWithRequired() will throw RuntimeException and cause its transaction to rollback.
        // But it has no bearing on the transaction here, which should be able to commit okay.
        try {
            remote.asyncWithRequired().get();
        } catch (java.util.concurrent.ExecutionException e) {
            // This is expected since the invoked async method throws a RuntimeException
        }
        userTransaction.commit();
    }
}
