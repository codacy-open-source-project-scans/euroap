/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.ejb2.stateful.passivation;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.WAIT_FOR_PASSIVATION_MS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.clustering.DMRUtil;
import org.jboss.as.test.clustering.ejb.EJBDirectory;
import org.jboss.as.test.clustering.ejb.RemoteEJBDirectory;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

/**
 * Base class for passivation tests on Enterprise Beans 2 beans.
 *
 * @author Ondrej Chaloupka
 */
public abstract class ClusterPassivationTestBase {
    private static Logger log = Logger.getLogger(ClusterPassivationTestBase.class);
    public static final String MODULE_NAME = ClusterPassivationTestBase.class.getSimpleName();

    protected EJBDirectory directory;

    @Before
    public void beforeTest() throws NamingException {
        directory = new RemoteEJBDirectory(MODULE_NAME);
    }

    @After
    public void afterTest() throws Exception {
        directory.close();
    }

    // Properties pass amongst tests
    protected static Map<String, String> node2deployment = new HashMap<String, String>();
    protected static Map<String, String> node2container = new HashMap<String, String>();

    /**
     * Setting all passivation attributes.
     */
    protected void setPassivationAttributes(ModelControllerClient client) throws Exception {
        DMRUtil.setMaxSize(client, 1);
    }

    /**
     * Unsetting all cache attributes - defining their default values.
     */
    protected void unsetPassivationAttributes(ModelControllerClient client) throws Exception {
        DMRUtil.unsetMaxSizeAttribute(client);
    }

    /**
     * Sets up the Jakarta Enterprise Beans client context to use a selector which processes and sets up Jakarta Enterprise Beans receivers based on this testcase
     * specific jboss-ejb-client.properties file
     */
    protected void setupEJBClientContextSelector() throws IOException {
        // TODO Elytron: Once support for legacy Jakarta Enterprise Beans properties has been added back, actually set the Jakarta Enterprise Beans properties
        // that should be used for this test using sfsb-failover-jboss-ejb-client.properties and ensure the Jakarta Enterprise Beans client
        // context is reset to its original state at the end of the test
        //EJBClientContextSelector.setup("cluster/ejb3/stateful/failover/sfsb-failover-jboss-ejb-client.properties");
    }

    /**
     * Start servers whether their are not started.
     *
     * @param client1 client for server1
     * @param client2 client for server2
     */
    protected abstract void startServers(ManagementClient client1, ManagementClient client2);

    /**
     * Waiting for getting cluster context - it could take some time for client to get info from cluster nodes
     */
    protected void waitForClusterContext() throws InterruptedException {
        int counter = 0;
        EJBClientContext ejbClientContext = EJBClientContext.getCurrent();
        // TODO Elytron: Determine how this should be adapted once the clustering and Jakarta Enterprise Beans client changes are in
        // ClusterContext clusterContext;
        //do {
        //    clusterContext = ejbClientContext.getClusterContext(CLUSTER_NAME);
        //    counter--;
        //    Thread.sleep(CLUSTER_ESTABLISHMENT_WAIT_MS);
        //} while (clusterContext == null && counter < CLUSTER_ESTABLISHMENT_LOOP_COUNT);
        //Assert.assertNotNull("Cluster context for " + CLUSTER_NAME + " was not taken in "
        //        + (CLUSTER_ESTABLISHMENT_LOOP_COUNT * CLUSTER_ESTABLISHMENT_WAIT_MS) + " ms", clusterContext);
    }

    /**
     * Testing passivation over nodes - switching a node on and off. Testing ejbPassivate bean function.
     */
    protected void runPassivation(StatefulRemote statefulBean, ContainerController controller, Deployer deployer) throws Exception {
        Assert.assertNotNull(statefulBean);

        // Calling on server one
        int clientNumber = 40;
        String calledNodeFirst = statefulBean.setNumber(clientNumber);
        statefulBean.setPassivationNode(calledNodeFirst);
        statefulBean.incrementNumber(); // 41
        Assert.assertEquals(++clientNumber, statefulBean.getNumber()); // 41
        // nodeName of nested bean should be the same as the node of parent
        log.trace("Called node name first: " + calledNodeFirst);
        Thread.sleep(WAIT_FOR_PASSIVATION_MS); // waiting for passivation

        // A small hack - deleting node (by name) from cluster which this client knows
        // It means that the next request (ejb call) will be passed to the server #2
        // TODO Elytron: Determine how this should be adapted once the clustering and Jakarta Enterprise Beans client changes are in
        //EJBClientContext.requireCurrent().getClusterContext(CLUSTER_NAME).removeClusterNode(calledNodeFirst);

        Assert.assertEquals("Supposing to get passivation node which was set", calledNodeFirst, statefulBean.getPassivatedBy());

        String calledNodeSecond = statefulBean.incrementNumber(); // 42
        statefulBean.setPassivationNode(calledNodeSecond);
        log.trace("Called node name second: " + calledNodeSecond);
        Thread.sleep(WAIT_FOR_PASSIVATION_MS); // waiting for passivation

        // Resetting cluster context to know both cluster nodes
        setupEJBClientContextSelector();
        // Waiting for getting cluster context - it could take some time for client to get info from cluster nodes
        waitForClusterContext();

        // Stopping node #2
        deployer.undeploy(node2deployment.get(calledNodeSecond));
        controller.stop(node2container.get(calledNodeSecond));

        // We killed second node and we check the value on first node
        Assert.assertEquals(++clientNumber, statefulBean.getNumber()); // 42
        // Calling on first server
        String calledNode = statefulBean.incrementNumber(); // 43
        // Checking called node and set number
        Assert.assertEquals("It can't be node " + calledNodeSecond + " because is switched off", calledNodeFirst, calledNode);

        Assert.assertEquals("Supposing to get passivation node which was set", calledNodeSecond, statefulBean.getPassivatedBy());

        Thread.sleep(WAIT_FOR_PASSIVATION_MS); // waiting for passivation
        Assert.assertEquals(++clientNumber, statefulBean.getNumber()); // 43
    }

}
