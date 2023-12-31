/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.controller;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * A read-attribute operation handler that translates a value from another attribute
 * @author Paul Ferraro
 */
public class ReadAttributeTranslationHandler implements OperationStepHandler {

    private final AttributeTranslation translation;

    public ReadAttributeTranslationHandler(AttributeTranslation translation) {
        this.translation = translation;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        PathAddress currentAddress = context.getCurrentAddress();
        PathAddress targetAddress = this.translation.getPathAddressTransformation().apply(currentAddress);
        Attribute targetAttribute = this.translation.getTargetAttribute();
        ModelNode targetOperation = Util.getReadAttributeOperation(targetAddress, targetAttribute.getName());
        if (operation.hasDefined(ModelDescriptionConstants.INCLUDE_DEFAULTS)) {
            targetOperation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS));
        }
        ImmutableManagementResourceRegistration registration = (currentAddress == targetAddress) ? context.getResourceRegistration() : context.getRootResourceRegistration().getSubModel(targetAddress);
        if (registration == null) {
            throw new OperationFailedException(ControllerLogger.MGMT_OP_LOGGER.noSuchResourceType(targetAddress));
        }
        OperationStepHandler readAttributeHandler = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, targetAttribute.getName()).getReadHandler();
        OperationStepHandler readTranslatedAttributeHandler = new ReadTranslatedAttributeStepHandler(readAttributeHandler, targetAttribute, this.translation.getReadTranslator());
        // If targetOperation applies to the current resource, we can execute in the current step
        if (targetAddress == currentAddress) {
            readTranslatedAttributeHandler.execute(context, targetOperation);
        } else {
            if (registration.isRuntimeOnly()) {
                try {
                    context.readResourceFromRoot(targetAddress, false);
                } catch (Resource.NoSuchResourceException ignore) {
                    // If the target runtime resource does not exist return UNDEFINED
                    return;
                }
            }
            context.addStep(targetOperation, readTranslatedAttributeHandler, context.getCurrentStage(), true);
        }
    }

    private static class ReadTranslatedAttributeStepHandler implements OperationStepHandler {
        private final OperationStepHandler readHandler;
        private final Attribute targetAttribute;
        private final AttributeValueTranslator translator;

        ReadTranslatedAttributeStepHandler(OperationStepHandler readHandler, Attribute targetAttribute, AttributeValueTranslator translator) {
            this.readHandler = readHandler;
            this.targetAttribute = targetAttribute;
            this.translator = translator;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (this.readHandler != null) {
                this.readHandler.execute(context, operation);
            } else {
                try {
                    // If attribute has no read handler, simulate one
                    ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel();
                    ModelNode result = context.getResult();
                    if (model.hasDefined(this.targetAttribute.getName())) {
                        result.set(model.get(this.targetAttribute.getName()));
                    } else if (operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).asBoolean(true)) {
                        ModelNode defaultValue = this.targetAttribute.getDefinition().getDefaultValue();
                        if (defaultValue != null) {
                            result.set(defaultValue);
                        }
                    }
                } catch (Resource.NoSuchResourceException ignore) {
                    // If the target resource does not exist return UNDEFINED
                    return;
                }
            }
            ModelNode result = context.getResult();
            result.set(this.translator.translate(context, result));
        }
    }
}
