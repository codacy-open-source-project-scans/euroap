/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.elytron.principaltransformers;

import java.net.MalformedURLException;
import java.net.URL;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.Utils;
import org.jboss.as.test.integration.security.common.servlets.SimpleSecuredServlet;
import org.jboss.as.test.integration.security.common.servlets.SimpleServlet;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.test.security.common.elytron.PropertyFileBasedDomain;

/**
 * Test case for 'regex-validating-principal-transformer' Elytron subsystem resource.
 *
 * @author olukas
 */
@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup({RegexValidatingPrincipalTransformerTestCase.SetupTask.class})
public class RegexValidatingPrincipalTransformerTestCase {

    private static final String DEP_SECURITY_DOMAIN_MATCH = "regex-validating-principal-transformer-domain-match";
    private static final String DEP_SECURITY_DOMAIN_NOT_MATCH = "regex-validating-principal-transformer-domain-not-match";

    private static final String USER = "user";
    private static final String USER2 = "user2";
    private static final String PASSWORD = "password";
    private static final String ROLE = "JBossAdmin";

    @Deployment(name = DEP_SECURITY_DOMAIN_MATCH)
    public static WebArchive deploymentMatch() {
        return createDeployment(DEP_SECURITY_DOMAIN_MATCH);
    }

    @Deployment(name = DEP_SECURITY_DOMAIN_NOT_MATCH)
    public static WebArchive deploymentNotMatch() {
        return createDeployment(DEP_SECURITY_DOMAIN_NOT_MATCH);
    }

