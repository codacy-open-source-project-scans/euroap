/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.txn.subsystem;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * The {@link org.jboss.staxmapper.XMLElementReader} that handles the version 3.0 of Transaction subsystem xml.
 */
class TransactionSubsystem30Parser extends TransactionSubsystem20Parser {

    TransactionSubsystem30Parser() {
        super(Namespace.TRANSACTIONS_3_0);
    }

    TransactionSubsystem30Parser(Namespace namespace) {
        super(namespace);
    }

    @Override
    protected void readElement(final XMLExtendedStreamReader reader, final Element element, final List<ModelNode> operations, final ModelNode subsystemOperation, final ModelNode logStoreOperation) throws XMLStreamException {
        switch (element) {
            case RECOVERY_ENVIRONMENT: {
                parseRecoveryEnvironmentElement(reader, subsystemOperation);
                break;
            }
            case CORE_ENVIRONMENT: {
                parseCoreEnvironmentElement(reader, subsystemOperation);
                break;
            }
            case COORDINATOR_ENVIRONMENT: {
                parseCoordinatorEnvironmentElement(reader, subsystemOperation);
                break;
            }
            case OBJECT_STORE: {
                parseObjectStoreEnvironmentElementAndEnrichOperation(reader, subsystemOperation);
                break;
            }
            case JTS: {
                parseJts(reader, subsystemOperation);
                break;
            }
            case USE_JOURNAL_STORE: {
                if (choiceObjectStoreEncountered) {
                    throw unexpectedElement(reader);
                }
                choiceObjectStoreEncountered = true;

                parseUseJournalstore(reader, logStoreOperation, subsystemOperation);
                subsystemOperation.get(CommonAttributes.USE_JOURNAL_STORE).set(true);
                break;
            }
            case JDBC_STORE: {
                if (choiceObjectStoreEncountered) {
                    throw unexpectedElement(reader);
                }
                choiceObjectStoreEncountered = true;

                parseJdbcStoreElementAndEnrichOperation(reader, logStoreOperation, subsystemOperation);
                subsystemOperation.get(CommonAttributes.USE_JDBC_STORE).set(true);
                break;
            }
            case CM_RESOURCES:
                parseCMs(reader, operations);
                break;
            default: {
                throw unexpectedElement(reader);
            }
        }
    }

    protected void parseCMs(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case CM_RESPOURCE:
                    parseCM(reader, operations);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseCM(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        final ModelNode address = new ModelNode();
        address.add(ModelDescriptionConstants.SUBSYSTEM, TransactionExtension.SUBSYSTEM_NAME);
        address.protect();

        final ModelNode cmrAddress = address.clone();
        final ModelNode cmrOperation = new ModelNode();
        cmrOperation.get(OP).set(ADD);

        String jndiName = null;
        for (Attribute attribute : Attribute.values()) {
            switch (attribute) {
                case JNDI_NAME: {
                    jndiName = rawAttributeText(reader, CMResourceResourceDefinition.JNDI_NAME.getXmlName(), null);
                    break;
                }
                default:
                    break;
            }
        }
        if (jndiName == null) {
            throw missingRequired(reader, CMResourceResourceDefinition.JNDI_NAME.getXmlName());
        }
        cmrAddress.add(CommonAttributes.CM_RESOURCE, jndiName);
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case END_ELEMENT: {
                    if (Element.forName(reader.getLocalName()) == Element.CM_RESPOURCE) {
                        cmrAddress.protect();
                        cmrOperation.get(OP_ADDR).set(cmrAddress);

                        operations.add(cmrOperation);
                        return;
                    } else {
                        if (Element.forName(reader.getLocalName()) == Element.UNKNOWN) {
                            throw unexpectedElement(reader);
                        }
                    }
                    break;
                }
                case START_ELEMENT: {
                    switch (Element.forName(reader.getLocalName())) {
                        case CM_TABLE: {
                            addAttribute(reader, cmrOperation, CMResourceResourceDefinition.CM_TABLE_NAME);
                            addAttribute(reader, cmrOperation, CMResourceResourceDefinition.CM_TABLE_BATCH_SIZE);
                            addAttribute(reader, cmrOperation, CMResourceResourceDefinition.CM_TABLE_IMMEDIATE_CLEANUP);
                            break;
                        }
                    }
                }

            }
        }
    }

    private void addAttribute(XMLExtendedStreamReader reader, ModelNode operation, SimpleAttributeDefinition attributeDefinition) throws XMLStreamException {
        String value = rawAttributeText(reader, attributeDefinition.getXmlName(), null);

        if (value != null) {
            attributeDefinition.parseAndSetParameter(value, operation, reader);
        }
    }

    /**
     * Reads and trims the text for the given attribute and returns it or {@code defaultValue} if there is no
     * value for the attribute
     *
     * @param reader        source for the attribute text
     * @param attributeName the name of the attribute
     * @param defaultValue  value to return if there is no value for the attribute
     * @return the string representing raw attribute text or {@code defaultValue} if there is none
     */
    private String rawAttributeText(XMLStreamReader reader, String attributeName, String defaultValue) {
        return reader.getAttributeValue("", attributeName) == null
                ? defaultValue :
                reader.getAttributeValue("", attributeName).trim();
    }
}
