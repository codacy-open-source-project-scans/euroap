/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.elytron.ssl;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.jboss.as.test.integration.security.common.SSLTruststoreUtil.HTTPS_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.security.auth.x500.X500Principal;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codehaus.plexus.util.FileUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.SSLTruststoreUtil;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.integration.security.common.Utils;
import org.jboss.as.test.integration.security.common.servlets.SimpleServlet;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.test.security.common.AbstractElytronSetupTask;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.CredentialReference;
import org.wildfly.test.security.common.elytron.Path;
import org.wildfly.test.security.common.elytron.SimpleKeyManager;
import org.wildfly.test.security.common.elytron.SimpleKeyStore;
import org.wildfly.test.security.common.elytron.SimpleServerSslContext;
import org.wildfly.test.security.common.elytron.UndertowSslContext;

/**
 * Test that a self-signed certificate is not automatically generated if the keystore
 * file already exists.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
@RunWith(Arquillian.class)
@ServerSetup({ AutomaticSelfSignedCertificateNotGeneratedTestCase.ElytronAndUndertowSetupTask.class })
@RunAsClient
@Category(CommonCriteria.class)
public class AutomaticSelfSignedCertificateNotGeneratedTestCase {

    private static final String NAME = AutomaticSelfSignedCertificateNotGeneratedTestCase.class.getSimpleName();
    private static final File WORK_DIR = new File("target" + File.separatorChar +  NAME);
    private static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    private static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);
    private static final String PASSWORD = SecurityTestConstants.KEYSTORE_PASSWORD;
    private static final String GENERATE_SELF_SIGNED_CERTIFICATE_HOST="customHostName";
    private static final String SERVER_ALIAS = "server";
    private static final String EXISTING_HOST = "existingHost";
    private static final byte[] IV = {
            0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,
            0x19,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F,0x20
    };
    private static final byte[] SALT = {
            0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08
    };
    private static final int ITERATION_COUNT = 1024;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, NAME + ".war").addClasses(SimpleServlet.class);
    }

    @Test
    public void testSelfSignedCertificateNotGeneratedIfKeyStoreFileExists() throws Exception {
        assertTrue(SERVER_KEYSTORE_FILE.exists());
        assertTrue(CLIENT_TRUSTSTORE_FILE.exists());

        final URL servletUrl = new URL("https", TestSuiteEnvironment.getServerAddress(), HTTPS_PORT,
                "/" + NAME + "/" + SimpleServlet.SERVLET_PATH.substring(1));
        HttpClient client = SSLTruststoreUtil.getHttpClientWithSSL(CLIENT_TRUSTSTORE_FILE, PASSWORD, "PKCS12");
        try {
            Utils.makeCallWithHttpClient(servletUrl, client, SC_OK);
            try (FileInputStream is = new FileInputStream(SERVER_KEYSTORE_FILE.getAbsolutePath())) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(is, PASSWORD.toCharArray());
                assertEquals(1, keyStore.size());
                X509Certificate serverCert = (X509Certificate) keyStore.getCertificate(SERVER_ALIAS);
                assertEquals("CN=" + EXISTING_HOST, serverCert.getSubjectX500Principal().getName());
            }
        } catch (IOException | URISyntaxException ex) {
            throw new IllegalStateException("Unable to request server root over HTTPS", ex);
        } finally {
            closeClient(client);
        }
    }

    @Test
    public void testSelfSignedCertificateNotGeneratedWithGenerateAttributeUndefined() throws Exception {
        // undefine generate-self-signed-certificate-host
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine(String.format("/subsystem=elytron/key-manager=%s:undefine-attribute(name=generate-self-signed-certificate-host)",
                    NAME));
            cli.sendLine(String.format("reload"));
        }
        try {
            assertTrue(SERVER_KEYSTORE_FILE.exists());
            assertTrue(CLIENT_TRUSTSTORE_FILE.exists());

            final URL servletUrl = new URL("https", TestSuiteEnvironment.getServerAddress(), HTTPS_PORT,
                    "/" + NAME + "/" + SimpleServlet.SERVLET_PATH.substring(1));
            HttpClient client = SSLTruststoreUtil.getHttpClientWithSSL(CLIENT_TRUSTSTORE_FILE, PASSWORD, "PKCS12");
            try {
                Utils.makeCallWithHttpClient(servletUrl, client, SC_OK);
                try (FileInputStream is = new FileInputStream(SERVER_KEYSTORE_FILE.getAbsolutePath())) {
                    KeyStore keyStore = KeyStore.getInstance("PKCS12");
                    keyStore.load(is, PASSWORD.toCharArray());
                    assertEquals(1, keyStore.size());
                    X509Certificate serverCert = (X509Certificate) keyStore.getCertificate(SERVER_ALIAS);
                    assertEquals("CN=" + EXISTING_HOST, serverCert.getSubjectX500Principal().getName());
                }
            } catch (IOException | URISyntaxException ex) {
                throw new IllegalStateException("Unable to request server root over HTTPS", ex);
            } finally {
                closeClient(client);
            }
        } finally {
            // clean up
            try (CLIWrapper cli = new CLIWrapper(true)) {
                cli.sendLine(String.format("/subsystem=elytron/key-manager=%s:write-attribute(name=generate-self-signed-certificate-host,value=%s)",
                        NAME, GENERATE_SELF_SIGNED_CERTIFICATE_HOST));
                cli.sendLine(String.format("reload"));
            }
        }
    }

    static class ElytronAndUndertowSetupTask extends AbstractElytronSetupTask {

        @Override
        protected void setup(final ModelControllerClient modelControllerClient) throws Exception {
            createServerKeyStoreAndClientTrustStore();
            super.setup(modelControllerClient);
        }

        @Override
        protected ConfigurableElement[] getConfigurableElements() {
            return new ConfigurableElement[] {
                    SimpleKeyStore.builder().withName(NAME + SecurityTestConstants.SERVER_KEYSTORE)
                            .withPath(Path.builder().withPath(SERVER_KEYSTORE_FILE.getAbsolutePath()).build())
                            .withCredentialReference(CredentialReference.builder().withClearText(PASSWORD).build())
                            .withType("PKCS12")
                            .build(),
                    SimpleKeyManager.builder().withName(NAME)
                            .withKeyStore(NAME + SecurityTestConstants.SERVER_KEYSTORE)
                            .withCredentialReference(CredentialReference.builder().withClearText(PASSWORD).build())
                            .withGenerateSelfSignedCertificateHost(GENERATE_SELF_SIGNED_CERTIFICATE_HOST)
                            .build(),
                    SimpleServerSslContext.builder().withName(NAME)
                            .withKeyManagers(NAME)
                            .build(),
                    UndertowSslContext.builder().withName(NAME).build()
            };
        }

        @Override
        protected void tearDown(ModelControllerClient modelControllerClient) throws Exception {
            super.tearDown(modelControllerClient);
            FileUtils.deleteDirectory(WORK_DIR);
        }

        private void createServerKeyStoreAndClientTrustStore() throws Exception {
            if (WORK_DIR.exists()) {
                FileUtils.deleteDirectory(WORK_DIR);
            }
            WORK_DIR.mkdirs();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            SelfSignedX509CertificateAndSigningKey selfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                    .setDn(new X500Principal("CN=existingHost"))
                    .setKeyAlgorithmName("RSA")
                    .setSignatureAlgorithmName("SHA256withRSA")
                    .build();
            PrivateKey privateKey = selfSignedX509CertificateAndSigningKey.getSigningKey();
            X509Certificate serverCert = selfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();
            KeyStore.Entry entry = new KeyStore.PrivateKeyEntry(privateKey, new X509Certificate[]{ serverCert });
            KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection(PASSWORD.toCharArray(), "PBEWithHmacSHA256AndAES_128",
                    new PBEParameterSpec(SALT, ITERATION_COUNT, new IvParameterSpec(IV)));
            ks.setEntry("server", entry, passwordProtection);
            try (FileOutputStream fos = new FileOutputStream(SERVER_KEYSTORE_FILE)) {
                ks.store(fos, PASSWORD.toCharArray());
            }
            try (FileOutputStream fos = new FileOutputStream(CLIENT_TRUSTSTORE_FILE)) {
                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                trustStore.load(null, null);
                trustStore.setCertificateEntry("server", serverCert);
                trustStore.store(fos, PASSWORD.toCharArray());
            }
        }
    }

    private void closeClient(HttpClient client) {
        try {
            ((CloseableHttpClient) client).close();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to close HTTP client", ex);
        }
    }
}
