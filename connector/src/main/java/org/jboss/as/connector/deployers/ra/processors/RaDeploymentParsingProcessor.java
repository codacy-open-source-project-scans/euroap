/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.deployers.ra.processors;


import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

import org.jboss.as.connector.deployers.Util;
import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.connector.metadata.xmldescriptors.ConnectorXmlDescriptor;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.jca.common.api.metadata.spec.Connector;
import org.jboss.jca.common.metadata.spec.RaParser;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;

/**
 * DeploymentUnitProcessor responsible for parsing a standard jca xml descriptor
 * and attaching the corresponding metadata. It take care also to register this
 * metadata into IronJacamar's MetadataRepository
 *
 * @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 */
public class RaDeploymentParsingProcessor implements DeploymentUnitProcessor {

    /**
     * Construct a new instance.
     */
    public RaDeploymentParsingProcessor() {
    }

    /**
     * Process a deployment for standard ra deployment files. Will parse the xml
     * file and attach a configuration discovered during processing.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final boolean resolveProperties = Util.shouldResolveSpec(deploymentUnit);

        final VirtualFile file = deploymentRoot.getRoot();
        if (file == null || !file.exists())
            return;

        final String deploymentRootName = file.getName().toLowerCase(Locale.ENGLISH);
        if (!deploymentRootName.endsWith(".rar")) {
            return;
        }

        final VirtualFile alternateDescriptor = deploymentRoot.getAttachment(org.jboss.as.ee.structure.Attachments.ALTERNATE_CONNECTOR_DEPLOYMENT_DESCRIPTOR);
        String prefix = "";

        if (deploymentUnit.getParent() != null) {
            prefix = deploymentUnit.getParent().getName() + "#";
        }

        String deploymentName = prefix + file.getName();
        ConnectorXmlDescriptor xmlDescriptor = process(resolveProperties, file, alternateDescriptor, deploymentName);


        phaseContext.getDeploymentUnit().putAttachment(ConnectorXmlDescriptor.ATTACHMENT_KEY, xmlDescriptor);

    }

    public static ConnectorXmlDescriptor process(boolean resolveProperties, VirtualFile file, VirtualFile alternateDescriptor, String deploymentName) throws DeploymentUnitProcessingException {
        // Locate the descriptor
        final VirtualFile serviceXmlFile;
        if (alternateDescriptor != null) {
            serviceXmlFile = alternateDescriptor;
        } else {
            serviceXmlFile = file.getChild("/META-INF/ra.xml");
        }
        InputStream xmlStream = null;
        Connector result = null;
        try {
            if (serviceXmlFile != null && serviceXmlFile.exists()) {

                xmlStream = serviceXmlFile.openStream();
                RaParser raParser = new RaParser();
                raParser.setSystemPropertiesResolved(resolveProperties);
                result = raParser.parse(xmlStream);
                if (result == null)
                    throw ConnectorLogger.ROOT_LOGGER.failedToParseServiceXml(serviceXmlFile);
            }
            File root = file.getPhysicalFile();
            URL url = root.toURI().toURL();
            return new ConnectorXmlDescriptor(result, root, url, deploymentName);

        } catch (Exception e) {
            throw ConnectorLogger.ROOT_LOGGER.failedToParseServiceXml(e, serviceXmlFile);
        } finally {
            VFSUtils.safeClose(xmlStream);
        }
    }
}
