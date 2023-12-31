/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ws.wsse.crypto;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;

@WebServlet(name = "TestServlet", urlPatterns = "/*")
public class TestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            final Document document = createDocument();
            final KeyPair keyPair = createKeyPair();

            signDocument(document, keyPair.getPrivate());

            if (validateSignature(document, keyPair.getPublic())) {
                res.getWriter().print("OK");
            } else {
                res.getWriter().print("null");
            }
        } catch (Exception e) {
            res.getWriter().println(e.getClass() + ": " + e.getMessage());
        }
    }

    private static boolean validateSignature(final Document document, final PublicKey publicKey) throws Exception {
        final KeySelector ks = new KeySelector() {
            @Override
            public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException {
                return new KeySelectorResult() {
                    public Key getKey() {
                        return publicKey;
                    }
                };
            }
        };

        final DOMValidateContext context = new DOMValidateContext(ks, document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0));

        return XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context);
    }

    private static void signDocument(final Document doc, final PrivateKey privateKey) throws Exception {
        final XMLSignatureFactory xsf = XMLSignatureFactory.getInstance("DOM");

        final Reference ref = xsf.newReference(
                "",
                xsf.newDigestMethod(DigestMethod.SHA256, null),
                Collections.singletonList(xsf.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                null,
                null);

        final SignedInfo si = xsf.newSignedInfo(xsf.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
                (C14NMethodParameterSpec) null),
                xsf.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                Collections.singletonList(ref));

        final KeyInfo ki = KeyInfoFactory.getInstance().newKeyInfo(Collections.singletonList(KeyInfoFactory.getInstance().newKeyName("dummy")));

        xsf.newXMLSignature(si, ki).sign(new DOMSignContext(privateKey, doc.getDocumentElement()));
    }

    private static Document createDocument() throws IOException, SAXException, ParserConfigurationException {
        return DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader("<dummy dummy=\"dummy\"/>")));
    }

    private static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }
}
