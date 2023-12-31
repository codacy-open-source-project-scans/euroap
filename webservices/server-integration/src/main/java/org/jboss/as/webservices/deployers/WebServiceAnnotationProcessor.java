/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.webservices.deployers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.EEModuleClassDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.metadata.ClassAnnotationInformation;
import org.jboss.as.ee.metadata.ClassAnnotationInformationFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.metadata.property.PropertyReplacers;

/**
 * @author <a href="mailto:ema@redhat.com">Jim Ma</a>
 */
public class WebServiceAnnotationProcessor implements DeploymentUnitProcessor {
    @SuppressWarnings("rawtypes")
    final List<ClassAnnotationInformationFactory> factories;

    public WebServiceAnnotationProcessor() {
        @SuppressWarnings("rawtypes")
        List<ClassAnnotationInformationFactory> factories = new ArrayList<ClassAnnotationInformationFactory>();
        factories.add(new WebServiceAnnotationInformationFactory());
        factories.add(new WebServiceProviderAnnotationInformationFactory());
        factories.add(new WebContextAnnotationInformationFactory());
        this.factories = Collections.unmodifiableList(factories);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public final void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        final EEModuleDescription eeModuleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        final CompositeIndex index = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (index == null || eeModuleDescription == null) {
            return;
        }

        for (final ClassAnnotationInformationFactory factory : factories) {
            final Map<String, ClassAnnotationInformation<?, ?>> data = factory.createAnnotationInformation(index, PropertyReplacers.noop());
            for (Map.Entry<String, ClassAnnotationInformation<?, ?>> entry : data.entrySet()) {
                EEModuleClassDescription clazz = eeModuleDescription.addOrGetLocalClassDescription(entry.getKey());
                clazz.addAnnotationInformation(entry.getValue());
            }
        }
    }
}