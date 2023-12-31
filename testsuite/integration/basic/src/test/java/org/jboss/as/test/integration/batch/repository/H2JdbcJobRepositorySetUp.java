/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.batch.repository;


import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.SnapshotRestoreSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

import java.io.IOException;

class H2JdbcJobRepositorySetUp extends SnapshotRestoreSetupTask {

    static final String REPOSITORY_NAME = "jdbc";

    @Override
    public void doSetup(final ManagementClient managementClient, final String containerId) throws Exception {
        final Operations.CompositeOperationBuilder operationBuilder = Operations.CompositeOperationBuilder.create();

        // Add a new JDBC job repository with the new data-source
        ModelNode op = Operations.createAddOperation(Operations.createAddress("subsystem", "batch-jberet", "jdbc-job-repository", REPOSITORY_NAME));
        configureJobRepository(op);
        operationBuilder.addStep(op);

        operationBuilder.addStep(Operations.createWriteAttributeOperation(
                Operations.createAddress("subsystem", "batch-jberet"),
                "default-job-repository",
                REPOSITORY_NAME));

        execute(managementClient.getControllerClient(), operationBuilder.build());
        ServerReload.reloadIfRequired(managementClient);
    }

    @SuppressWarnings("unused")
    protected void configureJobRepository(ModelNode op) {
        op.get("data-source").set("ExampleDS");
    }

    private static void execute(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).toString());
        }
        Operations.readResult(result);
    }
}
