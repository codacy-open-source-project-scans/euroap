/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.merging;

import java.util.Collection;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEApplicationClasses;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.metadata.MetadataCompleteMarker;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.metadata.ejb.spec.MethodInterfaceType;
import org.jboss.metadata.ejb.spec.MethodParametersMetaData;
import org.jboss.modules.Module;

/**
 * Superclass for the Jakarta Enterprise Beans metadata merging processors
 *
 * @author Stuart Douglas
 */
public abstract class AbstractMergingProcessor<T extends EJBComponentDescription> implements DeploymentUnitProcessor {

    private final Class<T> typeParam;

    public AbstractMergingProcessor(final Class<T> typeParam) {
        this.typeParam = typeParam;
    }


    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EEModuleDescription eeModuleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        final Collection<ComponentDescription> componentConfigurations = eeModuleDescription.getComponentDescriptions();
        final DeploymentReflectionIndex deploymentReflectionIndex = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.REFLECTION_INDEX);
        final EEApplicationClasses applicationClasses = deploymentUnit.getAttachment(Attachments.EE_APPLICATION_CLASSES_DESCRIPTION);


        if (componentConfigurations == null || componentConfigurations.isEmpty()) {
            return;
        }

        for (ComponentDescription componentConfiguration : componentConfigurations) {
            if (typeParam.isAssignableFrom(componentConfiguration.getClass())) {
                try {
                    processComponentConfig(deploymentUnit, applicationClasses, module, deploymentReflectionIndex, (T) componentConfiguration);
                } catch (Exception e) {
                    throw EjbLogger.ROOT_LOGGER.failToMergeData(componentConfiguration.getComponentName(), e);
                }
            }
        }
    }

    private void processComponentConfig(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses, final Module module, final DeploymentReflectionIndex deploymentReflectionIndex, final T description) throws DeploymentUnitProcessingException {

        final Class<?> componentClass;
        try {
            componentClass = module.getClassLoader().loadClass(description.getEJBClassName());
        } catch (ClassNotFoundException e) {
            throw EjbLogger.ROOT_LOGGER.failToLoadEjbClass(description.getEJBClassName(), e);
        }

        if (!MetadataCompleteMarker.isMetadataComplete(deploymentUnit)) {
            handleAnnotations(deploymentUnit, applicationClasses, deploymentReflectionIndex, componentClass, description);
        }
        handleDeploymentDescriptor(deploymentUnit, deploymentReflectionIndex, componentClass, description);
    }

    /**
     * Handle annotations relating to the component that have been found in the deployment. Will not be called if the deployment is metadata complete.
     */
    protected abstract void handleAnnotations(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final T description) throws DeploymentUnitProcessingException;

    /**
     * Handle the deployment descriptor
     */
    protected abstract void handleDeploymentDescriptor(final DeploymentUnit deploymentUnit, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final T description) throws DeploymentUnitProcessingException;

    protected MethodInterfaceType getMethodIntf(final MethodInterfaceType viewType, final MethodInterfaceType defaultMethodIntf) {
        return viewType == null ? defaultMethodIntf : viewType;
    }

    protected String[] getMethodParams(MethodParametersMetaData methodParametersMetaData) {
        if (methodParametersMetaData == null) {
            return null;
        }
        return methodParametersMetaData.toArray(new String[methodParametersMetaData.size()]);
    }
}
