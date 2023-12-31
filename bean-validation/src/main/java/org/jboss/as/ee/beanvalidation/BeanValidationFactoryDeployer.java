/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ee.beanvalidation;

import static org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME;

import jakarta.validation.ValidatorFactory;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.ComponentNamingMode;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.ValueManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.weld.WeldCapability;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Creates a Jakarta Bean Validation factory and adds it to the deployment and binds it to JNDI.
 * <p/>
 * We use a lazy wrapper around the ValidatorFactory to stop it being initialized until it is used.
 * TODO: it would be neat if hibernate validator could make use of our annotation scanning etc
 *
 * @author Stuart Douglas
 */
public class BeanValidationFactoryDeployer implements DeploymentUnitProcessor {
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        final EEModuleDescription moduleDescription = deploymentUnit.getAttachment(org.jboss.as.ee.component.Attachments.EE_MODULE_DESCRIPTION);
        if(module == null || moduleDescription == null) {
            return;
        }
        final LazyValidatorFactory factory  = new LazyValidatorFactory(module.getClassLoader());
        deploymentUnit.putAttachment(BeanValidationAttachments.VALIDATOR_FACTORY, factory);

        bindFactoryToJndi(factory,deploymentUnit,phaseContext,moduleDescription);

    }

    private void bindFactoryToJndi(LazyValidatorFactory factory, DeploymentUnit deploymentUnit, DeploymentPhaseContext phaseContext,EEModuleDescription moduleDescription) {

        if(moduleDescription == null) {
            return;
        }

        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        //if this is a war we need to bind to the modules comp namespace
        if(DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit) || DeploymentTypeMarker.isType(DeploymentType.APPLICATION_CLIENT, deploymentUnit)) {
            final ServiceName moduleContextServiceName = ContextNames.contextServiceNameOfModule(moduleDescription.getApplicationName(), moduleDescription.getModuleName());
            bindServices(factory, serviceTarget, moduleDescription, moduleDescription.getModuleName(), moduleContextServiceName);
        }

        for(ComponentDescription component : moduleDescription.getComponentDescriptions()) {
            if(component.getNamingMode() == ComponentNamingMode.CREATE) {
                final ServiceName compContextServiceName = ContextNames.contextServiceNameOfComponent(moduleDescription.getApplicationName(),moduleDescription.getModuleName(),component.getComponentName());
                bindServices(factory, serviceTarget, moduleDescription, component.getComponentName(), compContextServiceName);
            }
        }

    }

    /**
     *
     * @param factory The ValidatorFactory to bind
     * @param serviceTarget The service target
     * @param contextServiceName The service name of the context to bind to
     */
    private void bindServices(LazyValidatorFactory factory, ServiceTarget serviceTarget, EEModuleDescription description, String componentName, ServiceName contextServiceName) {

        BinderService validatorFactoryBindingService = new BinderService("ValidatorFactory");
        validatorFactoryBindingService.getManagedObjectInjector().inject(new ValueManagedReferenceFactory(factory));
        serviceTarget.addService(contextServiceName.append("ValidatorFactory"), validatorFactoryBindingService)
            .addDependency(contextServiceName, ServiceBasedNamingStore.class, validatorFactoryBindingService.getNamingStoreInjector())
            .install();

        BinderService validatorBindingService = new BinderService("Validator");
        validatorBindingService.getManagedObjectInjector().inject(new ValidatorJndiInjectable(factory));
        serviceTarget.addService(contextServiceName.append("Validator"), validatorBindingService)
            .addDependency(contextServiceName, ServiceBasedNamingStore.class, validatorBindingService.getNamingStoreInjector())
            .install();

    }
    @Override
    public void undeploy(DeploymentUnit context) {
        ValidatorFactory validatorFactory = context.getAttachment(BeanValidationAttachments.VALIDATOR_FACTORY);
        final CapabilityServiceSupport support = context.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);

        boolean partOfWeldDeployment = false;
        if (support.hasCapability(WELD_CAPABILITY_NAME)) {
            partOfWeldDeployment = support.getOptionalCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class)
                    .get().isPartOfWeldDeployment(context);
        }
        if (validatorFactory != null && !partOfWeldDeployment) {
            // If the ValidatorFactory is not Jakarta Contexts and Dependency Injection enabled, close it here. Otherwise, it's
            // closed via CdiValidatorFactoryService before the Weld service is stopped.
            validatorFactory.close();
        }
        context.removeAttachment(BeanValidationAttachments.VALIDATOR_FACTORY);
    }

}
