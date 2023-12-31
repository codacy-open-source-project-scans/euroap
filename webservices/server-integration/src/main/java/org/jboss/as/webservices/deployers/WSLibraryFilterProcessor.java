/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.webservices.deployers;

import static org.jboss.as.server.deployment.Attachments.ANNOTATION_INDEX;
import static org.jboss.as.server.deployment.Attachments.RESOURCE_ROOTS;
import static org.jboss.as.webservices.util.ASHelper.hasClassesFromPackage;
import static org.jboss.as.webservices.util.ASHelper.isJaxwsEndpoint;
import static org.jboss.as.webservices.util.DotNames.WEB_SERVICE_ANNOTATION;
import static org.jboss.as.webservices.util.DotNames.WEB_SERVICE_PROVIDER_ANNOTATION;

import java.util.List;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.webservices.logging.WSLogger;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

/**
 * @author <a href="mailto:alessio.soldano@jboss.com">Alessio Soldano</a>
 */
public class WSLibraryFilterProcessor implements DeploymentUnitProcessor {
    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit unit = phaseContext.getDeploymentUnit();
        if (DeploymentTypeMarker.isType(DeploymentType.EAR, unit)) {
            return;
        }

        final CompositeIndex index = unit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (index != null) {
            //perform checks on included libraries only if there're actually WS endpoints in the deployment
            if (hasWSEndpoints(index)) {
                AttachmentList<ResourceRoot> resourceRoots = unit.getAttachment(RESOURCE_ROOTS);
                if (resourceRoots != null) {
                    for (ResourceRoot root : resourceRoots) {
                        if (root.getRoot().getChild("META-INF/services/javax.xml.datatype.DatatypeFactory").exists()
                                || root.getRoot().getChild("META-INF/services/javax.xml.parsers.DocumentBuilderFactory").exists()
                                || root.getRoot().getChild("META-INF/services/javax.xml.parsers.SAXParserFactory").exists()
                                || root.getRoot().getChild("META-INF/services/javax.xml.validation.SchemaFactory").exists()) {
                            WSLogger.ROOT_LOGGER.warningLibraryInDeployment("JAXP Implementation", root.getRootName());
                        }
                        if (hasClassesFromPackage(root.getAttachment(ANNOTATION_INDEX), "org.apache.cxf")) {
                            throw WSLogger.ROOT_LOGGER.invalidLibraryInDeployment("Apache CXF", root.getRootName());
                        }
                    }
                }
            }
        } else {
            WSLogger.ROOT_LOGGER.tracef("Skipping WS annotation processing since no composite annotation index found in unit: %s", unit.getName());
        }
    }

    private boolean hasWSEndpoints(final CompositeIndex index) {
        final DotName[] dotNames = {WEB_SERVICE_ANNOTATION, WEB_SERVICE_PROVIDER_ANNOTATION};
        for (final DotName dotName : dotNames) {
            final List<AnnotationInstance> wsAnnotations = index.getAnnotations(dotName);
            if (!wsAnnotations.isEmpty()) {
                for (final AnnotationInstance wsAnnotation : wsAnnotations) {
                    final AnnotationTarget target = wsAnnotation.target();
                    if (target instanceof ClassInfo) {
                        final ClassInfo classInfo = (ClassInfo) target;
                        if (isJaxwsEndpoint(classInfo, index)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
