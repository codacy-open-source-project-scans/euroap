/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.tx;

import java.io.IOException;

import javax.naming.InitialContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.jboss.logging.Logger;

/**
 * A servlet that initiates a transaction, uses a RequestDispatcher to include or forward to {@link TxStatusServlet}
 * and then optionally commits the transaction. Used to test that the transaction propagates and that failure to
 * commit it is properly detected.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
@WebServlet(name = "TxControlServlet", urlPatterns = "/" + TxControlServlet.URL_PATTERN)
public class TxControlServlet extends HttpServlet {

    private static final long serialVersionUID = -853278446594804509L;

    private static Logger log = Logger.getLogger(TxControlServlet.class);

    /** The name of the context to which requests are forwarded */
    private static final String forwardContext = "/tx-status";
    private static final String forwardPath = TxStatusServlet.URL_PATTERN;
    static final String URL_PATTERN = "TxControlServlet";
    static final String INNER_STATUS_HEADER = "X-Inner-Transaction-Status";
    static final String OUTER_STATUS_HEADER = "X-Outer-Transaction-Status";

    /**
     * Lookup the UserTransaction and begin a transaction.
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (log.isTraceEnabled()) {
            log.trace("[" + forwardContext + "], PathInfo: " + request.getPathInfo() + ", QueryString: "
                    + request.getQueryString() + ", ContextPath: " + request.getContextPath() + ", HeaderNames: "
                    + request.getHeaderNames() + ", isCommitted: " + response.isCommitted());
        }

        String includeParam = request.getParameter("include");
        if (includeParam == null)
            throw new IllegalStateException("No include parameter seen");
        boolean include = Boolean.valueOf(includeParam);

        String commitParam = request.getParameter("commit");
        if (commitParam == null)
            throw new IllegalStateException("No commit parameter seen");
        boolean commit = Boolean.valueOf(commitParam);

        UserTransaction transaction;
        try {
            transaction = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
            transaction.begin();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ServletContext sc = getServletContext().getContext(forwardContext);
        if (sc != null) {
//            if (log.isTraceEnabled())
                log.trace("Found ServletContext for: " + forwardContext);
            RequestDispatcher rd = sc.getRequestDispatcher(forwardPath);
            if (rd != null) {
//                if (log.isTraceEnabled())
                    log.trace("Found RequestDispatcher for: " + forwardPath);
                if (include) {
                    rd.include(request, response);
                } else {
                    rd.forward(request, response);
                }

                // Get the tx status that TxStatusServlet saw
                Integer status = (Integer) request.getAttribute(TxStatusServlet.ATTRIBUTE);
                if (status == null) {
                    throw new ServletException("No transaction status");
                }
                if (include) {
                    // We can still write to the response w/ an include, so pass the status to the client
                    response.setHeader(INNER_STATUS_HEADER, status.toString());
                } else if (status.intValue() != Status.STATUS_ACTIVE) {
                    throw new ServletException("Status is " + status);
                }

            }  else {
                throw new ServletException("No RequestDispatcher for: " + forwardContext + forwardPath);
            }
        } else {
            throw new ServletException("No ServletContext for: " + forwardContext);
        }


        try {
            // Get the tx status now
            int ourStatus = transaction.getStatus();
            if (include) {
                // We can still write to the response w/ an include, so pass the status to the client
                response.setHeader(OUTER_STATUS_HEADER, String.valueOf(ourStatus));
            } else if (ourStatus != Status.STATUS_ACTIVE) {
                throw new ServletException("Status is " + ourStatus);
            }
            if (commit) {
                transaction.commit();
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }

    }

}
