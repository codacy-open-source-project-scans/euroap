/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.messaging.mgmt;

import static java.util.UUID.randomUUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.integration.management.base.ContainerResourceMgmtTestBase;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c)2012 Red Hat, inc
 *
 * https://issues.jboss.org/browse/AS7-5107
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ConnectionFactoryManagementTestCase extends ContainerResourceMgmtTestBase {

    private static final String CF_NAME = randomUUID().toString();

    @ContainerResource
    private ManagementClient managementClient;

    @Test
    public void testWriteDiscoveryGroupAttributeWhenConnectorIsAlreadyDefined() throws Exception {
        JMSOperations jmsOperations = JMSOperationsProvider.getInstance(managementClient.getControllerClient());

        ModelNode attributes = new ModelNode();
        attributes.get("connectors").add("in-vm");
        jmsOperations.addJmsConnectionFactory(CF_NAME, "java:/jms/" + CF_NAME, attributes);

        final ModelNode writeAttribute = new ModelNode();
        writeAttribute.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        writeAttribute.get(OP_ADDR).set(jmsOperations.getServerAddress().add("connection-factory", CF_NAME));
        writeAttribute.get(NAME).set("discovery-group");
        writeAttribute.get(VALUE).set(randomUUID().toString());

        try {
            executeOperation(writeAttribute);
            fail("it is not possible to define a discovery group when the connector attribute is already defined");
        } catch (MgmtOperationException e) {
            assertEquals(FAILED, e.getResult().get(OUTCOME).asString());
            assertEquals(true, e.getResult().get(ROLLED_BACK).asBoolean());
            assertTrue(e.getResult().get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0105"));
        }

        jmsOperations.removeJmsConnectionFactory(CF_NAME);
        ServerReload.executeReloadAndWaitForCompletion(managementClient);
    }

    @Test
    public void testRemoveReferencedConnector() throws Exception {
        JMSOperations jmsOperations = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
        ModelNode address = jmsOperations.getServerAddress().add("in-vm-connector", "in-vm-test");
        ModelNode addOp = Operations.createAddOperation(address);
        addOp.get("server-id").set(0);
        ModelNode params = addOp.get("params").setEmptyList();
        params.add("buffer-pooling", ModelNode.FALSE);
        managementClient.getControllerClient().execute(addOp);
        ModelNode attributes = new ModelNode();
        attributes.get("connectors").add("in-vm-test");
        jmsOperations.addJmsConnectionFactory(CF_NAME, "java:/jms/" + CF_NAME, attributes);
        try {
            execute(managementClient.getControllerClient(), Operations.createRemoveOperation(address));
            fail("it is not possible to remove a connector when it is referenced from a connection factory");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("WFLYCTL0367"));
        } finally {
            jmsOperations.removeJmsConnectionFactory(CF_NAME);
            managementClient.getControllerClient().execute( Operations.createRemoveOperation(address));
        }
        ServerReload.executeReloadAndWaitForCompletion(managementClient);
    }

    private static ModelNode execute(ModelControllerClient client, ModelNode operation) throws Exception {
        //System.out.println("operation = " + operation);
        ModelNode response = client.execute(operation);
        //System.out.println("response = " + response);
        boolean success = SUCCESS.equals(response.get(OUTCOME).asString());
        if (success) {
            return response.get(RESULT);
        }
        throw new Exception(response.get(FAILURE_DESCRIPTION).asString());
    }
}
