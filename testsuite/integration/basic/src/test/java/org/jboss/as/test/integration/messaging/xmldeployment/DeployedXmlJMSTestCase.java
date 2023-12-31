/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.messaging.xmldeployment;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.jms.Queue;
import jakarta.jms.Topic;
import javax.naming.InitialContext;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.integration.deployment.xml.datasource.DeployedXmlDataSourceTestCase;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test deployment of -jms.xml files
 *
 * @author Stuart Douglas
 */
@RunWith(Arquillian.class)
@ServerSetup(DeployedXmlJMSTestCase.DeployedXmlJMSTestCaseSetup.class)
public class DeployedXmlJMSTestCase {

    private static final String TEST_HORNETQ_JMS_XML = "test-hornetq-jms.xml";
    private static final String TEST_ACTIVEMQ_JMS_XML = "test-activemq-jms.xml";

    static class DeployedXmlJMSTestCaseSetup implements ServerSetupTask {

        @Override
        public void setup(final ManagementClient managementClient, final String containerId) throws Exception {
            final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(managementClient.getControllerClient());
            final String packageName = DeployedXmlJMSTestCase.class.getPackage().getName().replace(".", "/");

            JMSOperations jmsOperations = JMSOperationsProvider.getInstance(managementClient);
            String xmlFile = (jmsOperations.getProviderName().equals("hornetq")) ? TEST_HORNETQ_JMS_XML : TEST_ACTIVEMQ_JMS_XML;

            final DeploymentPlan plan = manager.newDeploymentPlan().add(DeployedXmlJMSTestCase.class.getResource("/" + packageName + "/" + xmlFile)).andDeploy().build();
            final Future<ServerDeploymentPlanResult> future = manager.execute(plan);
            final ServerDeploymentPlanResult result = future.get(20, TimeUnit.SECONDS);
            final ServerDeploymentActionResult actionResult = result.getDeploymentActionResult(plan.getId());
            if (actionResult != null) {
                if (actionResult.getDeploymentException() != null) {
                    throw new RuntimeException(actionResult.getDeploymentException());
                }
            }
        }

        @Override
        public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
            JMSOperations jmsOperations = JMSOperationsProvider.getInstance(managementClient);
            String xmlFile = (jmsOperations.getProviderName().equals("hornetq")) ? TEST_HORNETQ_JMS_XML : TEST_ACTIVEMQ_JMS_XML;

            final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(managementClient.getControllerClient());
            final DeploymentPlan undeployPlan = manager.newDeploymentPlan().undeploy(xmlFile).andRemoveUndeployed().build();
            manager.execute(undeployPlan).get();
        }
    }

    @Deployment
    public static Archive<?> deploy() {
        return ShrinkWrap.create(JavaArchive.class, "testDsXmlDeployment.jar")
                .addClass(DeployedXmlJMSTestCase.class)
                .addAsManifestResource(DeployedXmlDataSourceTestCase.class.getPackage(), "MANIFEST.MF", "MANIFEST.MF");
    }

    @ArquillianResource
    private InitialContext initialContext;

    @Test
    public void testDeployedQueue() throws Throwable {
        final Queue queue = (Queue) initialContext.lookup("java:/queue1");
        Assert.assertNotNull(queue);
    }

    @Test
    public void testDeployedTopic() throws Throwable {
        final Topic topic = (Topic) initialContext.lookup("java:/topic1");
        Assert.assertNotNull(topic);
    }


}
