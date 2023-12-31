/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors.dd;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.DeploymentDescriptorEnvironment;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ejb3.component.session.SessionBeanComponentDescription;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.metadata.ejb.spec.BusinessLocalsMetaData;
import org.jboss.metadata.ejb.spec.BusinessRemotesMetaData;
import org.jboss.metadata.ejb.spec.SessionBean31MetaData;
import org.jboss.metadata.ejb.spec.SessionBeanMetaData;

/**
 * @author Jaikiran Pai
 */
public class SessionBeanXmlDescriptorProcessor extends AbstractEjbXmlDescriptorProcessor<SessionBeanMetaData> {

    private final boolean appclient;

    public SessionBeanXmlDescriptorProcessor(final boolean appclient) {
        this.appclient = appclient;
    }

    @Override
    protected Class<SessionBeanMetaData> getMetaDataType() {
        return SessionBeanMetaData.class;
    }

    /**
     * Processes the passed {@link org.jboss.metadata.ejb.spec.SessionBeanMetaData} and creates appropriate {@link org.jboss.as.ejb3.component.session.SessionBeanComponentDescription} out of it.
     * The {@link org.jboss.as.ejb3.component.session.SessionBeanComponentDescription} is then added to the {@link org.jboss.as.ee.component.EEModuleDescription module description} available
     * in the deployment unit of the passed {@link DeploymentPhaseContext phaseContext}
     *
     * @param sessionBean  The session bean metadata
     * @param phaseContext
     * @throws DeploymentUnitProcessingException
     *
     */
    @Override
    protected void processBeanMetaData(final SessionBeanMetaData sessionBean, final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        // get the module description
        final EEModuleDescription moduleDescription = deploymentUnit.getAttachment(org.jboss.as.ee.component.Attachments.EE_MODULE_DESCRIPTION);

        final String beanName = sessionBean.getName();

        ComponentDescription bean = moduleDescription.getComponentByName(beanName);
        if (appclient && bean == null) {
            for (final ComponentDescription component : deploymentUnit
                    .getAttachmentList(Attachments.ADDITIONAL_RESOLVABLE_COMPONENTS)) {
                if (component.getComponentName().equals(beanName)) {
                    bean = component;
                    break;
                }
            }
        }
        if (!(bean instanceof SessionBeanComponentDescription)) {
            //TODO: this is a hack to deal with descriptor merging
            //if this is a GenericBeanMetadata it may actually represent an MDB
            return;
        }

        SessionBeanComponentDescription sessionBeanDescription = (SessionBeanComponentDescription) bean;

        sessionBeanDescription.setDeploymentDescriptorEnvironment(new DeploymentDescriptorEnvironment("java:comp/env/", sessionBean));

        // mapped-name
        sessionBeanDescription.setMappedName(sessionBean.getMappedName());
        // local business interface views
        final BusinessLocalsMetaData businessLocals = sessionBean.getBusinessLocals();
        if (businessLocals != null && !businessLocals.isEmpty()) {
            sessionBeanDescription.addLocalBusinessInterfaceViews(businessLocals);
        }

        final String local = sessionBean.getLocal();
        if (local != null) {
            sessionBeanDescription.addEjbLocalObjectView(local);
        }

        final String remote = sessionBean.getRemote();
        if (remote != null) {
            sessionBeanDescription.addEjbObjectView(remote);
        }

        // remote business interface views
        final BusinessRemotesMetaData businessRemotes = sessionBean.getBusinessRemotes();
        if (businessRemotes != null && !businessRemotes.isEmpty()) {
            sessionBeanDescription.addRemoteBusinessInterfaceViews(businessRemotes);
        }

        // process Enterprise Beans 3.1 specific session bean description
        if (sessionBean instanceof SessionBean31MetaData) {
            this.processSessionBean31((SessionBean31MetaData) sessionBean, sessionBeanDescription);
        }
    }


    private void processSessionBean31(final SessionBean31MetaData sessionBean31MetaData, final SessionBeanComponentDescription sessionBeanComponentDescription) {
        // no-interface view
        if (sessionBean31MetaData.isNoInterfaceBean()) {
            sessionBeanComponentDescription.addNoInterfaceView();
        }
    }

}
