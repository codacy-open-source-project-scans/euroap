/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors;

import static org.jboss.as.ee.component.Attachments.EE_MODULE_CONFIGURATION;
import static org.jboss.as.server.deployment.DeploymentModelUtils.DEPLOYMENT_RESOURCE;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEModuleConfiguration;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.subsystem.EJB3Extension;
import org.jboss.as.ejb3.subsystem.EJB3SubsystemModel;
import org.jboss.as.ejb3.subsystem.deployment.AbstractEJBComponentRuntimeHandler;
import org.jboss.as.ejb3.subsystem.deployment.EJBComponentType;
import org.jboss.as.ejb3.subsystem.deployment.InstalledComponent;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;


/**
 * {@link org.jboss.as.server.deployment.Phase#INSTALL} processor that adds management resources describing EJB components.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class EjbManagementDeploymentUnitProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EEModuleConfiguration moduleDescription = deploymentUnit.getAttachment(EE_MODULE_CONFIGURATION);
        if (moduleDescription == null) {
            // Nothing to do
            return;
        }
        if (deploymentUnit.getParent() != null && deploymentUnit.getParent().getParent() != null) {
            // We only expose management resources 2 levels deep
            return;
        }

        // Iterate through each component, installing it into the container
        for (final ComponentConfiguration configuration : moduleDescription.getComponentConfigurations()) {
            try {
                final ComponentDescription componentDescription = configuration.getComponentDescription();
                if (componentDescription instanceof EJBComponentDescription) {
                    installManagementResource(configuration, deploymentUnit);
                }
            } catch (RuntimeException e) {
                throw EjbLogger.ROOT_LOGGER.failedToInstallManagementResource(e, configuration.getComponentName());
            }
        }
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {

        if (deploymentUnit.getParent() != null && deploymentUnit.getParent().getParent() != null) {
            // We only expose management resources 2 levels deep
            return;
        }

        // Iterate through each component, uninstalling it
        for (final InstalledComponent configuration : deploymentUnit.getAttachmentList(EjbDeploymentAttachmentKeys.MANAGED_COMPONENTS)) {
            try {
                uninstallManagementResource(configuration, deploymentUnit);
            } catch (RuntimeException e) {
                EjbLogger.DEPLOYMENT_LOGGER.failedToRemoveManagementResources(configuration, e.getLocalizedMessage());
            }
        }

        deploymentUnit.removeAttachment(EjbDeploymentAttachmentKeys.MANAGED_COMPONENTS);
    }

    private void installManagementResource(ComponentConfiguration configuration, DeploymentUnit deploymentUnit) {
        final EJBComponentType type = EJBComponentType.getComponentType(configuration);
        PathAddress addr = getComponentAddress(type, configuration, deploymentUnit);
        final AbstractEJBComponentRuntimeHandler<?> handler = type.getRuntimeHandler();
        handler.registerComponent(addr, configuration.getComponentDescription().getStartServiceName());
        deploymentUnit.addToAttachmentList(EjbDeploymentAttachmentKeys.MANAGED_COMPONENTS, new InstalledComponent(type, addr));
        final DeploymentResourceSupport deploymentResourceSupport = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_RESOURCE_SUPPORT);
        deploymentResourceSupport.getDeploymentSubModel(EJB3Extension.SUBSYSTEM_NAME, addr.getLastElement());

        final EJBComponentDescription description = (EJBComponentDescription) configuration.getComponentDescription();
        Resource timerServiceResource = description.getTimerServiceResource();
        if (timerServiceResource != null) {
            final PathAddress timerServiceAddress = PathAddress.pathAddress(addr.getLastElement(), EJB3SubsystemModel.TIMER_SERVICE_PATH);
            deploymentResourceSupport.registerDeploymentSubResource(EJB3Extension.SUBSYSTEM_NAME, timerServiceAddress, timerServiceResource);
        }
    }

    private void uninstallManagementResource(final InstalledComponent component, DeploymentUnit deploymentUnit) {
        component.getType().getRuntimeHandler().unregisterComponent(component.getAddress());

        // Deregister possible /service=timer-service
        Resource root = deploymentUnit.getAttachment(DEPLOYMENT_RESOURCE);
        Resource subResource = root.getChild(component.getAddress().getParent().getLastElement());
        if (subResource != null) {
            Resource componentResource = subResource.getChild(component.getAddress().getLastElement());
            if (componentResource != null) {
                componentResource.removeChild(EJB3SubsystemModel.TIMER_SERVICE_PATH);
            }
        }
    }

    private static PathAddress getComponentAddress(EJBComponentType type, ComponentConfiguration configuration, DeploymentUnit deploymentUnit) {
        List<PathElement> elements = new ArrayList<PathElement>();
        if (deploymentUnit.getParent() == null) {
            elements.add(PathElement.pathElement(ModelDescriptionConstants.DEPLOYMENT, deploymentUnit.getName()));
        } else {
            elements.add(PathElement.pathElement(ModelDescriptionConstants.DEPLOYMENT, deploymentUnit.getParent().getName()));
            elements.add(PathElement.pathElement(ModelDescriptionConstants.SUBDEPLOYMENT, deploymentUnit.getName()));
        }
        elements.add(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, EJB3Extension.SUBSYSTEM_NAME));
        elements.add(PathElement.pathElement(type.getResourceType(), configuration.getComponentName()));
        return PathAddress.pathAddress(elements);
    }
}