    private static WebArchive createDeployment(String domain) {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, domain + ".war");
        war.addClasses(SimpleServlet.class);
        war.addClasses(SimpleSecuredServlet.class);
        war.addAsWebInfResource(RegexValidatingPrincipalTransformerTestCase.class.getPackage(), "principal-transformer-web.xml", "web.xml");
        war.addAsWebInfResource(Utils.getJBossWebXmlAsset(domain), "jboss-web.xml");
        return war;
    }

    /**
     * Test that validation in 'regex-validating-principal-transformer' fails in case when attribute 'match' is set to true and
     * user name does not match the pattern.
     */
    @Test
    @OperateOnDeployment(DEP_SECURITY_DOMAIN_MATCH)
    public void testMatchTrueAndUsernameDoesNotMatch(@ArquillianResource URL webAppURL) throws Exception {
        URL url = prepareUrl(webAppURL);
        Utils.makeCallWithBasicAuthn(url, USER, PASSWORD, SC_UNAUTHORIZED);
    }

    /**
     * Test that validation in 'regex-validating-principal-transformer' succeed in case when attribute 'match' is set to true
     * and user name matches the pattern.
     */
    @Test
    @OperateOnDeployment(DEP_SECURITY_DOMAIN_MATCH)
    public void testMatchTrueAndUsernameMatches(@ArquillianResource URL webAppURL) throws Exception {
        URL url = prepareUrl(webAppURL);
        Utils.makeCallWithBasicAuthn(url, USER2, PASSWORD, SC_OK);
    }

    /**
     * Test that validation in 'regex-validating-principal-transformer' succeed in case when attribute 'match' is set to false
     * and user name does not match the pattern.
     */
    @Test
    @OperateOnDeployment(DEP_SECURITY_DOMAIN_NOT_MATCH)
    public void testMatchFalseAndUsernameDoesNotMatch(@ArquillianResource URL webAppURL) throws Exception {
        URL url = prepareUrl(webAppURL);
        Utils.makeCallWithBasicAuthn(url, USER, PASSWORD, SC_OK);
    }

    /**
     * Test that validation in 'regex-validating-principal-transformer' fails in case when attribute 'match' is set to false and
     * user name matches the pattern.
     */
    @Test
    @OperateOnDeployment(DEP_SECURITY_DOMAIN_NOT_MATCH)
    public void testMatchFalseAndUsernameMatches(@ArquillianResource URL webAppURL) throws Exception {
        URL url = prepareUrl(webAppURL);
        Utils.makeCallWithBasicAuthn(url, USER2, PASSWORD, SC_UNAUTHORIZED);
    }

    private URL prepareUrl(URL url) throws MalformedURLException {
        return new URL(url.toExternalForm() + SimpleSecuredServlet.SERVLET_PATH.substring(1));
    }

    static class SetupTask implements ServerSetupTask {

        private static final String ELYTRON_SECURITY_MATCH = "elytronDomainMatch";
        private static final String ELYTRON_SECURITY_NOT_MATCH = "elytronDomainNotMatch";
        private static final String PRINCIPAL_TRANSFORMER_MATCH = "transformerMatch";
        private static final String PRINCIPAL_TRANSFORMER_NOT_MATCH = "transformerNotMatch";
        private static final String PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY = "global";

        private PropertyFileBasedDomain domainMatch;
        private PropertyFileBasedDomain domainNotMatch;

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            try (CLIWrapper cli = new CLIWrapper(true)) {
                domainMatch = prepareSingleConfiguration(cli, ELYTRON_SECURITY_MATCH, PRINCIPAL_TRANSFORMER_MATCH, DEP_SECURITY_DOMAIN_MATCH,
                        "/subsystem=elytron/regex-validating-principal-transformer=%s:add(pattern=user\\\\d+,match=true)");
                domainNotMatch = prepareSingleConfiguration(cli, ELYTRON_SECURITY_NOT_MATCH, PRINCIPAL_TRANSFORMER_NOT_MATCH, DEP_SECURITY_DOMAIN_NOT_MATCH,
                        "/subsystem=elytron/regex-validating-principal-transformer=%s:add(pattern=user\\\\d+,match=false)");
            }
            ServerReload.reloadIfRequired(managementClient);
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            try (CLIWrapper cli = new CLIWrapper(true)) {
                removeSingleConfiguration(cli, ELYTRON_SECURITY_NOT_MATCH, PRINCIPAL_TRANSFORMER_NOT_MATCH, DEP_SECURITY_DOMAIN_NOT_MATCH, domainNotMatch);
                removeSingleConfiguration(cli, ELYTRON_SECURITY_MATCH, PRINCIPAL_TRANSFORMER_MATCH, DEP_SECURITY_DOMAIN_MATCH, domainMatch);
            }
            ServerReload.reloadIfRequired(managementClient);
        }

        private PropertyFileBasedDomain prepareSingleConfiguration(CLIWrapper cli, String elytronName, String transformerName,
                String deploymentName, String addTransformerCli)
                throws Exception {
            PropertyFileBasedDomain domain = PropertyFileBasedDomain.builder().withName(elytronName)
                    .withUser(USER, PASSWORD, ROLE)
                    .withUser(USER2, PASSWORD, ROLE)
                    .build();
            domain.create(cli);
            cli.sendLine(String.format(addTransformerCli, transformerName));
            cli.sendLine(String.format("/subsystem=elytron/security-domain=%s:write-attribute(name=realms[0].principal-transformer,value=%s)",
                    elytronName, transformerName));
            cli.sendLine(String.format(
                    "/subsystem=elytron/http-authentication-factory=%1$s:add(http-server-mechanism-factory=%2$s,security-domain=%1$s,"
                    + "mechanism-configurations=[{mechanism-name=BASIC,mechanism-realm-configurations=[{realm-name=\"%1$s\"}]}])",
                    elytronName, PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY));
            cli.sendLine(String.format(
                    "/subsystem=undertow/application-security-domain=%s:add(http-authentication-factory=%s)",
                    deploymentName, elytronName));
            return domain;
        }

        private void removeSingleConfiguration(CLIWrapper cli, String elytronName, String transformerName,
                String deploymentName, PropertyFileBasedDomain domain) throws Exception {
            cli.sendLine(String.format(
                    "/subsystem=elytron/security-domain=%s:undefine-attribute(name=realms[0].principal-transformer)",
                    elytronName));
            cli.sendLine(String.format("/subsystem=undertow/application-security-domain=%s:remove()", deploymentName));
            cli.sendLine(String.format("/subsystem=elytron/http-authentication-factory=%s:remove()", elytronName));
            cli.sendLine(String.format("/subsystem=elytron/regex-validating-principal-transformer=%s:remove()", transformerName));
            domain.remove(cli);
        }

    }
}
