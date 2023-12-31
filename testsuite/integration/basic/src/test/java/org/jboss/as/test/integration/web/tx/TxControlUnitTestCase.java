/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.tx;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.URL;

import jakarta.transaction.Status;

import org.junit.Assert;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests of servlet transaction lifecycle handling, particularly AS7-5329.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
@RunWith(Arquillian.class)
@RunAsClient
public class TxControlUnitTestCase {

    private static Logger log = Logger.getLogger(TxControlUnitTestCase.class);

    private static final String STATUS_ACTIVE = String.valueOf(Status.STATUS_ACTIVE);

    @Deployment (name = "tx-control.war", testable = false)
    public static WebArchive controlDeployment() {

        WebArchive war = ShrinkWrap.create(WebArchive.class, "tx-control.war");
        war.addClass(TxControlServlet.class);

        return war;
    }

    @Deployment(name = "tx-status.war", testable = false)
    public static WebArchive statusDeployment() {

        WebArchive war = ShrinkWrap.create(WebArchive.class, "tx-status.war");
        war.addClass(TxStatusServlet.class);

        return war;
    }

    /**
     * Test a RequestDispatcher forward with tx commit.
     *
     * @throws Exception
     */
    @Test
    @OperateOnDeployment("tx-control.war")
    public void testForwardCommit(@ArquillianResource(TxControlServlet.class) URL baseURL) throws Exception {
        testURL(baseURL, false, true);
    }

    /**
     * Test a RequestDispatcher forward that fails to commit the tx.
     *
     * @throws Exception
     */
    @Test
    @OperateOnDeployment("tx-control.war")
    public void testForwardNoCommit(@ArquillianResource(TxControlServlet.class) URL baseURL) throws Exception {
        testURL(baseURL, false, false);
    }

    /**
     * Test a RequestDispatcher include with tx commit.
     *
     * @throws Exception
     */
    @Test
    @OperateOnDeployment("tx-control.war")
    public void testIncludeCommit(@ArquillianResource(TxControlServlet.class) URL baseURL) throws Exception {
        testURL(baseURL, true, true);
    }

    /**
     * Test a RequestDispatcher include that fails to commit the tx.
     *
     * @throws Exception
     */
    @Test
    @OperateOnDeployment("tx-control.war")
    public void testIncludeNoCommit(@ArquillianResource(TxControlServlet.class) URL baseURL) throws Exception {
        testURL(baseURL, true, false);
    }

    private void testURL(URL baseURL, boolean include, boolean commit) throws Exception {
        URL url = new URL(baseURL + TxControlServlet.URL_PATTERN + "?include=" + include + "&commit=" + commit);
        HttpGet httpget = new HttpGet(url.toURI());
        DefaultHttpClient httpclient = new DefaultHttpClient();

        log.trace("executing request" + httpget.getRequestLine());
        HttpResponse response = httpclient.execute(httpget);

        int statusCode = response.getStatusLine().getStatusCode();
        assertEquals("Wrong response code: " + statusCode, HttpURLConnection.HTTP_OK, statusCode);

        if (include) {
            Header outerStatus = response.getFirstHeader(TxControlServlet.OUTER_STATUS_HEADER);
            Assert.assertNotNull(TxControlServlet.OUTER_STATUS_HEADER + " is null", outerStatus);
            Header innerStatus = response.getFirstHeader(TxControlServlet.INNER_STATUS_HEADER);
            Assert.assertNotNull(TxControlServlet.INNER_STATUS_HEADER + " is null", innerStatus);
            assertEquals("Wrong inner transaction status: " + innerStatus.getValue(), STATUS_ACTIVE, innerStatus.getValue());
            assertEquals("Wrong inner transaction status: " + outerStatus.getValue(), STATUS_ACTIVE, outerStatus.getValue());
        } // else TxControlServlet is using RequestDispatcher.forward and can't write to the response


        // Unfortunately, there's no simple mechanism to test that in the commit=false case the server cleaned up
        // the uncommitted tx. The cleanup (TransactionRollbackSetupAction) rolls back the tx and logs, but this
        // does not result in any behavior visible to the client.
    }
}
