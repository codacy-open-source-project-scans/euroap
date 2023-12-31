/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.subsystem.deployment;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.ee.component.BasicComponent;
import org.jboss.as.ejb3.component.EJBComponent;
import org.jboss.as.server.deployment.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public abstract class AbstractRuntimeMetricsHandler extends AbstractRuntimeOnlyHandler {
    private static ServiceName componentServiceName(final OperationContext context, final ModelNode operation) {
        final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        final String parent;
        final String module;
        int i = 2;
        if (address.getElement(1).getKey().equals(ModelDescriptionConstants.SUBDEPLOYMENT)) {
            parent = resolveRuntimeName(context,address.getElement(0));
            module = address.getElement(1).getValue();
            i++;
        } else {
            parent = null;
            module = resolveRuntimeName(context,address.getElement(0));
        }
        final String component = address.getElement(i).getValue();
        final ServiceName deploymentUnitServiceName;
        if (parent == null) {
            deploymentUnitServiceName = Services.deploymentUnitName(module);
        }
        else {
            deploymentUnitServiceName = Services.deploymentUnitName(parent, module);
        }
        // Hmm, don't like the START bit
        return BasicComponent.serviceNameOf(deploymentUnitServiceName, component).append("START");
    }

    protected abstract void executeReadMetricStep(final OperationContext context, final ModelNode operation, final EJBComponent component) throws OperationFailedException;

    @Override
    protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final ServiceName componentServiceName = componentServiceName(context,operation);
        final EJBComponent component = (EJBComponent) context.getServiceRegistry(false).getRequiredService(componentServiceName).getValue();
        executeReadMetricStep(context, operation, component);
    }

    /**
     * Resolves runtime name of model resource.
     * @param context - operation context in which handler is invoked
     * @param address - deployment address
     * @return runtime name of module. Value which is returned is never null.
     */
    protected static String resolveRuntimeName(final OperationContext context, final PathElement address){
        final ModelNode runtimeName = context.readResourceFromRoot(PathAddress.pathAddress(address),false).getModel()
                .get(ModelDescriptionConstants.RUNTIME_NAME);
            return runtimeName.asString();
    }
}
