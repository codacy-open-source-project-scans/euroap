/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ws.injection.ejb.as1675;

import java.net.URL;

import javax.xml.namespace.QName;
import jakarta.xml.ws.Service;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.ws.injection.ejb.as1675.shared.BeanIface;
import org.jboss.as.test.integration.ws.injection.ejb.as1675.shared.BeanImpl;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * [AS7-1675] Problem with @Resource lookups on EJBs
 * <p>
 * https://issues.jboss.org/browse/AS7-1675
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class AS1675TestCase {

    @ArquillianResource
    URL baseUrl;

    @Deployment
    public static Archive<?> deployment() {
        // construct shared jar
        JavaArchive sharedJar = ShrinkWrap.create(JavaArchive.class, "shared.jar");
        sharedJar.addClass(BeanIface.class);
        sharedJar.addClass(BeanImpl.class);
        sharedJar.addClass(EndpointIface.class);
        sharedJar.addClass(AbstractEndpointImpl.class);
        // construct ejb3 jar
        JavaArchive ejb3Jar = ShrinkWrap.create(JavaArchive.class, "ejb3.jar");
        ejb3Jar.addClass(EJB3Bean.class);
        ejb3Jar.addClass(AS1675TestCase.class);
        ejb3Jar.addAsManifestResource(new StringAsset(EJB_JAR), "ejb-jar.xml");
        // construct ear
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "as1675.ear");
        ear.addAsModule(sharedJar);
        ear.addAsModule(ejb3Jar);
        // return ear
        return ear;
    }

    @Test
    public void testEjb3EndpointInjection() throws Exception {
        QName serviceName = new QName("http://jbossws.org/as1675", "EJB3Service");
        URL wsdlURL = new URL(baseUrl + "/as1675/EJB3Service?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EndpointIface proxy = (EndpointIface) service.getPort(EndpointIface.class);
        Assert.assertEquals("Hello World!:EJB3Bean", proxy.echo("Hello World!"));
    }

    private static final String EJB_JAR = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<ejb-jar version=\"3.0\" xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
            "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/ejb-jar_3_0.xsd\">\n" +
            "\n" +
            "  <enterprise-beans>\n" +
            "    <session>\n" +
            "\n" +
            "      <ejb-name>EJB3Bean</ejb-name>\n" +
            "      <ejb-class>org.jboss.as.test.integration.ws.injection.ejb.as1675.EJB3Bean</ejb-class>\n" +
            "\n" +
            "      <env-entry>\n" +
            "        <env-entry-name>boolean1</env-entry-name>\n" +
            "        <env-entry-type>java.lang.Boolean</env-entry-type>\n" +
            "        <env-entry-value>true</env-entry-value>\n" +
            "      </env-entry>\n" +
            "\n" +
            "    </session>\n" +
            "\n" +
            "  </enterprise-beans>\n" +
            "\n" +
            "</ejb-jar>";
}
