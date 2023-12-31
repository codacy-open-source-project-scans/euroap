/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.xsite;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.infinispan.Cache;

/**
 * Servlet providing get/put access to Infinispan cache instance.
 *
 * http://127.0.0.1:8080/cache?operation=get&key=a
 * http://127.0.0.1:8080/cache?operation=put&key=a;value=b
 *
 * where keys are Strings and Values are String representations of int.
 *
 * NOTE: Caches defined in the infinispan subsystem need to be started on demand.
 * On demand in this case means either:
 * (i) through deployment of a distributable web app which uses the cache as a session cache
 * (ii) by way of @Resource(lookup=) which will cause the corresponding JNDI binding instance to be started
 * and so the cache.
 *
 * Cache instances are started in this test case by way of a res-ref in the web.xml file in order to
 * permit parametrization of the JNDI name.
 *
 * @author Richard Achmatowicz
 */
@WebServlet(urlPatterns = { CacheAccessServlet.SERVLET_PATH }, loadOnStartup = 0)
public class CacheAccessServlet extends HttpServlet {
    private static final long serialVersionUID = 9130271954748513391L;
    private static final String SERVLET_NAME = "cache";
    static final String SERVLET_PATH = "/" + SERVLET_NAME;
    private static final String OPERATION = "operation";
    private static final String GET = "get";
    private static final String PUT = "put";
    private static final String KEY = "key";
    private static final String VALUE = "value";

    public static URI createGetURI(URL baseURL, String key) throws URISyntaxException {
        return createURI(baseURL, GET, key, null);
    }

    public static URI createPutURI(URL baseURL, String key, String value) throws URISyntaxException {
        return createURI(baseURL, PUT, key, value);
    }

    public static URI createURI(URL baseURL, String operation, String key, String value) throws URISyntaxException {
        StringBuilder builder = new StringBuilder(SERVLET_NAME);
        builder.append('?').append(OPERATION).append('=').append(operation);
        builder.append('&').append(KEY).append('=').append(key);
        if (value != null) {
            builder.append('&').append(VALUE).append('=').append(value);
        }
        return baseURL.toURI().resolve(builder.toString());
    }

    @Resource(name="infinispan/cache")
    private Cache<String, Custom> cache;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String operation = req.getParameter(OPERATION);
        if (operation == null) {
            throw new ServletException(String.format("No '%s' parameter specified)", OPERATION));
        }
        //
        switch (operation) {
            case GET: {
                String key = req.getParameter(KEY);
                validateKeyParam(operation, key);
                Custom value = cache.get(key);
                if (value == null) {
                    throw new ServletException(String.format("No value is defined for key '%s'", key));
                }
                resp.setIntHeader("value", value.getValue());
                resp.getWriter().write("Success");

                break;
            }
            case PUT: {
                String key = req.getParameter(KEY);
                validateKeyParam(operation, key);
                String value = req.getParameter(VALUE);
                validateValueParam(operation, value);
                int intValue = Integer.parseInt(value);
                // put the new instance of Custom here to the cache
                // todo: difference between putting new value and modifying existing old value
                cache.put(key, new Custom(intValue));
                resp.getWriter().write("Success");
                break;
            }
            default: {
                throw new ServletException(String.format("Unknown operation '%s': valid operations are get/put)", operation));
            }
        }
    }

    private void validateKeyParam(String operation, String key) throws ServletException {
        if (key == null || key.length() == 0) {
            throw new ServletException(String.format("key parameter for operation %s is null or has zero length", operation));
        }
    }

    private void validateValueParam(String operation, String value) throws ServletException {
        if (value == null || value.length() == 0) {
            throw new ServletException(String.format("key parameter for operation %s is null or has zero length", operation));
        }
        try {
            Integer.parseInt(value) ;
        }
        catch(NumberFormatException nfe) {
            throw new ServletException(String.format("value parameter for operation %s must be int", operation));
        }
    }

    /**
     * Serializable object holding an int value
     */
    public static class Custom implements Serializable {
        private static final long serialVersionUID = -5129400250276547619L;
        private transient boolean serialized = false;
        private int value;

        public Custom(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public boolean wasSerialized() {
            return this.serialized;
        }

        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            this.serialized = true;
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            this.serialized = true;
        }
    }
}
