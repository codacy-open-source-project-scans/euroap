/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.tx;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.jboss.as.ee.component.ComponentInstance;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.component.EJBComponent;
import org.jboss.as.ejb3.component.stateful.StatefulSessionComponentInstance;
import org.jboss.invocation.InterceptorContext;

import static org.jboss.as.ejb3.tx.util.StatusHelper.statusAsString;

/**
 * A per instance interceptor that keeps an association with the outcoming transaction.
 * <p/>
 * Enterprise Beans 3 13.6.1:
 * In the case of a stateful session bean, it is possible that the business method that started a transaction
 * completes without committing or rolling back the transaction. In such a case, the container must retain
 * the association between the transaction and the instance across multiple client calls until the instance
 * commits or rolls back the transaction. When the client invokes the next business method, the container
 * must invoke the business method (and any applicable interceptor methods for the bean) in this transac-
 * tion context.
 *
 * @author <a href="cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class StatefulBMTInterceptor extends BMTInterceptor {

    public StatefulBMTInterceptor(final EJBComponent component) {
        super(component);
    }

    private void checkBadStateful() {
        int status = Status.STATUS_NO_TRANSACTION;
        TransactionManager tm = getComponent().getTransactionManager();
        try {
            status = tm.getStatus();
        } catch (SystemException ex) {
            EjbLogger.ROOT_LOGGER.failedToGetStatus(ex);
        }

        switch (status) {
            case Status.STATUS_COMMITTING:
            case Status.STATUS_MARKED_ROLLBACK:
            case Status.STATUS_PREPARING:
            case Status.STATUS_ROLLING_BACK:
                try {
                    tm.rollback();
                } catch (Exception ex) {
                    EjbLogger.ROOT_LOGGER.failedToRollback(ex);
                }
                EjbLogger.ROOT_LOGGER.transactionNotComplete(getComponent().getComponentName(), statusAsString(status));
        }
    }

    @Override
    protected Object handleInvocation(final InterceptorContext invocation) throws Exception {
        final StatefulSessionComponentInstance instance = (StatefulSessionComponentInstance) invocation.getPrivateData(ComponentInstance.class);

        TransactionManager tm = getComponent().getTransactionManager();
        assert tm.getTransaction() == null : "can't handle BMT transaction, there is a transaction active";

        // Is the instance already associated with a transaction?
        Transaction tx = instance.getTransaction();
        if (tx != null) {
            // then resume that transaction.
            instance.setTransaction(null);
            tm.resume(tx);
        }
        try {
            return invocation.proceed();
        } catch (Throwable e) {
            throw this.handleException(invocation, e);
        } finally {
            checkBadStateful();
            // Is the instance finished with the transaction?
            Transaction newTx = tm.getTransaction();
            //always set it, even if null
            instance.setTransaction(newTx);
            if (newTx != null) {
                // remember the association
                // and suspend it.
                tm.suspend();
            }
        }
    }
}
