/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jsf.deployment;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.jsf.logging.JSFLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.web.common.ServletContextAttribute;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.modules.Module;

/**
 * {@link DeploymentUnitProcessor} implementation responsible for extracting Jakarta Server Faces annotations from a deployment and attaching them
 * to the deployment unit to eventually be added to the {@link jakarta.servlet.ServletContext}.
 *
 * @author John Bailey
 */
public class JSFAnnotationProcessor implements DeploymentUnitProcessor {

    public static final String FACES_ANNOTATIONS_SC_ATTR =  "org.jboss.as.jsf.FACES_ANNOTATIONS";
    private static final String MANAGED_ANNOTATION_PARAMETER = "managed";


    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if(JsfVersionMarker.isJsfDisabled(deploymentUnit)) {
            return;
        }

        final Map<Class<? extends Annotation>, Set<Class<?>>> instances = new HashMap<Class<? extends Annotation>, Set<Class<?>>>();

        final CompositeIndex compositeIndex = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (compositeIndex == null) {
            return; // Can not continue without index
        }

        final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        if (module == null) {
            return; // Can not continue without module
        }
        final ClassLoader classLoader = module.getClassLoader();

        for (FacesAnnotation annotation : FacesAnnotation.values()) {
            final List<AnnotationInstance> annotationInstances = compositeIndex.getAnnotations(annotation.indexName);
            if (annotationInstances == null || annotationInstances.isEmpty()) {
                continue;
            }
            final Set<Class<?>> discoveredClasses = new HashSet<Class<?>>();
            instances.put(annotation.annotationClass, discoveredClasses);
            for (AnnotationInstance annotationInstance : annotationInstances) {
                final AnnotationTarget target = annotationInstance.target();
                if (target instanceof ClassInfo) {
                    final DotName className = ClassInfo.class.cast(target).name();
                    final Class<?> annotatedClass;
                    try {
                        annotatedClass = classLoader.loadClass(className.toString());
                    } catch (ClassNotFoundException e) {
                        throw new DeploymentUnitProcessingException(JSFLogger.ROOT_LOGGER.classLoadingFailed(className));
                    }
                    discoveredClasses.add(annotatedClass);
                } else {
                    if (annotation == FacesAnnotation.FACES_VALIDATOR || annotation == FacesAnnotation.FACES_CONVERTER || annotation == FacesAnnotation.FACES_BEHAVIOR) {
                        // Support for Injection into Jakarta Server Faces Managed Objects allows to inject FacesValidator, FacesConverter and FacesBehavior if managed = true
                        // Jakarta Server Faces 2.3 spec chapter 5.9.1 - Jakarta Server Faces Objects Valid for @Inject Injection
                        AnnotationValue value = annotationInstance.value(MANAGED_ANNOTATION_PARAMETER);
                        if (value != null && value.asBoolean()) {
                            continue;
                        }
                    }
                    throw new DeploymentUnitProcessingException(JSFLogger.ROOT_LOGGER.invalidAnnotationLocation(annotation, target));
                }
            }
        }
        deploymentUnit.addToAttachmentList(ServletContextAttribute.ATTACHMENT_KEY, new ServletContextAttribute(FACES_ANNOTATIONS_SC_ATTR, instances));
    }
}
