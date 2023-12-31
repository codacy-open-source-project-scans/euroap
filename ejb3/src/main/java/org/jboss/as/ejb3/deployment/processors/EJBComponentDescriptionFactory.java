/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors;

import static org.jboss.as.ejb3.deployment.processors.AnnotatedEJBComponentDescriptionDeploymentUnitProcessor.getEjbJarDescription;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.deployment.EjbJarDescription;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.EnterpriseBeanMetaData;
import org.jboss.metadata.ejb.spec.EnterpriseBeansMetaData;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public abstract class EJBComponentDescriptionFactory {

    /**
     * If this is an appclient we want to make the components as not installable, so we can still look up which Jakarta Enterprise Beans's are in
     * the deployment, but do not actually install them
     */
    protected final boolean appclient;

    protected EJBComponentDescriptionFactory(final boolean appclient) {
        this.appclient = appclient;
    }

    protected void addComponent(final DeploymentUnit deploymentUnit, final EJBComponentDescription beanDescription) {
        final EjbJarDescription ejbJarDescription = getEjbJarDescription(deploymentUnit);
        if (appclient) {
            deploymentUnit.addToAttachmentList(Attachments.ADDITIONAL_RESOLVABLE_COMPONENTS, beanDescription);
        } else {
            // Add this component description to module description
            ejbJarDescription.getEEModuleDescription().addComponent(beanDescription);
        }
    }

    static EnterpriseBeansMetaData getEnterpriseBeansMetaData(final DeploymentUnit deploymentUnit) {
        final EjbJarMetaData jarMetaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        if (jarMetaData == null)
            return null;
        return jarMetaData.getEnterpriseBeans();
    }

    static <B extends EnterpriseBeanMetaData> B getEnterpriseBeanMetaData(final DeploymentUnit deploymentUnit, final String name, final Class<B> expectedType) {
        final EnterpriseBeansMetaData enterpriseBeansMetaData = getEnterpriseBeansMetaData(deploymentUnit);
        if (enterpriseBeansMetaData == null)
            return null;
        return expectedType.cast(enterpriseBeansMetaData.get(name));
    }

    /**
     * Process annotations and merge any available metadata at the same time.
     */
    protected abstract void processAnnotations(final DeploymentUnit deploymentUnit, final CompositeIndex compositeIndex) throws DeploymentUnitProcessingException;

    protected abstract void processBeanMetaData(final DeploymentUnit deploymentUnit, final EnterpriseBeanMetaData enterpriseBeanMetaData) throws DeploymentUnitProcessingException;

    protected static <T> T override(T original, T override) {
        return override != null ? override : original;
    }
}
