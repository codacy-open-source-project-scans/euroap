/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet which makes a simple test on the given datasource ("ExampleDS" by default, use {@value #PARAM_DS} request parameter
 * to change the tested datasource). This servlet lookups the datasource through JNDI and follows these steps:
 * <ol>
 * <li>get Connection from DS</li>
 * <li>create Statement</li>
 * <li>create table</li>
 * <li>insert record</li>
 * <li>run query and check if the inserted record is returned</li>
 * <li>drop table</li>
 * <li>close resources</li>
 * <ol>
 *
 * If everything finishes as expected then "true" is returned as the response body. If query doesn't return expected value then
 * the response body is "false".<br>
 * The response contains stack trace in case of SQLException or NamingException.<br>
 * The response content type is text/plain.
 *
 * @author Josef Cacek
 */
@WebServlet(DataSourceTestServlet.SERVLET_PATH)
public class DataSourceTestServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String SERVLET_PATH = "/DataSourceTestServlet";

    public static final String PARAM_DS = "datasource";
    public static final String PARAM_DS_DEFAULT = "ExampleDS";

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");

        String datasourceName = req.getParameter(PARAM_DS);
        if (datasourceName == null || datasourceName.length() == 0) {
            datasourceName = PARAM_DS_DEFAULT;
        }
        final PrintWriter writer = resp.getWriter();

        Connection con = null;

        try {
            InitialContext iniCtx = new InitialContext();
            DataSource ds = (DataSource) iniCtx.lookup("java:jboss/datasources/" + datasourceName);
            con = ds.getConnection();
            Statement stmt = con.createStatement();
            stmt.executeUpdate("create table testtable(testid int)");
            stmt.executeUpdate("insert into testtable values (1)");
            ResultSet rs = stmt.executeQuery("select * from testtable");
            writer.print(rs.next() && 1 == rs.getInt(1));
            rs.close();
            stmt.executeUpdate("drop table testtable");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace(writer);
        } catch (NamingException e) {
            e.printStackTrace(writer);
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
