/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.logging.config;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.OverProtocol;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(LoggingProfileSharedTestCase.LoggingProfileSetupTask.class)
public class LoggingProfileSharedTestCase extends AbstractConfigTestCase {

    private static final String PROFILE_NAME = "test-shared-profile";

    private static final String WAR_DEPLOYMENT_1 = "logging-profile-war-1";
    private static final String WAR_DEPLOYMENT_2 = "logging-profile-war-2";
    private static final String EJB_DEPLOYMENT = "logging-profile-ejb";

    private static final String EJB_DEPLOYMENT_NAME = "logging-profile-ejb.jar";
    private static final String WAR_DEPLOYMENT_1_NAME = "logging-profile-war-1.war";
    private static final String WAR_DEPLOYMENT_2_NAME = "logging-profile-war-2.war";
    private static final String FILE_NAME = "logging-profile-shared-json.log";

    // This needs to be deployed first as the two other deployments rely on this
    @Deployment(name = EJB_DEPLOYMENT, order = 1)
    public static JavaArchive createEjbDeployment() {
        return ShrinkWrap.create(JavaArchive.class, EJB_DEPLOYMENT_NAME)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(LoggingStartup.class, LoggerResource.class);
    }

    // This should be deployed last to ensure that WAR_DEPLOYMENT_2 does register a log context
    @Deployment(name = WAR_DEPLOYMENT_1, order = 3)
    @OverProtocol("Servlet 5.0")
    public static WebArchive createWar1() {
        return ShrinkWrap.create(WebArchive.class, WAR_DEPLOYMENT_1_NAME)
                .addAsManifestResource(createJBossDeploymentStructure(), "jboss-deployment-structure.xml")
                .addAsManifestResource(new StringAsset("Logging-Profile: " + PROFILE_NAME), "MANIFEST.MF")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(LoggingServlet.class);
    }

    @Deployment(name = WAR_DEPLOYMENT_2, order = 2)
    public static WebArchive createWar2() {
        return ShrinkWrap.create(WebArchive.class, WAR_DEPLOYMENT_2_NAME)
                .addAsManifestResource(createJBossDeploymentStructure(), "jboss-deployment-structure.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(LoggingServlet.class);
    }

    @Test
    public void testDeployments(@OperateOnDeployment(WAR_DEPLOYMENT_1) @ArquillianResource URL war1, @OperateOnDeployment(WAR_DEPLOYMENT_2) @ArquillianResource URL war2) throws Exception {
        // First invoke the first deployment to log
        final String msg1 = "Test from shared WAR1";
        UrlBuilder builder = UrlBuilder.of(war1, "log");
        builder.addParameter("msg", msg1);
        performCall(builder.build());

        // Next invoke the second deployment which should not use the log context from the first deployment
        final String msg2 = "Test from shared WAR2";
        builder = UrlBuilder.of(war2, "log");
        builder.addParameter("msg", msg2);
        performCall(builder.build());

        final List<JsonObject> depLogs = readJsonLogFileFromModel(PROFILE_NAME, FILE_NAME);

        // There should only be a log message from the servlet
        assertLength(depLogs, 1, FILE_NAME);

        // Check the expected dep log file
        Collection<JsonObject> unexpectedLogs = depLogs.stream()
                .filter(logMessage -> {
                    final String msg = logMessage.getString("message");
                    return (!msg.equals(LoggingServlet.formatMessage(msg1)));
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, FILE_NAME);

        // Now we want to make sure that only WAR2 logs made it into the default log context
        final List<JsonObject> defaultLogs = readJsonLogFileFromModel(null, DEFAULT_LOG_FILE);

        // There should be 1 startup messages in this file, 3 from WAR2 servlet and 2 from the static logger in WAR1
        assertLength(defaultLogs, 6, DEFAULT_LOG_FILE);

        unexpectedLogs = defaultLogs.stream()
                .filter(logMessage -> {
                    final String msg = logMessage.getString("message");
                    return !msg.equals(LoggerResource.formatStaticLogMsg(msg1)) &&
                            !msg.equals(LoggerResource.formatLogMsg(msg1)) &&
                            !msg.equals(LoggingServlet.formatMessage(msg2)) &&
                            !msg.equals(LoggerResource.formatLogMsg(msg2)) &&
                            !msg.equals(LoggerResource.formatStaticLogMsg(msg2)) &&
                            !msg.equals(LoggingStartup.STARTUP_MESSAGE);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, DEFAULT_LOG_FILE);
    }

    private static Asset createJBossDeploymentStructure() {
        return new StringAsset(
                "<jboss-deployment-structure>\n" +
                        "   <deployment>\n" +
                        "       <dependencies>\n" +
                        "           <module name=\"deployment." + EJB_DEPLOYMENT_NAME + "\" meta-inf=\"import\" />\n" +
                        "       </dependencies>\n" +
                        "   </deployment>\n" +
                        "</jboss-deployment-structure>");
    }

    public static class LoggingProfileSetupTask extends LogFileServerSetupTask {
        @Override
        protected Operations.CompositeOperationBuilder createBuilder() {
            final Operations.CompositeOperationBuilder builder = super.createBuilder();
            builder.addStep(Operations.createAddOperation(createSubsystemAddress(PROFILE_NAME)));
            addDefaults(builder, PROFILE_NAME, FILE_NAME);
            return builder;
        }
    }
}
