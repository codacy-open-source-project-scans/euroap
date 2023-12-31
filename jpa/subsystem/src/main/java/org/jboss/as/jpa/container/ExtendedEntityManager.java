/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jpa.container;

import static org.jboss.as.jpa.messages.JpaLogger.ROOT_LOGGER;

import java.io.IOException;
import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedAction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.SynchronizationType;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.jboss.as.jpa.messages.JpaLogger;
import org.jboss.as.jpa.transaction.TransactionUtil;
import org.jboss.as.jpa.util.JPAServiceNames;
import org.jboss.as.server.CurrentServiceContainer;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.transaction.client.ContextTransactionManager;

/**
 * Represents the Extended persistence context injected into a stateful bean.  At bean invocation time,
 * will join the active Jakarta Transactions transaction if one is present.  If no active Jakarta Transactions transaction is present,
 * created/deleted/updated/loaded entities will remain associated with the entity manager until it is joined with a
 * transaction (commit will save the changes, rollback will lose them).
 * <p/>
 * At injection time, an instance of this class is associated with the SFSB.
 * During a SFSB1 invocation, if a new SFSB2 is created with an XPC referencing the same
 * persistence unit, the new SFSB2 will inherit the same persistence context from SFSB1.
 * Both SFSB1 + SFSB2 will maintain a reference to the underlying persistence context, such that
 * the underlying persistence context will be kept around until both SFSB1 + SFSB2 are destroyed.
 * At cluster replication time or passivation, both SFSB1 + SFSB2 will be serialized consecutively and this
 * instance will only be serialized once.
 * <p/>
 * Note:  Unlike TransactionScopedEntityManager, ExtendedEntityManager will directly be shared instead of the
 * underlying EntityManager.
 * <p/>
 *
 * During serialization, A NotSerializableException will be thrown if the following conditions are not met:
 * - The underlying persistence provider (entity manager) must be Serializable.
 * - The entity classes in the extended persistence context must also be Serializable.
 *
 * @author Scott Marlow
 */
public class ExtendedEntityManager extends AbstractEntityManager implements Serializable, SynchronizationTypeAccess {

    /**
     * Adding fields to this class, may require incrementing the serialVersionUID (always increment it).
     * If a transient field is added that isn't serialized, serialVersionUID doesn't need to change.
     * By default transient fields are not serialized but can be manually (de)serialized in readObject/writeObject.
     * Just make sure you think about whether the newly added field should be serialized.
     */
    private static final long serialVersionUID = 432438L;

    /**
     * EntityManager obtained from the persistence provider that represents the XPC.
     */
    private EntityManager underlyingEntityManager;

    /**
     * fully application scoped persistence unit name
     */
    private String puScopedName;

    private transient boolean isInTx;

    /**
     * Track the number of stateful session beans that are referencing the extended persistence context.
     * when the reference count reaches zero, the persistence context is closed.
     */
    private int referenceCount = 1;

    /**
     * the UUID representing the extended persistence context
     */
    private final ExtendedEntityManagerKey ID = ExtendedEntityManagerKey.extendedEntityManagerID();

    private final transient boolean isTraceEnabled = ROOT_LOGGER.isTraceEnabled();

    private final SynchronizationType synchronizationType;
    private transient TransactionSynchronizationRegistry transactionSynchronizationRegistry;
    private transient TransactionManager transactionManager;

    public ExtendedEntityManager(final String puScopedName, final EntityManager underlyingEntityManager, final SynchronizationType synchronizationType, TransactionSynchronizationRegistry transactionSynchronizationRegistry, TransactionManager transactionManager) {
        this.underlyingEntityManager = underlyingEntityManager;
        this.puScopedName = puScopedName;
        this.synchronizationType = synchronizationType;
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
        this.transactionManager = transactionManager;
    }

    /**
     * The Jakarta Persistence SFSB interceptor will track the stack of SFSB invocations.  The underlying EM will be obtained from
     * the current SFSB being invoked (via our Jakarta Persistence SFSB interceptor).
     *
     * Every entity manager call (to AbstractEntityManager) will call this method to get the underlying entity manager
     * (e.g. the Hibernate persistence provider).
     *
     * See org.jboss.ejb3.stateful.EJB3XPCResolver.getExtendedPersistenceContext() to see the as6 implementation of this.
     *
     * @return EntityManager
     */
    @Override
    protected EntityManager getEntityManager() {

        internalAssociateWithJtaTx();
        return underlyingEntityManager;
    }

