/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jpa.puparser;

import java.io.StringReader;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.jpa.config.PersistenceUnitMetadataHolder;
import org.jboss.metadata.property.PropertyReplacers;
import org.jipijapa.plugin.spi.PersistenceUnitMetadata;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class PersistenceUnitXmlParserTestCase {
    /**
     * See http://issues.jboss.org/browse/STXM-8
     */
    @Test
    public void testVersion() throws Exception {
        final String persistence_xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?> " +
                "<persistence xmlns=\"http://java.sun.com/xml/ns/persistence\" version=\"1.0\">" +
                "  <persistence-unit name=\"mypc\">" +
                "    <description>Persistence Unit." +
                "    </description>" +
                "    <jta-data-source>java:/H2DS</jta-data-source>" +
                "    <class>org.jboss.as.test.integration.jpa.epcpropagation.MyEntity</class>" +
                "    <properties> <property name=\"hibernate.hbm2ddl.auto\" value=\"create-drop\"/></properties>" +
                "  </persistence-unit>" +
                "</persistence>";
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(persistence_xml));
        PersistenceUnitMetadataHolder metadataHolder = PersistenceUnitXmlParser.parse(reader, PropertyReplacers.noop());
        PersistenceUnitMetadata metadata = metadataHolder.getPersistenceUnits().get(0);
        String version = metadata.getPersistenceXMLSchemaVersion();
        assertEquals("1.0", version);
    }

    @Test
    public void testVersion22() throws XMLStreamException {
        testEverything("http://java.sun.com/xml/ns/persistence", "2.2");
    }

    @Test
    public void testVersion30() throws XMLStreamException {
        testEverything("https://jakarta.ee/xml/ns/persistence", "3.0");
    }

    private void testEverything(String namespace, String version) throws XMLStreamException {
        final String persistence_xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?> " +
                        "<persistence xmlns=\"" + namespace + "\" version=\"" + version + "\">" +
                        "  <persistence-unit name=\"mypc\" transaction-type=\"RESOURCE_LOCAL\">" +
                        "    <description>Persistence Unit." +
                        "    </description>" +
                        "    <jta-data-source>java:/H2DS</jta-data-source>" +
                        "    <class>org.jboss.as.test.integration.jpa.epcpropagation.MyEntity</class>" +
                        "    <exclude-unlisted-classes/>" +
                        "    <jar-file>foo.jar</jar-file>" +
                        "    <mapping-file>mapping.xml</mapping-file>" +
                        "    <non-jta-data-source>java:/FooDS</non-jta-data-source>" +
                        "    <properties> <property name=\"hibernate.hbm2ddl.auto\" value=\"create-drop\"/></properties>" +
                        "    <provider>org.foo.Provider</provider>" +
                        "    <shared-cache-mode>DISABLE_SELECTIVE</shared-cache-mode>" +
                        "    <validation-mode>CALLBACK</validation-mode>" +
                        "  </persistence-unit>" +
                        "</persistence>";
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(persistence_xml));
        PersistenceUnitMetadataHolder metadataHolder = PersistenceUnitXmlParser.parse(reader, PropertyReplacers.noop());
        assertEquals(1, metadataHolder.getPersistenceUnits().size());
        PersistenceUnitMetadata metadata = metadataHolder.getPersistenceUnits().get(0);
        assertEquals("mypc", metadata.getPersistenceUnitName());
        assertEquals(version, metadata.getPersistenceXMLSchemaVersion());
        assertEquals("java:/H2DS", metadata.getJtaDataSourceName());
        assertEquals("java:/FooDS", metadata.getNonJtaDataSourceName());
        assertEquals(1, metadata.getManagedClassNames().size());
        assertEquals("org.jboss.as.test.integration.jpa.epcpropagation.MyEntity", metadata.getManagedClassNames().get(0));
        assertTrue(metadata.excludeUnlistedClasses());
        assertEquals(1, metadata.getJarFiles().size());
        assertEquals("foo.jar", metadata.getJarFiles().get(0));
        assertEquals(1, metadata.getMappingFileNames().size());
        assertEquals("mapping.xml", metadata.getMappingFileNames().get(0));
        assertEquals(1, metadata.getProperties().size());
        assertEquals("create-drop", metadata.getProperties().get("hibernate.hbm2ddl.auto"));
        assertEquals("org.foo.Provider", metadata.getPersistenceProviderClassName());
        assertEquals(SharedCacheMode.DISABLE_SELECTIVE, metadata.getSharedCacheMode());
        assertEquals(ValidationMode.CALLBACK, metadata.getValidationMode());
        assertEquals(PersistenceUnitTransactionType.RESOURCE_LOCAL, metadata.getTransactionType());

    }
}
