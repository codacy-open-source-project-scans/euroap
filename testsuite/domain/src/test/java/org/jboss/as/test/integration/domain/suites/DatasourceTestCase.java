/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Basic tests of datasource support in a managed domain.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class DatasourceTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static final ModelNode ROOT_ADDRESS = new ModelNode().setEmptyList();
    private static final ModelNode PRIMARY_ROOT_ADDRESS = new ModelNode().add(HOST, "primary");
    private static final ModelNode SECONDARY_ROOT_ADDRESS = new ModelNode().add(HOST, "secondary");
    private static final ModelNode MAIN_RUNNING_SERVER_ADDRESS = new ModelNode().add(HOST, "primary").add(SERVER, "main-one");
    private static final ModelNode MAIN_RUNNING_SERVER_DS_ADDRESS = new ModelNode().add(HOST, "primary")
            .add(SERVER, "main-one").add(SUBSYSTEM, "datasources").add("data-source", "ExampleDS");

    static {
        ROOT_ADDRESS.protect();
        PRIMARY_ROOT_ADDRESS.protect();
        SECONDARY_ROOT_ADDRESS.protect();
        MAIN_RUNNING_SERVER_ADDRESS.protect();
        MAIN_RUNNING_SERVER_DS_ADDRESS.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(DatasourceTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    private DomainClient primaryClient;
    private DomainClient secondaryClient;

    @Before
    public void setup() throws Exception {
        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
    }

    @Test
    public void testDatasourceConnection() throws IOException {

        // AS7-6062 -- validate that  ExampleDS works on a domain server
        ModelNode response = primaryClient.execute(getEmptyOperation("test-connection-in-pool", MAIN_RUNNING_SERVER_DS_ADDRESS));
        validateResponse(response);
    }

    private static ModelNode getEmptyOperation(String operationName, ModelNode address) {
        ModelNode op = new ModelNode();
        op.get(OP).set(operationName);
        if (address != null) {
            op.get(OP_ADDR).set(address);
        }
        else {
            // Just establish the standard structure; caller can fill in address later
            op.get(OP_ADDR).setEmptyList();
        }
        return op;
    }
}
