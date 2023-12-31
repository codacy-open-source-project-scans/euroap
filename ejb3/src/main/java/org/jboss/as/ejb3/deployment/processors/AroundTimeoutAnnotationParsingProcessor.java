/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors;

import java.util.List;

import jakarta.interceptor.AroundTimeout;
import jakarta.interceptor.InvocationContext;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.EEModuleClassDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.component.interceptors.InterceptorClassDescription;
import org.jboss.as.ee.metadata.MetadataCompleteMarker;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.invocation.proxy.MethodIdentifier;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Deployment processor responsible for finding @AroundTimeout annotated methods in classes within a deployment.
 *
 * @author John Bailey
 * @author Stuart Douglas
 */
public class AroundTimeoutAnnotationParsingProcessor implements DeploymentUnitProcessor {

    private static final DotName AROUND_TIMEOUT_ANNOTATION_NAME = DotName.createSimple(AroundTimeout.class.getName());

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        if(MetadataCompleteMarker.isMetadataComplete(deploymentUnit)) {
            return;
        }

        final EEModuleDescription eeModuleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        final CompositeIndex index = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.COMPOSITE_ANNOTATION_INDEX);

        final List<AnnotationInstance> aroundInvokes = index.getAnnotations(AROUND_TIMEOUT_ANNOTATION_NAME);
        for (AnnotationInstance annotation : aroundInvokes) {
            processAroundInvoke(annotation.target(), eeModuleDescription);
        }
    }

    private void processAroundInvoke(final AnnotationTarget target, final EEModuleDescription eeModuleDescription) {
        if (!(target instanceof MethodInfo)) {
            throw EjbLogger.ROOT_LOGGER.annotationApplicableOnlyForMethods(AROUND_TIMEOUT_ANNOTATION_NAME.toString());
        }
        final MethodInfo methodInfo = MethodInfo.class.cast(target);
        final ClassInfo classInfo = methodInfo.declaringClass();
        final EEModuleClassDescription classDescription = eeModuleDescription.addOrGetLocalClassDescription(classInfo.name().toString());

        validateArgumentType(classInfo, methodInfo);
        final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder(classDescription.getInterceptorClassDescription());
        builder.setAroundTimeout(MethodIdentifier.getIdentifier(Object.class, methodInfo.name(), InvocationContext.class));
        classDescription.setInterceptorClassDescription(builder.build());
    }

    private void validateArgumentType(final ClassInfo classInfo, final MethodInfo methodInfo) {
        final Type[] args = methodInfo.args();
        switch (args.length) {
            case 0:
                throw EjbLogger.ROOT_LOGGER.aroundTimeoutMethodExpectedWithInvocationContextParam(methodInfo.name(), classInfo.toString());
            case 1:
                if (!InvocationContext.class.getName().equals(args[0].name().toString())) {
                    throw EjbLogger.ROOT_LOGGER.aroundTimeoutMethodExpectedWithInvocationContextParam(methodInfo.name(), classInfo.toString());
                }
                break;
            default:
                throw EjbLogger.ROOT_LOGGER.aroundTimeoutMethodExpectedWithInvocationContextParam(methodInfo.name(), classInfo.toString());
        }
        if (!methodInfo.returnType().name().toString().equals(Object.class.getName())) {
            throw EjbLogger.ROOT_LOGGER.aroundTimeoutMethodMustReturnObjectType(methodInfo.name(), classInfo.toString());
        }
    }
}
