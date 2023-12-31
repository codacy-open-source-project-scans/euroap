/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.deployers.ra;

import org.jboss.as.connector.services.mdr.AS7MetadataRepository;
import org.jboss.as.connector.services.resourceadapters.AdminObjectReferenceFactoryService;
import org.jboss.as.connector.services.resourceadapters.DirectAdminObjectActivatorService;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.ee.resource.definition.ResourceDefinitionInjectionSource;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.modules.Module;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

import static org.jboss.as.connector.logging.ConnectorLogger.DEPLOYMENT_CONNECTOR_LOGGER;
import static org.jboss.as.connector.logging.ConnectorLogger.SUBSYSTEM_RA_LOGGER;

/**
 * A binding description for AdministeredObjectDefinition annotations.
 * <p/>
 * The referenced admin object must be directly visible to the
 * component declaring the annotation.
 *
 * @author Jesper Pedersen
 */
public class AdministeredObjectDefinitionInjectionSource extends ResourceDefinitionInjectionSource {

    public static final String DESCRIPTION = "description";
    public static final String INTERFACE = "interfaceName";
    public static final String PROPERTIES = "properties";

    private final String className;
    private final String resourceAdapter;

    private String description;
    private String interfaceName;

    public AdministeredObjectDefinitionInjectionSource(final String jndiName, final String className, final String resourceAdapter) {
        super(jndiName);
        this.className = className;
        this.resourceAdapter = resourceAdapter;
    }

    public void addProperty(String key, String value) {
        properties.put(key, value);
    }

    public void getResourceValue(final ResolutionContext context, final ServiceBuilder<?> serviceBuilder, final DeploymentPhaseContext phaseContext, final Injector<ManagedReferenceFactory> injector) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        String raId = resourceAdapter;

        if (resourceAdapter.startsWith("#")) {
            raId = deploymentUnit.getParent().getName() + raId;
        }
        String deployerServiceName = raId;
        if (!raId.endsWith(".rar")) {
            deployerServiceName = deployerServiceName + ".rar";
            raId = deployerServiceName;
        }
        SUBSYSTEM_RA_LOGGER.debugf("@AdministeredObjectDefinition: %s for %s binding to %s ", className, resourceAdapter, jndiName);

        ContextNames.BindInfo bindInfo = ContextNames.bindInfoForEnvEntry(context.getApplicationName(), context.getModuleName(), context.getComponentName(), !context.isCompUsesModule(), jndiName);

        DirectAdminObjectActivatorService service = new DirectAdminObjectActivatorService(jndiName, className, resourceAdapter,
                raId, properties, module, bindInfo);
        ServiceName serviceName = DirectAdminObjectActivatorService.SERVICE_NAME_BASE.append(jndiName);
        final ServiceBuilder sb = phaseContext.getServiceTarget().addService(serviceName, service);
        sb.addDependency(ConnectorServices.IRONJACAMAR_MDR, AS7MetadataRepository.class, service.getMdrInjector());
        sb.requires(ConnectorServices.RESOURCE_ADAPTER_DEPLOYER_SERVICE_PREFIX.append(deployerServiceName));
        sb.setInitialMode(ServiceController.Mode.ACTIVE).install();

        serviceBuilder.addDependency(AdminObjectReferenceFactoryService.SERVICE_NAME_BASE.append(bindInfo.getBinderServiceName()), ManagedReferenceFactory.class, injector);
        serviceBuilder.addListener(new LifecycleListener() {
            private volatile boolean bound;
            public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                switch (event) {
                    case UP: {
                        DEPLOYMENT_CONNECTOR_LOGGER.adminObjectAnnotation(jndiName);
                        bound = true;
                        break;
                    }
                    case DOWN: {
                        if (bound) {
                            DEPLOYMENT_CONNECTOR_LOGGER.unboundJca("AdminObject", jndiName);
                        }
                        break;
                    }
                    case REMOVED: {
                        DEPLOYMENT_CONNECTOR_LOGGER.debugf("Removed JCA AdminObject [%s]", jndiName);
                    }
                }
            }
        });

    }


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInterface() {
        return interfaceName;
    }

    public void setInterface(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AdministeredObjectDefinitionInjectionSource that = (AdministeredObjectDefinitionInjectionSource) o;

        if (className != null ? !className.equals(that.className) : that.className != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (interfaceName != null ? !interfaceName.equals(that.interfaceName) : that.interfaceName != null)
            return false;
        if (resourceAdapter != null ? !resourceAdapter.equals(that.resourceAdapter) : that.resourceAdapter != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (className != null ? className.hashCode() : 0);
        result = 31 * result + (resourceAdapter != null ? resourceAdapter.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (interfaceName != null ? interfaceName.hashCode() : 0);
        return result;
    }
}
