/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.web.authentication;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.test.security.common.elytron.ServletElytronDomainSetup;

/**
 * Validates that a user remains authenticated following failover when using BASIC authentication.
 * @author Paul Ferraro
 */
@RunWith(Arquillian.class)
@ServerSetup({BasicAuthenticationWebFailoverTestCase.ElytronDomainSetupOverride.class, BasicAuthenticationWebFailoverTestCase.ServletElytronDomainSetupOverride.class})
public class BasicAuthenticationWebFailoverTestCase extends AbstractClusteringTestCase {

    private static final String MODULE_NAME = BasicAuthenticationWebFailoverTestCase.class.getSimpleName();
    private static final String SECURITY_DOMAIN_NAME = "authentication";

    @Deployment(name = DEPLOYMENT_1, managed = false, testable = false)
    @TargetsContainer(NODE_1)
    public static Archive<?> deployment0() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false, testable = false)
    @TargetsContainer(NODE_2)
    public static Archive<?> deployment1() {
        return getDeployment();
    }

    private static Archive<?> getDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, MODULE_NAME + ".war");
        war.addClass(SecureServlet.class);
        war.setWebXML(SecureServlet.class.getPackage(), "web-basic.xml");
        war.addAsWebInfResource(SecureServlet.class.getPackage(), "jboss-web.xml", "jboss-web.xml");
        return war;
    }

    @Test
    public void test(
            @ArquillianResource(SecureServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
            @ArquillianResource(SecureServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2)
            throws IOException, URISyntaxException {

        CredentialsProvider provider = new BasicCredentialsProvider();
        HttpClient client = HttpClients.custom().setDefaultCredentialsProvider(provider).build();

        URI uri1 = SecureServlet.createURI(baseURL1);
        URI uri2 = SecureServlet.createURI(baseURL2);

        try {
            // Valid login, invalid role
            setCredentials(provider, "forbidden", "password", baseURL1, baseURL2);
            HttpResponse response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatusLine().getStatusCode());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            // Invalid login, valid role
            setCredentials(provider, "allowed", "bad", baseURL1, baseURL2);
            response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            // Valid login, valid role
            setCredentials(provider, "allowed", "password", baseURL1, baseURL2);
            String sessionId = null;
            response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertNotNull(response.getFirstHeader(SecureServlet.SESSION_ID_HEADER));
                sessionId = response.getFirstHeader(SecureServlet.SESSION_ID_HEADER).getValue();
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            undeploy(DEPLOYMENT_1);

            response = client.execute(new HttpGet(uri2));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(sessionId, response.getFirstHeader(SecureServlet.SESSION_ID_HEADER).getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            deploy(DEPLOYMENT_1);

            response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(sessionId, response.getFirstHeader(SecureServlet.SESSION_ID_HEADER).getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }
        } finally {
            HttpClientUtils.closeQuietly(client);
        }
    }

    private static void setCredentials(CredentialsProvider provider, String user, String password, URL... urls) {
        for (URL url: urls) {
            provider.setCredentials(new AuthScope(url.getHost(), url.getPort()), new UsernamePasswordCredentials(user, password));
        }
    }

    static class ElytronDomainSetupOverride extends ElytronDomainServerSetupTask {

        public ElytronDomainSetupOverride() {
            super(SECURITY_DOMAIN_NAME);
        }
    }

    static class ServletElytronDomainSetupOverride extends ServletElytronDomainSetup {

        protected ServletElytronDomainSetupOverride() {
            super(SECURITY_DOMAIN_NAME, false);
        }
    }
}
