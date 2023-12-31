/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors;

import java.util.List;

import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.metadata.MetadataCompleteMarker;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.deployment.EjbJarDescription;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.EjbDeploymentMarker;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.EnterpriseBeanMetaData;
import org.jboss.metadata.ejb.spec.EnterpriseBeansMetaData;

/**
 * Make sure we process annotations first and then start on the descriptors.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class AnnotatedEJBComponentDescriptionDeploymentUnitProcessor implements DeploymentUnitProcessor {
    /**
     * If this is an appclient we want to make the components as not installable, so we can still look up which Jakarta Enterprise Beans's are in
     * the deployment, but do not actual install them
     */
    private final boolean appclient;

    private final EJBComponentDescriptionFactory[] factories;

    public AnnotatedEJBComponentDescriptionDeploymentUnitProcessor(final boolean appclient, final boolean defaultMdbPoolAvailable, final boolean defaultSlsbPoolAvailable) {
        this.appclient = appclient;
        this.factories = new EJBComponentDescriptionFactory[] {
                new MessageDrivenComponentDescriptionFactory(appclient, defaultMdbPoolAvailable),
                new SessionBeanComponentDescriptionFactory(appclient, defaultSlsbPoolAvailable)
        };
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        // get hold of the deployment unit
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            return;
        }

        final CompositeIndex compositeIndex = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (compositeIndex == null) {
            EjbLogger.DEPLOYMENT_LOGGER.tracef("Skipping Jakarta Enterprise Beans annotation processing since no composite annotation index found in unit: %s", deploymentUnit);
        } else {
            if (MetadataCompleteMarker.isMetadataComplete(deploymentUnit)) {
                EjbLogger.DEPLOYMENT_LOGGER.trace("Skipping Jakarta Enterprise Beans annotation processing due to deployment being metadata-complete. ");
            } else {
                processAnnotations(deploymentUnit, compositeIndex);
            }
        }
        processDeploymentDescriptor(deploymentUnit);
    }

    protected static EjbJarDescription getEjbJarDescription(final DeploymentUnit deploymentUnit) {
        EjbJarDescription ejbJarDescription = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_DESCRIPTION);
        if (ejbJarDescription == null) {
            final EEModuleDescription moduleDescription = deploymentUnit.getAttachment(org.jboss.as.ee.component.Attachments.EE_MODULE_DESCRIPTION);
            ejbJarDescription = new EjbJarDescription(moduleDescription, deploymentUnit.getName().endsWith(".war"));
            deploymentUnit.putAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_DESCRIPTION, ejbJarDescription);
        }
        return ejbJarDescription;
    }

    private void processDeploymentDescriptor(final DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException {
        // find the Jakarta Enterprise Beans jar metadata and start processing it
        final EjbJarMetaData ejbJarMetaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        if (ejbJarMetaData == null) {
            return;
        }
        final SimpleSet<String> annotatedEJBs;
        if (appclient) {
            final List<ComponentDescription> additionalComponents = deploymentUnit.getAttachmentList(org.jboss.as.ee.component.Attachments.ADDITIONAL_RESOLVABLE_COMPONENTS);
            annotatedEJBs = new SimpleSet<String>() {
                @Override
                public boolean contains(Object o) {
                    for (final ComponentDescription component : additionalComponents) {
                        if (component.getComponentName().equals(o)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        } else {
            final EjbJarDescription ejbJarDescription = getEjbJarDescription(deploymentUnit);
            annotatedEJBs = new SimpleSet<String>() {
                @Override
                public boolean contains(Object o) {
                    return ejbJarDescription.hasComponent((String) o);
                }
            };
        }
        // process Jakarta Enterprise Beans
        final EnterpriseBeansMetaData ejbs = ejbJarMetaData.getEnterpriseBeans();
        if (ejbs != null && !ejbs.isEmpty()) {
            for (final EnterpriseBeanMetaData ejb : ejbs) {
                final String beanName = ejb.getName();
                // the important bit is to skip already processed Jakarta Enterprise Beans via annotations
                if (annotatedEJBs.contains(beanName)) {
                    continue;
                }

                processBeanMetaData(deploymentUnit, ejb);
            }
        }
        EjbDeploymentMarker.mark(deploymentUnit);
    }

    private void processAnnotations(final DeploymentUnit deploymentUnit, final CompositeIndex compositeIndex) throws DeploymentUnitProcessingException {
        for (final EJBComponentDescriptionFactory factory : factories) {
            factory.processAnnotations(deploymentUnit, compositeIndex);
        }
    }

    private void processBeanMetaData(final DeploymentUnit deploymentUnit, final EnterpriseBeanMetaData ejb) throws DeploymentUnitProcessingException {
        for (final EJBComponentDescriptionFactory factory : factories) {
            factory.processBeanMetaData(deploymentUnit, ejb);
        }
    }

    private interface SimpleSet<E> {
        boolean contains(Object o);
    }
}
