/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.connector.deployers.ra.processors;

import org.jboss.as.connector.metadata.xmldescriptors.ConnectorXmlDescriptor;
import org.jboss.as.connector.subsystems.resourceadapters.ResourceAdaptersSubsystemService;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

public class RarDependencyProcessor implements DeploymentUnitProcessor {

    private static String JMS_ID = "jakarta.jms.api";
    private static String IRON_JACAMAR_ID = "org.jboss.ironjacamar.api";
    private static String IRON_JACAMAR_IMPL_ID = "org.jboss.ironjacamar.impl";
    private static String VALIDATION_ID = "jakarta.validation.api";
    private static String HIBERNATE_VALIDATOR_ID = "org.hibernate.validator";
    private static String RESOURCE_API_ID = "jakarta.resource.api";


    private final boolean appclient;

    public RarDependencyProcessor(final boolean appclient) {
        this.appclient = appclient;
    }

    /**
     * Add dependencies for modules required for ra deployments
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);

        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, RESOURCE_API_ID, false, false, false, false));
        if (phaseContext.getDeploymentUnit().getAttachment(ConnectorXmlDescriptor.ATTACHMENT_KEY) == null) {
            return;  // Skip non ra deployments
        }

        //if a module depends on a rar it also needs a dep on all the rar's "local dependencies"
        moduleSpecification.setLocalDependenciesTransitive(true);
        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, JMS_ID, false, false, false, false));
        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, VALIDATION_ID, false, false, false, false));
        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, IRON_JACAMAR_ID, false, false, false, false));
        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, IRON_JACAMAR_IMPL_ID, false, true, false, false));
        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, HIBERNATE_VALIDATOR_ID, false, false, true, false));
        if (! appclient)
            phaseContext.addDeploymentDependency(ConnectorServices.RESOURCEADAPTERS_SUBSYSTEM_SERVICE, ResourceAdaptersSubsystemService.ATTACHMENT_KEY);
    }
}
