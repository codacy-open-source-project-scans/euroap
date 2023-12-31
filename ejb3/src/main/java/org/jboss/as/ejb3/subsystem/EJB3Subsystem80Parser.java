/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.subsystem;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.ejb3.subsystem.EJB3SubsystemModel.REMOTE;
import static org.jboss.as.ejb3.subsystem.EJB3SubsystemModel.SERVICE;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser for ejb3:8.0 namespace.
 */
public class EJB3Subsystem80Parser extends EJB3Subsystem70Parser {

    EJB3Subsystem80Parser() {
    }

    @Override
    protected EJB3SubsystemNamespace getExpectedNamespace() {
        return EJB3SubsystemNamespace.EJB3_8_0;
    }

    protected void parseRemote(final XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        final PathAddress ejb3RemoteServiceAddress = SUBSYSTEM_PATH.append(SERVICE, REMOTE);
        ModelNode operation = Util.createAddOperation(ejb3RemoteServiceAddress);
        final EnumSet<EJB3SubsystemXMLAttribute> required = EnumSet.of(EJB3SubsystemXMLAttribute.CONNECTORS, EJB3SubsystemXMLAttribute.THREAD_POOL_NAME);

        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final EJB3SubsystemXMLAttribute attribute = EJB3SubsystemXMLAttribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case CLIENT_MAPPINGS_CLUSTER_NAME:
                    EJB3RemoteResourceDefinition.CLIENT_MAPPINGS_CLUSTER_NAME.parseAndSetParameter(value, operation, reader);
                    break;
                case CONNECTORS:
                    // can't use the obvious: EJB3RemoteResourceDefinition.CONNECTORS.parseAndSetParameter(value, operation, reader);
                    EJB3RemoteResourceDefinition.CONNECTORS.getParser().parseAndSetParameter(EJB3RemoteResourceDefinition.CONNECTORS, value, operation, reader);
                    break;
                case THREAD_POOL_NAME:
                    EJB3RemoteResourceDefinition.THREAD_POOL_NAME.parseAndSetParameter(value, operation, reader);
                    break;
                case EXECUTE_IN_WORKER:
                    EJB3RemoteResourceDefinition.EXECUTE_IN_WORKER.parseAndSetParameter(value, operation, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        // each profile adds it's own operation
        operations.add(operation);

        final Set<EJB3SubsystemXMLElement> parsedElements = new HashSet<EJB3SubsystemXMLElement>();
        while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
            EJB3SubsystemXMLElement element = EJB3SubsystemXMLElement.forName(reader.getLocalName());
            switch (element) {
                case CHANNEL_CREATION_OPTIONS: {
                    if (parsedElements.contains(EJB3SubsystemXMLElement.CHANNEL_CREATION_OPTIONS)) {
                        throw unexpectedElement(reader);
                    }
                    parsedElements.add(EJB3SubsystemXMLElement.CHANNEL_CREATION_OPTIONS);
                    this.parseChannelCreationOptions(reader, ejb3RemoteServiceAddress, operations);
                    break;
                }
                case PROFILES: {
                    parseProfiles(reader, operations);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    protected void parseProfile(final XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        String profileName = null;

        final EJBClientContext.Builder builder = new EJBClientContext.Builder();

        final ModelNode operation = Util.createAddOperation();

        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final EJB3SubsystemXMLAttribute attribute = EJB3SubsystemXMLAttribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME:
                    profileName = value;
                    break;
                case EXCLUDE_LOCAL_RECEIVER:
                    RemotingProfileResourceDefinition.EXCLUDE_LOCAL_RECEIVER.parseAndSetParameter(value, operation, reader);
                    break;
                case LOCAL_RECEIVER_PASS_BY_VALUE:
                    RemotingProfileResourceDefinition.LOCAL_RECEIVER_PASS_BY_VALUE.parseAndSetParameter(value, operation, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (profileName == null) {
            throw missingRequired(reader, Collections.singleton(EJB3SubsystemXMLAttribute.NAME.getLocalName()));
        }


        final PathAddress address = SUBSYSTEM_PATH.append(EJB3SubsystemModel.REMOTING_PROFILE, profileName);
        operation.get(OP_ADDR).set(address.toModelNode());
        operations.add(operation);

        while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
            switch (EJB3SubsystemXMLElement.forName(reader.getLocalName())) {
                case STATIC_EJB_DISCOVERY:
                    final ModelNode staticEjb = parseStaticEjbDiscoveryType(reader);
                    operation.get(StaticEJBDiscoveryDefinition.STATIC_EJB_DISCOVERY).set(staticEjb);
                    break;
                case REMOTING_EJB_RECEIVER: {
                    parseRemotingReceiver(reader, address, operations);
                    break;
                }
                case REMOTE_HTTP_CONNECTION:
                    parseRemoteHttpConnection(reader, address, operations);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    protected void parseRemoteHttpConnection(final XMLExtendedStreamReader reader, final PathAddress profileAddress,
                                             final List<ModelNode> operations) throws XMLStreamException {

        final ModelNode operation = Util.createAddOperation();

        String name = null;
        final Set<EJB3SubsystemXMLAttribute> required = EnumSet.of(EJB3SubsystemXMLAttribute.URI);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final EJB3SubsystemXMLAttribute attribute = EJB3SubsystemXMLAttribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME:
                    name = value;
                    break;
                case URI:
                    RemoteHttpConnectionDefinition.URI.parseAndSetParameter(value, operation, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        final PathAddress receiverAddress = profileAddress.append(EJB3SubsystemModel.REMOTE_HTTP_CONNECTION, name);
        operation.get(OP_ADDR).set(receiverAddress.toModelNode());
        operations.add(operation);

        while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
            switch (EJB3SubsystemXMLElement.forName(reader.getLocalName())) {
                case CHANNEL_CREATION_OPTIONS:
                    parseChannelCreationOptions(reader, receiverAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }
}
