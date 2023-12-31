/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.security;

import java.io.File;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.AbstractSecurityDomainSetup;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.logging.Logger;
import org.wildfly.test.security.common.elytron.PropertyFileBasedDomain;
import org.wildfly.test.security.common.elytron.UndertowDomainMapper;

/**
 * @author Stuart Douglas
 */
public class WebTestsSecurityDomainSetup extends AbstractSecurityDomainSetup {

    private static final Logger log = Logger.getLogger(WebTestsSecurityDomainSetup.class);

    public static final String WEB_SECURITY_DOMAIN = "web-tests";

    private static final String GOOD_USER_NAME = "anil";
    private static final String GOOD_USER_PASSWORD = "anil";
    private static final String GOOD_USER_ROLE = "gooduser";

    private static final String SUPER_USER_NAME = "marcus";
    private static final String SUPER_USER_PASSWORD = "marcus";
    private static final String SUPER_USER_ROLE = "superuser";

    private static final String BAD_GUY_NAME = "peter";
    private static final String BAD_GUY_PASSWORD = "peter";
    private static final String BAD_GUY_ROLE = "badguy";

    private CLIWrapper cli;
    private PropertyFileBasedDomain ps;
    private UndertowDomainMapper domainMapper;

    @Override
    public void setup(final ManagementClient managementClient, final String containerId) throws Exception {
        // Retrieve application.keystore file from jar archive of wildfly-testsuite-shared module
        File destFile = new File(System.getProperty("jboss.home") + File.separator + "standalone" + File.separator +
                "configuration" + File.separatorChar + "application.keystore");
        URL resourceUrl = this.getClass().getResource("/org/jboss/as/test/shared/shared-keystores/application.keystore");
        FileUtils.copyInputStreamToFile(resourceUrl.openStream(), destFile);

        cli = new CLIWrapper(true);
        setElytronBased();
    }

    @Override
    protected String getSecurityDomainName() {
        return WEB_SECURITY_DOMAIN;
    }

    protected void setElytronBased() throws Exception {
        log.debug("start of the elytron based domain creation");
        // Prepare properties files with users, passwords and roles
        ps = PropertyFileBasedDomain.builder()
                .withUser(GOOD_USER_NAME, GOOD_USER_PASSWORD, GOOD_USER_ROLE)
                .withUser(SUPER_USER_NAME, SUPER_USER_PASSWORD, SUPER_USER_ROLE)
                .withUser(BAD_GUY_NAME, BAD_GUY_PASSWORD, BAD_GUY_ROLE)
                .withName(WEB_SECURITY_DOMAIN).build();
        ps.create(cli);
        domainMapper = UndertowDomainMapper.builder().withName(WEB_SECURITY_DOMAIN).build();
        domainMapper.create(cli);
        log.debug("end of the elytron based domain creation");
    }

    @Override
    public void tearDown(ManagementClient managementClient, String containerId) {
        try {
            domainMapper.remove(cli);
            ps.remove(cli);
            cli.close();
            ServerReload.executeReloadAndWaitForCompletion(managementClient);
        } catch (Exception e) {
            throw new RuntimeException("Cleaning up for Elytron based security domain failed.", e);
        }
    }
}
