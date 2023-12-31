/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.single.web;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Function;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * @author Paul Ferraro
 */
@WebServlet(urlPatterns = { SimpleServlet.SERVLET_PATH })
public class SimpleServlet extends HttpServlet {
    private static final long serialVersionUID = -592774116315946908L;
    private static final String SERVLET_NAME = "simple";
    static final String SERVLET_PATH = "/" + SERVLET_NAME;
    public static final String REQUEST_DURATION_PARAM = "requestduration";
    public static final String HEADER_SERIALIZED = "serialized";
    public static final String VALUE_HEADER = "value";
    public static final String SESSION_ID_HEADER = "sessionId";
    public static final String ATTRIBUTE = "test";
    public static final String HEADER_NODE_NAME = "nodename";

    public static URI createURI(URL baseURL) throws URISyntaxException {
        return createURI(baseURL.toURI());
    }

    public static URI createURI(URI baseURI) {
        return baseURI.resolve(SERVLET_NAME);
    }

    public static URI createURI(URL baseURL, int requestDuration) throws URISyntaxException {
        return createURI(baseURL.toURI(), requestDuration);
    }

    public static URI createURI(URI baseURI, int requestDuration) {
        return baseURI.resolve(SERVLET_NAME + '?' + REQUEST_DURATION_PARAM + '=' + requestDuration);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String query = request.getQueryString();
        this.getServletContext().log(String.format("[%s] %s%s", request.getMethod(), request.getRequestURI(), (query != null) ? '?' + query : ""));
        super.service(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            response.addHeader(SESSION_ID_HEADER, session.getId());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        response.addHeader(SESSION_ID_HEADER, session.getId());
        session.removeAttribute(ATTRIBUTE);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Function<HttpSession, Mutable> accessor = session -> {
            Mutable mutable = (Mutable) session.getAttribute(ATTRIBUTE);
            if (mutable == null) {
                mutable = new Mutable(0);
                session.setAttribute(ATTRIBUTE, mutable);
            }
            return mutable;
        };
        this.increment(request, response, accessor);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Function<HttpSession, Mutable> accessor = session -> {
            Mutable mutable = (Mutable) session.getAttribute(ATTRIBUTE);
            if (mutable == null) {
                session.setAttribute(ATTRIBUTE, new Mutable(0));
            }
            return (mutable == null) ? (Mutable) session.getAttribute(ATTRIBUTE) : mutable;
        };
        this.increment(request, response, accessor);
    }

    private void increment(HttpServletRequest request, HttpServletResponse response, Function<HttpSession, Mutable> accessor) throws IOException {
        HttpSession session = request.getSession(true);
        response.addHeader(SESSION_ID_HEADER, session.getId());
        Mutable mutable = accessor.apply(session);
        int value = mutable.increment();
        response.setIntHeader(VALUE_HEADER, value);
        response.setHeader(HEADER_SERIALIZED, Boolean.toString(mutable.wasSerialized()));

        Mutable current = (Mutable) session.getAttribute(ATTRIBUTE);
        if (!mutable.equals(current)) {
            throw new IllegalStateException(String.format("Session attribute value = %s, expected %s", current, mutable));
        }

        try {
            String nodeName = System.getProperty("jboss.node.name");
            response.setHeader(HEADER_NODE_NAME, nodeName);
        } catch (Exception ignore) {
        }

        this.getServletContext().log(request.getRequestURI() + ", value = " + value);

        // Long running request?
        if (request.getParameter(REQUEST_DURATION_PARAM) != null) {
            int duration = Integer.parseInt(request.getParameter(REQUEST_DURATION_PARAM));
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        response.getWriter().write("Success");
    }
}
