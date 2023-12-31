/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.naming.remote.multiple;

import javax.naming.InitialContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;

@WebServlet(name = "BindRmiServlet", urlPatterns = {"/BindRmiServlet"}, loadOnStartup = 1)
public class BindRmiServlet extends HttpServlet {
    public void init() throws ServletException {
        try {
            InitialContext ctx = new InitialContext();
            ctx.bind("java:jboss/exported/loc/stub", new MyObject());
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
