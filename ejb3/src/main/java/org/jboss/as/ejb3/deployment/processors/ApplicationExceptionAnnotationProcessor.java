/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors;

import java.util.List;

import jakarta.ejb.ApplicationException;

import org.jboss.as.ee.metadata.MetadataCompleteMarker;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.deployment.ApplicationExceptionDescriptions;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

/**
 * User: jpai
 */
public class ApplicationExceptionAnnotationProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        if(MetadataCompleteMarker.isMetadataComplete(deploymentUnit)) {
            return;
        }

        final CompositeIndex compositeIndex = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (compositeIndex == null) {
            return;
        }
        List<AnnotationInstance> applicationExceptionAnnotations = compositeIndex.getAnnotations(DotName.createSimple(ApplicationException.class.getName()));
        if (applicationExceptionAnnotations == null || applicationExceptionAnnotations.isEmpty()) {
            return;
        }
        ApplicationExceptionDescriptions descriptions = new ApplicationExceptionDescriptions();
        deploymentUnit.putAttachment(EjbDeploymentAttachmentKeys.APPLICATION_EXCEPTION_DESCRIPTIONS, descriptions);
        for (AnnotationInstance annotationInstance : applicationExceptionAnnotations) {
            AnnotationTarget target = annotationInstance.target();
            if (!(target instanceof ClassInfo)) {
                throw EjbLogger.ROOT_LOGGER.annotationOnlyAllowedOnClass(ApplicationException.class.getName(), target);
            }
            String exceptionClassName = ((ClassInfo) target).name().toString();
            boolean rollback = false;
            AnnotationValue rollBackAnnValue = annotationInstance.value("rollback");
            if (rollBackAnnValue != null) {
                rollback = rollBackAnnValue.asBoolean();
            }
            // default "inherited" is true
            boolean inherited = true;
            AnnotationValue inheritedAnnValue = annotationInstance.value("inherited");
            if (inheritedAnnValue != null) {
                inherited = inheritedAnnValue.asBoolean();
            }
            descriptions.addApplicationException(exceptionClassName, rollback, inherited);
        }
    }
}
