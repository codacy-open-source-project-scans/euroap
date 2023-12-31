/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import static org.jboss.as.controller.client.helpers.MeasurementUnit.MILLISECONDS;
import static org.jboss.as.controller.registry.AttributeAccess.Flag.COUNTER_METRIC;

import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.wildfly.extension.undertow.deployment.UndertowDeploymentService;
import org.wildfly.extension.undertow.deployment.UndertowMetricsCollector;

/**
 * @author Tomaz Cerar
 * @created 23.2.12 18:35
 */
public class DeploymentServletDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition SERVLET_NAME = new SimpleAttributeDefinitionBuilder("servlet-name", ModelType.STRING, false).setStorageRuntime().build();
    static final SimpleAttributeDefinition SERVLET_CLASS = new SimpleAttributeDefinitionBuilder("servlet-class", ModelType.STRING, false).setStorageRuntime().build();
    static final SimpleAttributeDefinition MAX_REQUEST_TIME = new SimpleAttributeDefinitionBuilder("max-request-time", ModelType.LONG)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .setMeasurementUnit(MILLISECONDS)
            .setStorageRuntime()
            .build();
    static final SimpleAttributeDefinition MIN_REQUEST_TIME = new SimpleAttributeDefinitionBuilder("min-request-time", ModelType.LONG)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .setMeasurementUnit(MILLISECONDS)
            .setStorageRuntime()
            .build();
    static final SimpleAttributeDefinition TOTAL_REQUEST_TIME = new SimpleAttributeDefinitionBuilder("total-request-time", ModelType.LONG)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .setMeasurementUnit(MILLISECONDS)
            .setFlags(COUNTER_METRIC)
            .setStorageRuntime()
            .build();
    static final SimpleAttributeDefinition REQUEST_COUNT = new SimpleAttributeDefinitionBuilder("request-count", ModelType.LONG)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .setFlags(COUNTER_METRIC)
            .setStorageRuntime()
            .build();
    static final SimpleListAttributeDefinition SERVLET_MAPPINGS = new SimpleListAttributeDefinition.Builder("mappings", new SimpleAttributeDefinitionBuilder("mapping", ModelType.STRING).setRequired(false).build())
            .setRequired(false)
            .setStorageRuntime()
            .build();


    DeploymentServletDefinition() {
        super(PathElement.pathElement("servlet"), UndertowExtension.getResolver("deployment.servlet"));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        registration.registerReadOnlyAttribute(SERVLET_NAME, null);
        registration.registerReadOnlyAttribute(SERVLET_CLASS, null);
        registration.registerMetric(MAX_REQUEST_TIME, new AbstractMetricsHandler() {
            @Override
            void handle(final ModelNode response, final MetricsHandler.MetricResult metricResult) {
                response.set((long) metricResult.getMaxRequestTime());
            }
        });
        registration.registerMetric(MIN_REQUEST_TIME, new AbstractMetricsHandler() {
            @Override
            void handle(final ModelNode response, final MetricsHandler.MetricResult metricResult) {
                response.set((long) metricResult.getMinRequestTime());
            }
        });
        registration.registerMetric(TOTAL_REQUEST_TIME, new AbstractMetricsHandler() {
            @Override
            void handle(final ModelNode response, final MetricsHandler.MetricResult metricResult) {
                response.set(metricResult.getTotalRequestTime());
            }
        });
        registration.registerMetric(REQUEST_COUNT, new AbstractMetricsHandler() {
            @Override
            void handle(final ModelNode response, final MetricsHandler.MetricResult metricResult) {
                response.set(metricResult.getTotalRequests());
            }
        });
        registration.registerReadOnlyAttribute(SERVLET_MAPPINGS, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));

                final Resource web = context.readResourceFromRoot(address.subAddress(0, address.size() - 1), false);
                final ModelNode subModel = web.getModel();

                final String host = DeploymentDefinition.VIRTUAL_HOST.resolveModelAttribute(context, subModel).asString();
                final String path = DeploymentDefinition.CONTEXT_ROOT.resolveModelAttribute(context, subModel).asString();
                final String server = DeploymentDefinition.SERVER.resolveModelAttribute(context, subModel).asString();

                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(final OperationContext context, final ModelNode operation) {
                        final ServiceController<?> deploymentServiceController = context.getServiceRegistry(false).getService(UndertowService.deploymentServiceName(server, host, path));
                        if (deploymentServiceController == null || deploymentServiceController.getState() != ServiceController.State.UP) {
                            return;
                        }
                        final UndertowDeploymentService deploymentService = (UndertowDeploymentService) deploymentServiceController.getService();
                        final DeploymentInfo deploymentInfo = deploymentService.getDeploymentInfo();
                        final ServletInfo servlet = deploymentInfo.getServlets().get(context.getCurrentAddressValue());

                        ModelNode response = new ModelNode();
                        response.setEmptyList();
                        for (String mapping : servlet.getMappings()) {
                            response.add(mapping);
                        }
                    }
                }, OperationContext.Stage.RUNTIME);
            }
        });
    }

    abstract static class AbstractMetricsHandler implements OperationStepHandler {

        abstract void handle(ModelNode response, MetricsHandler.MetricResult metricResult);

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));

            final Resource web = context.readResourceFromRoot(address.subAddress(0, address.size() - 1), false);
            final ModelNode subModel = web.getModel();

            final String host = DeploymentDefinition.VIRTUAL_HOST.resolveModelAttribute(context, subModel).asString();
            final String path = DeploymentDefinition.CONTEXT_ROOT.resolveModelAttribute(context, subModel).asString();
            final String server = DeploymentDefinition.SERVER.resolveModelAttribute(context, subModel).asString();

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) {
                    final ServiceController<?> deploymentServiceController = context.getServiceRegistry(false).getService(UndertowService.deploymentServiceName(server, host, path));
                    if (deploymentServiceController == null || deploymentServiceController.getState() != ServiceController.State.UP) {
                        return;
                    }
                    final UndertowDeploymentService deploymentService = (UndertowDeploymentService) deploymentServiceController.getService();
                    final DeploymentInfo deploymentInfo = deploymentService.getDeploymentInfo();
                    final UndertowMetricsCollector collector = (UndertowMetricsCollector)deploymentInfo.getMetricsCollector();

                    MetricsHandler.MetricResult result = collector != null ? collector.getMetrics(context.getCurrentAddressValue()) : null;
                    if (result != null) {
                        final ModelNode response = new ModelNode();
                        handle(response, result);
                        context.getResult().set(response);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

}
