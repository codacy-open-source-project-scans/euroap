/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.common.pool;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import java.util.Objects;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.jca.common.api.metadata.Defaults;
import org.jboss.jca.common.api.metadata.common.FlushStrategy;
import org.jboss.jca.common.api.metadata.common.Pool;
import org.jboss.jca.common.api.metadata.ds.TimeOut;
import org.jboss.jca.common.api.metadata.ds.Validation;

/**
 * @author @author <a href="mailto:stefano.maestri@redhat.com">Stefano
 *         Maestri</a>
 */
public class Constants {

    private static final String MIN_POOL_SIZE_NAME = "min-pool-size";

    private static final String INITIAL_POOL_SIZE_NAME = "initial-pool-size";

    private static final String MAX_POOL_SIZE_NAME = "max-pool-size";

    private static final String POOL_PREFILL_NAME = "pool-prefill";

    private static final String POOL_FAIR_NAME = "pool-fair";

    private static final String POOL_USE_STRICT_MIN_NAME = "pool-use-strict-min";

    private static final String BACKGROUNDVALIDATIONMILLIS_NAME = "background-validation-millis";

    private static final String BACKGROUNDVALIDATION_NAME = "background-validation";

    private static final String USE_FAST_FAIL_NAME = "use-fast-fail";

    private static final String VALIDATEONMATCH_NAME = "validate-on-match";

    private static final String BLOCKING_TIMEOUT_WAIT_MILLIS_NAME = "blocking-timeout-wait-millis";

    private static final String IDLETIMEOUTMINUTES_NAME = "idle-timeout-minutes";

    private static final String FLUSH_STRATEGY_NAME = "flush-strategy";

    private static final String CAPACITY_INCREMENTER_CLASS_NAME = "capacity-incrementer-class";

    private static final String CAPACITY_INCREMENTER_PROPERTIES_NAME = "capacity-incrementer-properties";

    private static final String CAPACITY_DECREMENTER_CLASS_NAME = "capacity-decrementer-class";

    private static final String CAPACITY_DECREMENTER_PROPERTIES_NAME = "capacity-decrementer-properties";


    public static final SimpleAttributeDefinition BLOCKING_TIMEOUT_WAIT_MILLIS = new SimpleAttributeDefinitionBuilder(BLOCKING_TIMEOUT_WAIT_MILLIS_NAME, ModelType.LONG, true)
            .setXmlName(TimeOut.Tag.BLOCKING_TIMEOUT_MILLIS.getLocalName())
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setAllowExpression(true)
            .build();


