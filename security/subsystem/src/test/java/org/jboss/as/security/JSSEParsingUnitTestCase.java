/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.security;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.model.test.ModelTestParser;
import org.jboss.as.model.test.ModelTestUtils;

import org.jboss.as.subsystem.test.TestParser;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * TODO class javadoc.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class JSSEParsingUnitTestCase {

    private static final ModelNode SUCCESS;

    static {
        SUCCESS = new ModelNode();
        SUCCESS.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
        SUCCESS.get(ModelDescriptionConstants.RESULT);
        SUCCESS.protect();
    }

    private ExtensionRegistry extensionParsingRegistry;
    private ModelTestParser testParser;
    private XMLMapper xmlMapper;

    private final String TEST_NAMESPACE = "urn.org.jboss.test:1.0";

    protected final String mainSubsystemName = SecurityExtension.SUBSYSTEM_NAME;
    private final Extension mainExtension = new SecurityExtension();

    @Before
    public void initializeParser() throws Exception {
        //Initialize the parser
        xmlMapper = XMLMapper.Factory.create();
        extensionParsingRegistry = new ExtensionRegistry(ProcessType.EMBEDDED_SERVER, new RunningModeControl(RunningMode.NORMAL), null, null, null, null);
        testParser = new TestParser(mainSubsystemName, extensionParsingRegistry);
        xmlMapper.registerRootElement(new QName(TEST_NAMESPACE, "test"), testParser);
        mainExtension.initializeParsers(extensionParsingRegistry.getExtensionParsingContext("Test", xmlMapper));
    }

    @After
    public void cleanup() throws Exception {
        xmlMapper = null;
        extensionParsingRegistry = null;
        testParser = null;
    }

    /**
     * Parse the subsystem xml and create the operations that will be passed into the controller
     *
     * @param subsystemXml the subsystem xml to be parsed
     * @return the created operations
     * @throws XMLStreamException if there is a parsing problem
     */
    List<ModelNode> parse(String subsystemXml) throws XMLStreamException, IOException {
        String xml = "<test xmlns=\"" + TEST_NAMESPACE + "\">"
                + ModelTestUtils.readResource(getClass(), subsystemXml)
                + "</test>";
        final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
        final List<ModelNode> operationList = new ArrayList<ModelNode>();
        xmlMapper.parseDocument(operationList, reader);
        return operationList;
    }

    @Test
    public void testParseMissingPasswordJSSE() throws Exception {
        try {
            parse("securityErrorMissingPassword.xml");
            Assert.fail("There should have been an error.");
        } catch (XMLStreamException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("WFLYSEC0023"));
        }
    }


    @Test
    public void testParseWrongJSSE() throws Exception {
        try {
            parse("securityParserError.xml");
            Assert.fail("There should have been an error.");
        } catch (XMLStreamException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("WFLYSEC0023"));
        }
    }

    @Test
    public void testParseValidJSSE() throws Exception {
        parse("securityParserValidJSSE.xml");

    }

}
