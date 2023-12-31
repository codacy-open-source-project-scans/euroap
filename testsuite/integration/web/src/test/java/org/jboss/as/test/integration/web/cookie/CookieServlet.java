/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.cookie;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A servlet that is used to test different way of setting and retrieving cookies.
 *
 * @author prabhat.jha@jboss.com
 */
@WebServlet(name = "CookieServlet", urlPatterns = { "/CookieServlet" })
public class CookieServlet extends HttpServlet {

    private static final long serialVersionUID = -5891682551205336273L;

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("<head><title>Cookie Servlet</title></head><body><pre>");
        setRFC2109cookies(request, response);
        out.println("sever set some cookies. verify on the client that you can see them");
        out.println("</pre></body></html>");
        out.close();
    }

    private void setRFC2109cookies(HttpServletRequest request, HttpServletResponse response) {
        // A very simple cookie
        Cookie cookie = new Cookie("simpleCookie", "jboss");
        response.addCookie(cookie);

        // A cookie with space in the value. As per ASPATCH-70, there has been some issue with this.
        cookie = new Cookie("withSpace", "jboss rocks");
        response.addCookie(cookie);

        // cookie with comment
        // TODO read servlet 2.5 spec and rfc2109, then re-fix it
        /*
         * Servlet 2.5 Cookie.java disable comment attribute
         * cookie = new Cookie("comment", "commented cookie");
         * cookie.setComment("This is a comment");
         * response.addCookie(cookie);
         */

        // cookie with expiry time. This cookie must not be set on client side
        cookie = new Cookie("expired", "expired cookie");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        cookie = new Cookie("withComma", "little,comma");
        response.addCookie(cookie);

        cookie = new Cookie("expireIn10Sec", "will expire in 10 seconds");
        cookie.setMaxAge(10);
        response.addCookie(cookie);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }
}
