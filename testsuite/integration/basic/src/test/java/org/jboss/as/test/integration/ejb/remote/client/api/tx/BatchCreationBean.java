/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.remote.client.api.tx;

import org.jboss.logging.Logger;

import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * @author Jaikiran Pai
 */
@Stateless
@Remote(RemoteBatch.class)
@TransactionAttribute (TransactionAttributeType.MANDATORY)
public class BatchCreationBean implements RemoteBatch {

    private static final Logger logger = Logger.getLogger(BatchCreationBean.class);

    @PersistenceContext(unitName = "ejb-client-tx-pu")
    private EntityManager entityManager;

    public void createBatch(final String batchName) {
        final Batch batch = new Batch();
        batch.setBatchName(batchName);
        logger.trace("Persisting new batch " + batchName);
        this.entityManager.persist(batch);
    }

    public void step1(final String batchName, final String stepName) {
        this.addStepToBatch(batchName, stepName);
    }

    public void successfulStep2(final String batchName, final String stepName) {
        this.addStepToBatch(batchName, stepName);
    }

    public void appExceptionFailingStep2(final String batchName, final String stepName) throws SimpleAppException {
        this.addStepToBatch(batchName, stepName);
        throw new SimpleAppException();
    }

    public void systemExceptionFailingStep2(final String batchName, final String stepName) {
        this.addStepToBatch(batchName, stepName);
        throw new RuntimeException("Intentional exception from " + this.getClass().getSimpleName());
    }

    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void independentStep3(final String batchName, final String stepName) {
        this.addStepToBatch(batchName, stepName);
    }

    public void step4(final String batchName, final String stepName) {
        this.addStepToBatch(batchName, stepName);
    }

    private Batch requireBatch(final String batchName) {
        final Batch batch = this.entityManager.find(Batch.class, batchName);
        if (batch == null) {
            throw new IllegalArgumentException("No such batch named " + batchName);
        }
        return batch;
    }

    private void addStepToBatch(final String batchName, final String stepName) {
        final Batch batch = this.requireBatch(batchName);
        batch.addStep(stepName);
        this.entityManager.persist(batch);
    }
}
