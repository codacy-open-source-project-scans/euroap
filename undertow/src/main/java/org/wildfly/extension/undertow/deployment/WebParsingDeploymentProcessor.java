/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.deployment;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.ee.structure.SpecDescriptorPropertyReplacement;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.metadata.parser.servlet.WebMetaDataParser;
import org.jboss.metadata.parser.util.MetaDataElementParser;
import org.jboss.metadata.parser.util.XMLResourceResolver;
import org.jboss.metadata.parser.util.XMLSchemaValidator;
import org.jboss.metadata.web.spec.WebMetaData;
import org.jboss.vfs.VirtualFile;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xml.sax.SAXException;

/**
 * @author Jean-Frederic Clere
 * @author Thomas.Diesler@jboss.com
 */
public class WebParsingDeploymentProcessor implements DeploymentUnitProcessor {

    private static final String WEB_XML = "WEB-INF/web.xml";
    private final boolean schemaValidation;

    public WebParsingDeploymentProcessor() {
        String property = WildFlySecurityManager.getPropertyPrivileged(XMLSchemaValidator.PROPERTY_SCHEMA_VALIDATION, "false");
        this.schemaValidation = Boolean.parseBoolean(property);
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        ClassLoader old = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(WebParsingDeploymentProcessor.class);
            final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
            if (!DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
                return; // Skip non web deployments
            }
            final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
            final VirtualFile alternateDescriptor = deploymentRoot.getAttachment(org.jboss.as.ee.structure.Attachments.ALTERNATE_WEB_DEPLOYMENT_DESCRIPTOR);
            // Locate the descriptor
            final VirtualFile webXml;
            if (alternateDescriptor != null) {
                webXml = alternateDescriptor;
            } else {
                webXml = deploymentRoot.getRoot().getChild(WEB_XML);
            }
            final WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
            assert warMetaData != null;
            if (webXml.exists()) {
                InputStream is = null;
                try {
                    is = webXml.openStream();
                    final XMLInputFactory inputFactory = XMLInputFactory.newInstance();

                    MetaDataElementParser.DTDInfo dtdInfo = new MetaDataElementParser.DTDInfo();
                    inputFactory.setXMLResolver(dtdInfo);
                    final XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(is);

                    WebMetaData webMetaData = WebMetaDataParser.parse(xmlReader, dtdInfo, SpecDescriptorPropertyReplacement.propertyReplacer(deploymentUnit));

                    if (schemaValidation && webMetaData.getSchemaLocation() != null) {
                        XMLSchemaValidator validator = new XMLSchemaValidator(new XMLResourceResolver());
                        InputStream xmlInput = webXml.openStream();
                        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
                        try {
                            Thread.currentThread().setContextClassLoader(WebMetaDataParser.class.getClassLoader());
                            if (webMetaData.is23())
                                validator.validate("-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN", xmlInput);
                            else if (webMetaData.is24())
                                validator.validate("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd", xmlInput);
                            else if (webMetaData.is25())
                                validator.validate("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd", xmlInput);
                            else if (webMetaData.is30())
                                validator.validate("http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd", xmlInput);
                            else if (webMetaData.is31())
                                validator.validate("http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd", xmlInput);
                            else if (webMetaData.getVersion() != null && webMetaData.getVersion().equals("4.0"))
                                validator.validate("http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd", xmlInput);
                            else if (webMetaData.getVersion() != null && webMetaData.getVersion().equals("5.0"))
                                validator.validate("https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd", xmlInput);
                            else if (webMetaData.getVersion() != null && webMetaData.getVersion().equals("6.0"))
                                validator.validate("https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd", xmlInput);
                            else
                                validator.validate("-//Sun Microsystems, Inc.//DTD Web Application 2.2//EN", xmlInput);
                        } catch (SAXException e) {
                            throw new DeploymentUnitProcessingException("Failed to validate " + webXml, e);
                        } finally {
                            xmlInput.close();
                            Thread.currentThread().setContextClassLoader(oldCl);
                        }
                    }
                    warMetaData.setWebMetaData(webMetaData);

                } catch (XMLStreamException e) {
                    Integer lineNumber = null;
                    Integer columnNumber = null;
                    if (e.getLocation() != null) {
                        lineNumber = e.getLocation().getLineNumber();
                        columnNumber = e.getLocation().getColumnNumber();
                    }
                    throw new DeploymentUnitProcessingException(UndertowLogger.ROOT_LOGGER.failToParseXMLDescriptor(webXml.toString(), lineNumber, columnNumber), e);
                } catch (IOException e) {
                    throw new DeploymentUnitProcessingException(UndertowLogger.ROOT_LOGGER.failToParseXMLDescriptor(webXml.toString()), e);
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(old);
        }
    }
}
