/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.xts.suspend.wsat;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.mw.wst.TxContext;
import com.arjuna.mw.wst11.TransactionManager;
import com.arjuna.mw.wst11.UserTransaction;
import com.arjuna.mwlabs.wst11.at.remote.UserTransactionImple;
import org.jboss.as.test.xts.suspend.ExecutorService;
import org.jboss.as.test.xts.suspend.RemoteService;
import org.jboss.jbossts.xts.environment.XTSPropertyManager;
import org.jboss.logging.Logger;

import jakarta.jws.HandlerChain;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static org.jboss.as.test.xts.suspend.Helpers.getRemoteService;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@WebService(targetNamespace = "org.jboss.as.test.xts.suspend", serviceName = "ExecutorService", portName = "ExecutorService")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@HandlerChain(file = "/context-handlers.xml")
public class AtomicTransactionExecutionService implements ExecutorService {

    private static final Logger LOGGER = Logger.getLogger(AtomicTransactionExecutionService.class);

    private volatile RemoteService remoteService;

    private volatile TxContext currentTransaction;

    private volatile boolean wasInitialised;

    @Override
    public void init(String activationServiceUrl, String remoteServiceUrl) {
        LOGGER.debugf("initialising with activationServiceUrl=%s and remoteServiceUrl=%s", activationServiceUrl,
                remoteServiceUrl);

        if (!wasInitialised) {
            // This is done only for testing purposes. In real application application server configuration should be used.
            XTSPropertyManager.getWSCEnvironmentBean().setCoordinatorURL11(activationServiceUrl);
            UserTransaction.setUserTransaction(new UserTransactionImple());
            wasInitialised = true;
        }

        try {
            remoteService = getRemoteService(new URL(remoteServiceUrl));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void begin() throws Exception {
        assert currentTransaction == null : "Transaction already started";
        LOGGER.debugf("trying to start a new transaction");

        UserTransaction.getUserTransaction().begin();
        currentTransaction = TransactionManager.getTransactionManager().suspend();

        LOGGER.debugf("started transaction %s", currentTransaction);
    }

    @Override
    public void commit() throws Exception {
        assert currentTransaction != null : "No active transaction";
        LOGGER.debugf("trying to commit transaction %s", currentTransaction);

        TransactionManager.getTransactionManager().resume(currentTransaction);
        UserTransaction.getUserTransaction().commit();
        currentTransaction = null;

        LOGGER.debugf("committed transaction");
    }

    @Override
    public void rollback() throws Exception {
        assert currentTransaction != null : "No active transaction";
        LOGGER.debugf("trying to rollback transaction %s", currentTransaction);

        TransactionManager.getTransactionManager().resume(currentTransaction);
        UserTransaction.getUserTransaction().rollback();
        currentTransaction = null;

        LOGGER.debugf("rolled back transaction");
    }

    @Override
    public void enlistParticipant() throws Exception {
        assert currentTransaction != null : "No active transaction";
        LOGGER.debugf("trying to enlist participant to the transaction %s", currentTransaction);

        TransactionManager.getTransactionManager().resume(currentTransaction);
        String participantId = new Uid().stringForm();
        TransactionParticipant transactionParticipant = new TransactionParticipant(participantId);
        TransactionManager.getTransactionManager().enlistForVolatileTwoPhase(transactionParticipant, participantId);
        currentTransaction = TransactionManager.getTransactionManager().suspend();

        LOGGER.debugf("enlisted participant %s", transactionParticipant);
    }

    @Override
    public void execute() throws Exception {
        assert remoteService != null : "Remote service was not initialised";
        assert currentTransaction != null : "No active transaction";
        LOGGER.debugf("trying to execute remote service in transaction %s", currentTransaction);

        TransactionManager.getTransactionManager().resume(currentTransaction);
        remoteService.execute();
        currentTransaction = TransactionManager.getTransactionManager().suspend();

        LOGGER.debugf("executed remote service");
    }

    @Override
    public void reset() {
        TransactionParticipant.resetInvocations();
    }

    @Override
    public List<String> getParticipantInvocations() {
        return TransactionParticipant.getInvocations();
    }
}
