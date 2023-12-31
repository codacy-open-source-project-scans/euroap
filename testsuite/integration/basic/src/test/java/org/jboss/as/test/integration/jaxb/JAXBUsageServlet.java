/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jaxb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.jboss.as.test.integration.jaxb.bindings.PurchaseOrderType;

@WebServlet (urlPatterns = JAXBUsageServlet.URL_PATTERN)
public class JAXBUsageServlet extends HttpServlet {

    public static final String URL_PATTERN = "/jaxbServlet";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            Unmarshaller unmarshaller = JAXBContext.newInstance("org.jboss.as.test.integration.jaxb.bindings").createUnmarshaller();
            String xml = "<?xml version=\"1.0\"?>\n"

                    + "<purchaseOrder orderDate=\"1999-10-20\">\n" +
                    "   <shipTo country=\"US\">\n" +
                    "      <name>Alice Smith</name>\n" +
                    "      <street>123 Maple Street</street>\n" +
                    "      <city>Mill Valley</city>\n" +
                    "      <state>CA</state>\n" +
                    "      <zip>90952</zip>\n" +
                    "   </shipTo>\n" +
                    "   <billTo country=\"US\">\n" +
                    "      <name>Robert Smith</name>\n" +
                    "      <street>8 Oak Avenue</street>\n" +
                    "      <city>Old Town</city>\n" +
                    "      <state>PA</state>\n" +
                    "      <zip>95819</zip>\n" +
                    "   </billTo>\n" +
                    "   <comment>Hurry, my lawn is going wild!</comment>\n" +
                    "   <items>\n" +
                    "      <item partNum=\"872-AA\">\n" +
                    "         <productName>Lawnmower</productName>\n" +
                    "         <quantity>1</quantity>\n" +
                    "         <USPrice>148.95</USPrice>\n" +
                    "         <comment>Confirm this is electric</comment>\n" +
                    "      </item>\n" +
                    "      <item partNum=\"926-AA\">\n" +
                    "         <productName>Baby Monitor</productName>\n" +
                    "         <quantity>1</quantity>\n" +
                    "         <USPrice>39.98</USPrice>\n" +
                    "         <shipDate>1999-05-21</shipDate>\n" +
                    "      </item>\n" +
                    "   </items>\n" +
                    "</purchaseOrder>";

            PurchaseOrderType order = ((JAXBElement<PurchaseOrderType>)unmarshaller.unmarshal(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))).getValue();
            resp.getOutputStream().println(order.getShipTo().getCity());
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }

    }


}
