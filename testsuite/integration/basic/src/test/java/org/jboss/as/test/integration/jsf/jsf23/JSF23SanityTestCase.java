/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jsf.jsf23;

import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A simple test to verify that Jakarta Server Faces 2.3 is used.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class JSF23SanityTestCase {
    @ArquillianResource
    private URL url;

    private final Pattern viewStatePattern = Pattern.compile("id=\".*jakarta.faces.ViewState.*\" value=\"([^\"]*)\"");

    @Deployment(testable = false)
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "registration-jsf.war");
        war.addClass(ConfirmBean.class);
        war.addClass(ConfirmRegistrationBehavior.class);
        war.addClass(DummyBean.class);
        war.addAsWebResource(JSF23SanityTestCase.class.getPackage(), "index.xhtml", "index.xhtml");
        war.addAsWebResource(JSF23SanityTestCase.class.getPackage(), "confirmation.xhtml", "confirmation.xhtml");
        war.addAsWebInfResource(JSF23SanityTestCase.class.getPackage(), "beans.xml", "beans.xml");
        war.addAsWebInfResource(JSF23SanityTestCase.class.getPackage(), "register.taglib.xml", "register.taglib.xml");
        war.addAsWebInfResource(JSF23SanityTestCase.class.getPackage(), "faces-config.xml", "faces-config.xml");
        war.addAsManifestResource(new StringAsset("Manifest-Version: 1.0\n"), "MANIFEST.MF");
        return war;
    }

    @Test
    public void testJSF23InjectCanBeUsed() throws Exception {
        String responseString;
        DefaultHttpClient client = new DefaultHttpClient();

        try {
            // Create and execute a GET request
            String jsfViewState = null;
            String requestUrl = url.toString() + "index.jsf";
            HttpGet getRequest = new HttpGet(requestUrl);
            HttpResponse response = client.execute(getRequest);
            try {
                responseString = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

                // Get the Jakarta Server Faces view state
                Matcher jsfViewMatcher = viewStatePattern.matcher(responseString);
                if (jsfViewMatcher.find()) {
                    jsfViewState = jsfViewMatcher.group(1);
                }
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            // Create and execute a POST request
            HttpPost post = new HttpPost(requestUrl);

            List<NameValuePair> list = new ArrayList<NameValuePair>();
            list.add(new BasicNameValuePair("jakarta.faces.ViewState", jsfViewState));
            list.add(new BasicNameValuePair("register", "register"));
            list.add(new BasicNameValuePair("register:registerButton", "Register"));

            post.setEntity(new StringEntity(URLEncodedUtils.format(list, StandardCharsets.UTF_8), ContentType.APPLICATION_FORM_URLENCODED));
            response = client.execute(post);

            try {
                responseString = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            } finally {
                HttpClientUtils.closeQuietly(response);
            }
        } finally {
            HttpClientUtils.closeQuietly(client);
        }
        assertTrue(responseString.contains("Jakarta Server Faces 2.3 Inject worked!"));
    }
}
