/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.merging;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import jakarta.ejb.AfterBegin;
import jakarta.ejb.AfterCompletion;
import jakarta.ejb.BeforeCompletion;
import jakarta.ejb.SessionSynchronization;

import org.jboss.as.ee.component.EEApplicationClasses;
import org.jboss.as.ee.metadata.MethodAnnotationAggregator;
import org.jboss.as.ee.metadata.RuntimeAnnotationInformation;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.component.stateful.StatefulComponentDescription;
import org.jboss.as.ejb3.deployment.processors.dd.MethodResolutionUtils;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndex;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.metadata.ejb.spec.SessionBean31MetaData;
import org.jboss.metadata.ejb.spec.SessionBeanMetaData;

/**
 * Merging processor that handles session synchronization callback methods
 *
 * @author Stuart Douglas
 */
public class SessionSynchronizationMergingProcessor extends AbstractMergingProcessor<StatefulComponentDescription> {

    public SessionSynchronizationMergingProcessor() {
        super(StatefulComponentDescription.class);
    }

    @Override
    protected void handleAnnotations(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final StatefulComponentDescription description) throws DeploymentUnitProcessingException {

        if (SessionSynchronization.class.isAssignableFrom(componentClass)) {
            //handled in handleDeploymentDescriptor
            return;
        }

        RuntimeAnnotationInformation<Boolean> afterBegin = MethodAnnotationAggregator.runtimeAnnotationInformation(componentClass, applicationClasses, deploymentReflectionIndex, AfterBegin.class);
        if (afterBegin.getMethodAnnotations().size() > 1) {
            throw EjbLogger.ROOT_LOGGER.multipleAnnotationsOnBean("@AfterBegin", description.getEJBClassName());
        } else if (!afterBegin.getMethodAnnotations().isEmpty()) {
            Map.Entry<Method, List<Boolean>> entry = afterBegin.getMethodAnnotations().entrySet().iterator().next();
            description.setAfterBegin(entry.getKey());
        }

        RuntimeAnnotationInformation<Boolean> afterComp = MethodAnnotationAggregator.runtimeAnnotationInformation(componentClass, applicationClasses, deploymentReflectionIndex, AfterCompletion.class);
        if (afterComp.getMethodAnnotations().size() > 1) {
            throw EjbLogger.ROOT_LOGGER.multipleAnnotationsOnBean("@AfterCompletion", description.getEJBClassName());
        } else if (!afterComp.getMethodAnnotations().isEmpty()) {
            Map.Entry<Method, List<Boolean>> entry = afterComp.getMethodAnnotations().entrySet().iterator().next();
            description.setAfterCompletion(entry.getKey());
        }

        RuntimeAnnotationInformation<Boolean> beforeComp = MethodAnnotationAggregator.runtimeAnnotationInformation(componentClass, applicationClasses, deploymentReflectionIndex, BeforeCompletion.class);
        if (beforeComp.getMethodAnnotations().size() > 1) {
            throw EjbLogger.ROOT_LOGGER.multipleAnnotationsOnBean("@BeforeCompletion", description.getEJBClassName());
        } else if (!beforeComp.getMethodAnnotations().isEmpty()) {
            Map.Entry<Method, List<Boolean>> entry = beforeComp.getMethodAnnotations().entrySet().iterator().next();
            description.setBeforeCompletion(entry.getKey());
        }

    }

    @Override
    protected void handleDeploymentDescriptor(final DeploymentUnit deploymentUnit, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final StatefulComponentDescription description) throws DeploymentUnitProcessingException {

        final DeploymentReflectionIndex reflectionIndex = deploymentUnit.getAttachment(Attachments.REFLECTION_INDEX);

        //if we implement SessionSynchronization we can ignore any DD information
        if (SessionSynchronization.class.isAssignableFrom(componentClass)) {
            final ClassReflectionIndex classIndex = reflectionIndex.getClassIndex(SessionSynchronization.class);
            description.setAfterBegin(classIndex.getMethod(void.class, "afterBegin"));
            description.setAfterCompletion(classIndex.getMethod(void.class, "afterCompletion", boolean.class));
            description.setBeforeCompletion(classIndex.getMethod(void.class,"beforeCompletion"));
            return;
        }

        SessionBeanMetaData data = description.getDescriptorData();
        if (data instanceof SessionBean31MetaData) {
            SessionBean31MetaData metaData = (SessionBean31MetaData) data;
            if (metaData.getAfterBeginMethod() != null)
                description.setAfterBegin(MethodResolutionUtils.resolveMethod(metaData.getAfterBeginMethod(), componentClass,reflectionIndex));
            if (metaData.getAfterCompletionMethod() != null)
                description.setAfterCompletion(MethodResolutionUtils.resolveMethod(metaData.getAfterCompletionMethod(), componentClass,reflectionIndex));
            if (metaData.getBeforeCompletionMethod() != null)
                description.setBeforeCompletion(MethodResolutionUtils.resolveMethod(metaData.getBeforeCompletionMethod(), componentClass,reflectionIndex));
        }
    }
}
