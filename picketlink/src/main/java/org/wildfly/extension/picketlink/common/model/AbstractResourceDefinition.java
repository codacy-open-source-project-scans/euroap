/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.picketlink.common.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Silva</a>
 * @since Mar 18, 2012
 */
public abstract class AbstractResourceDefinition extends SimpleResourceDefinition {

    private static final Map<ModelElement, List<SimpleAttributeDefinition>> attributeDefinitions;
    private static final Map<ModelElement, List<ResourceDefinition>> childResourceDefinitions;

    static {
        attributeDefinitions = new HashMap<>();
        childResourceDefinitions = new HashMap<>();
    }

    private final ModelElement modelElement;
    private final List<SimpleAttributeDefinition> attributes;

    protected AbstractResourceDefinition(ModelElement modelElement, final OperationStepHandler addHandler,
                                            ResourceDescriptionResolver resourceDescriptor, SimpleAttributeDefinition... attributes) {
        this(modelElement, PathElement.pathElement(modelElement.getName()), resourceDescriptor, addHandler, ModelOnlyRemoveStepHandler.INSTANCE, attributes);
    }

    protected AbstractResourceDefinition(ModelElement modelElement, final OperationStepHandler addHandler,
                                         final OperationStepHandler removeHandler,
                                         ResourceDescriptionResolver resourceDescriptor, SimpleAttributeDefinition... attributes) {
        this(modelElement, PathElement.pathElement(modelElement.getName()), resourceDescriptor, addHandler, removeHandler, attributes);
    }

    protected AbstractResourceDefinition(ModelElement modelElement, String name, final ModelOnlyAddStepHandler addHandler,ResourceDescriptionResolver resourceDescriptor, SimpleAttributeDefinition... attributes) {
        this(modelElement, PathElement.pathElement(modelElement.getName(), name), resourceDescriptor, addHandler, ModelOnlyRemoveStepHandler.INSTANCE, attributes);
    }

    private AbstractResourceDefinition(ModelElement modelElement, PathElement pathElement,ResourceDescriptionResolver resourceDescriptor,
                                         final OperationStepHandler addHandler, final OperationStepHandler removeHandler, SimpleAttributeDefinition... attributes) {
        super(new Parameters(pathElement, resourceDescriptor)
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
        );
        this.modelElement = modelElement;
        this.attributes = Collections.unmodifiableList(Arrays.asList(attributes));
    }

    public static List<SimpleAttributeDefinition> getAttributeDefinition(ModelElement modelElement) {
        List<SimpleAttributeDefinition> definitions = attributeDefinitions.get(modelElement);

        if (definitions == null) {
            return Collections.emptyList();
        }

        return definitions;
    }

    public static Map<ModelElement, List<ResourceDefinition>> getChildResourceDefinitions() {
        return Collections.unmodifiableMap(childResourceDefinitions);
    }

    private void addAttributeDefinition(ModelElement resourceDefinitionKey, SimpleAttributeDefinition attribute) {
        List<SimpleAttributeDefinition> resourceAttributes = attributeDefinitions.get(resourceDefinitionKey);

        if (resourceAttributes == null) {
            resourceAttributes = new ArrayList<>();
            attributeDefinitions.put(resourceDefinitionKey, resourceAttributes);
        }

        if (!resourceAttributes.contains(attribute)) {
            resourceAttributes.add(attribute);
        }
    }

    private void addChildResourceDefinition(ModelElement resourceDefinitionKey, ResourceDefinition resourceDefinition) {
        List<ResourceDefinition> childResources = childResourceDefinitions.get(resourceDefinitionKey);

        if (childResources == null) {
            childResources = new ArrayList<>();
            childResourceDefinitions.put(resourceDefinitionKey, childResources);
        }

        if (!childResources.contains(resourceDefinition)) {
            for (ResourceDefinition childResource : childResources) {
                if (childResource.getPathElement().getKey().equals(resourceDefinition.getPathElement().getKey())) {
                    return;
                }
            }

            childResources.add(resourceDefinition);
        }
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler writeAttributeHandler = createAttributeWriterHandler();
        for (SimpleAttributeDefinition attribute : getAttributes()) {
            addAttributeDefinition(attribute, writeAttributeHandler, resourceRegistration);
        }
    }

    public List<SimpleAttributeDefinition> getAttributes() {
        return attributes;
    }

    private void addAttributeDefinition(SimpleAttributeDefinition definition, OperationStepHandler writeHandler, ManagementResourceRegistration resourceRegistration) {
        addAttributeDefinition(this.modelElement, definition);
        resourceRegistration.registerReadWriteAttribute(definition, null, writeHandler);
    }

    protected void addChildResourceDefinition(AbstractResourceDefinition definition, ManagementResourceRegistration resourceRegistration) {
        addChildResourceDefinition(this.modelElement, definition);
        resourceRegistration.registerSubModel(definition);
    }

    protected OperationStepHandler createAttributeWriterHandler() {
        return new ModelOnlyWriteAttributeHandler(attributes.toArray(new AttributeDefinition[0]));
    }
}
