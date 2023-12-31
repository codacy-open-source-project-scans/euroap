/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.xts.suspend.wsba;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import jakarta.jws.HandlerChain;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

import com.arjuna.mw.wst11.BusinessActivityManager;
import com.arjuna.mw.wst11.UserBusinessActivity;
import com.arjuna.mwlabs.wst11.ba.remote.UserBusinessActivityImple;
import com.arjuna.wst11.BAParticipantManager;
import org.jboss.as.test.xts.suspend.ExecutorService;
import org.jboss.as.test.xts.suspend.RemoteService;
import org.jboss.jbossts.xts.environment.XTSPropertyManager;
import org.jboss.logging.Logger;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.mw.wst.TxContext;

import static org.jboss.as.test.xts.suspend.Helpers.getRemoteService;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@WebService(targetNamespace = "org.jboss.as.test.xts.suspend", serviceName = "ExecutorService", portName = "ExecutorService")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@HandlerChain(file = "/context-handlers.xml")
public class BusinessActivityExecutionService implements ExecutorService {

    private static final Logger LOGGER = Logger.getLogger(BusinessActivityExecutionService.class);

    private RemoteService remoteService;

    private TxContext currentActivity;

    private boolean wasInitialised;

    @Override
    public void init(String activationServiceUrl, String remoteServiceUrl) {
        LOGGER.debugf("initialising with activationServiceUrl=%s and remoteServiceUrl=%s", activationServiceUrl,
                remoteServiceUrl);

        if (!wasInitialised) {
            // This is done only for testing purposes. In real application application server configuration should be used.
            XTSPropertyManager.getWSCEnvironmentBean().setCoordinatorURL11(activationServiceUrl);
            UserBusinessActivity.setUserBusinessActivity(new UserBusinessActivityImple());
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
        assert currentActivity == null : "Business activity already started";
        LOGGER.debugf("trying to begin transaction on %s", XTSPropertyManager.getWSCEnvironmentBean().getCoordinatorURL11());

        UserBusinessActivity.getUserBusinessActivity().begin();
        currentActivity = BusinessActivityManager.getBusinessActivityManager().suspend();

        LOGGER.debugf("started business activity %s", currentActivity);
    }

    @Override
    public void commit() throws Exception {
        assert currentActivity != null : "No active business activity";
        LOGGER.debugf("trying to close business activity %s", currentActivity);

        BusinessActivityManager.getBusinessActivityManager().resume(currentActivity);
        UserBusinessActivity.getUserBusinessActivity().close();
        currentActivity = null;

        LOGGER.debugf("closed business activity");
    }

    @Override
    public void rollback() throws Exception {
        assert currentActivity != null : "No active business activity";
        LOGGER.debugf("trying to cancel business activity %s", currentActivity);

        BusinessActivityManager.getBusinessActivityManager().resume(currentActivity);
        UserBusinessActivity.getUserBusinessActivity().cancel();
        currentActivity = null;

        LOGGER.debugf("canceled business activity");
    }

    @Override
    public void enlistParticipant() throws Exception {
        assert currentActivity != null : "No active business activity";
        LOGGER.debugf("trying to enlist participant to the business activity %s", currentActivity);

        BusinessActivityManager.getBusinessActivityManager().resume(currentActivity);
        BusinessActivityParticipant businessActivityParticipant = new BusinessActivityParticipant(new Uid().stringForm());
        BAParticipantManager participantManager = BusinessActivityManager.getBusinessActivityManager()
                .enlistForBusinessAgreementWithParticipantCompletion(businessActivityParticipant,
                        businessActivityParticipant.getId());
        participantManager.completed();
        currentActivity = BusinessActivityManager.getBusinessActivityManager().suspend();

        LOGGER.debugf("enlisted participant %s", businessActivityParticipant);
    }

    @Override
    public void execute() throws Exception {
        assert remoteService != null : "Remote service was not initialised";
        assert currentActivity != null : "No active business activity";
        LOGGER.debugf("trying to execute remote service in business activity %s", currentActivity);

        BusinessActivityManager.getBusinessActivityManager().resume(currentActivity);
        remoteService.execute();
        currentActivity = BusinessActivityManager.getBusinessActivityManager().suspend();

        LOGGER.debugf("executed remote service");
    }

    @Override
    public void reset() {
        BusinessActivityParticipant.resetInvocations();
    }

    @Override
    public List<String> getParticipantInvocations() {
        return BusinessActivityParticipant.getInvocations();
    }
}
