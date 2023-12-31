/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.util;

import static org.jboss.as.connector.subsystems.jca.Constants.DEFAULT_NAME;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.connector.metadata.deployment.ResourceAdapterDeployment;
import org.jboss.as.connector.metadata.xmldescriptors.ConnectorXmlDescriptor;
import org.jboss.as.connector.services.mdr.AS7MetadataRepository;
import org.jboss.as.connector.services.resourceadapters.deployment.ResourceAdapterXmlDeploymentService;
import org.jboss.as.connector.services.resourceadapters.deployment.registry.ResourceAdapterDeploymentRegistry;
import org.jboss.as.connector.subsystems.jca.JcaSubsystemConfiguration;
import org.jboss.as.connector.subsystems.resourceadapters.ResourceAdaptersSubsystemService;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.naming.service.NamingService;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.common.api.metadata.resourceadapter.Activation;
import org.jboss.jca.core.api.connectionmanager.ccm.CachedConnectionManager;
import org.jboss.jca.core.api.management.ManagementRepository;
import org.jboss.jca.core.spi.rar.ResourceAdapterRepository;
import org.jboss.jca.core.spi.transaction.TransactionIntegration;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;

public class RaServicesFactory {

    public static void createDeploymentService(final ManagementResourceRegistration registration, ConnectorXmlDescriptor connectorXmlDescriptor, Module module,
                                               ServiceTarget serviceTarget, final String deploymentUnitName, ServiceName deploymentUnitServiceName, String deployment,
                                               Activation raxml, final Resource deploymentResource, final ServiceRegistry serviceRegistry, final CapabilityServiceSupport support) {
        // Create the service

        ServiceName serviceName = ConnectorServices.getDeploymentServiceName(deploymentUnitName,raxml);
        ResourceAdapterXmlDeploymentService service = new ResourceAdapterXmlDeploymentService(connectorXmlDescriptor,
                raxml, module, deployment, serviceName, deploymentUnitServiceName);
        String bootStrapCtxName = DEFAULT_NAME;
        if (raxml.getBootstrapContext() != null && !raxml.getBootstrapContext().equals("undefined")) {
            bootStrapCtxName = raxml.getBootstrapContext();
        }
        ServiceBuilder<ResourceAdapterDeployment> builder =
                Services.addServerExecutorDependency(
                        serviceTarget.addService(serviceName, service),
                        service.getExecutorServiceInjector())
                .addDependency(ConnectorServices.IRONJACAMAR_MDR, AS7MetadataRepository.class, service.getMdrInjector())
                .addDependency(ConnectorServices.RA_REPOSITORY_SERVICE, ResourceAdapterRepository.class,
                        service.getRaRepositoryInjector())
                .addDependency(ConnectorServices.MANAGEMENT_REPOSITORY_SERVICE, ManagementRepository.class,
                        service.getManagementRepositoryInjector())
                .addDependency(ConnectorServices.RESOURCE_ADAPTER_REGISTRY_SERVICE,
                        ResourceAdapterDeploymentRegistry.class, service.getRegistryInjector())
                .addDependency(support.getCapabilityServiceName(ConnectorServices.TRANSACTION_INTEGRATION_CAPABILITY_NAME), TransactionIntegration.class,
                        service.getTxIntegrationInjector())
                .addDependency(ConnectorServices.CONNECTOR_CONFIG_SERVICE, JcaSubsystemConfiguration.class,
                        service.getConfigInjector())
                .addDependency(ConnectorServices.CCM_SERVICE, CachedConnectionManager.class, service.getCcmInjector())
                .addDependency(ConnectorServices.RESOURCEADAPTERS_SUBSYSTEM_SERVICE, ResourceAdaptersSubsystemService.class, service.getResourceAdaptersSubsystem());
        builder.requires(ConnectorServices.IDLE_REMOVER_SERVICE);
        builder.requires(ConnectorServices.CONNECTION_VALIDATOR_SERVICE);
        builder.requires(support.getCapabilityServiceName(NamingService.CAPABILITY_NAME));
        builder.requires(ConnectorServices.BOOTSTRAP_CONTEXT_SERVICE.append(bootStrapCtxName));
        builder.requires(ConnectorServices.RESOURCE_ADAPTER_DEPLOYER_SERVICE_PREFIX.append(connectorXmlDescriptor.getDeploymentName()));

        String raName = deployment;
        if (raxml.getId() != null) {
            raName = raxml.getId();
        }
        ServiceName parentName = ServiceName.of(ConnectorServices.RA_SERVICE, raName);
        for(ServiceName subServiceName: serviceRegistry.getServiceNames()) {
            if (parentName.isParentOf(subServiceName)
                    && !subServiceName.getSimpleName().equals(ConnectorServices.STATISTICS_SUFFIX)) {
                builder.requires(subServiceName);
            }
        }

        if (registration != null && deploymentResource != null
                && registration.isAllowsOverride()
                && registration.getOverrideModel(deploymentUnitName) == null) {

            registration.registerOverrideModel(deploymentUnitName, new OverrideDescriptionProvider() {
                @Override
                public Map<String, ModelNode> getAttributeOverrideDescriptions(Locale locale) {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, ModelNode> getChildTypeOverrideDescriptions(Locale locale) {
                    return Collections.emptyMap();
                }
            });
        }


        builder.setInitialMode(ServiceController.Mode.ACTIVE).install();

    }
}
