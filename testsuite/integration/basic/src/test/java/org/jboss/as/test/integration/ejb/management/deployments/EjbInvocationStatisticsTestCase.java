/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.management.deployments;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.test.integration.ejb.management.deployments.EjbJarRuntimeResourceTestBase.MODULE_NAME;
import static org.jboss.as.test.integration.ejb.management.deployments.EjbJarRuntimeResourceTestBase.componentAddress;
import static org.jboss.as.test.integration.ejb.management.deployments.EjbJarRuntimeResourceTestBase.execute;
import static org.jboss.as.test.integration.ejb.management.deployments.EjbJarRuntimeResourceTestBase.executeOperation;
import static org.jboss.as.test.integration.ejb.management.deployments.EjbJarRuntimeResourceTestBase.getEJBJar;
import static org.jboss.as.test.integration.ejb.remote.common.EJBManagementUtil.SINGLETON;
import static org.jboss.as.test.integration.ejb.remote.common.EJBManagementUtil.STATEFUL;
import static org.jboss.as.test.integration.ejb.remote.common.EJBManagementUtil.STATELESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests whether the invocation statistics actually make sense.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
@ServerSetup(EjbInvocationStatisticsTestCaseSetup.class)
public class EjbInvocationStatisticsTestCase {
    private static final String SUBSYSTEM_NAME = "ejb3";

    @ContainerResource
    private ManagementClient managementClient;
    private Boolean statisticsEnabled;

    private static InitialContext context;

    @After
    public void after() throws IOException {
        setEnableStatistics(managementClient, statisticsEnabled);
    }

    @Before
    public void before() throws IOException {
        statisticsEnabled = getEnableStatistics(managementClient);
        setEnableStatistics(managementClient, true);
        assertEquals(Boolean.TRUE, getEnableStatistics(managementClient));
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        final Hashtable props = new Hashtable();
        props.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        context = new InitialContext(props);
    }

    @Deployment
    public static Archive<?> deployment() {
        return getEJBJar();
    }

