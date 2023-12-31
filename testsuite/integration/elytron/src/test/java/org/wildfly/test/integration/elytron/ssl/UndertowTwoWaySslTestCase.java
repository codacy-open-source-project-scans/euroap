/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.elytron.ssl;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.jboss.as.test.integration.security.common.SSLTruststoreUtil.HTTPS_PORT;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codehaus.plexus.util.FileUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SSLTruststoreUtil;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.integration.security.common.Utils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.test.integration.elytron.util.WelcomeContent;
import org.wildfly.test.security.common.AbstractElytronSetupTask;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.CredentialReference;
import org.wildfly.test.security.common.elytron.Path;
import org.wildfly.test.security.common.elytron.SimpleKeyManager;
import org.wildfly.test.security.common.elytron.SimpleKeyStore;
import org.wildfly.test.security.common.elytron.SimpleServerSslContext;
import org.wildfly.test.security.common.elytron.SimpleTrustManager;
import org.wildfly.test.security.common.elytron.UndertowSslContext;

/**
 * Smoke test for two way SSL connection with Undertow HTTPS listener backed by Elytron server-ssl-context with default
 * settings (client certificate is not required).
 *
 * In case the client certificate is not trusted or present, the request should be successful.
 *
 * @author Ondrej Kotek
 */
@RunWith(Arquillian.class)
@ServerSetup({ UndertowTwoWaySslTestCase.ElytronSslContextInUndertowSetupTask.class, WelcomeContent.SetupTask.class })
@RunAsClient
public class UndertowTwoWaySslTestCase {

    private static final String NAME = UndertowTwoWaySslTestCase.class.getSimpleName();
    private static final File WORK_DIR = new File("target" + File.separatorChar +  NAME);
    private static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    private static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    private static final File CLIENT_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_KEYSTORE);
    private static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);
    private static final File UNTRUSTED_STORE_FILE = new File(WORK_DIR, SecurityTestConstants.UNTRUSTED_KEYSTORE);
    private static final String PASSWORD = SecurityTestConstants.KEYSTORE_PASSWORD;

    private static URL securedRootUrl;

    // just to make server setup task work
    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, NAME + ".war")
                .add(new StringAsset("index page"), "index.html");
    }

    @BeforeClass
    public static void setSecuredRootUrl() throws Exception {
        try {
            securedRootUrl = new URL("https", TestSuiteEnvironment.getServerAddress(), HTTPS_PORT, "");
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Unable to create HTTPS URL to server root", ex);
        }
    }

    @Test
    public void testSendingTrustedClientCertificate() {
        HttpClient client = SSLTruststoreUtil
                .getHttpClientWithSSL(CLIENT_KEYSTORE_FILE, PASSWORD, CLIENT_TRUSTSTORE_FILE, PASSWORD);
        assertConnectionToServer(client, SC_OK);
        closeClient(client);
    }

    @Test
    public void testSendingNonTrustedClientCertificate() {
        HttpClient client = SSLTruststoreUtil
                .getHttpClientWithSSL(UNTRUSTED_STORE_FILE, PASSWORD, CLIENT_TRUSTSTORE_FILE, PASSWORD);
        assertConnectionToServer(client, SC_OK);
        closeClient(client);
    }

    @Test
    public void testSendingNoClientCertificate() {
        HttpClient client = SSLTruststoreUtil.getHttpClientWithSSL(CLIENT_TRUSTSTORE_FILE, PASSWORD);
        assertConnectionToServer(client, SC_OK);
        closeClient(client);
    }

    private void assertConnectionToServer(HttpClient client, int expectedStatusCode) {
        try {
            Utils.makeCallWithHttpClient(securedRootUrl, client, expectedStatusCode);
        } catch (IOException | URISyntaxException ex) {
            throw new IllegalStateException("Unable to request server root over HTTPS", ex);
        }
    }

    private void closeClient(HttpClient client) {
        try {
            ((CloseableHttpClient) client).close();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to close HTTP client", ex);
        }
    }

    /**
     * Creates Elytron server-ssl-context and key/trust stores.
     */
    static class ElytronSslContextInUndertowSetupTask extends AbstractElytronSetupTask {

        @Override
        protected void setup(final ModelControllerClient modelControllerClient) throws Exception {
            keyMaterialSetup(WORK_DIR);
            super.setup(modelControllerClient);
        }

        @Override
        protected ConfigurableElement[] getConfigurableElements() {
            return new ConfigurableElement[] {
                SimpleKeyStore.builder().withName(NAME + SecurityTestConstants.SERVER_KEYSTORE)
                        .withPath(Path.builder().withPath(SERVER_KEYSTORE_FILE.getPath()).build())
                        .withCredentialReference(CredentialReference.builder().withClearText(PASSWORD).build())
                        .build(),
                SimpleKeyStore.builder().withName(NAME + SecurityTestConstants.SERVER_TRUSTSTORE)
                        .withPath(Path.builder().withPath(SERVER_TRUSTSTORE_FILE.getPath()).build())
                        .withCredentialReference(CredentialReference.builder().withClearText(PASSWORD).build())
                        .build(),
                SimpleKeyManager.builder().withName(NAME)
                        .withKeyStore(NAME + SecurityTestConstants.SERVER_KEYSTORE)
                        .withCredentialReference(CredentialReference.builder().withClearText(PASSWORD).build())
                        .build(),
                SimpleTrustManager.builder().withName(NAME)
                        .withKeyStore(NAME + SecurityTestConstants.SERVER_TRUSTSTORE)
                        .build(),
                SimpleServerSslContext.builder().withName(NAME).withKeyManagers(NAME).withTrustManagers(NAME).build(),
                UndertowSslContext.builder().withName(NAME).build()
            };
        }

        @Override
        protected void tearDown(ModelControllerClient modelControllerClient) throws Exception {
            super.tearDown(modelControllerClient);
            FileUtils.deleteDirectory(WORK_DIR);
        }

        protected static void keyMaterialSetup(File workDir) throws Exception {
            FileUtils.deleteDirectory(workDir);
            workDir.mkdirs();
            Assert.assertTrue(workDir.exists());
            Assert.assertTrue(workDir.isDirectory());
            CoreUtils.createKeyMaterial(workDir);
        }
    }
}
