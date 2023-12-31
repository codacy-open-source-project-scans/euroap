/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.merging;

import java.util.List;

import org.jboss.as.ee.component.EEApplicationClasses;
import org.jboss.as.ee.component.EEModuleClassDescription;
import org.jboss.as.ee.metadata.ClassAnnotationInformation;
import org.jboss.as.ejb3.component.messagedriven.MessageDrivenComponentDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.resourceadapterbinding.metadata.EJBBoundResourceAdapterBindingMetaData;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.ejb3.annotation.ResourceAdapter;
import org.jboss.metadata.ejb.spec.AssemblyDescriptorMetaData;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;

/**
 * Handles the {@link org.jboss.ejb3.annotation.ResourceAdapter} annotation merging
 *
 * @author Stuart DouglasrunAs
 */
public class ResourceAdaptorMergingProcessor extends AbstractMergingProcessor<MessageDrivenComponentDescription> {

    public ResourceAdaptorMergingProcessor() {
        super(MessageDrivenComponentDescription.class);
    }

    @Override
    protected void handleAnnotations(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final MessageDrivenComponentDescription componentConfiguration) throws DeploymentUnitProcessingException {

        final EEModuleClassDescription clazz = applicationClasses.getClassByName(componentClass.getName());

        //we only care about annotations on the bean class itself
        if (clazz == null) {
            return;
        }
        final ClassAnnotationInformation<ResourceAdapter, String> resourceAdapter = clazz.getAnnotationInformation(ResourceAdapter.class);
        if (resourceAdapter == null || resourceAdapter.getClassLevelAnnotations().isEmpty()) {
            return;
        }

        final String configuredAdapterName = resourceAdapter.getClassLevelAnnotations().get(0);
        final String adapterName = addEarPrefixIfRelativeName(configuredAdapterName, deploymentUnit, componentClass);

        componentConfiguration.setResourceAdapterName(adapterName);
    }

    @Override
    protected void handleDeploymentDescriptor(final DeploymentUnit deploymentUnit, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final MessageDrivenComponentDescription componentConfiguration) throws DeploymentUnitProcessingException {

        final String ejbName = componentConfiguration.getEJBName();
        final EjbJarMetaData metaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        if (metaData == null) {
            return;
        }
        final AssemblyDescriptorMetaData assemblyDescriptor = metaData.getAssemblyDescriptor();
        if (assemblyDescriptor == null) {
            return;
        }
        final List<EJBBoundResourceAdapterBindingMetaData> resourceAdapterBindingDataList = assemblyDescriptor.getAny(EJBBoundResourceAdapterBindingMetaData.class);

        String configuredAdapterName = null;
        if (resourceAdapterBindingDataList != null) {
            for (EJBBoundResourceAdapterBindingMetaData resourceAdapterBindingData: resourceAdapterBindingDataList) {
                if ("*".equals(resourceAdapterBindingData.getEjbName()) && configuredAdapterName == null) {
                    configuredAdapterName = resourceAdapterBindingData.getResourceAdapterName();
                } else if (ejbName.equals(resourceAdapterBindingData.getEjbName())) {
                    configuredAdapterName = resourceAdapterBindingData.getResourceAdapterName();
                }
            }
        }
        if (configuredAdapterName != null) {
            final String adapterName = addEarPrefixIfRelativeName(configuredAdapterName,deploymentUnit,componentClass);
            componentConfiguration.setResourceAdapterName(adapterName);
        }
    }

    // adds ear prefix to configured adapter name if it is specified in relative form
    private String addEarPrefixIfRelativeName(final String configuredName, final DeploymentUnit deploymentUnit,
            final Class<?> componentClass) throws DeploymentUnitProcessingException {
        if (!configuredName.startsWith("#")) {
            return configuredName;
        }
        final DeploymentUnit parent = deploymentUnit.getParent();
        if (parent == null) {
            throw EjbLogger.ROOT_LOGGER.relativeResourceAdapterNameInStandaloneModule(deploymentUnit.getName(),
                    componentClass.getName(), configuredName);
        }
        return new StringBuilder().append(parent.getName()).append(configuredName).toString();
    }
}
