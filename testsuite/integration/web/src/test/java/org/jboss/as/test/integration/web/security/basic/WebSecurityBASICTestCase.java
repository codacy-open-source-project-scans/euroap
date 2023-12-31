/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.security.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.security.WebSecurityPasswordBasedBase;
import org.jboss.as.test.integration.web.security.SecuredServlet;
import org.jboss.as.test.integration.web.security.WebTestsSecurityDomainSetup;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Unit Test the BASIC authentication
 *
 * @author Anil Saldhana
 */
@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(WebTestsSecurityDomainSetup.class)
@Category(CommonCriteria.class)
public class WebSecurityBASICTestCase extends WebSecurityPasswordBasedBase {
    private static final Logger log = Logger.getLogger(WebSecurityBASICTestCase.class);

    private static final String JBOSS_WEB_CONTENT = "<?xml version=\"1.0\"?>\n" +
            "<jboss-web>\n" +
            "    <security-domain>" + WebTestsSecurityDomainSetup.WEB_SECURITY_DOMAIN + "</security-domain>\n" +
            "</jboss-web>";

    @Deployment
    public static WebArchive deployment() throws Exception {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "web-secure-basic.war");
        war.addClass(SecuredServlet.class);

        war.addAsWebInfResource(new StringAsset(JBOSS_WEB_CONTENT), "jboss-web.xml");
        war.addAsWebInfResource(WebSecurityBASICTestCase.class.getPackage(), "web.xml", "web.xml");

        return war;
    }

    @ArquillianResource
    private URL url;

    @Override
    protected void makeCall(String user, String pass, int expectedStatusCode) throws Exception {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(url.getHost(), url.getPort()),
                new UsernamePasswordCredentials(user, pass));
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build()) {

            HttpGet httpget = new HttpGet(url.toExternalForm() + "secured/");

            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();

            StatusLine statusLine = response.getStatusLine();
            if (entity != null) {
                log.trace("Response content length: " + entity.getContentLength());
            }
            assertEquals(expectedStatusCode, statusLine.getStatusCode());

            if (200 == expectedStatusCode) {
                // check only in case authentication was successfull
                checkResponsecontent(EntityUtils.toString(entity), user, pass);
            }
            EntityUtils.consume(entity);
        }
    }

    private void checkResponsecontent(String response, String user, String password) {
        assertNotNull("Response is 'null', we expected non-null response!", response);
        assertTrue("Remote user different from what we expected!", response.contains("Remote user: " + user));
        assertTrue("User principal different from what we expected!", response.contains("User principal: " + password));
        assertTrue("Authentication type different from what we expected!", response.contains("Authentication type: " +
                "BASIC"));
    }
}