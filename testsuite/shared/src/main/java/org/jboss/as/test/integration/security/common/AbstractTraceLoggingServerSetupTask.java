/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.security.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 *
 * @author Josef Cacek
 */
public abstract class AbstractTraceLoggingServerSetupTask implements ServerSetupTask {

    private static final Logger LOGGER = Logger.getLogger(AbstractTraceLoggingServerSetupTask.class);

    private static final PathAddress PATH_LOGGING = PathAddress.pathAddress(SUBSYSTEM, "logging");

    protected Collection<String> categories;

    /*
     * (non-Javadoc)
     *
     * @see org.jboss.as.arquillian.api.ServerSetupTask#setup(org.jboss.as.arquillian.container.ManagementClient,
     * java.lang.String)
     */
    @Override
    public void setup(ManagementClient managementClient, String containerId) throws Exception {
        categories = getCategories(managementClient, containerId);
        if (categories == null || categories.isEmpty()) {
            LOGGER.warn("getCategories() returned empty collection.");
            return;
        }

        final List<ModelNode> updates = new ArrayList<ModelNode>();

        for (String category : categories) {
            if (category == null || category.length() == 0) {
                LOGGER.warn("Empty category name provided.");
                continue;
            }
            ModelNode op = Util.createAddOperation(PATH_LOGGING.append("logger", category));
            op.get("level").set("TRACE");
            updates.add(op);
        }
        ModelNode op = Util.createEmptyOperation("undefine-attribute", PATH_LOGGING.append("console-handler", "CONSOLE"));
        op.get("name").set("level");
        updates.add(op);
        CoreUtils.applyUpdates(updates, managementClient.getControllerClient());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jboss.as.arquillian.api.ServerSetupTask#tearDown(org.jboss.as.arquillian.container.ManagementClient,
     * java.lang.String)
     */
    @Override
    public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
        if (categories == null || categories.isEmpty()) {
            return;
        }

        final List<ModelNode> updates = new ArrayList<ModelNode>();

        for (String category : categories) {
            if (category == null || category.length() == 0) {
                continue;
            }
            updates.add(Util.createRemoveOperation(PATH_LOGGING.append("logger", category)));
        }
        ModelNode op = Util.createEmptyOperation("write-attribute", PATH_LOGGING.append("console-handler", "CONSOLE"));
        op.get("name").set("level");
        op.get("value").set("INFO");
        updates.add(op);
        CoreUtils.applyUpdates(updates, managementClient.getControllerClient());
    }

    protected abstract Collection<String> getCategories(ManagementClient managementClient, String containerId);

}
