/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ee.naming;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.ComponentNamingMode;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Processor responsible for binding java:comp/InstanceName
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class InstanceNameBindingProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EEModuleDescription moduleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);

        if(moduleDescription == null) {
            return;
        }

        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        //if this is a war we need to bind to the modules comp namespace
        if(DeploymentTypeMarker.isType(DeploymentType.WAR,deploymentUnit) ||
                DeploymentTypeMarker.isType(DeploymentType.APPLICATION_CLIENT, deploymentUnit)) {
            final ServiceName moduleContextServiceName = ContextNames.contextServiceNameOfModule(moduleDescription.getApplicationName(),moduleDescription.getModuleName());
            bindServices(deploymentUnit, serviceTarget, moduleContextServiceName);
        }

        for(ComponentDescription component : moduleDescription.getComponentDescriptions()) {
            if(component.getNamingMode() == ComponentNamingMode.CREATE) {
                final ServiceName compContextServiceName = ContextNames.contextServiceNameOfComponent(moduleDescription.getApplicationName(),moduleDescription.getModuleName(),component.getComponentName());
                bindServices(deploymentUnit, serviceTarget, compContextServiceName);
            }
        }

    }

    private void bindServices(DeploymentUnit deploymentUnit, ServiceTarget serviceTarget, ServiceName contextServiceName) {

        final ServiceName instanceNameServiceName = contextServiceName.append("InstanceName");
        final BinderService instanceNameService = new BinderService("InstanceName");
        serviceTarget.addService(instanceNameServiceName, instanceNameService)
            .addDependency(contextServiceName, ServiceBasedNamingStore.class, instanceNameService.getNamingStoreInjector())
            .addDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, new Injector<ServerEnvironment>() {
                @Override
                public void inject(final ServerEnvironment serverEnvironment) throws InjectionException {
                    instanceNameService.getManagedObjectInjector().inject(new ManagedReferenceFactory() {
                        @Override
                        public ManagedReference getReference() {
                            return new ManagedReference() {
                                @Override
                                public void release() {

                                }

                                @Override
                                public Object getInstance() {
                                    final String nodeName = serverEnvironment.getNodeName();
                                    return nodeName == null ? "" : nodeName;
                                }
                            };
                        }
                    });
                }

                @Override
                public void uninject() {
                    instanceNameService.getManagedObjectInjector().uninject();
                }
            })
            .install();
        final Map<ServiceName, Set<ServiceName>> jndiComponentDependencies = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.COMPONENT_JNDI_DEPENDENCIES);
        Set<ServiceName> jndiDependencies = jndiComponentDependencies.get(contextServiceName);
        if (jndiDependencies == null) {
            jndiComponentDependencies.put(contextServiceName, jndiDependencies = new HashSet<>());
        }
        jndiDependencies.add(instanceNameServiceName);
    }
}
