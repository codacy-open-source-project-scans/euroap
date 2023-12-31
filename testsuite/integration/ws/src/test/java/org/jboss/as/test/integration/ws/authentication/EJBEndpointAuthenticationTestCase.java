/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ws.authentication;

import java.net.URL;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for authentication against EJB endpoint
 *
 * @author Rostislav Svoboda
 */
@RunWith(Arquillian.class)
@RunAsClient
public class EJBEndpointAuthenticationTestCase {

    @ArquillianResource
    URL baseUrl;

    QName serviceName = new QName("http://jbossws.org/authentication", "EJB3AuthService");

    @Deployment(testable = false)
    public static Archive<?> deployment() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "jaxws-authentication-ejb.jar")
                .addClasses(EJBEndpointIface.class, EJBEndpoint.class);
        return jar;
    }

    // ------------------------------------------------------------------------------
    //
    // Tests for hello method
    //

    @Test
    public void accessHelloWithoutUsernameAndPassord() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        try {
            proxy.hello("World");
            Assert.fail("Test should fail, HTTP response '401: Unauthorized' was expected");
        } catch (WebServiceException e) {
            // failure is expected
            Assert.assertTrue("HTTPException '401: Unauthorized' was expected", e.getCause().getMessage().contains("401: Unauthorized"));
        }
    }

    @Test
    public void accessHelloWithBadPassword() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user1");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password-XYZ");

        try {
            proxy.hello("World");
            Assert.fail("Test should fail, HTTP response '401: Unauthorized' was expected");
        } catch (WebServiceException e) {
            // failure is expected
            Assert.assertTrue("HTTPException '401: Unauthorized' was expected", e.getCause().getMessage().contains("401: Unauthorized"));
        }
    }

    @Test
    public void accessHelloWithUnauthorizedUser() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user2");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password2");

        try {
            proxy.hello("World");
            Assert.fail("Test should fail, user shouldn't be allowed to invoke that method");
        } catch (WebServiceException e) {
            // failure is expected
            checkMessage(e, "hello");
        }
    }

    @Test
    public void accessHelloWithValidUser() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user1");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password1");

        final String result = proxy.hello("World");
        Assert.assertEquals("Hello World!", result);
    }


    // ------------------------------------------------------------------------------
    //
    // Tests for helloForRole method
    //

    @Test
    public void accessHelloForRoleWithInvalidRole() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user1");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password1");

        try {
            proxy.helloForRole("World");
            Assert.fail("Test should fail, user shouldn't be allowed to invoke that method");
        } catch (WebServiceException e) {
            // failure is expected
            checkMessage(e, "helloForRole");
        }
    }

    @Test
    public void accessHelloForRoleWithValidRole() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user2");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password2");

        final String result = proxy.helloForRole("World");
        Assert.assertEquals("Hello World!", result);
    }


    // ------------------------------------------------------------------------------
    //
    // Tests for helloForRoles method
    //

    @Test
    public void accessHelloForRolesWithValidRole1() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user1");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password1");

        final String result = proxy.helloForRoles("World");
        Assert.assertEquals("Hello World!", result);
    }

    @Test
    public void accessHelloForRolesWithValidRole2() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user2");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password2");

        final String result = proxy.helloForRoles("World");
        Assert.assertEquals("Hello World!", result);
    }

    @Test
    public void accessHelloForRolesWithInvalidRole() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "guest");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "guest");

        try {
            proxy.helloForRoles("World");
            Assert.fail("Test should fail, user shouldn't be allowed to invoke that method");
        } catch (WebServiceException e) {
            // failure is expected
            checkMessage(e, "helloForRoles");
        }
    }


    // ------------------------------------------------------------------------------
    //
    // Tests for helloForAll method
    //

    @Test
    public void accessHelloForAllWithValidRole1() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user1");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password1");

        final String result = proxy.helloForAll("World");
        Assert.assertEquals("Hello World!", result);
    }

    @Test
    public void accessHelloForAllWithValidRole2() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user2");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password2");

        final String result = proxy.helloForAll("World");
        Assert.assertEquals("Hello World!", result);
    }

    @Test
    public void accessHelloForAllWithValidRole3() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "guest");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "guest");

        final String result = proxy.helloForAll("World");
        Assert.assertEquals("Hello World!", result);
    }

    // ------------------------------------------------------------------------------
    //
    // Tests for helloForNone method
    //

    @Test
    public void accessHelloForNoneWithValidRole1() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user1");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password1");

        try {
            proxy.helloForNone("World");
            Assert.fail("Test should fail, user shouldn't be allowed to invoke that method");
        } catch (WebServiceException e) {
            // failure is expected
            checkMessage(e, "helloForNone");
        }
    }

    @Test
    public void accessHelloForNoneWithValidRole2() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "user2");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "password2");

        try {
            proxy.helloForNone("World");
            Assert.fail("Test should fail, user shouldn't be allowed to invoke that method");
        } catch (WebServiceException e) {
            // failure is expected
            checkMessage(e, "helloForNone");
        }
    }

    @Test
    public void accessHelloForNoneWithValidRole3() throws Exception {
        URL wsdlURL = new URL(baseUrl, "/jaxws-authentication-ejb3/EJB3AuthService?wsdl");

        Service service = Service.create(wsdlURL, serviceName);
        EJBEndpointIface proxy = service.getPort(EJBEndpointIface.class);

        Map<String, Object> reqContext = ((BindingProvider) proxy).getRequestContext();
        reqContext.put(BindingProvider.USERNAME_PROPERTY, "guest");
        reqContext.put(BindingProvider.PASSWORD_PROPERTY, "guest");

        try {
            proxy.helloForNone("World");
            Assert.fail("Test should fail, user shouldn't be allowed to invoke that method");
        } catch (WebServiceException e) {
            // failure is expected
            checkMessage(e, "helloForNone");
        }
    }

    private void checkMessage(final Throwable t, final String methodName) {
        final Pattern pattern = Pattern.compile("(.*WFLYEJB0364:.*" + methodName + ".*EJBEndpoint.*)");
        final String foundMsg = t.getMessage();
        Assert.assertTrue(String.format("Expected to find method name %s in error: %s", methodName, foundMsg),
                pattern.matcher(t.getMessage()).matches());
    }
}
