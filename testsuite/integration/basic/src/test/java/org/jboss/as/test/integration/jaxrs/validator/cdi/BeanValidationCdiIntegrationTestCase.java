/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jaxrs.validator.cdi;

import java.net.URL;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for the integration of Jakarta RESTful Web Services, Jakarta Bean Validation and Jakarta Contexts and Dependency Injection. See WFLY-278.
 *
 * @author Gunnar Morling
 */
@RunWith(Arquillian.class)
@RunAsClient
public class BeanValidationCdiIntegrationTestCase {

    @ApplicationPath("/myjaxrs")
    public static class TestApplication extends Application {
    }

    @Deployment(testable = false)
    public static Archive<?> deploy() {
        return ShrinkWrap.create(WebArchive.class, "jaxrsnoap.war")
                .addPackage(HttpRequest.class.getPackage())
                .addClasses(
                        BeanValidationCdiIntegrationTestCase.class,
                        OrderModel.class,
                        OrderResource.class,
                        CustomMax.class,
                        CustomMaxValidator.class,
                        MaximumValueProvider.class
                )
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @ArquillianResource
    private URL url;

    @Test
    public void testValidRequest() throws Exception {
        DefaultHttpClient client = new DefaultHttpClient(new PoolingClientConnectionManager());

        HttpGet get = new HttpGet(url + "myjaxrs/order/5");
        HttpResponse result = client.execute(get);

        Assert.assertEquals(200, result.getStatusLine().getStatusCode());
        Assert.assertEquals("OrderModel{id=5}", EntityUtils.toString(result.getEntity()));
    }

    @Test
    public void testInvalidRequest() throws Exception {
        DefaultHttpClient client = new DefaultHttpClient(new PoolingClientConnectionManager());

        HttpGet get = new HttpGet(url + "myjaxrs/order/11");
        HttpResponse result = client.execute(get);
        result = client.execute(get);

        Assert.assertEquals("Parameter constraint violated", 400, result.getStatusLine().getStatusCode());
    }
}
