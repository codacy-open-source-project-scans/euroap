/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jpa.hibernate.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Jakarta Persistence management operations to ensure jboss-cli.sh/admin console will work with Jakarta Persistence statistics
 *
 * @author Scott Marlow
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ManagementTestCase {

    private static final String ARCHIVE_NAME = "jpa_ManagementTestCase";

    @ArquillianResource
    private Deployer deployer;

    @Deployment(name = ARCHIVE_NAME, managed = false)
    public static Archive<?> deploy() {

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar");
        jar.addClasses(ManagementTestCase.class,
                Employee.class,
                SFSB1.class
        );

        jar.addAsManifestResource(ManagementTestCase.class.getPackage(), "persistence.xml", "persistence.xml");

        jar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.as.controller-client, org.jboss.dmr, org.jboss.marshalling \n"), "MANIFEST.MF");
        return jar;
    }

    @ArquillianResource
    private InitialContext iniCtx;

    protected <T> T lookup(String beanName, Class<T> interfaceType) throws NamingException {
        return interfaceType.cast(iniCtx.lookup("java:global/" + ARCHIVE_NAME + "/" + beanName + "!" + interfaceType.getName()));
    }

    protected <T> T rawLookup(String name, Class<T> interfaceType) throws NamingException {
        return interfaceType.cast(iniCtx.lookup(name));
    }

    /**
     * Test that we can get the entity-insert-count attribute from the Hibernate 4 management statistics.
     *
     * @throws Exception
     */
    @Test
    public void getEntityInsertCountAttribute() throws Exception {

        try {
            deployer.deploy(ARCHIVE_NAME);
            assertTrue("obtained entity-insert-count attribute from JPA persistence unit", 0 == getEntityInsertCount());
        } finally {
            deployer.undeploy(ARCHIVE_NAME);
        }

    }

    @ContainerResource
    private ManagementClient managementClient;

    int getEntityInsertCount() throws IOException {

        final ModelNode address = new ModelNode();
        address.add("deployment", ARCHIVE_NAME + ".jar");
        address.add("subsystem", "jpa");
        address.add("hibernate-persistence-unit", ARCHIVE_NAME + ".jar#mypc");
        address.protect();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set("read-attribute");
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set("entity-insert-count");
        ModelNode result = managementClient.getControllerClient().execute(operation);
        //System.out.println("\n\ngetEntityInsertCount result asString = " + result.asString());

        assertTrue("success".equals(result.get("outcome").asString()));
        result = result.get("result");
        return result.asInt();
    }

}
