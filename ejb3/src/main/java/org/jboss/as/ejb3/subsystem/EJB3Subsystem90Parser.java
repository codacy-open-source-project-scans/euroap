/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.subsystem;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.ejb3.subsystem.EJB3SubsystemModel.APPLICATION_SECURITY_DOMAIN;

import java.util.Collections;
import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser for ejb3:9.0 namespace.
 */
public class EJB3Subsystem90Parser extends EJB3Subsystem80Parser {

    EJB3Subsystem90Parser() {
    }

    @Override
    protected EJB3SubsystemNamespace getExpectedNamespace() {
        return EJB3SubsystemNamespace.EJB3_9_0;
    }

    @Override
    protected void parseApplicationSecurityDomain(final XMLExtendedStreamReader reader, final List<ModelNode> operations) throws XMLStreamException {
        String applicationSecurityDomain = null;
        ModelNode operation = Util.createAddOperation();
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String attributeValue = reader.getAttributeValue(i);
            final EJB3SubsystemXMLAttribute attribute = EJB3SubsystemXMLAttribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME:
                    applicationSecurityDomain = attributeValue;
                    break;
                case SECURITY_DOMAIN:
                    ApplicationSecurityDomainDefinition.SECURITY_DOMAIN.parseAndSetParameter(attributeValue, operation, reader);
                    break;
                case ENABLE_JACC:
                    ApplicationSecurityDomainDefinition.ENABLE_JACC.parseAndSetParameter(attributeValue, operation, reader);
                    break;
                case LEGACY_COMPLIANT_PRINCIPAL_PROPAGATION:
                    ApplicationSecurityDomainDefinition.LEGACY_COMPLIANT_PRINCIPAL_PROPAGATION.parseAndSetParameter(attributeValue, operation, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (applicationSecurityDomain == null) {
            throw missingRequired(reader, Collections.singleton(EJB3SubsystemXMLAttribute.NAME.getLocalName()));
        }
        requireNoContent(reader);
        final PathAddress address = this.getEJB3SubsystemAddress().append(PathElement.pathElement(APPLICATION_SECURITY_DOMAIN, applicationSecurityDomain));
        operation.get(OP_ADDR).set(address.toModelNode());
        operations.add(operation);
    }
}
