/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.microprofile.health;

import static org.jboss.as.controller.OperationContext.Stage.RUNTIME;
import static org.jboss.as.server.deployment.Phase.DEPENDENCIES;
import static org.jboss.as.server.deployment.Phase.DEPENDENCIES_MICROPROFILE_HEALTH;
import static org.jboss.as.server.deployment.Phase.POST_MODULE;
import static org.jboss.as.server.deployment.Phase.POST_MODULE_MICROPROFILE_HEALTH;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.microprofile.health._private.MicroProfileHealthLogger;
import org.wildfly.extension.microprofile.health.deployment.DependencyProcessor;
import org.wildfly.extension.microprofile.health.deployment.DeploymentProcessor;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2018 Red Hat inc.
 */
class MicroProfileHealthSubsystemAdd extends AbstractBoottimeAddStepHandler {

    static MicroProfileHealthSubsystemAdd INSTANCE = new MicroProfileHealthSubsystemAdd();

    private MicroProfileHealthSubsystemAdd() {
        super(MicroProfileHealthSubsystemDefinition.ATTRIBUTES);
    }

    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.performBoottime(context, operation, model);

        context.addStep(new AbstractDeploymentChainStep() {
            public void execute(DeploymentProcessorTarget processorTarget) {
                processorTarget.addDeploymentProcessor(MicroProfileHealthExtension.SUBSYSTEM_NAME, DEPENDENCIES, DEPENDENCIES_MICROPROFILE_HEALTH, new DependencyProcessor());
                processorTarget.addDeploymentProcessor(MicroProfileHealthExtension.SUBSYSTEM_NAME, POST_MODULE, POST_MODULE_MICROPROFILE_HEALTH, new DeploymentProcessor());
            }
        }, RUNTIME);

        final boolean securityEnabled = MicroProfileHealthSubsystemDefinition.SECURITY_ENABLED.resolveModelAttribute(context, model).asBoolean();
        final String emptyLivenessChecksStatus = MicroProfileHealthSubsystemDefinition.EMPTY_LIVENESS_CHECKS_STATUS.resolveModelAttribute(context, model).asString();
        final String emptyReadinessChecksStatus = MicroProfileHealthSubsystemDefinition.EMPTY_READINESS_CHECKS_STATUS.resolveModelAttribute(context, model).asString();
        final String emptyStartupChecksStatus = MicroProfileHealthSubsystemDefinition.EMPTY_STARTUP_CHECKS_STATUS.resolveModelAttribute(context, model).asString();

        HealthHTTPSecurityService.install(context, securityEnabled);
        MicroProfileHealthReporterService.install(context, emptyLivenessChecksStatus, emptyReadinessChecksStatus, emptyStartupChecksStatus);
        MicroProfileHealthContextService.install(context);

        MicroProfileHealthLogger.LOGGER.activatingSubsystem();
    }
}
