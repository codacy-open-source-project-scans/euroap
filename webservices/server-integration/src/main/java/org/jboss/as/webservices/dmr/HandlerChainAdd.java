/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.webservices.dmr;

import static org.jboss.as.webservices.dmr.Constants.HANDLER;
import static org.jboss.as.webservices.dmr.PackageUtils.getConfigServiceName;
import static org.jboss.as.webservices.dmr.PackageUtils.getHandlerChainServiceName;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.webservices.logging.WSLogger;
import org.jboss.as.webservices.service.HandlerChainService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.wsf.spi.metadata.j2ee.serviceref.UnifiedHandlerChainMetaData;
import org.jboss.wsf.spi.metadata.j2ee.serviceref.UnifiedHandlerMetaData;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:alessio.soldano@jboss.com">Alessio Soldano</a>
 */
final class HandlerChainAdd extends AbstractAddStepHandler {

    static final HandlerChainAdd INSTANCE = new HandlerChainAdd();

    private HandlerChainAdd() {
        // forbidden instantiation
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        super.rollbackRuntime(context, operation, resource);
        if (!context.isBooting()) {
            context.revertReloadRequired();
        }
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        //modify the runtime if we're booting, otherwise set reload required and leave the runtime unchanged
        if (context.isBooting()) {
            final String protocolBindings = Attributes.PROTOCOL_BINDINGS.resolveModelAttribute(context, model).asStringOrNull();
            final PathAddress address = context.getCurrentAddress();
            final PathElement confElem = address.getElement(address.size() - 2);
            final String configType = confElem.getKey();
            final String configName = confElem.getValue();
            final String handlerChainType = address.getElement(address.size() - 1).getKey();
            final String handlerChainId = address.getElement(address.size() - 1).getValue();

            final ServiceName configServiceName = getConfigServiceName(configType, configName);
            if (context.getServiceRegistry(false).getService(configServiceName) == null) {
                throw WSLogger.ROOT_LOGGER.missingConfig(configName);
            }

            final ServiceName handlerChainServiceName = getHandlerChainServiceName(configServiceName, handlerChainType, handlerChainId);
            final ServiceTarget target = context.getServiceTarget();
            final ServiceBuilder<?> handlerChainServiceBuilder = target.addService(handlerChainServiceName);
            final Consumer<UnifiedHandlerChainMetaData> handlerChainConsumer = handlerChainServiceBuilder.provides(handlerChainServiceName);
            final List<Supplier<UnifiedHandlerMetaData>> handlerSuppliers = new ArrayList<>();
            for (final ServiceName sn : PackageUtils.getServiceNameDependencies(context, handlerChainServiceName, address, HANDLER)) {
                handlerSuppliers.add(handlerChainServiceBuilder.requires(sn));
            }
            handlerChainServiceBuilder.setInstance(new HandlerChainService(handlerChainType, handlerChainId, protocolBindings, handlerChainConsumer, handlerSuppliers));
            handlerChainServiceBuilder.install();
        } else {
            context.reloadRequired();
        }
    }

    @Override
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        Attributes.PROTOCOL_BINDINGS.validateAndSet(operation, model);
    }
}
