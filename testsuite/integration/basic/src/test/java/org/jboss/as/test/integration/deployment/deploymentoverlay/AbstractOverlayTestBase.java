/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.deployment.deploymentoverlay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.security.common.SecurityTraceLoggingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 * @author baranowb
 * @author lgao
 */
// Uncomment if TRACE logging is necessary. Don't leave it on all the time; CI resources aren't free.
//@ServerSetup(AbstractOverlayTestBase.TraceLoggingSetup.class)
public abstract class AbstractOverlayTestBase {

    protected static final Logger LOGGER = Logger.getLogger(AbstractOverlayTestBase.class);

    @ArquillianResource
    protected ManagementClient managementClient;

    @ArquillianResource
    protected Deployer deployer;

    private boolean removeOverlay = false;

    public void setupOverlay(final String deployment, final String overlayName, final Map<String, String> overlay)
            throws Exception {

        // create overlay
        ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP_ADDR).set(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, overlayName);
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
        ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
        removeOverlay=true;

        for (Map.Entry<String, String> overlayItem : overlay.entrySet()) {
            // add content
            op = new ModelNode();
            op.get(ModelDescriptionConstants.OP_ADDR).set(new ModelNode());
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.UPLOAD_DEPLOYMENT_BYTES);
            op.get(ModelDescriptionConstants.BYTES).set(overlayItem.getValue().getBytes(StandardCharsets.UTF_8));
            ModelNode result = ManagementOperations.executeOperation(managementClient.getControllerClient(), op);

            // link content to specific file
            op = new ModelNode();
            ModelNode addr = new ModelNode();
            addr.add(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, overlayName);
            addr.add(ModelDescriptionConstants.CONTENT, overlayItem.getKey());
            op.get(ModelDescriptionConstants.OP_ADDR).set(addr);
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
            op.get(ModelDescriptionConstants.CONTENT).get(ModelDescriptionConstants.HASH).set(result);
            ManagementOperations.executeOperation(managementClient.getControllerClient(), op);

        }

        // add link
        op = new ModelNode();
        ModelNode addr = new ModelNode();
        addr.add(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, overlayName);
        addr.add(ModelDescriptionConstants.DEPLOYMENT, deployment);
        op.get(ModelDescriptionConstants.OP_ADDR).set(addr);
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
        ManagementOperations.executeOperation(managementClient.getControllerClient(), op);

        op = new ModelNode();
        addr = new ModelNode();
        addr.add(ModelDescriptionConstants.DEPLOYMENT, deployment);
        op.get(ModelDescriptionConstants.OP_ADDR).set(addr);
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.REDEPLOY);
        ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
    }

    public void setupOverlay(final String deployment, final String overlayName, final String overlayPath,
            final String overlayedContent) throws Exception {
        setupOverlay(deployment, overlayName, Collections.singletonMap(overlayPath, overlayedContent));
    }

    public void removeOverlay(final String deployment, final String overlayName, final Set<String> overlayPaths)
            throws Exception {
        if (!removeOverlay) {
            return;
        }
        for (String overlayPath : overlayPaths) {
            removeContentItem(overlayName, overlayPath);
        }
        removeDeploymentItem(overlayName, deployment);

        ModelNode op = new ModelNode();
        op.get(ModelDescriptionConstants.OP_ADDR).set(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, overlayName);
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.REMOVE);
        ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
        removeOverlay = false;
    }

    public void removeOverlay(final String deployment, final String overlayName, final String overlayPath) throws Exception {
        removeOverlay(deployment, overlayName, Collections.singleton(overlayPath));
    }

    protected void removeContentItem(final String w, final String a) throws IOException, MgmtOperationException {
        final ModelNode op;
        final ModelNode addr;
        op = new ModelNode();
        addr = new ModelNode();
        addr.add(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, w);
        addr.add(ModelDescriptionConstants.CONTENT, a);
        op.get(ModelDescriptionConstants.OP_ADDR).set(addr);
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.REMOVE);
        ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
    }

    protected void removeDeploymentItem(final String w, final String a) throws IOException, MgmtOperationException {
        final ModelNode op;
        final ModelNode addr;
        op = new ModelNode();
        addr = new ModelNode();
        addr.add(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, w);
        addr.add(ModelDescriptionConstants.DEPLOYMENT, a);
        op.get(ModelDescriptionConstants.OP_ADDR).set(addr);
        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.REMOVE);
        ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
    }

    public static class TraceLoggingSetup extends SecurityTraceLoggingServerSetupTask {

        @Override
        protected Collection<String> getCategories(ManagementClient managementClient, String containerId) {
            Set<String> coll = new HashSet<>(super.getCategories(managementClient, containerId));
            coll.add("org.jboss.sasl");
            coll.add("org.jboss.as.ejb3");
            coll.add("org.jboss.as.remoting");
            coll.add("org.jboss.remoting3");
            coll.add("org.jboss.remoting");
            coll.add("org.jboss.naming.remote");
            return coll;
        }
    }
}
