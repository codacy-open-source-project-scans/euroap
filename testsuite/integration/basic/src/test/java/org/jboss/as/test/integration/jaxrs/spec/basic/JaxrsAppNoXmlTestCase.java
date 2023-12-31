/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jaxrs.spec.basic;

import java.net.URL;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.jaxrs.spec.basic.resource.JaxrsApp;
import org.jboss.as.test.integration.jaxrs.spec.basic.resource.JaxrsAppResource;
import org.jboss.as.test.integration.jaxrs.spec.basic.resource.JaxrsAppThree;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

/**
 */
@RunWith(Arquillian.class)
@RunAsClient
public class JaxrsAppNoXmlTestCase {
    @ArquillianResource
    URL baseUrl;

    private static final String CONTENT_ERROR_MESSAGE = "Wrong content of response";

    @Deployment
    public static Archive<?> deploySimpleResource() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, JaxrsAppNoXmlTestCase.class.getSimpleName() + ".war");
        war.addClasses(JaxrsAppResource.class, JaxrsApp.class, JaxrsAppThree.class);
        return war;
    }


    /**
     * The jaxrs 2.0 spec says when no web.xml file is present the
     * archive is scanned for Application subclasses, resource and
     * provider classes.
     */
    @Test
    public void testDemo() throws Exception {
        // check 1st Application subclass
        Client client = ClientBuilder.newClient();
        try {
            String url = baseUrl.toString() + "resourcesAppPath";
            WebTarget base = client.target(url);
            String value = base.path("example").request().get(String.class);
            Assert.assertEquals(CONTENT_ERROR_MESSAGE, "Hello world!", value);
        } finally {
            client.close();
        }

        // check 2nd Application subclass
        client = ClientBuilder.newClient();
        try {
            String url = baseUrl.toString() + "resourcesThree";
            WebTarget base = client.target(url);
            String value = base.path("example").request().get(String.class);
            Assert.assertEquals(CONTENT_ERROR_MESSAGE, "Hello world!", value);
        } finally {
            client.close();
        }
    }
}
