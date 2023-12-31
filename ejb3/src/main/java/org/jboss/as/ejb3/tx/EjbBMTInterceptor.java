/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.tx;

import jakarta.ejb.EJBException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.ComponentInstanceInterceptorFactory;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.component.EJBComponent;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.InterceptorFactoryContext;

/**
 * EJB 3 13.6.1:
 * If a stateless session bean instance starts a transaction in a business method or interceptor method, it
 * must commit the transaction before the business method (or all its interceptor methods) returns.
 *
 * @author <a href="mailto:carlo.dewolf@jboss.com">Carlo de Wolf</a>
 * @version $Revision: $
 */
public class EjbBMTInterceptor extends BMTInterceptor {

    public static final InterceptorFactory FACTORY = new ComponentInstanceInterceptorFactory() {
        @Override
        protected Interceptor create(final Component component, final InterceptorFactoryContext context) {
            return new EjbBMTInterceptor((EJBComponent) component);
        }
    };

    EjbBMTInterceptor(final EJBComponent component) {
        super(component);
    }

    private void checkStatelessDone(final EJBComponent component, final InterceptorContext invocation, final TransactionManager tm, Throwable ex) throws Exception {
        int status = Status.STATUS_NO_TRANSACTION;

        try {
            status = tm.getStatus();
        } catch (SystemException sex) {
            EjbLogger.ROOT_LOGGER.failedToGetStatus(sex);
        }

        switch (status) {
            case Status.STATUS_ACTIVE:
            case Status.STATUS_COMMITTING:
            case Status.STATUS_MARKED_ROLLBACK:
            case Status.STATUS_PREPARING:
            case Status.STATUS_ROLLING_BACK:
                try {
                    tm.rollback();
                } catch (Exception sex) {
                    EjbLogger.ROOT_LOGGER.failedToRollback(sex);
                }
                // fall through...
            case Status.STATUS_PREPARED:
                final String msg = EjbLogger.ROOT_LOGGER.transactionNotComplete(component.getComponentName());
                EjbLogger.ROOT_LOGGER.error(msg);
                if (ex instanceof Exception) {
                    throw new EJBException(msg, (Exception) ex);
                } else {
                    throw new EJBException(msg, new RuntimeException(ex));
                }
        }
        // the instance interceptor will discard the instance
        if (ex != null)
            throw this.handleException(invocation, ex);
    }


    @Override
    protected Object handleInvocation(final InterceptorContext invocation) throws Exception {
        final EJBComponent ejbComponent = getComponent();
        TransactionManager tm = ejbComponent.getTransactionManager();
        assert tm.getTransaction() == null : "can't handle BMT transaction, there is a transaction active";

        boolean exceptionThrown = false;
        try {
            return invocation.proceed();
        } catch (Throwable ex) {
            exceptionThrown = true;
            checkStatelessDone(ejbComponent, invocation, tm, ex);
            //we should never get here, as checkStatelessDone should re-throw
            throw (Exception)ex;
        } finally {
            try {
                if (!exceptionThrown) checkStatelessDone(ejbComponent, invocation, tm, null);
            } finally {
                tm.suspend();
            }
        }
    }

}
