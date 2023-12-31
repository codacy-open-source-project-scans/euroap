/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.security.common.config.DataSource;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.SnapshotRestoreSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 * {@link ServerSetupTask} instance for datasources setup.
 *
 * @author Josef Cacek
 */
public abstract class AbstractDataSourceServerSetupTask extends SnapshotRestoreSetupTask {

    private static final Logger LOGGER = Logger.getLogger(AbstractDataSourceServerSetupTask.class);
    private static final String SUBSYSTEM_DATASOURCES = "datasources";
    private static final String DATASOURCE = "data-source";

    // Public methods --------------------------------------------------------

    /**
     * Adds a security domain represented by this class to the AS configuration.
     *
     * @param managementClient
     * @param containerId
     * @throws Exception
     * @see ServerSetupTask#setup(ManagementClient,
     *      String)
     */
    public final void doSetup(final ManagementClient managementClient, String containerId) throws Exception {
        final DataSource[] dataSourceConfigurations = getDataSourceConfigurations(managementClient, containerId);

        if (dataSourceConfigurations == null) {
            LOGGER.warn("Null DataSourceConfiguration array provided");
            return;
        }

        final List<ModelNode> updates = new ArrayList<ModelNode>();
        for (final DataSource config : dataSourceConfigurations) {
            final String name = config.getName();
            LOGGER.trace("Adding datasource " + name);
            final ModelNode dsNode = new ModelNode();
            dsNode.get(OP).set(ADD);
            dsNode.get(OP_ADDR).add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
            dsNode.get(OP_ADDR).add(DATASOURCE, name);

            dsNode.get("connection-url").set(config.getConnectionUrl());
            dsNode.get("jndi-name").set(config.getJndiName());
            dsNode.get("driver-name").set(config.getDriver());
            dsNode.get("enabled").set("false");
            if (StringUtils.isNotEmpty(config.getUsername())) {
                dsNode.get("user-name").set(config.getUsername());
            }
            if (StringUtils.isNotEmpty(config.getPassword())) {
                dsNode.get("password").set(config.getPassword());
            }
            updates.add(dsNode);
            final ModelNode enableNode = new ModelNode();
            enableNode.get("name").set("enabled");
            enableNode.get("value").set(true);
            enableNode.get(OP).set("write-attribute");
            enableNode.get(OP_ADDR).add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
            enableNode.get(OP_ADDR).add(DATASOURCE, name);
            updates.add(enableNode);
        }
        CoreUtils.applyUpdates(updates, managementClient.getControllerClient());
        ServerReload.executeReloadAndWaitForCompletion(managementClient, 50000);
    }

    // Protected methods -----------------------------------------------------

    protected DataSource[] getDataSourceConfigurations(final ManagementClient managementClient, String containerId) {
        return null;
    }
}
