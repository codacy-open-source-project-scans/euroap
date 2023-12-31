/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.interceptor;

import org.jboss.metadata.ejb.parser.spec.AbstractMetaDataParser;
import org.jboss.metadata.ejb.parser.spec.InterceptorBindingMetaDataParser;
import org.jboss.metadata.ejb.spec.InterceptorBindingMetaData;
import org.jboss.metadata.property.PropertyReplacer;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Responsible for parsing the <code>container-interceptors</code> in jboss-ejb3.xml
 *
 * @author Jaikiran Pai
 */
public class ContainerInterceptorsParser extends AbstractMetaDataParser<ContainerInterceptorsMetaData> {

    public static final ContainerInterceptorsParser INSTANCE = new ContainerInterceptorsParser();
    public static final String NAMESPACE_URI_1_0 = "urn:container-interceptors:1.0";
    public static final String NAMESPACE_URI_2_0 = "urn:container-interceptors:2.0";

    private static final String ELEMENT_CONTAINER_INTERCEPTORS = "container-interceptors";
    private static final String ELEMENT_INTERCEPTOR_BINDING = "interceptor-binding";

    @Override
    public ContainerInterceptorsMetaData parse(XMLStreamReader reader, PropertyReplacer propertyReplacer) throws XMLStreamException {
        // make sure it's the right namespace
        if (!NAMESPACE_URI_1_0.equals(reader.getNamespaceURI()) && !NAMESPACE_URI_2_0.equals(reader.getNamespaceURI())) {
            throw unexpectedElement(reader);
        }
        // only process relevant elements
        if (!reader.getLocalName().equals(ELEMENT_CONTAINER_INTERCEPTORS)) {
            throw unexpectedElement(reader);
        }

        final ContainerInterceptorsMetaData metaData = new ContainerInterceptorsMetaData();
        processElements(metaData, reader, propertyReplacer);
        return metaData;
    }

    @Override
    protected void processElement(final ContainerInterceptorsMetaData containerInterceptorsMetadata, final XMLStreamReader reader, final PropertyReplacer propertyReplacer) throws XMLStreamException {
        final String localName = reader.getLocalName();
        if (!localName.equals(ELEMENT_INTERCEPTOR_BINDING)) {
            throw unexpectedElement(reader);
        }
        // parse the interceptor-binding
        final InterceptorBindingMetaData interceptorBinding = this.readInterceptorBinding(reader, propertyReplacer);
        // add the interceptor binding to the container interceptor metadata
        containerInterceptorsMetadata.addInterceptorBinding(interceptorBinding);
    }

    /**
     * Parses the <code>interceptor-binding</code> element and returns the corresponding {@link InterceptorBindingMetaData}
     *
     * @param reader
     * @param propertyReplacer
     * @return
     * @throws XMLStreamException
     */
    private InterceptorBindingMetaData readInterceptorBinding(final XMLStreamReader reader, final PropertyReplacer propertyReplacer) throws XMLStreamException {
        return InterceptorBindingMetaDataParser.INSTANCE.parse(reader, propertyReplacer);
    }
}
