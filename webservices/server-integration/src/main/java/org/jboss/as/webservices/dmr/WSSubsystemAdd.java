/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.webservices.dmr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.webservices.dmr.Constants.WSDL_HOST;
import static org.jboss.as.webservices.dmr.Constants.WSDL_PATH_REWRITE_RULE;
import static org.jboss.as.webservices.dmr.Constants.WSDL_PORT;
import static org.jboss.as.webservices.dmr.Constants.WSDL_SECURE_PORT;
import static org.jboss.as.webservices.dmr.Constants.WSDL_URI_SCHEME;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.jmx.JMXExtension;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.web.host.CommonWebServer;
import org.jboss.as.webservices.config.ServerConfigFactoryImpl;
import org.jboss.as.webservices.config.ServerConfigImpl;
import org.jboss.as.webservices.logging.WSLogger;
import org.jboss.as.webservices.service.ServerConfigService;
import org.jboss.as.webservices.service.XTSClientIntegrationService;
import org.jboss.as.webservices.util.ModuleClassLoaderProvider;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * @author <a href="mailto:alessio.soldano@jboss.com">Alessio Soldano</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class WSSubsystemAdd extends AbstractBoottimeAddStepHandler {
    static final WSSubsystemAdd INSTANCE = new WSSubsystemAdd();

    @Override
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        Attributes.STATISTICS_ENABLED.validateAndSet(operation, model);
        for (AttributeDefinition attr : Attributes.SUBSYSTEM_ATTRIBUTES) {
            attr.validateAndSet(operation, model);
        }
    }

    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        WSLogger.ROOT_LOGGER.activatingWebservicesExtension();
        ModuleClassLoaderProvider.register();
        final boolean appclient = context.getProcessType() == ProcessType.APPLICATION_CLIENT;

        context.addStep(new AbstractDeploymentChainStep() {
            protected void execute(DeploymentProcessorTarget processorTarget) {
                // add the DUP for dealing with WS deployments
                WSDeploymentActivator.activate(processorTarget, appclient);
            }
        }, OperationContext.Stage.RUNTIME);

        ServiceTarget serviceTarget = context.getServiceTarget();
        final boolean jmxAvailable = isJMXSubsystemAvailable(context);
        if (appclient && model.hasDefined(WSDL_HOST)) {
            ServerConfigImpl serverConfig = createServerConfig(model, true, context);
            ServerConfigFactoryImpl.setConfig(serverConfig);
            ServerConfigService.install(serviceTarget, serverConfig, getServerConfigDependencies(context, appclient), jmxAvailable, false);
        }
        if (!appclient) {
            ServerConfigImpl serverConfig = createServerConfig(model, false, context);
            ServerConfigFactoryImpl.setConfig(serverConfig);
            ServerConfigService.install(serviceTarget, serverConfig, getServerConfigDependencies(context, appclient), jmxAvailable, true);
        }
        XTSClientIntegrationService.install(serviceTarget);
    }

    private static ServerConfigImpl createServerConfig(ModelNode configuration, boolean appclient, OperationContext context) throws OperationFailedException {
        final ServerConfigImpl config = ServerConfigImpl.newInstance();
        try {
            ModelNode wsdlHost = Attributes.WSDL_HOST.resolveModelAttribute(context, configuration);
            config.setWebServiceHost(wsdlHost.isDefined() ? wsdlHost.asString() : null);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        if (!appclient) {
            config.setModifySOAPAddress(Attributes.MODIFY_WSDL_ADDRESS.resolveModelAttribute(context, configuration).asBoolean());
            config.setStatisticsEnabled(Attributes.STATISTICS_ENABLED.resolveModelAttribute(context, configuration).asBoolean());
        }
        if (configuration.hasDefined(WSDL_PORT)) {
            config.setWebServicePort(Attributes.WSDL_PORT.resolveModelAttribute(context, configuration).asInt());
        }
        if (configuration.hasDefined(WSDL_SECURE_PORT)) {
            config.setWebServiceSecurePort(Attributes.WSDL_SECURE_PORT.resolveModelAttribute(context, configuration).asInt());
        }
        if (configuration.hasDefined(WSDL_URI_SCHEME)) {
            config.setWebServiceUriScheme(Attributes.WSDL_URI_SCHEME.resolveModelAttribute(context, configuration).asString());
        }
        if (configuration.hasDefined(WSDL_PATH_REWRITE_RULE)) {
            config.setWebServicePathRewriteRule(Attributes.WSDL_PATH_REWRITE_RULE.resolveModelAttribute(context, configuration).asString());
        }
        return config;
    }

    /**
     * Process the model to figure out the name of the services the server config service has to depend on
     *
     */
    private static List<ServiceName> getServerConfigDependencies(OperationContext context, boolean appclient) {
        final List<ServiceName> serviceNames = new ArrayList<ServiceName>();
        final Resource subsystemResource = context.readResourceFromRoot(PathAddress.pathAddress(WSExtension.SUBSYSTEM_PATH), false);
        readConfigServiceNames(serviceNames, subsystemResource, Constants.CLIENT_CONFIG);
        readConfigServiceNames(serviceNames, subsystemResource, Constants.ENDPOINT_CONFIG);
        if (!appclient) {
            serviceNames.add(CommonWebServer.SERVICE_NAME);
        }
        return serviceNames;
    }

    private static void readConfigServiceNames(List<ServiceName> serviceNames, Resource subsystemResource, String configType) {
        for (String name : subsystemResource.getChildrenNames(configType)) {
            ServiceName configServiceName = Constants.CLIENT_CONFIG.equals(configType) ? PackageUtils
                    .getClientConfigServiceName(name) : PackageUtils.getEndpointConfigServiceName(name);
            serviceNames.add(configServiceName);
        }
    }

    private static boolean isJMXSubsystemAvailable(final OperationContext context) {
        Resource root = context.readResourceFromRoot(PathAddress.pathAddress(PathAddress.EMPTY_ADDRESS), false);
        return root.hasChild(PathElement.pathElement(SUBSYSTEM, JMXExtension.SUBSYSTEM_NAME));
    }
}
