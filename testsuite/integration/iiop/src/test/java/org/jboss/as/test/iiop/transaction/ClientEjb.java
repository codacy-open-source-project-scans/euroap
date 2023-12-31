/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.iiop.transaction;

import java.rmi.RemoteException;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;

import org.junit.Assert;

/**
 * @author Stuart Douglas
 */
@Stateless
@TransactionManagement(TransactionManagementType.BEAN)
public class ClientEjb {

    @Resource
    private UserTransaction userTransaction;

    private IIOPTransactionalHome home;

    private IIOPTransactionalStatefulHome statefulHome;

    public void basicTransactionPropagationTest() throws RemoteException, SystemException, NotSupportedException {

        final IIOPTransactionalRemote remote = home.create();
        Assert.assertEquals(Status.STATUS_NO_TRANSACTION, remote.transactionStatus());
        userTransaction.begin();
        try {
            Assert.assertEquals(Status.STATUS_ACTIVE, remote.transactionStatus());
        } finally {
            userTransaction.rollback();
        }

    }

    public void testSameTransactionEachCall() throws RemoteException, SystemException, NotSupportedException {
        final IIOPTransactionalStatefulRemote remote = statefulHome.create();
        userTransaction.begin();
        try {
            remote.sameTransaction(true);
            remote.sameTransaction(false);
        } finally {
            userTransaction.rollback();
        }
    }

    public void testSynchronization(final boolean succeeded) throws RemoteException, SystemException, NotSupportedException, RollbackException, HeuristicRollbackException, HeuristicMixedException {
        final IIOPTransactionalStatefulRemote remote = statefulHome.create();
        userTransaction.begin();
        try {
            remote.sameTransaction(true);
            remote.sameTransaction(false);
        } finally {
            if (succeeded) {
                userTransaction.commit();
            } else {
                userTransaction.rollback();
            }
        }
        Assert.assertEquals(succeeded, remote.isBeforeCompletion());
        Assert.assertEquals((Boolean) succeeded, remote.getCommitSucceeded());
    }

    public void testRollbackOnly() throws RemoteException, SystemException, NotSupportedException {
        final IIOPTransactionalStatefulRemote remote = statefulHome.create();
        userTransaction.begin();
        try {
            Assert.assertEquals(Status.STATUS_ACTIVE, remote.transactionStatus());
            remote.rollbackOnly();
            Assert.assertEquals(Status.STATUS_MARKED_ROLLBACK, remote.transactionStatus());
        } finally {
            userTransaction.rollback();
        }
        Assert.assertFalse(remote.isBeforeCompletion());
        Assert.assertFalse(remote.getCommitSucceeded());
    }

    public void testRollbackOnlyBeforeCompletion() throws RemoteException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException {
        final IIOPTransactionalStatefulRemote remote = statefulHome.create();
        userTransaction.begin();
        try {
            Assert.assertEquals(Status.STATUS_ACTIVE, remote.transactionStatus());
            remote.setRollbackOnlyBeforeCompletion(true);
            userTransaction.commit();
        } catch (RollbackException expected) {
        }
        Assert.assertFalse(remote.getCommitSucceeded());
        Assert.assertEquals(Status.STATUS_NO_TRANSACTION, remote.transactionStatus());
    }

}
