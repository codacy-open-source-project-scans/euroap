/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jaxrs.cfg;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.jaxrs.cfg.applicationclasses.HelloWorldApplication;
import org.jboss.as.test.integration.jaxrs.packaging.war.WebXml;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for RESTEasy configuration parameter 'resteasy.scan.resources'
 *
 * @author Pavel Janousek
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ResteasyScanResourcesTestCase {

    private static final String depNameTrue = "dep_true";
    private static final String depNameFalse = "dep_false";
    private static final String depNameInvalid = "dep_invalid";
    private static final String depNameTrueApp = "dep_true_app";
    private static final String depNameFalseApp = "dep_false_app";
    private static final String depNameInvalidApp = "dep_invalid_app";

    @Deployment(name = depNameTrue, managed = true)
    public static Archive<?> deploy_true() {
        return ShrinkWrap.create(WebArchive.class, depNameTrue + ".war")
                .addClasses(ResteasyScanResourcesTestCase.class, HelloWorldResource.class)
                .setWebXML(webXmlWithMapping("resteasy.scan.resources", "true"));
    }

    @Deployment(name = depNameFalse, managed = true)
    public static Archive<?> deploy_false() {
        return ShrinkWrap.create(WebArchive.class, depNameFalse + ".war")
                .addClasses(ResteasyScanResourcesTestCase.class, HelloWorldResource.class)
                .setWebXML(webXmlWithMapping("resteasy.scan.resources", "false"));
    }

    @Deployment(name = depNameInvalid, managed = false)
    public static Archive<?> deploy_invalid() {
        return ShrinkWrap.create(WebArchive.class, depNameInvalid + ".war")
                .addClasses(ResteasyScanResourcesTestCase.class, HelloWorldResource.class)
                .setWebXML(webXmlWithMapping("resteasy.scan.resources", "blah"));
    }

    @Deployment(name = depNameTrueApp, managed = true)
    public static Archive<?> deploy_true_app() {
        return ShrinkWrap.create(WebArchive.class, depNameTrueApp + ".war")
                .addClasses(ResteasyScanResourcesTestCase.class, HelloWorldResource.class, HelloWorldApplication.class)
                .setWebXML(webXml("resteasy.scan.resources", "true"));
    }

    @Deployment(name = depNameFalseApp, managed = true)
    public static Archive<?> deploy_false_app() {
        return ShrinkWrap.create(WebArchive.class, depNameFalseApp + ".war")
                .addClasses(ResteasyScanResourcesTestCase.class, HelloWorldResource.class, HelloWorldApplication.class)
                .setWebXML(webXml("resteasy.scan.resources", "false"));
    }

    @Deployment(name = depNameInvalidApp, managed = false)
    public static Archive<?> deploy_invalid_app() {
        return ShrinkWrap.create(WebArchive.class, depNameInvalidApp + ".war")
                .addClasses(ResteasyScanResourcesTestCase.class, HelloWorldResource.class, HelloWorldApplication.class)
                .setWebXML(webXml("resteasy.scan.resources", "blah"));
    }

    private static StringAsset webXml(final String paramName, final String paramValue) {
        return WebXml.get(getCfgString(paramName, paramValue));
    }

    private static StringAsset webXmlWithMapping(final String paramName, final String paramValue) {
        return WebXml.get("<servlet-mapping>\n" + "        <servlet-name>jakarta.ws.rs.core.Application</servlet-name>\n"
                + "        <url-pattern>/myjaxrs/*</url-pattern>\n" + "</servlet-mapping>\n"
                + getCfgString(paramName, paramValue));
    }

    private static String getCfgString(final String paramName, final String paramValue) {
        return "<context-param>\n" + "        <param-name>" + paramName + "</param-name>\n" + "        <param-value>"
                + paramValue + "</param-value>\n" + "</context-param>\n" + "\n";
    }

    @ArquillianResource
    private Deployer deployer;

    @Test
    @OperateOnDeployment(depNameTrue)
    public void testDeployTrue(@ArquillianResource URL url) throws Exception {
        String result = HttpRequest.get(url.toExternalForm() + "myjaxrs/helloworld", 10, TimeUnit.SECONDS);
        assertEquals("Hello World!", result);
    }

    @Test
    @OperateOnDeployment(depNameFalse)
    public void testDeployFalse(@ArquillianResource URL url) throws Exception {
        try {
            @SuppressWarnings("unused")
            String result = HttpRequest.get(url.toExternalForm() + "myjaxrs/helloworld", 10, TimeUnit.SECONDS);
            Assert.fail("Scan of Resources is disabled so we should not pass to there - HTTP 404 must occur!");
        } catch (Exception e) {
            Assert.assertTrue(e.toString().contains("HTTP Status 404"));
        }
    }

    @Test
    public void testDeployInvalid() throws Exception {
        try {
            deployer.deploy(depNameInvalid);
            Assert.fail("Test should not go here - invalid deployment (invalid value of resteasy.scan.resources)!");
        } catch (Exception e) {
        }
    }

    @Test
    @OperateOnDeployment(depNameTrueApp)
    public void testDeployTrueApp(@ArquillianResource URL url) throws Exception {
        String result = HttpRequest.get(url.toExternalForm() + "app1/helloworld", 10, TimeUnit.SECONDS);
        assertEquals("Hello World!", result);
    }

    @Test
    @OperateOnDeployment(depNameFalseApp)
    public void testDeployFalseApp(@ArquillianResource URL url) throws Exception {
        try {
            @SuppressWarnings("unused")
            String result = HttpRequest.get(url.toExternalForm() + "app1/helloworld", 10, TimeUnit.SECONDS);
            Assert.fail("Scan of Resources is disabled so we should not pass to there - HTTP 404 must occur!");
        } catch (Exception e) {
            Assert.assertTrue(e.toString().contains("HTTP Status 404"));
        }
    }

    @Test
    public void testDeployInvalidApp() throws Exception {
        try {
            deployer.deploy(depNameInvalidApp);
            Assert.fail("Test should not go here - invalid deployment (invalid value of resteasy.scan.resources)!");
        } catch (Exception e) {
        }
    }
}
