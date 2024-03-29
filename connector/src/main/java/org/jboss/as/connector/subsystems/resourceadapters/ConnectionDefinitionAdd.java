/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.resourceadapters;

import static org.jboss.as.connector.logging.ConnectorLogger.SUBSYSTEM_RA_LOGGER;
import static org.jboss.as.connector.subsystems.common.jndi.Constants.JNDI_NAME;
import static org.jboss.as.connector.subsystems.common.jndi.Constants.USE_JAVA_CONTEXT;
import static org.jboss.as.connector.subsystems.jca.Constants.DEFAULT_NAME;
import static org.jboss.as.connector.subsystems.resourceadapters.CommonAttributes.CONNECTION_DEFINITIONS_NODE_ATTRIBUTE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ARCHIVE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.AUTHENTICATION_CONTEXT;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.AUTHENTICATION_CONTEXT_AND_APPLICATION;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.MODULE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_AUTHENTICATION_CONTEXT;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_CREDENTIAL_REFERENCE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.RECOVERY_SECURITY_DOMAIN;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.SECURITY_DOMAIN;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.SECURITY_DOMAIN_AND_APPLICATION;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.STATISTICS_ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;

import org.jboss.as.connector._private.Capabilities;
import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.connector.services.resourceadapters.statistics.ConnectionDefinitionStatisticsService;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.common.api.metadata.common.TransactionSupportEnum;
import org.jboss.jca.common.api.metadata.resourceadapter.Activation;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * Adds a recovery-environment to the Transactions subsystem
 */
public class ConnectionDefinitionAdd extends AbstractAddStepHandler {

