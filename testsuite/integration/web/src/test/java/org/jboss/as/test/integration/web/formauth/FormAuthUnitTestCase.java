/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.formauth;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests of form authentication
 *
 * @author Scott.Stark@jboss.org
 * @author lbarreiro@redhat.com
 */
@RunWith(Arquillian.class)
@RunAsClient
public class FormAuthUnitTestCase {

    private static Logger log = Logger.getLogger(FormAuthUnitTestCase.class);

    @ArquillianResource
    private URL baseURLNoAuth;

    @ArquillianResource
    private ManagementClient managementClient;

    private static final String USERNAME = "user2";
    private static final String PASSWORD = "password2";

    DefaultHttpClient httpclient = new DefaultHttpClient();

    @Deployment(name="form-auth.war", testable = false)
    public static WebArchive deployment() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        String resourcesLocation = "org/jboss/as/test/integration/web/formauth/resources/";

        WebArchive war = ShrinkWrap.create(WebArchive.class, "form-auth.war");
        war.setWebXML(tccl.getResource(resourcesLocation + "web.xml"));
        war.addAsWebInfResource(tccl.getResource(resourcesLocation + "jboss-web.xml"), "jboss-web.xml");

        war.addClass(SecureServlet.class);
        war.addClass(SecuredPostServlet.class);
        war.addClass(LogoutServlet.class);

        war.addAsWebResource(tccl.getResource(resourcesLocation + "index.html"), "index.html");
        war.addAsWebResource(tccl.getResource(resourcesLocation + "unsecure_form.html"), "unsecure_form.html");
        war.addAsWebResource(tccl.getResource(resourcesLocation + "restricted/errors.jsp"), "restricted/errors.jsp");
        war.addAsWebResource(tccl.getResource(resourcesLocation + "restricted/error.html"), "restricted/error.html");
        war.addAsWebResource(tccl.getResource(resourcesLocation + "restricted/login.html"), "restricted/login.html");

