/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.validation;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 * */
@RunWith(Arquillian.class)
@RunAsClient
public class EjbBeanValidationTestCase {

    @ApplicationPath("")
    public static class TestApplication extends Application {
    }

    @Deployment(testable = false)
    public static Archive<?> deploy() {
        return ShrinkWrap.create(WebArchive.class, "ejbvalidation.war")
                .addPackage(HttpRequest.class.getPackage())
                .addClasses(
                        EjbBeanValidationTestCase.class, TestApplication.class, TestResource.class, EchoResourceImpl.class, EchoResource.class,
                        DummySubclass.class, DummyAbstractClass.class, DummyClass.class, DummyFlagImpl.class, DummyFlag.class);
    }

    @ArquillianResource
    private URL url;

    static Client client;

    @BeforeClass
    public static void setUpClient() {
        client = ClientBuilder.newClient();
    }

    @AfterClass
    public static void close() {
        client.close();
    }


    @Test
    public void testValidation() {
        Response response = client.target(url.toExternalForm() + "validate/1").request().get();
        Assert.assertNotNull("Request should have been negatively validated", response.getMetadata().getFirst("validation-exception"));
    }

    /**
     * This test check whether the EJB proxy is being correctly normalized by {@link org.jboss.as.ejb3.validator.EjbProxyBeanMetaDataClassNormalizer}.
     * As the proxy does not support contain information about generics, without normalization validation would fail.
     */
    @Test
    public void testProxyNormalization() {
        String result = client.target(url.toExternalForm() + "put/list")
                .request(MediaType.APPLICATION_JSON).post(Entity.json("[\"a\",\"b\",\"c\"]"), String.class);
        Assert.assertEquals("a, b, c", result);
    }

    @Test
    public void testObjectValidationOnConcreteClass() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(url.toURI().toString());
        ResteasyWebTarget rtarget = (ResteasyWebTarget) target;
        EchoResource customerResource = rtarget.proxy(EchoResource.class);
        DummyFlag dummyFlag = rtarget.proxy(DummyFlag.class);

        // Create a concrete class with valid values
        DummyClass validDummyClass = new DummyClass();
        validDummyClass.setSpeed(5);
        validDummyClass.setDirection("north");

        // Create a concrete class with invalid values (direction is null and speed is less than 1)
        DummyClass invalidDummyClass = new DummyClass();
        invalidDummyClass.setSpeed(0);

        Response response = customerResource.validateEchoThroughClass(validDummyClass);

        // Verify that we received a Bad Request Code from HTTP
        assertTrue(String.format("Return code should be 200. It was %d", response.getStatus()), 200 == response.getStatus());

        // Verify that the service call has not been executed (flag set to false)
        assertTrue("Executed flag should be true", dummyFlag.getExecutedServiceCallFlag());

        // Reset flag
        dummyFlag.clearExecution();

        Response response2 = customerResource.validateEchoThroughClass(invalidDummyClass);

        // Verify that we received a Bad Request Code from HTTP
        assertTrue(String.format("Return code should either be 400 or 500, it was %d", response2.getStatus()), 400 == response2.getStatus() || 500 == response2.getStatus());

        // Verify that the service call has not been executed (flag set to false)
        assertFalse("Executed flag should be false", dummyFlag.getExecutedServiceCallFlag());
    }

    @Test
    public void testObjectValidationOnSubclassThatExtendsAbstractClass() throws Exception {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(url.toURI().toString());
        ResteasyWebTarget rtarget = (ResteasyWebTarget) target;
        EchoResource customerResource = rtarget.proxy(EchoResource.class);
        DummyFlag dummyFlag = rtarget.proxy(DummyFlag.class);

        // Create subclass of DummyAbstractClass with valid values
        DummySubclass validDummySubclass = new DummySubclass();
        validDummySubclass.setDirection("north");
        validDummySubclass.setSpeed(10);

        // Create subclass of DummyAbstractClass with an invalid value (speed should be greater than 0)
        DummySubclass invalidDummySubclass = new DummySubclass();
        invalidDummySubclass.setDirection("north");
        invalidDummySubclass.setSpeed(0);

        Response response = customerResource.validateEchoThroughAbstractClass(validDummySubclass);

        // Verify that we received a Bad Request Code from HTTP
        assertTrue(String.format("Return code should be 200. It was %d", response.getStatus()), 200 == response.getStatus());

        // Verify that the service call has not been executed (flag set to false)
        assertTrue("Executed flag should be true", dummyFlag.getExecutedServiceCallFlag());

        dummyFlag.clearExecution();

        Response response2 = customerResource.validateEchoThroughAbstractClass(invalidDummySubclass);

        // Verify that we received a Bad Request Code from HTTP
        assertTrue(String.format("Return code should either be 400 or 500. It was %d", response2.getStatus()), 400 == response2.getStatus() || 500 == response2.getStatus());

        // Verify that the service call has not been executed (flag set to false)
        assertFalse("Executed flag should be false", dummyFlag.getExecutedServiceCallFlag());
    }



}