    public static final ConnectionDefinitionAdd INSTANCE = new ConnectionDefinitionAdd();

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        super.populateModel(context, operation, resource);
        final ModelNode model = resource.getModel();
        handleCredentialReferenceUpdate(context, model.get(RECOVERY_CREDENTIAL_REFERENCE.getName()), RECOVERY_CREDENTIAL_REFERENCE.getName());
    }

    @Override
    protected void populateModel(ModelNode operation, ModelNode modelNode) throws OperationFailedException {
        for (AttributeDefinition attribute : CONNECTION_DEFINITIONS_NODE_ATTRIBUTE) {
            attribute.validateAndSet(operation, modelNode);
        }
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, final Resource resource) throws OperationFailedException {

        final ModelNode address = operation.require(OP_ADDR);
        PathAddress path = context.getCurrentAddress();
        final String jndiName = JNDI_NAME.resolveModelAttribute(context, operation).asString();
        final String raName = path.getParent().getLastElement().getValue();

        final String archiveOrModuleName;
        ModelNode raModel = context.readResourceFromRoot(path.getParent(), false).getModel();
        final boolean statsEnabled = STATISTICS_ENABLED.resolveModelAttribute(context, raModel).asBoolean();

        if (!raModel.hasDefined(ARCHIVE.getName()) && !raModel.hasDefined(MODULE.getName())) {
            throw ConnectorLogger.ROOT_LOGGER.archiveOrModuleRequired();
        }
        ModelNode resourceModel = resource.getModel();

        // add extra security validation: legacy security configuration should not be used
        if (resourceModel.hasDefined(SECURITY_DOMAIN.getName()))
            throw SUBSYSTEM_RA_LOGGER.legacySecurityAttributeNotSupported(SECURITY_DOMAIN.getName());
        else if (resourceModel.hasDefined(SECURITY_DOMAIN_AND_APPLICATION.getName()))
            throw SUBSYSTEM_RA_LOGGER.legacySecurityAttributeNotSupported(SECURITY_DOMAIN_AND_APPLICATION.getName());
        // do the same for recovery security attributes
        if (resourceModel.hasDefined(RECOVERY_SECURITY_DOMAIN.getName()))
            throw SUBSYSTEM_RA_LOGGER.legacySecurityAttributeNotSupported(RECOVERY_SECURITY_DOMAIN.getName());

        final ModelNode credentialReference = RECOVERY_CREDENTIAL_REFERENCE.resolveModelAttribute(context, resourceModel);
        // add extra security validation: legacy security configuration should not be used
        boolean hasSecurityDomain = resourceModel.hasDefined(SECURITY_DOMAIN.getName());
        boolean hasSecurityDomainAndApp = resourceModel.hasDefined(SECURITY_DOMAIN_AND_APPLICATION.getName());
        if (hasSecurityDomain) {
            throw SUBSYSTEM_RA_LOGGER.legacySecurityAttributeNotSupported(SECURITY_DOMAIN.getName());
        }
        else if (hasSecurityDomainAndApp) {
            throw SUBSYSTEM_RA_LOGGER.legacySecurityAttributeNotSupported(SECURITY_DOMAIN_AND_APPLICATION.getName());
        }
        boolean hasRecoverySecurityDomain = resourceModel.hasDefined(RECOVERY_SECURITY_DOMAIN.getName());
        if (hasRecoverySecurityDomain) {
            throw SUBSYSTEM_RA_LOGGER.legacySecurityAttributeNotSupported(RECOVERY_SECURITY_DOMAIN.getName());
        }
        if (raModel.get(ARCHIVE.getName()).isDefined()) {
            archiveOrModuleName = ARCHIVE.resolveModelAttribute(context, raModel).asString();
        } else {
            archiveOrModuleName = MODULE.resolveModelAttribute(context, raModel).asString();
        }
        final String poolName = PathAddress.pathAddress(address).getLastElement().getValue();

        try {
            ServiceName serviceName = ServiceName.of(ConnectorServices.RA_SERVICE, raName, poolName);
            ServiceName raServiceName = ServiceName.of(ConnectorServices.RA_SERVICE, raName);

            final ModifiableResourceAdapter ravalue = ((ModifiableResourceAdapter) context.getServiceRegistry(false).getService(raServiceName).getValue());
            boolean isXa = ravalue.getTransactionSupport() == TransactionSupportEnum.XATransaction;

            final ServiceTarget serviceTarget = context.getServiceTarget();

            final ConnectionDefinitionService service = new ConnectionDefinitionService();
            service.getConnectionDefinitionSupplierInjector().inject(
                    () -> RaOperationUtil.buildConnectionDefinitionObject(context, resourceModel, poolName, isXa, service.getCredentialSourceSupplier().getOptionalValue())
            );

            final ServiceBuilder<ModifiableConnDef> cdServiceBuilder = serviceTarget.addService(serviceName, service).setInitialMode(ServiceController.Mode.ACTIVE)
                    .addDependency(raServiceName, ModifiableResourceAdapter.class, service.getRaInjector());

            // Add a dependency to the required authentication-contexts. These will be looked in the ElytronSecurityFactory
            // and this should be changed to use a proper capability in the future.
            if (resourceModel.hasDefined(AUTHENTICATION_CONTEXT.getName())) {
                cdServiceBuilder.requires(context.getCapabilityServiceName(
                        Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY,
                        AUTHENTICATION_CONTEXT.resolveModelAttribute(context, resourceModel).asString(),
                        AuthenticationContext.class));
            } else if (resourceModel.hasDefined(AUTHENTICATION_CONTEXT_AND_APPLICATION.getName())) {
                cdServiceBuilder.requires(context.getCapabilityServiceName(
                        Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY,
                        AUTHENTICATION_CONTEXT_AND_APPLICATION.resolveModelAttribute(context, resourceModel).asString(),
                        AuthenticationContext.class));
            }


            if (resourceModel.hasDefined(RECOVERY_AUTHENTICATION_CONTEXT.getName())) {
                cdServiceBuilder.requires(context.getCapabilityServiceName(Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY,
                        RECOVERY_AUTHENTICATION_CONTEXT.resolveModelAttribute(context, resourceModel).asString(),
                        AuthenticationContext.class));
            }

            if (credentialReference.isDefined()) {
                service.getCredentialSourceSupplier().inject(
                        CredentialReference.getCredentialSourceSupplier(context, RECOVERY_CREDENTIAL_REFERENCE, resourceModel, cdServiceBuilder));
            }


            // Install the ConnectionDefinitionService
            cdServiceBuilder.install();


            ServiceRegistry registry = context.getServiceRegistry(true);

            final ServiceController<?> RaxmlController = registry.getService(ServiceName.of(ConnectorServices.RA_SERVICE, raName));
            Activation raxml = (Activation) RaxmlController.getValue();
            ServiceName deploymentServiceName = ConnectorServices.getDeploymentServiceName(archiveOrModuleName, raName);
            String bootStrapCtxName = DEFAULT_NAME;
            if (raxml.getBootstrapContext() != null && !raxml.getBootstrapContext().equals("undefined")) {
                bootStrapCtxName = raxml.getBootstrapContext();
            }

            final boolean useJavaContext = USE_JAVA_CONTEXT.resolveModelAttribute(context, raModel).asBoolean();

            ConnectionDefinitionStatisticsService connectionDefinitionStatisticsService = new ConnectionDefinitionStatisticsService(context.getResourceRegistrationForUpdate(), jndiName, useJavaContext, poolName, statsEnabled);

            ServiceBuilder statsServiceBuilder = serviceTarget.addService(serviceName.append(ConnectorServices.STATISTICS_SUFFIX), connectionDefinitionStatisticsService);
            statsServiceBuilder.addDependency(ConnectorServices.BOOTSTRAP_CONTEXT_SERVICE.append(bootStrapCtxName), Object.class, connectionDefinitionStatisticsService.getBootstrapContextInjector())
                    .addDependency(deploymentServiceName, Object.class, connectionDefinitionStatisticsService.getResourceAdapterDeploymentInjector())
                    .setInitialMode(ServiceController.Mode.PASSIVE)
                    .install();

            PathElement peCD = PathElement.pathElement(Constants.STATISTICS_NAME, "pool");

            final Resource cdResource = new IronJacamarResource.IronJacamarRuntimeResource();

            resource.registerChild(peCD, cdResource);

            PathElement peExtended = PathElement.pathElement(Constants.STATISTICS_NAME, "extended");

            final Resource extendedResource = new IronJacamarResource.IronJacamarRuntimeResource();

            resource.registerChild(peExtended, extendedResource);


        } catch (Exception e) {
            throw new OperationFailedException(e, new ModelNode().set(ConnectorLogger.ROOT_LOGGER.failedToCreate("ConnectionDefinition", operation, e.getLocalizedMessage())));
        }
    }

    @Override
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
        rollbackCredentialStoreUpdate(RECOVERY_CREDENTIAL_REFERENCE, context, resource);
    }


}