        return war;
    }

    /**
     * Test form authentication of a secured servlet
     *
     * @throws Exception
     */
    @Test
    @OperateOnDeployment("form-auth.war")
    public void testFormAuth() throws Exception {
        log.trace("+++ testFormAuth");
        doSecureGetWithLogin("restricted/SecuredServlet");
        /*
         * Access the resource without attempting a login to validate that the
         * session is valid and that any caching on the server is working as
         * expected.
         */
        doSecureGet("restricted/SecuredServlet");
    }

    /**
     * Test that a bad login is redirected to the errors.jsp and that the
     * session j_exception is not null.
     */
    @Test
    @OperateOnDeployment("form-auth.war")
    public void testFormAuthException() throws Exception {
        log.trace("+++ testFormAuthException");

        URL url = new URL(baseURLNoAuth + "restricted/SecuredServlet");
        HttpGet httpget = new HttpGet(url.toURI());

        log.trace("Executing request " + httpget.getRequestLine());
        HttpResponse response = httpclient.execute(httpget);

        int statusCode = response.getStatusLine().getStatusCode();
        Header[] errorHeaders = response.getHeaders("X-NoJException");
        assertTrue("Wrong response code: " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);

        HttpEntity entity = response.getEntity();
        if ((entity != null) && (entity.getContentLength() > 0)) {
            String body = EntityUtils.toString(entity);
            assertTrue("Redirected to login page", body.indexOf("j_security_check") > 0);
        } else {
            fail("Empty body in response");
        }

        String sessionID = null;
        for (Cookie k : httpclient.getCookieStore().getCookies()) {
            if (k.getName().equalsIgnoreCase("JSESSIONID"))
                sessionID = k.getValue();
        }
        log.trace("Saw JSESSIONID=" + sessionID);

        // Submit the login form
        HttpPost formPost = new HttpPost(baseURLNoAuth + "j_security_check");
        formPost.addHeader("Referer", baseURLNoAuth + "restricted/login.html");

        List<NameValuePair> formparams = new ArrayList<NameValuePair>();
        formparams.add(new BasicNameValuePair("j_username", "baduser"));
        formparams.add(new BasicNameValuePair("j_password", "badpass"));
        formPost.setEntity(new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8));

        log.trace("Executing request " + formPost.getRequestLine());
        HttpResponse postResponse = httpclient.execute(formPost);

        statusCode = postResponse.getStatusLine().getStatusCode();
        errorHeaders = postResponse.getHeaders("X-NoJException");
        assertTrue("Should see HTTP_OK. Got " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is not null", errorHeaders.length != 0);
        log.debug("Saw X-JException, " + Arrays.toString(errorHeaders));
    }

    /**
     * Test form authentication of a secured servlet and validate that there is
     * a SecurityAssociation setting Subject.
     */
    @Test
    @OperateOnDeployment("form-auth.war")
    public void testFormAuthSubject() throws Exception {
        log.trace("+++ testFormAuthSubject");
        doSecureGetWithLogin("restricted/SecuredServlet");
    }

    /**
     * Test that a post from an unsecured form to a secured servlet does not
     * loose its data during the redirect to the form login.
     */
    @Test
    @OperateOnDeployment("form-auth.war")
    public void testPostDataFormAuth() throws Exception {
        log.trace("+++ testPostDataFormAuth");

        URL url = new URL(baseURLNoAuth + "unsecure_form.html");
        HttpGet httpget = new HttpGet(url.toURI());

        log.trace("Executing request " + httpget.getRequestLine());
        HttpResponse response = httpclient.execute(httpget);

        int statusCode = response.getStatusLine().getStatusCode();
        Header[] errorHeaders = response.getHeaders("X-NoJException");
        assertTrue("Wrong response code: " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);
        EntityUtils.consume(response.getEntity());

        // Submit the form to /restricted/SecuredPostServlet
        HttpPost restrictedPost = new HttpPost(baseURLNoAuth + "restricted/SecuredPostServlet");

        List<NameValuePair> restrictedParams = new ArrayList<NameValuePair>();
        restrictedParams.add(new BasicNameValuePair("checkParam", "123456"));
        restrictedPost.setEntity(new UrlEncodedFormEntity(restrictedParams, StandardCharsets.UTF_8));

        log.trace("Executing request " + restrictedPost.getRequestLine());
        HttpResponse restrictedResponse = httpclient.execute(restrictedPost);

        statusCode = restrictedResponse.getStatusLine().getStatusCode();
        errorHeaders = restrictedResponse.getHeaders("X-NoJException");
        assertTrue("Wrong response code: " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);

        HttpEntity entity = restrictedResponse.getEntity();
        if ((entity != null) && (entity.getContentLength() > 0)) {
            String body = EntityUtils.toString(entity);
            assertTrue("Redirected to login page", body.indexOf("j_security_check") > 0);
        } else {
            fail("Empty body in response");
        }

        String sessionID = null;
        for (Cookie k : httpclient.getCookieStore().getCookies()) {
            if (k.getName().equalsIgnoreCase("JSESSIONID"))
                sessionID = k.getValue();
        }
        log.trace("Saw JSESSIONID=" + sessionID);

        // Submit the login form
        HttpPost formPost = new HttpPost(baseURLNoAuth + "j_security_check");
        formPost.addHeader("Referer", baseURLNoAuth + "restricted/login.html");

        List<NameValuePair> formparams = new ArrayList<NameValuePair>();
        formparams.add(new BasicNameValuePair("j_username", "user1"));
        formparams.add(new BasicNameValuePair("j_password", "password1"));
        formPost.setEntity(new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8));

        log.trace("Executing request " + formPost.getRequestLine());
        HttpResponse postResponse = httpclient.execute(formPost);

        statusCode = postResponse.getStatusLine().getStatusCode();
        errorHeaders = postResponse.getHeaders("X-NoJException");
        assertTrue("Should see HTTP_MOVED_TEMP. Got " + statusCode, statusCode == HttpURLConnection.HTTP_MOVED_TEMP);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);
        EntityUtils.consume(postResponse.getEntity());

        // Follow the redirect to the SecureServlet
        Header location = postResponse.getFirstHeader("Location");
        URL indexURI = new URL(location.getValue());
        HttpGet war1Index = new HttpGet(indexURI.toURI());

        log.trace("Executing request " + war1Index.getRequestLine());
        HttpResponse war1Response = httpclient.execute(war1Index);

        statusCode = war1Response.getStatusLine().getStatusCode();
        errorHeaders = war1Response.getHeaders("X-NoJException");
        assertTrue("Wrong response code: " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);

        HttpEntity war1Entity = war1Response.getEntity();
        if ((war1Entity != null) && (entity.getContentLength() > 0)) {
            String body = EntityUtils.toString(war1Entity);
            if (body.indexOf("j_security_check") > 0)
                fail("Get of " + indexURI + " redirected to login page");
        } else {
            fail("Empty body in response");
        }
    }

    public HttpPost doSecureGetWithLogin(String path) throws Exception {
        return doSecureGetWithLogin(path, USERNAME, PASSWORD);
    }

    public HttpPost doSecureGetWithLogin(String path, String username, String password) throws Exception {
        log.trace("+++ doSecureGetWithLogin : " + path);

        URL url = new URL(baseURLNoAuth + path);
        HttpGet httpget = new HttpGet(url.toURI());

        log.trace("Executing request " + httpget.getRequestLine());
        HttpResponse response = httpclient.execute(httpget);

        int statusCode = response.getStatusLine().getStatusCode();
        Header[] errorHeaders = response.getHeaders("X-NoJException");
        assertTrue("Wrong response code: " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);

        HttpEntity entity = response.getEntity();
        if ((entity != null) && (entity.getContentLength() > 0)) {
            String body = EntityUtils.toString(entity);
            assertTrue("Redirected to login page", body.indexOf("j_security_check") > 0);
        } else {
            fail("Empty body in response");
        }

        String sessionID = null;
        for (Cookie k : httpclient.getCookieStore().getCookies()) {
            if (k.getName().equalsIgnoreCase("JSESSIONID"))
                sessionID = k.getValue();
        }
        log.trace("Saw JSESSIONID=" + sessionID);

        // Submit the login form
        HttpPost formPost = new HttpPost(baseURLNoAuth + "j_security_check");
        formPost.addHeader("Referer", baseURLNoAuth + "restricted/login.html");

        List<NameValuePair> formparams = new ArrayList<NameValuePair>();
        formparams.add(new BasicNameValuePair("j_username", username));
        formparams.add(new BasicNameValuePair("j_password", password));
        formPost.setEntity(new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8));

        log.trace("Executing request " + formPost.getRequestLine());
        HttpResponse postResponse = httpclient.execute(formPost);

        statusCode = postResponse.getStatusLine().getStatusCode();
        errorHeaders = postResponse.getHeaders("X-NoJException");
        assertTrue("Should see HTTP_MOVED_TEMP. Got " + statusCode, statusCode == HttpURLConnection.HTTP_MOVED_TEMP);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);
        EntityUtils.consume(postResponse.getEntity());

        // Follow the redirect to the SecureServlet
        Header location = postResponse.getFirstHeader("Location");
        URL indexURI = new URL(location.getValue());
        HttpGet war1Index = new HttpGet(url.toURI());

        log.trace("Executing request " + war1Index.getRequestLine());
        HttpResponse war1Response = httpclient.execute(war1Index);

        statusCode = war1Response.getStatusLine().getStatusCode();
        errorHeaders = war1Response.getHeaders("X-NoJException");
        assertTrue("Wrong response code: " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);

        HttpEntity war1Entity = war1Response.getEntity();
        if ((war1Entity != null) && (entity.getContentLength() > 0)) {
            String body = EntityUtils.toString(war1Entity);
            if (body.indexOf("j_security_check") > 0)
                fail("Get of " + indexURI + " redirected to login page");
        } else {
            fail("Empty body in response");
        }

        return formPost;
    }

    public void doSecureGet(String path) throws Exception {
        log.trace("+++ doSecureGet : " + path);

        String sessionID = null;
        for (Cookie k : httpclient.getCookieStore().getCookies()) {
            if (k.getName().equalsIgnoreCase("JSESSIONID"))
                sessionID = k.getValue();
        }
        log.trace("Saw JSESSIONID=" + sessionID);

        URL url = new URL(baseURLNoAuth + path);
        HttpGet httpget = new HttpGet(url.toURI());

        log.trace("Executing request" + httpget.getRequestLine());
        HttpResponse response = httpclient.execute(httpget);

        int statusCode = response.getStatusLine().getStatusCode();
        Header[] errorHeaders = response.getHeaders("X-NoJException");
        assertTrue("Wrong response code: " + statusCode, statusCode == HttpURLConnection.HTTP_OK);
        assertTrue("X-NoJException(" + Arrays.toString(errorHeaders) + ") is null", errorHeaders.length == 0);
    }
}
