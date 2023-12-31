/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.web.async;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase;
import org.jboss.as.test.clustering.cluster.web.async.servlet.AsyncServlet;
import org.jboss.as.test.clustering.single.web.SimpleServlet;
import org.jboss.as.test.http.util.TestHttpClientUtils;
import org.jboss.as.test.shared.CLIServerSetupTask;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test case for WFLY-3715
 * @author Paul Ferraro
 */
@RunWith(Arquillian.class)
@ServerSetup(AsyncServletTestCase.ServerSetupTask.class)
public class AsyncServletTestCase extends AbstractClusteringTestCase {

    private static final String MODULE_NAME = AsyncServletTestCase.class.getSimpleName();
    private static final String DEPLOYMENT_NAME = MODULE_NAME + ".war";

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
        WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME);
        war.addPackage(AsyncServlet.class.getPackage());
        // Take web.xml from the managed test.
        war.setWebXML(SimpleServlet.class.getPackage(), "web.xml");
        war.addAsWebInfResource(AsyncServletTestCase.class.getPackage(), "jboss-all.xml", "jboss-all.xml");
        return war;
    }

    @Test
    public void test(
            @ArquillianResource(AsyncServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
            @ArquillianResource(AsyncServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2)
            throws IOException, URISyntaxException {

        URI uri1 = AsyncServlet.createURI(baseURL1);
        URI uri2 = AsyncServlet.createURI(baseURL2);

        HttpClient client = TestHttpClientUtils.promiscuousCookieHttpClient();

        try {
            assertValue(client, uri1, 1);
            assertValue(client, uri1, 2);

            assertValue(client, uri2, 3);
            assertValue(client, uri2, 4);

            assertValue(client, uri1, 5);
            assertValue(client, uri1, 6);
        } finally {
            HttpClientUtils.closeQuietly(client);
        }
    }

    private static void assertValue(HttpClient client, URI uri, int value) throws IOException {
        HttpResponse response = client.execute(new HttpGet(uri));
        try {
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(value, Integer.parseInt(response.getFirstHeader(SimpleServlet.VALUE_HEADER).getValue()));
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    public static class ServerSetupTask extends CLIServerSetupTask {
        public ServerSetupTask() {
            this.builder.node(AbstractClusteringTestCase.THREE_NODES)
                    .setup("/subsystem=distributable-web/infinispan-session-management=attribute:add(cache-container=web, granularity=ATTRIBUTE)")
                    .teardown("/subsystem=distributable-web/infinispan-session-management=attribute:remove()")
            ;
        }
    }
}
