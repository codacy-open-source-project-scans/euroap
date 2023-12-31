/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.picketlink.federation.model.parser;

import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.extension.picketlink.common.model.ModelElement;
import org.wildfly.extension.picketlink.common.model.XMLElement;
import org.wildfly.extension.picketlink.common.parser.ModelXMLElementWriter;
import org.wildfly.extension.picketlink.federation.Namespace;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wildfly.extension.picketlink.common.model.ModelElement.COMMON_HANDLER;
import static org.wildfly.extension.picketlink.common.model.ModelElement.COMMON_HANDLER_PARAMETER;
import static org.wildfly.extension.picketlink.common.model.ModelElement.COMMON_NAME;
import static org.wildfly.extension.picketlink.common.model.ModelElement.FEDERATION;
import static org.wildfly.extension.picketlink.common.model.ModelElement.IDENTITY_PROVIDER;
import static org.wildfly.extension.picketlink.common.model.ModelElement.IDENTITY_PROVIDER_ATTRIBUTE_MANAGER;
import static org.wildfly.extension.picketlink.common.model.ModelElement.IDENTITY_PROVIDER_ROLE_GENERATOR;
import static org.wildfly.extension.picketlink.common.model.ModelElement.IDENTITY_PROVIDER_SAML_METADATA;
import static org.wildfly.extension.picketlink.common.model.ModelElement.IDENTITY_PROVIDER_SAML_METADATA_ORGANIZATION;
import static org.wildfly.extension.picketlink.common.model.ModelElement.IDENTITY_PROVIDER_TRUST_DOMAIN;
import static org.wildfly.extension.picketlink.common.model.ModelElement.KEY;
import static org.wildfly.extension.picketlink.common.model.ModelElement.KEY_STORE;
import static org.wildfly.extension.picketlink.common.model.ModelElement.SAML;
import static org.wildfly.extension.picketlink.common.model.ModelElement.SERVICE_PROVIDER;
import static org.wildfly.extension.picketlink.common.model.XMLElement.HANDLERS;
import static org.wildfly.extension.picketlink.common.model.XMLElement.SERVICE_PROVIDERS;

/**
 * <p> XML Writer for the subsystem schema, version 1.0. </p>
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Silva</a>
 */
public class FederationSubsystemWriter implements XMLStreamConstants, XMLElementWriter<SubsystemMarshallingContext> {

    private static final Map<String, ModelXMLElementWriter> writers = new HashMap<String, ModelXMLElementWriter>();

    public static final FederationSubsystemWriter INSTANCE = new FederationSubsystemWriter();

    static {
        // federation elements writers
        registerWriter(FEDERATION, COMMON_NAME);
        registerWriter(IDENTITY_PROVIDER, COMMON_NAME);
        registerWriter(KEY_STORE);
        registerWriter(KEY, COMMON_NAME, XMLElement.KEYS);
        registerWriter(IDENTITY_PROVIDER_SAML_METADATA);
        registerWriter(IDENTITY_PROVIDER_SAML_METADATA_ORGANIZATION);
        registerWriter(IDENTITY_PROVIDER_TRUST_DOMAIN, COMMON_NAME, XMLElement.TRUST);
        registerWriter(IDENTITY_PROVIDER_ROLE_GENERATOR, COMMON_NAME);
        registerWriter(IDENTITY_PROVIDER_ATTRIBUTE_MANAGER, COMMON_NAME);
        registerWriter(COMMON_HANDLER, COMMON_NAME, HANDLERS);
        registerWriter(COMMON_HANDLER_PARAMETER, COMMON_NAME);
        registerWriter(SERVICE_PROVIDER, COMMON_NAME, SERVICE_PROVIDERS);
        registerWriter(SAML);
    }

    private FederationSubsystemWriter() {
        // singleton
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        // Start subsystem
        context.startSubsystemElement(Namespace.CURRENT.getUri(), false);

        ModelNode subsystemNode = context.getModelNode();

        if (subsystemNode.isDefined()) {
            List<ModelNode> identityManagement = subsystemNode.asList();

            for (ModelNode modelNode : identityManagement) {
                writers.get(FEDERATION.getName()).write(writer, modelNode);
            }
        }

        // End subsystem
        writer.writeEndElement();
    }

    private static void registerWriter(final ModelElement element, final ModelElement keyAttribute) {
        writers.put(element.getName(), new ModelXMLElementWriter(element, keyAttribute.getName(), writers));
    }

    private static void registerWriter(final ModelElement element) {
        writers.put(element.getName(), new ModelXMLElementWriter(element, writers));
    }

    private static void registerWriter(final ModelElement element, final ModelElement keyAttribute, final XMLElement parent) {
        writers.put(element.getName(), new ModelXMLElementWriter(element, keyAttribute.getName(), parent, writers));
    }
}
