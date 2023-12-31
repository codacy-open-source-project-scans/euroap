/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.component.deployers;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.metadata.ear.spec.EarMetaData;

/**
 * The Java EE6 spec and Enterprise Beans 3.1 spec contradict each other on what the "application-name" semantics are.
 * The Java EE6 spec says that in the absence of a (top level) .ear, the application-name is same as the
 * (top level) module name. So if a blah.jar is deployed, as per Java EE6 spec, both the module name and
 * application name are "blah". This is contradictory to the Enterprise Beans 3.1 spec (JNDI naming section) which says
 * that in the absence of a (top level) .ear, the application-name is null.
 * <p/>
 * This deployment processor, sets up the {@link Attachments#EAR_APPLICATION_NAME} attachment with the value
 * that's semantically equivalent to what the Enterprise Beans 3.1 spec expects.
 *
 * @author Jaikiran Pai
 */
public class EarApplicationNameProcessor implements DeploymentUnitProcessor {
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final String earApplicationName = this.getApplicationName(deploymentUnit);
        deploymentUnit.putAttachment(Attachments.EAR_APPLICATION_NAME, earApplicationName);
    }

    /**
     * Returns the application name for the passed deployment. If the passed deployment isn't an .ear or doesn't belong
     * to a .ear, then this method returns null. Else it returns the application-name set in the application.xml of the .ear
     * or if that's not set, will return the .ear deployment unit name (stripped off the .ear suffix).
     *
     * @param deploymentUnit The deployment unit
     */
    private String getApplicationName(DeploymentUnit deploymentUnit) {
        final DeploymentUnit parentDU = deploymentUnit.getParent();
        if (parentDU == null) {
            final EarMetaData earMetaData = deploymentUnit.getAttachment(org.jboss.as.ee.structure.Attachments.EAR_METADATA);
            if (earMetaData != null) {
                final String overriddenAppName = earMetaData.getApplicationName();
                if (overriddenAppName == null) {
                    return this.getEarName(deploymentUnit);
                }
                return overriddenAppName;
            } else {
                return this.getEarName(deploymentUnit);
            }
        }
        // traverse to top level DU
        return this.getApplicationName(parentDU);
    }

    /**
     * Returns the name (stripped off the .ear suffix) of the passed <code>deploymentUnit</code>.
     * Returns null if the passed <code>deploymentUnit</code>'s name doesn't end with .ear suffix.
     *
     * @param deploymentUnit Deployment unit
     * @return
     */
    private String getEarName(final DeploymentUnit deploymentUnit) {
        final String duName = deploymentUnit.getName();
        if (duName.endsWith(".ear")) {
            return duName.substring(0, duName.length() - ".ear".length());
        }
        return null;
    }
}
