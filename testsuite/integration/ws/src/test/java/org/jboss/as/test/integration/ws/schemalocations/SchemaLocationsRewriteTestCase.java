/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ws.schemalocations;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.cxf.helpers.IOUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that schema locations are rewritten.
 * <p>
 * CXF-6469
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
@RunWith(Arquillian.class)
@RunAsClient
public class SchemaLocationsRewriteTestCase {

    @ArquillianResource
    URL baseUrl;

    private static final Logger log = Logger.getLogger(SchemaLocationsRewriteTestCase.class.getName());

    @Deployment
    public static WebArchive createDeployment() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "schema-location-rewrite.war");
        war.addPackage(SimpleService.class.getPackage()).
                addAsWebInfResource(SchemaLocationsRewriteTestCase.class.getPackage(), "SimpleService.wsdl", "wsdl/SimpleService.wsdl").
                addAsWebInfResource(SchemaLocationsRewriteTestCase.class.getPackage(), "imported/AnotherService.wsdl", "wsdl/imported/AnotherService.wsdl").
                addAsWebInfResource(SchemaLocationsRewriteTestCase.class.getPackage(), "imported/SimpleService.xsd", "wsdl/imported/SimpleService.xsd").
                addAsWebInfResource(SchemaLocationsRewriteTestCase.class.getPackage(), "imported/importedschema.xsd", "wsdl/imported/importedschema.xsd");
        war.addClass(SimpleService.class);
        return war;
    }

    @Test
    public void testSchemaLocationsRewritten() throws Exception {
        // first path: SimpleService.wsdl -> imported/AnotherService.wsdl -> SimpleService.xsd -> importschema.xsd

        String importedWsdlLocation = getWsdlLocation(new URL(baseUrl, "SimpleService?wsdl"), "AnotherService.wsdl");
        verifyLocationRewritten(importedWsdlLocation);

        String xsdLocation = getSchemaLocation(new URL(importedWsdlLocation), "SimpleService.xsd");
        verifyLocationRewritten(xsdLocation);

        String importedXsdLocation = getSchemaLocation(new URL(xsdLocation), "importedschema.xsd");
        verifyLocationRewritten(importedXsdLocation);

        // second path: SimpleService.wsdl -> imported/SimpleService.xsd -> importedschema.xsd

        xsdLocation = getSchemaLocation(new URL(baseUrl, "SimpleService?wsdl"), "SimpleService.xsd");
        verifyLocationRewritten(xsdLocation);

        importedXsdLocation = getSchemaLocation(new URL(xsdLocation), "importedschema.xsd");
        verifyLocationRewritten(importedXsdLocation);
    }

    private String getSchemaLocation(URL url, String locationSuffix) throws Exception {
        List<String> schemaLocations = getAttributeValues(url, "schemaLocation");
        return findLocation(schemaLocations, locationSuffix);
    }

    private String getWsdlLocation(URL url, String locationSuffix) throws Exception {
        List<String> schemaLocations = getAttributeValues(url, "location");
        return findLocation(schemaLocations, locationSuffix);
    }

    private String findLocation(List<String> values, String locationSuffix) {
        String result = null;
        for (String location : values) {
            if (location.endsWith(locationSuffix)) {
                if (result == null) {
                    result = location;
                } else {
                    throw new IllegalStateException("Schema or WSDL location end is not unique for given document.");
                }
            }
        }
        Assert.assertNotNull(String.format("Location ending with '%s' not found in", locationSuffix), result);
        return result;
    }

    private void verifyLocationRewritten(String schemaLocation) {
        Assert.assertTrue(String.format("Location was not rewritten: %s", schemaLocation),
                schemaLocation.contains("?xsd=") || schemaLocation.contains("?wsdl="));
    }

    private List<String> getAttributeValues(URL url, String localPart) throws Exception {
        String document = IOUtils.toString(url.openStream());
        log.trace(document);

        List<String> values = new ArrayList<>();
        XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLEventReader eventReader = xmlif.createXMLEventReader(new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)));

        while (eventReader.hasNext()) {
            XMLEvent xmlEvent = eventReader.nextEvent();
            if (xmlEvent.getEventType() == XMLStreamConstants.START_ELEMENT) {
                StartElement startElement = xmlEvent.asStartElement();
                Attribute attribute = startElement.getAttributeByName(new QName("", localPart));
                if (attribute != null) {
                    values.add(attribute.getValue());
                }
            }
        }

        return values;
    }
}
