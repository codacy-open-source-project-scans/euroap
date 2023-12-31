/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.resourceadapters;

import static org.jboss.as.connector.subsystems.jca.Constants.DEFAULT_NAME;
import static org.jboss.as.connector.subsystems.resourceadapters.CommonAttributes.ADMIN_OBJECTS_NODE_ATTRIBUTE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.ARCHIVE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.MODULE;
import static org.jboss.as.connector.subsystems.resourceadapters.Constants.STATISTICS_ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.connector.services.resourceadapters.statistics.AdminObjectStatisticsService;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.common.api.metadata.resourceadapter.Activation;
import org.jboss.jca.common.api.validator.ValidateException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;

/**
 * Adds a recovery-environment to the Transactions subsystem
 */
public class AdminObjectAdd extends AbstractAddStepHandler {
    static final AdminObjectAdd INSTANCE = new AdminObjectAdd();

    private AdminObjectAdd() {

    }

    @Override
    protected void populateModel(ModelNode operation, ModelNode modelNode) throws OperationFailedException {
        for (AttributeDefinition attribute : ADMIN_OBJECTS_NODE_ATTRIBUTE) {
            attribute.validateAndSet(operation, modelNode);
        }
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, final Resource resource) throws OperationFailedException {

        final ModelNode address = operation.require(OP_ADDR);
        PathAddress path = PathAddress.pathAddress(address);
        final String raName = context.getCurrentAddress().getParent().getLastElement().getValue();
        final String archiveOrModuleName;
        ModelNode raModel = context.readResourceFromRoot(path.subAddress(0, path.size() - 1), false).getModel();
        final boolean statsEnabled = STATISTICS_ENABLED.resolveModelAttribute(context, raModel).asBoolean();

        if (!raModel.hasDefined(ARCHIVE.getName()) && !raModel.hasDefined(MODULE.getName())) {
            throw ConnectorLogger.ROOT_LOGGER.archiveOrModuleRequired();
        }
        if (raModel.get(ARCHIVE.getName()).isDefined()) {
            archiveOrModuleName = ARCHIVE.resolveModelAttribute(context, raModel).asString();
        } else {
            archiveOrModuleName = MODULE.resolveModelAttribute(context, raModel).asString();
        }
        final String poolName = PathAddress.pathAddress(address).getLastElement().getValue();


        final ModifiableAdminObject adminObjectValue;
        try {
            adminObjectValue = RaOperationUtil.buildAdminObjects(context, operation, poolName);
        } catch (ValidateException e) {
            throw new OperationFailedException(e, new ModelNode().set(ConnectorLogger.ROOT_LOGGER.failedToCreate("AdminObject", operation, e.getLocalizedMessage())));
        }


        ServiceName serviceName = ServiceName.of(ConnectorServices.RA_SERVICE, raName, poolName);
        ServiceName raServiceName = ServiceName.of(ConnectorServices.RA_SERVICE, raName);

        final ServiceTarget serviceTarget = context.getServiceTarget();

        final AdminObjectService service = new AdminObjectService(adminObjectValue);
        serviceTarget.addService(serviceName, service).setInitialMode(ServiceController.Mode.ACTIVE)
                .addDependency(raServiceName, ModifiableResourceAdapter.class, service.getRaInjector())
                .install();

        ServiceRegistry registry = context.getServiceRegistry(true);

        final ServiceController<?> RaxmlController = registry.getService(ServiceName.of(ConnectorServices.RA_SERVICE, raName));
        Activation raxml = (Activation) RaxmlController.getValue();
        ServiceName deploymentServiceName = ConnectorServices.getDeploymentServiceName(archiveOrModuleName, raName);
        String bootStrapCtxName = DEFAULT_NAME;
        if (raxml.getBootstrapContext() != null && !raxml.getBootstrapContext().equals("undefined")) {
            bootStrapCtxName = raxml.getBootstrapContext();
        }


        AdminObjectStatisticsService adminObjectStatisticsService = new AdminObjectStatisticsService(context.getResourceRegistrationForUpdate(), poolName, statsEnabled);

        ServiceBuilder statsServiceBuilder = serviceTarget.addService(serviceName.append(ConnectorServices.STATISTICS_SUFFIX), adminObjectStatisticsService);
        statsServiceBuilder.addDependency(ConnectorServices.BOOTSTRAP_CONTEXT_SERVICE.append(bootStrapCtxName), Object.class, adminObjectStatisticsService.getBootstrapContextInjector())
                .addDependency(deploymentServiceName, Object.class, adminObjectStatisticsService.getResourceAdapterDeploymentInjector())
                .setInitialMode(ServiceController.Mode.PASSIVE)
                .install();

        PathElement peAO = PathElement.pathElement(Constants.STATISTICS_NAME, "extended");

        final Resource aoResource = new IronJacamarResource.IronJacamarRuntimeResource();

        resource.registerChild(peAO, aoResource);
    }
}