    private static Boolean getEnableStatistics(final ManagementClient managementClient) throws IOException {
        final ModelNode address = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME)).toModelNode();
        address.protect();
        final ModelNode value = readAttribute(managementClient, address, "statistics-enabled");
        if (value.isDefined())
            return value.asBoolean();
        return null;
    }

    private static ModelNode readAttribute(final ManagementClient managementClient, final ModelNode address, final String attributeName) throws IOException {
        final ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
        op.get(ModelDescriptionConstants.OP_ADDR).set(address);
        op.get(ModelDescriptionConstants.NAME).set(attributeName);
        op.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).set(true);
        return execute(managementClient, op);
    }

    private static void setEnableStatistics(final ManagementClient managementClient, final Boolean enableStatistics) throws IOException {
        final ModelNode address = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME)).toModelNode();
        address.protect();
        if (enableStatistics == null)
            undefineAttribute(managementClient, address, "enable-statistics");
        else
            writeAttributeBoolean(managementClient, address, "enable-statistics", enableStatistics);
    }

    @Test
    public void testSingleton() throws Exception {
        validateBean(SINGLETON, ManagedSingletonBean.class);
    }

    @Test
    //TODO Elytron - ejb-client4 integration
    public void testSFSB() throws Exception {
        validateBean(STATEFUL, ManagedStatefulBean.class);
    }

    @Test
    public void testSLSB() throws Exception {
        validateBean(STATELESS, ManagedStatelessBean.class);
    }

    @Test
    @Ignore("WFLY-14107 intermittent failure")
    public void testSingletonWaitTime() throws Exception {
        validateWaitTimeStatistic(SINGLETON, WaitTimeSingletonBean.class);
    }

    /**
     * Invoke the singleton bean business method multiple times.
     * The target bean has multiple timers that are competing for the same
     * singleton bean instance. The wait-time statistic should show
     * a number greater than zero, because the singleton uses a write lock.
     *
     * @param type type of bean (EJBManagementUtil.STATEFUL, STATELESS, SINGLETON, MESSAGE_DRIVEN)
     * @param beanClass the bean class
     * @throws Exception on errors
     */
    private void validateWaitTimeStatistic(final String type, final Class<?> beanClass) throws Exception {
        final String name = beanClass.getSimpleName();
        final BusinessInterface bean = (BusinessInterface) context.lookup("ejb:/" + MODULE_NAME + "//" + name + "!" + BusinessInterface.class.getName() + (STATEFUL.equals(type) ? "?stateful" : ""));

        for (int i = 0; i < 3; i++) {
            bean.doIt();
        }
        bean.remove();

        // check the wait-time statistic
        final ModelNode address = componentAddress(EjbJarRuntimeResourcesTestCase.BASE_ADDRESS, type, name).toModelNode();
        address.protect();
        {
            final ModelNode result = executeOperation(managementClient,
                    ModelDescriptionConstants.READ_RESOURCE_OPERATION, address);
            assertTrue("Expecting wait-time attribute value > 0, but got " + result.toString(), result.get("wait-time").asLong() > 0L);
        }
    }

    private void validateBean(final String type, final Class<?> beanClass) throws Exception {
        final String name = beanClass.getSimpleName();
        final ModelNode address = componentAddress(EjbJarRuntimeResourcesTestCase.BASE_ADDRESS, type, name).toModelNode();
        address.protect();
        {
            final ModelNode result = executeOperation(managementClient, ModelDescriptionConstants.READ_RESOURCE_OPERATION, address);
            assertEquals(0L, result.get("execution-time").asLong());
            assertEquals(0L, result.get("invocations").asLong());
            assertEquals(0L, result.get("peak-concurrent-invocations").asLong());
            assertEquals(0L, result.get("wait-time").asLong());
            assertEquals(0L, result.get("methods").asInt());

            if (type.equals(STATEFUL)) {
                assertEquals(0L, result.get("cache-size").asLong());
                assertEquals(0L, result.get("passivated-count").asLong());
                assertEquals(0L, result.get("total-size").asLong());
            }
        }
        final BusinessInterface bean = (BusinessInterface) context.lookup("ejb:/" + MODULE_NAME + "//" + name + "!" + BusinessInterface.class.getName() + (type == STATEFUL ? "?stateful" : ""));
        bean.doIt();
        {
            ModelNode result = executeOperation(managementClient, ModelDescriptionConstants.READ_RESOURCE_OPERATION, address);

            assertTrue(result.get("execution-time").asLong() >= 50L);
            assertEquals(1L, result.get("invocations").asLong());
            assertEquals(1L, result.get("peak-concurrent-invocations").asLong());
            assertTrue(result.get("wait-time").asLong() >= 0L);
            assertEquals(1L, result.get("methods").asInt());
            final List<Property> methods = result.get("methods").asPropertyList();
            assertEquals("doIt", methods.get(0).getName());
            final ModelNode invocationValues = methods.get(0).getValue();
            assertTrue(invocationValues.get("execution-time").asLong() >= 50L);
            assertEquals(1L, invocationValues.get("invocations").asLong());
            assertTrue(invocationValues.get("wait-time").asLong() >= 0L);

            if (type.equals(STATEFUL)) {
                assertEquals(1L, result.get("cache-size").asLong());
                assertEquals(0L, result.get("passivated-count").asLong());
                assertEquals(1L, result.get("total-size").asLong());

                // Create a second bean forcing the first bean to passivate
                BusinessInterface bean2 = (BusinessInterface) context.lookup("ejb:/" + MODULE_NAME + "//" + name + "!" + BusinessInterface.class.getName() + (type == STATEFUL ? "?stateful" : ""));
                bean2.doIt();

                // Eviction is asynchronous, so wait a bit for this to take effect
                Thread.sleep(TimeoutUtil.adjust(500));

                result = executeOperation(managementClient, ModelDescriptionConstants.READ_RESOURCE_OPERATION, address);
                assertEquals(1L, result.get("cache-size").asLong());
                assertEquals(1L, result.get("passivated-count").asLong());
                assertEquals(2L, result.get("total-size").asLong());

                // remove bean from container
                bean.remove();
                bean2.remove();
                result = executeOperation(managementClient, ModelDescriptionConstants.READ_RESOURCE_OPERATION, address);
                assertEquals(0L, result.get("cache-size").asLong());
                assertEquals(0L, result.get("passivated-count").asLong());
                assertEquals(0L, result.get("total-size").asLong());
            }
        }
    }

    private static ModelNode undefineAttribute(final ManagementClient managementClient, final ModelNode address, final String attributeName) throws IOException {
        final ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION);
        op.get(ModelDescriptionConstants.OP_ADDR).set(address);
        op.get(ModelDescriptionConstants.NAME).set(attributeName);
        return execute(managementClient, op);
    }

    private static ModelNode writeAttributeBoolean(final ManagementClient managementClient, final ModelNode address, final String attributeName, final boolean value) throws IOException {
        final ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        op.get(ModelDescriptionConstants.OP_ADDR).set(address);
        op.get(ModelDescriptionConstants.NAME).set(attributeName);
        op.get(ModelDescriptionConstants.VALUE).set(value);
        return execute(managementClient, op);
    }
}
