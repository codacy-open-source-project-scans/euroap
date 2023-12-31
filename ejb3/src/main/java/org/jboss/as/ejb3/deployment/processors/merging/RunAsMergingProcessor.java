/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.merging;

import jakarta.annotation.security.RunAs;
import java.util.List;

import org.jboss.as.ee.component.EEApplicationClasses;
import org.jboss.as.ee.component.EEModuleClassDescription;
import org.jboss.as.ee.metadata.ClassAnnotationInformation;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.security.metadata.EJBBoundSecurityMetaData;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.ejb3.annotation.RunAsPrincipal;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.SecurityIdentityMetaData;
import org.jboss.metadata.javaee.spec.RunAsMetaData;

/**
 * Handles the {@link jakarta.annotation.security.RunAs} annotation merging
 *
 * @author Stuart Douglas
 */
public class RunAsMergingProcessor extends AbstractMergingProcessor<EJBComponentDescription> {

    private static final String DEFAULT_RUN_AS_PRINCIPAL = "anonymous";

    public RunAsMergingProcessor() {
        super(EJBComponentDescription.class);
    }

    @Override
    protected void handleAnnotations(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses,
                                     final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass,
                                     final EJBComponentDescription componentConfiguration) throws DeploymentUnitProcessingException {
        final EEModuleClassDescription clazz = applicationClasses.getClassByName(componentClass.getName());
        // we only care about annotations on the bean class itself
        if (clazz == null) {
            return;
        }
        final ClassAnnotationInformation<RunAs, String> runAs = clazz.getAnnotationInformation(RunAs.class);
        final ClassAnnotationInformation<RunAsPrincipal, String> runAsPrincipal = clazz
                .getAnnotationInformation(RunAsPrincipal.class);
        if (runAs == null) {
            if(runAsPrincipal != null) {
                EjbLogger.DEPLOYMENT_LOGGER.missingRunAsAnnotation(componentClass.getName());
            }
            return;
        }
        if (!runAs.getClassLevelAnnotations().isEmpty()) {
            componentConfiguration.setRunAs(runAs.getClassLevelAnnotations().get(0));
        }

        String principal = DEFAULT_RUN_AS_PRINCIPAL;
        if (runAsPrincipal != null
                && !runAsPrincipal.getClassLevelAnnotations().isEmpty()) {
            principal = runAsPrincipal.getClassLevelAnnotations().get(0);
        }
        componentConfiguration.setRunAsPrincipal(principal);
    }

    @Override
    protected void handleDeploymentDescriptor(final DeploymentUnit deploymentUnit,
                                              final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass,
                                              final EJBComponentDescription componentConfiguration) throws DeploymentUnitProcessingException {
        if (componentConfiguration.getDescriptorData() != null) {
            final SecurityIdentityMetaData identity = componentConfiguration.getDescriptorData().getSecurityIdentity();

            if (identity != null) {
                final RunAsMetaData runAs = identity.getRunAs();
                if (runAs != null) {
                    final String role = runAs.getRoleName();
                    if (role != null && !role.trim().isEmpty()) {
                        componentConfiguration.setRunAs(role.trim());
                    }
                }
            }
        }
        if (componentConfiguration.getRunAs() != null) {
            String principal = null;
            String globalRunAsPrincipal = null;
            EjbJarMetaData jbossMetaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
            if (jbossMetaData != null && jbossMetaData.getAssemblyDescriptor() != null) {
                List<EJBBoundSecurityMetaData> securityMetaDatas = jbossMetaData.getAssemblyDescriptor().getAny(EJBBoundSecurityMetaData.class);
                if (securityMetaDatas != null) {
                    for (EJBBoundSecurityMetaData securityMetaData : securityMetaDatas) {
                        if (securityMetaData.getEjbName().equals(componentConfiguration.getComponentName())) {
                            principal = securityMetaData.getRunAsPrincipal();
                            break;
                        }
                        // check global run-as principal
                        if (securityMetaData.getEjbName().equals("*")) {
                            globalRunAsPrincipal = securityMetaData.getRunAsPrincipal();
                            continue;
                        }
                    }
                }

                if (principal != null)
                    componentConfiguration.setRunAsPrincipal(principal);
                else if (globalRunAsPrincipal != null)
                    componentConfiguration.setRunAsPrincipal(globalRunAsPrincipal);
                else {
                    // we only set the run-as-principal to default, if it's not already set (via annotation) on the component
                    if (componentConfiguration.getRunAsPrincipal() == null) {
                        componentConfiguration.setRunAsPrincipal(DEFAULT_RUN_AS_PRINCIPAL);
                    }
                }

            }
        }
    }
}