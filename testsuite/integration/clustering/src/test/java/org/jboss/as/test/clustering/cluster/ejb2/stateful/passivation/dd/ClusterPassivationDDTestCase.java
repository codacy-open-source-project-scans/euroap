/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.ejb2.stateful.passivation.dd;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.*;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.clustering.NodeInfoServlet;
import org.jboss.as.test.clustering.NodeNameGetter;
import org.jboss.as.test.clustering.cluster.ejb2.stateful.passivation.ClusterPassivationTestBase;
import org.jboss.as.test.clustering.cluster.ejb2.stateful.passivation.StatefulBeanBase;
import org.jboss.as.test.clustering.cluster.ejb2.stateful.passivation.StatefulRemote;
import org.jboss.as.test.clustering.cluster.ejb2.stateful.passivation.StatefulRemoteHome;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Clustering ejb passivation of EJB2 bean defined by descriptor.
 *
 * @author Ondrej Chaloupka
 */
@Ignore("Uses legacy client hack")
@RunWith(Arquillian.class)
public class ClusterPassivationDDTestCase extends ClusterPassivationTestBase {
    private static Logger log = Logger.getLogger(ClusterPassivationDDTestCase.class);

    @ArquillianResource
    private ContainerController controller;
    @ArquillianResource
    private Deployer deployer;

    @Deployment(name = DEPLOYMENT_1, managed = false, testable = false)
    @TargetsContainer(NODE_1)
    public static Archive<?> deployment1() {
        return createDeployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false, testable = false)
    @TargetsContainer(NODE_2)
    public static Archive<?> deployment2() {
        return createDeployment();
    }

    private static Archive<?> createDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, MODULE_NAME + ".war");
        war.addClasses(StatefulBeanBase.class, StatefulBeanDD.class, StatefulRemote.class, StatefulRemoteHome.class);
        war.addAsWebInfResource(ClusterPassivationDDTestCase.class.getPackage(), "ejb-jar.xml", "ejb-jar.xml");
        war.addClasses(NodeNameGetter.class, NodeInfoServlet.class);
        return war;
    }

    @Override
    protected void startServers(ManagementClient client1, ManagementClient client2) {
        if (client1 == null || !client1.isServerInRunningState()) {
            log.trace("Starting server: " + NODE_1);
            controller.start(NODE_1);
            deployer.deploy(DEPLOYMENT_1);
        }
        if (client2 == null || !client2.isServerInRunningState()) {
            log.trace("Starting server: " + NODE_2);
            controller.start(NODE_2);
            deployer.deploy(DEPLOYMENT_2);
        }
    }

    @Test
    @InSequence(-2)
    public void startServersForTests() {
        startServers(null, null);
    }

    /**
     * Association of node names to deployment,container names and client context
     */
    @Test
    @InSequence(-1)
    public void defineMaps(@ArquillianResource @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
                           @ArquillianResource @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2,
                           @ArquillianResource @OperateOnDeployment(DEPLOYMENT_1) ManagementClient client1,
                           @ArquillianResource @OperateOnDeployment(DEPLOYMENT_2) ManagementClient client2) throws Exception {
        String nodeName1 = HttpRequest.get(baseURL1.toString() + NodeInfoServlet.SERVLET_NAME, HTTP_REQUEST_WAIT_TIME_S, TimeUnit.SECONDS);
        node2deployment.put(nodeName1, DEPLOYMENT_1);
        node2container.put(nodeName1, NODE_1);
        log.trace("URL1 nodename: " + nodeName1);
        String nodeName2 = HttpRequest.get(baseURL2.toString() + NodeInfoServlet.SERVLET_NAME, HTTP_REQUEST_WAIT_TIME_S, TimeUnit.SECONDS);
        node2deployment.put(nodeName2, DEPLOYMENT_2);
        node2container.put(nodeName2, NODE_2);
        log.trace("URL2 nodename: " + nodeName2);
    }

    @Test
    @InSequence(1)
    public void testPassivationBeanDefinedByDescriptor(
            @ArquillianResource @OperateOnDeployment(DEPLOYMENT_1) ManagementClient client1,
            @ArquillianResource @OperateOnDeployment(DEPLOYMENT_2) ManagementClient client2) throws Exception {
        setPassivationAttributes(client1.getControllerClient());
        setPassivationAttributes(client2.getControllerClient());

        // Setting context from .properties file to get ejb:/ remote context
        setupEJBClientContextSelector();

        StatefulRemoteHome home = directory.lookupHome(StatefulBeanDD.class, StatefulRemoteHome.class);
        StatefulRemote statefulBean = home.create();

        runPassivation(statefulBean, controller, deployer);
        startServers(client1, client2);
    }


    @Test
    @InSequence(100)
    public void stopAndClean(
            @OperateOnDeployment(DEPLOYMENT_1) @ArquillianResource ManagementClient client1,
            @OperateOnDeployment(DEPLOYMENT_2) @ArquillianResource ManagementClient client2) throws Exception {
        log.trace("Stop&Clean...");

        // unset & undeploy & stop
        if (client1.isServerInRunningState()) {
            unsetPassivationAttributes(client1.getControllerClient());
            deployer.undeploy(DEPLOYMENT_1);
            controller.stop(NODE_1);
        }
        if (client2.isServerInRunningState()) {
            unsetPassivationAttributes(client2.getControllerClient());
            deployer.undeploy(DEPLOYMENT_2);
            controller.stop(NODE_2);
        }
    }
}
