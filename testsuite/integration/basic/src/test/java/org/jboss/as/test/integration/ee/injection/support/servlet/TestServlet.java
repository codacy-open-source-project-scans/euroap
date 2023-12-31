/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ee.injection.support.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jboss.as.test.integration.ee.injection.support.Alpha;
import org.jboss.as.test.integration.ee.injection.support.AroundConstructBinding;
import org.jboss.as.test.integration.ee.injection.support.AroundConstructInterceptor;
import org.jboss.as.test.integration.ee.injection.support.Bravo;
import org.jboss.as.test.integration.ee.injection.support.ComponentInterceptor;
import org.jboss.as.test.integration.ee.injection.support.ComponentInterceptorBinding;
import org.jboss.as.test.integration.ee.injection.support.ProducedString;

@SuppressWarnings("serial")
@AroundConstructBinding
@ComponentInterceptorBinding
@WebServlet("/TestServlet")
public class TestServlet extends HttpServlet {

    @Inject
    private Alpha alpha;

    private Bravo bravo;

    private String name;

    @Inject
    public void setBravo(Bravo bravo) {
        this.bravo = bravo;
    }

    @Inject
    public TestServlet(@ProducedString String name) {
        this.name = name + "#TestServlet";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String mode = req.getParameter("mode");
        resp.setContentType("text/plain");

        if ("field".equals(mode)) {
            assertNotNull(alpha);
            resp.getWriter().append(alpha.getId());
        } else if ("method".equals(mode)) {
            assertNotNull(bravo);
            resp.getWriter().append(bravo.getAlphaId());
        } else if ("constructor".equals(mode)) {
            assertNotNull(name);
            assertTrue(name.contains("Joe"));
            resp.getWriter().append(name);
        } else if ("interceptorReset".equals(mode)) {
            // Reset the interceptions - it will be incremented at the end of the service() method invocation
            ComponentInterceptor.resetInterceptions();
            assertEquals(0, ComponentInterceptor.getInterceptions().size());
            resp.getWriter().append(""+ComponentInterceptor.getInterceptions().size());
        } else if ("aroundInvokeVerify".equals(mode)) {
            assertEquals("Servlet invocation not intercepted", 1, ComponentInterceptor.getInterceptions().size());
            assertEquals("service", ComponentInterceptor.getInterceptions().get(0).getMethodName());
            resp.getWriter().append(""+ComponentInterceptor.getInterceptions().size());
        } else if ("aroundConstructVerify".equals(mode)) {
            assertTrue("AroundConstruct interceptor method not invoked", AroundConstructInterceptor.aroundConstructCalled);
            assertNotNull(name);
            assertTrue(name.contains("AroundConstructInterceptor#"));
            resp.getWriter().append(name);
        } else {
            resp.setStatus(404);
        }
    }

}