    /**
     * Associate the extended persistence context with the current Jakarta Transactions transaction (if one is found)
     *
     * this method is private to the Jakarta Persistence subsystem
     */
    public void internalAssociateWithJtaTx() {
        isInTx = TransactionUtil.isInTx(transactionManager);

        // ensure that a different XPC (with same name) is not already present in the TX
        if (isInTx) {

            // 7.6.3.1 throw EJBException if a different persistence context is already joined to the
            // transaction (with the same puScopedName).
            EntityManager existing = TransactionUtil.getTransactionScopedEntityManager(puScopedName, transactionSynchronizationRegistry);
            if (existing != null && existing != this) {
                // should be enough to test if not the same object
                throw JpaLogger.ROOT_LOGGER.cannotUseExtendedPersistenceTransaction(puScopedName, existing, this);
            } else if (existing == null) {

                if (SynchronizationType.SYNCHRONIZED.equals(synchronizationType)) {
                    // Jakarta Persistence 7.9.1 join the transaction if not already done for SynchronizationType.SYNCHRONIZED.
                    underlyingEntityManager.joinTransaction();
                }
                // associate the entity manager with the current transaction
                TransactionUtil.putEntityManagerInTransactionRegistry(puScopedName, this, transactionSynchronizationRegistry);
            }
        }
    }

    @Override
    protected boolean isExtendedPersistenceContext() {
        return true;
    }

    @Override
    protected boolean isInTx() {
        return this.isInTx;
    }

    /**
     * Catch the application trying to close the container managed entity manager and throw an IllegalStateException
     */
    @Override
    public void close() {
        // An extended entity manager will be closed when the Jakarta Enterprise Beans SFSB @remove method is invoked.
        throw JpaLogger.ROOT_LOGGER.cannotCloseContainerManagedEntityManager();

    }

    /**
     * Start of reference count handling.
     * synchronize on *this* to protect access to the referenceCount (should be mostly uncontended locks).
     *
     */

    public synchronized void increaseReferenceCount() {
        referenceCount++;
    }

    public synchronized int getReferenceCount() {
        return referenceCount;
    }

    public synchronized void refCountedClose() {

        referenceCount--;
        if (referenceCount == 0) {
            if (underlyingEntityManager.isOpen()) {
                underlyingEntityManager.close();
                if (isTraceEnabled) {
                    ROOT_LOGGER.tracef("closed extended persistence context (%s)", puScopedName);
                }
            }
        }
        else if (isTraceEnabled) {
            ROOT_LOGGER.tracef("decremented extended persistence context (%s) owner count to %d",
                    puScopedName, referenceCount);
        }

        // referenceCount should never be negative, if it is we need to fix the bug that caused it to decrement too much
        if (referenceCount < 0) {
            throw JpaLogger.ROOT_LOGGER.referenceCountedEntityManagerNegativeCount(referenceCount, getScopedPuName());
        }

    }


    /**
     * End of reference count handling
     */

    @Override
    public String toString() {
        return "ExtendedEntityManager [" + puScopedName + "]";
    }

    /**
     * Get the fully application scoped persistence unit name
     * Private api
     * @return scoped pu name
     */
    public String getScopedPuName() {
        return puScopedName;
    }

    /**
     * Check if this object's UUID is equal to the otherObject's UUID
     *
     * @param otherObject
     * @return
     */
    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) return true;
        if (otherObject == null || getClass() != otherObject.getClass()) return false;

        ExtendedEntityManager that = (ExtendedEntityManager) otherObject;

        if (!ID.equals(that.ID)) return false;

        return true;
    }


    @Override
    public int hashCode() {
        // return hashCode of the ExtendedEntityManagerKey
        return ID != null ? ID.hashCode() : 0;
    }

    @Override // SynchronizationTypeAccess
    public SynchronizationType getSynchronizationType() {
        return synchronizationType;
    }

    @Override
    protected boolean deferEntityDetachUntilClose() {
        return false;
    }

    @Override
    protected boolean skipQueryDetach() {
        return false;
    }



    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if(WildFlySecurityManager.isChecking()) {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    transactionManager = ContextTransactionManager.getInstance();
                    transactionSynchronizationRegistry = (TransactionSynchronizationRegistry) CurrentServiceContainer.getServiceContainer().getService(JPAServiceNames.TRANSACTION_SYNCHRONIZATION_REGISTRY_SERVICE).getValue();
                    return null;
                }
            });
        } else {
            transactionManager = ContextTransactionManager.getInstance();
            transactionSynchronizationRegistry = (TransactionSynchronizationRegistry) CurrentServiceContainer.getServiceContainer().getService(JPAServiceNames.TRANSACTION_SYNCHRONIZATION_REGISTRY_SERVICE).getValue();
        }
    }
}
