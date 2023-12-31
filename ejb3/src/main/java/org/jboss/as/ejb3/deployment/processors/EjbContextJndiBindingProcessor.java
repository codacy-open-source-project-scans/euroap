/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors;

import java.util.Collection;

import jakarta.ejb.EJBContext;
import jakarta.ejb.EntityContext;
import jakarta.ejb.MessageDrivenContext;
import jakarta.ejb.SessionContext;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.BindingConfiguration;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.ComponentNamingMode;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.component.InjectionSource;
import org.jboss.as.ee.component.deployers.EEResourceReferenceProcessorRegistry;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.context.CurrentInvocationContext;
import org.jboss.as.ejb3.context.EjbContextResourceReferenceProcessor;
import org.jboss.as.naming.ContextListManagedReferenceFactory;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;

/**
 * Deployment processor responsible for detecting Jakarta Enterprise Beans components and adding a {@link BindingConfiguration} for the
 * java:comp/EJBContext entry.
 *
 * @author John Bailey
 */
public class EjbContextJndiBindingProcessor implements DeploymentUnitProcessor {

    /**
     * {@inheritDoc} *
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EEResourceReferenceProcessorRegistry registry = deploymentUnit.getAttachment(Attachments.RESOURCE_REFERENCE_PROCESSOR_REGISTRY);

        //setup ejb context jndi handlers
        registry.registerResourceReferenceProcessor(new EjbContextResourceReferenceProcessor(EJBContext.class));
        registry.registerResourceReferenceProcessor(new EjbContextResourceReferenceProcessor(SessionContext.class));
        registry.registerResourceReferenceProcessor(new EjbContextResourceReferenceProcessor(EntityContext.class));
        registry.registerResourceReferenceProcessor(new EjbContextResourceReferenceProcessor(MessageDrivenContext.class));


        final EEModuleDescription eeModuleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        final Collection<ComponentDescription> componentConfigurations = eeModuleDescription.getComponentDescriptions();
        if (componentConfigurations == null || componentConfigurations.isEmpty()) {
            return;
        }

        for (ComponentDescription componentConfiguration : componentConfigurations) {
            final CompositeIndex index = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.COMPOSITE_ANNOTATION_INDEX);
            if (index != null) {
                processComponentConfig(componentConfiguration);
            }
        }
    }

    protected void processComponentConfig(final ComponentDescription componentDescription) throws DeploymentUnitProcessingException {
        if (!(componentDescription instanceof EJBComponentDescription)) {
            return;  // Only process Jakarta Enterprise Beans
        }
        // if the Jakarta Enterprise Beans are packaged in a .war, then we need to bind the java:comp/EJBContext only once for the entire module
        if (componentDescription.getNamingMode() != ComponentNamingMode.CREATE) {
            // get the module description
            final EEModuleDescription moduleDescription = componentDescription.getModuleDescription();
            // the java:module/EJBContext binding configuration
            // Note that we bind to java:module/EJBContext since it's a .war. End users can still lookup java:comp/EJBContext
            // and that will internally get translated to  java:module/EJBContext for .war, since java:comp == java:module in
            // a web ENC. So binding to java:module/EJBContext is OK.
            final BindingConfiguration ejbContextBinding = new BindingConfiguration("java:module/EJBContext", directEjbContextReferenceSource);
            moduleDescription.getBindingConfigurations().add(ejbContextBinding);
        } else { // Jakarta Enterprise Beans packaged outside of a .war. So process normally.
            // add the binding configuration to the component description
            final BindingConfiguration ejbContextBinding = new BindingConfiguration("java:comp/EJBContext", directEjbContextReferenceSource);
            componentDescription.getBindingConfigurations().add(ejbContextBinding);
        }
    }

    private static final ManagedReference ejbContextManagedReference = new ManagedReference() {
        public void release() {
        }

        public Object getInstance() {
            return CurrentInvocationContext.getEjbContext();
        }
    };

    private static final ManagedReferenceFactory ejbContextManagedReferenceFactory = new ContextListManagedReferenceFactory() {

        public ManagedReference getReference() {
            return ejbContextManagedReference;
        }

        @Override
        public String getInstanceClassName() {
            return EJBContext.class.getName();
        }
    };

    private static final InjectionSource directEjbContextReferenceSource = new InjectionSource() {
        public void getResourceValue(final ResolutionContext resolutionContext, final ServiceBuilder<?> serviceBuilder, final DeploymentPhaseContext phaseContext, final Injector<ManagedReferenceFactory> injector) throws DeploymentUnitProcessingException {
            injector.inject(ejbContextManagedReferenceFactory);
        }

        public boolean equals(Object o) {
            return o != null && o.getClass() == this.getClass();
        }

        public int hashCode() {
            return 1;
        }
    };
}
