/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.merging;

import org.jboss.as.ee.component.EEApplicationClasses;
import org.jboss.as.ee.component.EEModuleClassDescription;
import org.jboss.as.ee.metadata.ClassAnnotationInformation;
import org.jboss.as.ejb3.component.stateful.StatefulComponentDescription;
import org.jboss.as.ejb3.component.stateful.StatefulTimeoutInfo;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.metadata.ejb.spec.SessionBean31MetaData;
import org.jboss.metadata.ejb.spec.SessionBeanMetaData;
import org.jboss.metadata.ejb.spec.StatefulTimeoutMetaData;

import jakarta.ejb.StatefulTimeout;
import java.util.concurrent.TimeUnit;

/**
 * Handles the {@link jakarta.annotation.security.RunAs} annotation merging
 *
 * @author Stuart DouglasrunAs
 */
public class StatefulTimeoutMergingProcessor extends AbstractMergingProcessor<StatefulComponentDescription> {

    public StatefulTimeoutMergingProcessor() {
        super(StatefulComponentDescription.class);
    }

    @Override
    protected void handleAnnotations(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final StatefulComponentDescription componentConfiguration) throws DeploymentUnitProcessingException {
        final EEModuleClassDescription clazz = applicationClasses.getClassByName(componentClass.getName());
        //we only care about annotations on the bean class itself
        if (clazz == null) {
            return;
        }
        final ClassAnnotationInformation<StatefulTimeout, StatefulTimeoutInfo> timeout = clazz.getAnnotationInformation(StatefulTimeout.class);
        if (timeout == null) {
            return;
        }
        if (!timeout.getClassLevelAnnotations().isEmpty()) {
            componentConfiguration.setStatefulTimeout(timeout.getClassLevelAnnotations().get(0));
        }
    }

    @Override
    protected void handleDeploymentDescriptor(final DeploymentUnit deploymentUnit, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final StatefulComponentDescription componentConfiguration) throws DeploymentUnitProcessingException {
        final SessionBeanMetaData data = componentConfiguration.getDescriptorData();
        if (data == null) {
            return;
        }
        if (data instanceof SessionBean31MetaData) {
            SessionBean31MetaData sessionBean31MetaData = (SessionBean31MetaData) data;
            final StatefulTimeoutMetaData statefulTimeout = sessionBean31MetaData.getStatefulTimeout();
            if (statefulTimeout != null) {
                TimeUnit unit = TimeUnit.MINUTES;
                if (statefulTimeout.getUnit() != null) {
                    unit = statefulTimeout.getUnit();
                }
                componentConfiguration.setStatefulTimeout(new StatefulTimeoutInfo(statefulTimeout.getTimeout(), unit));
            }
        }
    }
}
