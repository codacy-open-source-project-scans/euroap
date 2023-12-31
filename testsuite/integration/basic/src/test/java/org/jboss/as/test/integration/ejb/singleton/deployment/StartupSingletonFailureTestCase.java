/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.singleton.deployment;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.cli.DeployURLTestCase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.ejb.NoSuchEJBException;
import java.io.File;
import java.net.URL;

/**
 * @author Bartosz Spyrko-Smietanko
 */
@RunWith(Arquillian.class)
@RunAsClient
public class StartupSingletonFailureTestCase extends AbstractCliTestBase {

    public static EnterpriseArchive deployment(boolean includeInOrder) {
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "TestEar.ear");

        JavaArchive ejb1 = ShrinkWrap.create(JavaArchive.class, "ejb1.jar");
        ejb1.addClass(SingletonOne.class);
        JavaArchive ejb2 = ShrinkWrap.create(JavaArchive.class, "ejb2.jar");
        ejb2.addClass(HelloBean.class);
        ejb2.addClass(HelloRemote.class);
        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");
        war.addAsWebResource(new StringAsset("Hello"), "index.html");
        war.addAsWebInfResource(new StringAsset("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<web-app xmlns=\"http://java.sun.com/xml/ns/javaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd\" " +
                "version=\"3.0\"></web-app>"), "web.xml");

        ear.addAsModule(ejb1);
        ear.addAsModule(ejb2);
        ear.addAsModule(war);
        if (includeInOrder) {
            ear.setApplicationXML(new StringAsset("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<application>" +
                    "<initialize-in-order>true</initialize-in-order>" +
                    "<module><web><web-uri>test.war</web-uri><context-root>test</context-root></web></module>" +
                    "<module><ejb>ejb1.jar</ejb></module>" +
                    "<module><ejb>ejb2.jar</ejb></module>" +
                    "</application>"));
        }

        return ear;
    }

    // dummy deployment to keep Arquillian happy - not used in test
    @Deployment
    public static Archive<?> getDeployment() {
        JavaArchive ja = ShrinkWrap.create(JavaArchive.class, "dummy.jar");
        ja.addClass(DeployURLTestCase.class);
        return ja;
    }

    private static File unorderedEarFile;
    private static File inOrderEarFile;

    @BeforeClass
    public static void before() throws Exception {
        unorderedEarFile = exportEar(false, "TestEar.ear");
        inOrderEarFile = exportEar(true, "TestEarInOrder.ear");

        AbstractCliTestBase.initCLI();
    }

    private static File exportEar(boolean inOrder, String name) {
        EnterpriseArchive ear = deployment(inOrder);
        String tempDir = TestSuiteEnvironment.getTmpDir();
        File earFile = new File(tempDir + File.separator + name);
        new ZipExporterImpl(ear).exportTo(earFile, true);
        return earFile;
    }

    @AfterClass
    public static void after() throws Exception {
        unorderedEarFile.delete();
        inOrderEarFile.delete();
        AbstractCliTestBase.closeCLI();
    }

    @Test
    @SuppressWarnings({"unchecked"})
    public void testEjbInAnotherModuleShouldFail() throws Exception {
        testEjbInAnotherModuleShouldFail(unorderedEarFile);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    public void testInOrderDeploymentEjbInAnotherModuleShouldFail() throws Exception {
        testEjbInAnotherModuleShouldFail(inOrderEarFile);
    }

    private void testEjbInAnotherModuleShouldFail(File earFile) throws Exception {
        try {
            cli.sendLine("deploy --url=" + earFile.toURI().toURL().toExternalForm() + " --name=" + earFile.getName() + " --headers={rollback-on-runtime-failure=false}");

            final StatelessEJBLocator<HelloRemote> locator = new StatelessEJBLocator(HelloRemote.class, "TestEar", "ejb2", HelloBean.class.getSimpleName(), "");
            final HelloRemote proxy = EJBClient.createProxy(locator);
            Assert.assertNotNull("Received a null proxy", proxy);
            try {
                proxy.hello();
                Assert.fail("Call should have failed");
            } catch (NoSuchEJBException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("EJBCLIENT000079"));
            }
        } finally {
            cli.sendLine("undeploy " + earFile.getName());
        }
    }

    @Test
    public void testWebModuleShouldFail(@ArquillianResource URL url) throws Exception {
        testWebModuleShouldFail(url, unorderedEarFile);
    }

    @Test
    public void testInOrderDeploymentWebModuleShouldFail(@ArquillianResource URL url) throws Exception {
        testWebModuleShouldFail(url, inOrderEarFile);
    }

    private void testWebModuleShouldFail(URL url, File earFile) throws Exception {
        try {
            cli.sendLine("deploy --url=" + earFile.toURI().toURL().toExternalForm() + " --name=" + earFile.getName() + " --headers={rollback-on-runtime-failure=false}");

            try(CloseableHttpClient client = HttpClientBuilder.create().build()) {
                final HttpGet get = new HttpGet(url.toExternalForm() + "/test/index.html");
                Assert.assertEquals(500, client.execute(get).getStatusLine().getStatusCode());
            }
        } finally {
            cli.sendLine("undeploy " + earFile.getName());
        }
    }
}
