/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.controller;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.parsing.Element;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * {@link MapAttributeDefinition} for maps with keys and values of type {@link ModelType#STRING}.
 *
 * @author Paul Ferraro
 */
public class PropertiesAttributeDefinition extends MapAttributeDefinition {
    private final Consumer<ModelNode> descriptionContributor;

    PropertiesAttributeDefinition(Builder builder) {
        super(builder);
        this.descriptionContributor = node -> {
            node.get(ModelDescriptionConstants.VALUE_TYPE).set(ModelType.STRING);
            node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(new ModelNode(this.isAllowExpression()));
        };
    }

    @Override
    protected void addValueTypeDescription(ModelNode node, ResourceBundle bundle) {
        this.descriptionContributor.accept(node);
    }

    @Override
    protected void addAttributeValueTypeDescription(ModelNode node, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        this.descriptionContributor.accept(node);
    }

    @Override
    protected void addOperationParameterValueTypeDescription(ModelNode node, String operationName, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        this.descriptionContributor.accept(node);
    }

    static final AttributeMarshaller MARSHALLER = new AttributeMarshaller() {
        @Override
        public boolean isMarshallableAsElement() {
            return true;
        }

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode model, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            if (model.hasDefined(attribute.getName())) {
                for (Property property : model.get(attribute.getName()).asPropertyList()) {
                    writer.writeStartElement(Element.PROPERTY.getLocalName());
                    writer.writeAttribute(Element.NAME.getLocalName(), property.getName());
                    // TODO if WFCORE-4625 goes in, use the util method.
                    String content = property.getValue().asString();
                    if (content.indexOf('\n') > -1) {
                        // Multiline content. Use the overloaded variant that staxmapper will format
                        writer.writeCharacters(content);
                    } else {
                        // Staxmapper will just output the chars without adding newlines if this is used
                        char[] chars = content.toCharArray();
                        writer.writeCharacters(chars, 0, chars.length);
                    }
                    writer.writeEndElement();
                }
            }
        }
    };

    static final AttributeParser PARSER = new AttributeParser() {
        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            assert attribute instanceof MapAttributeDefinition;
            String name = reader.getAttributeValue(null, ModelDescriptionConstants.NAME);
            ((MapAttributeDefinition) attribute).parseAndAddParameterElement(name, reader.getElementText(), operation, reader);
        }
    };

    public static class Builder extends MapAttributeDefinition.Builder<Builder, PropertiesAttributeDefinition> {

        public Builder(String name) {
            super(name);
            setRequired(false);
            this.setAllowNullElement(false);
            setAttributeMarshaller(MARSHALLER);
            setAttributeParser(PARSER);
        }

        public Builder(PropertiesAttributeDefinition basis) {
            super(basis);
        }

        @Override
        public PropertiesAttributeDefinition build() {
            if (this.elementValidator == null) {
                this.elementValidator = new ModelTypeValidator(ModelType.STRING, this.isNillable(), this.isAllowExpression());
            }
            return new PropertiesAttributeDefinition(this);
        }
    }
}
