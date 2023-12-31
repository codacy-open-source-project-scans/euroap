/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jsf.flash;

import java.net.URL;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class FlashScopeDistributedSessionTestCase {

    @ArquillianResource
    URL url;

    @Deployment
    public static Archive<?> deploy() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "jsfflashscope.war");
        Package pakkage = FlashScopeDistributedSessionTestCase.class.getPackage();
        war.addAsWebInfResource(pakkage, "web.xml", "web.xml");
        war.addAsWebInfResource(pakkage, "faces-config.xml", "faces-config.xml");
        war.addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        war.addAsWebResource(pakkage, "getvar.xhtml", "getvar.xhtml");
        war.addAsWebResource(pakkage, "getvar2.xhtml", "getvar2.xhtml");
        war.addAsWebResource(pakkage, "setvar.xhtml", "setvar.xhtml");
        war.addAsWebResource(pakkage, "setvar2.xhtml", "setvar2.xhtml");
        return war;
    }

    @Test
    public void testSettingAndReadingFlashVar() throws Exception {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        try (CloseableHttpClient client = httpClientBuilder.build()) {

            HttpUriRequest setVarRequest = new HttpGet(url.toExternalForm() + "setvar.jsf");
            HttpUriRequest getVarRequest = new HttpGet(url.toExternalForm() + "getvar.jsf");
            client.execute(setVarRequest).close();
            try (CloseableHttpResponse getVarResponse = client.execute(getVarRequest)) {
                String text = EntityUtils.toString(getVarResponse.getEntity());
                Assert.assertTrue("Text should contain \"Flash Variable: hello\", but is [" + text + "]", text.contains("Flash Variable: hello"));
            }
        }
    }

    @Test
    public void testReadingEmptyFlashVar() throws Exception {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        try (CloseableHttpClient client = httpClientBuilder.build()) {

            HttpUriRequest getVarRequest = new HttpGet(url.toExternalForm() + "getvar2.jsf");
            try (CloseableHttpResponse getVarResponse = client.execute(getVarRequest)) {
                String text = EntityUtils.toString(getVarResponse.getEntity());
                Assert.assertTrue("Text should contain \"Flash Variable: null\", but is [" + text + "]", text.contains("Flash Variable: null"));
            }
        }
    }

    @Test
    public void testPuttingFlashVarOnNonTransientPage() throws Exception {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        try (CloseableHttpClient client = httpClientBuilder.build()) {

            HttpUriRequest setVarRequest = new HttpGet(url.toExternalForm() + "setvar2.jsf");
            try (CloseableHttpResponse setVarResponse = client.execute(setVarRequest)) {
                String text = EntityUtils.toString(setVarResponse.getEntity());
                Assert.assertEquals("Put operation failedwith: " + text, 200, setVarResponse.getStatusLine().getStatusCode());
            }
        }
    }
}