    public static final SimpleAttributeDefinition IDLETIMEOUTMINUTES = new SimpleAttributeDefinitionBuilder(IDLETIMEOUTMINUTES_NAME, ModelType.LONG, true)
            .setXmlName(TimeOut.Tag.IDLE_TIMEOUT_MINUTES.getLocalName())
            .setMeasurementUnit(MeasurementUnit.MINUTES)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, true, true))
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition BACKGROUNDVALIDATIONMILLIS = new SimpleAttributeDefinitionBuilder(BACKGROUNDVALIDATIONMILLIS_NAME, ModelType.LONG, true)
            .setXmlName(Validation.Tag.BACKGROUND_VALIDATION_MILLIS.getLocalName())
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setValidator(new IntRangeValidator(1, true, true))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition BACKGROUNDVALIDATION = new SimpleAttributeDefinitionBuilder(BACKGROUNDVALIDATION_NAME, ModelType.BOOLEAN, true)
            .setXmlName(Validation.Tag.BACKGROUND_VALIDATION.getLocalName())
            .setAllowExpression(true)
            //.setDefaultValue(new ModelNode(Defaults.BACKGROUND_VALIDATION))
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition USE_FAST_FAIL = new SimpleAttributeDefinitionBuilder(USE_FAST_FAIL_NAME, ModelType.BOOLEAN, true)
            .setXmlName(Validation.Tag.USE_FAST_FAIL.getLocalName())
            .setDefaultValue(new ModelNode(Defaults.USE_FAST_FAIL))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition VALIDATE_ON_MATCH = new SimpleAttributeDefinitionBuilder(VALIDATEONMATCH_NAME, ModelType.BOOLEAN, true)
            .setXmlName(Validation.Tag.VALIDATE_ON_MATCH.getLocalName())
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition MAX_POOL_SIZE = new SimpleAttributeDefinitionBuilder(MAX_POOL_SIZE_NAME, ModelType.INT, true)
            .setXmlName(Pool.Tag.MAX_POOL_SIZE.getLocalName())
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(Defaults.MAX_POOL_SIZE))
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition MIN_POOL_SIZE = new SimpleAttributeDefinitionBuilder(MIN_POOL_SIZE_NAME, ModelType.INT, true)
            .setXmlName(Pool.Tag.MIN_POOL_SIZE.getLocalName())
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(Defaults.MIN_POOL_SIZE))
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition INITIAL_POOL_SIZE = new SimpleAttributeDefinitionBuilder(INITIAL_POOL_SIZE_NAME, ModelType.INT)
            .setXmlName(Pool.Tag.INITIAL_POOL_SIZE.getLocalName())
            .setAllowExpression(true)
            .setRequired(false)
            .build();

    public static final SimpleAttributeDefinition CAPACITY_INCREMENTER_CLASS = new SimpleAttributeDefinitionBuilder(CAPACITY_INCREMENTER_CLASS_NAME, ModelType.STRING, true)
            .setXmlName(org.jboss.jca.common.api.metadata.common.Extension.Attribute.CLASS_NAME.getLocalName())
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final PropertiesAttributeDefinition CAPACITY_INCREMENTER_PROPERTIES = new PropertiesAttributeDefinition.Builder(CAPACITY_INCREMENTER_PROPERTIES_NAME, true)
            .setXmlName(org.jboss.jca.common.api.metadata.common.Extension.Tag.CONFIG_PROPERTY.getLocalName())
            .setRequired(false)
            .setAllowExpression(true)
            .setAttributeMarshaller(new AttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    for (ModelNode property : resourceModel.get(attribute.getName()).asList()) {
                        writer.writeStartElement(attribute.getXmlName());
                        writer.writeAttribute(org.jboss.as.controller.parsing.Attribute.NAME.getLocalName(), property.asProperty().getName());
                        writer.writeCharacters(property.asProperty().getValue().asString());
                        writer.writeEndElement();
                    }
                }

            })
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition CAPACITY_DECREMENTER_CLASS = new SimpleAttributeDefinitionBuilder(CAPACITY_DECREMENTER_CLASS_NAME, ModelType.STRING, true)
            .setXmlName(org.jboss.jca.common.api.metadata.common.Extension.Attribute.CLASS_NAME.getLocalName())
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final PropertiesAttributeDefinition CAPACITY_DECREMENTER_PROPERTIES = new PropertiesAttributeDefinition.Builder(CAPACITY_DECREMENTER_PROPERTIES_NAME, true)
            .setXmlName(org.jboss.jca.common.api.metadata.common.Extension.Tag.CONFIG_PROPERTY.getLocalName())
            .setRequired(false)
            .setAllowExpression(true)
            .setAttributeMarshaller(new AttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    for (ModelNode property : resourceModel.get(attribute.getName()).asList()) {
                        writer.writeStartElement(attribute.getXmlName());
                        writer.writeAttribute(org.jboss.as.controller.parsing.Attribute.NAME.getLocalName(), property.asProperty().getName());
                        writer.writeCharacters(property.asProperty().getValue().asString());
                        writer.writeEndElement();
                    }
                }

            })
            .setRestartAllServices()
            .build();


    public static final SimpleAttributeDefinition POOL_PREFILL = new SimpleAttributeDefinitionBuilder(POOL_PREFILL_NAME, ModelType.BOOLEAN, true)
            .setDefaultValue(new ModelNode(Defaults.PREFILL))
            .setAllowExpression(true)
            .setXmlName(Pool.Tag.PREFILL.getLocalName())
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition POOL_FAIR = new SimpleAttributeDefinitionBuilder(POOL_FAIR_NAME, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setXmlName(Pool.Tag.FAIR.getLocalName())
            .build();

    public static final SimpleAttributeDefinition POOL_USE_STRICT_MIN = new SimpleAttributeDefinitionBuilder(POOL_USE_STRICT_MIN_NAME, ModelType.BOOLEAN, true)
            .setDefaultValue(new ModelNode(Defaults.USE_STRICT_MIN))
            .setAllowExpression(true)
            .setXmlName(Pool.Tag.USE_STRICT_MIN.getLocalName())
            .build();

    public static final SimpleAttributeDefinition POOL_FLUSH_STRATEGY = new SimpleAttributeDefinitionBuilder(FLUSH_STRATEGY_NAME, ModelType.STRING)
            .setDefaultValue(new ModelNode(Defaults.FLUSH_STRATEGY.getName()))
            .setXmlName(Pool.Tag.FLUSH_STRATEGY.getLocalName())
            .setRequired(false)
            .setAllowExpression(true)
            .setAllowedValues(Stream.of(FlushStrategy.values()).map(FlushStrategy::toString).filter(Objects::nonNull).toArray(String[]::new))
            .setValidator(EnumValidator.create(FlushStrategy.class))
            .setRestartAllServices()
            .build();


    public static final AttributeDefinition[] POOL_ATTRIBUTES = {BLOCKING_TIMEOUT_WAIT_MILLIS, IDLETIMEOUTMINUTES, BACKGROUNDVALIDATIONMILLIS,
            BACKGROUNDVALIDATION, USE_FAST_FAIL, VALIDATE_ON_MATCH, MAX_POOL_SIZE, MIN_POOL_SIZE, INITIAL_POOL_SIZE, POOL_PREFILL, POOL_FAIR, POOL_USE_STRICT_MIN, POOL_FLUSH_STRATEGY,
            CAPACITY_INCREMENTER_CLASS, CAPACITY_DECREMENTER_CLASS, CAPACITY_INCREMENTER_PROPERTIES, CAPACITY_DECREMENTER_PROPERTIES};

    public static final SimpleAttributeDefinition POOL_STATISTICS_ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.STATISTICS_ENABLED, ModelType.BOOLEAN)
            .setStorageRuntime()
            .build();
}
