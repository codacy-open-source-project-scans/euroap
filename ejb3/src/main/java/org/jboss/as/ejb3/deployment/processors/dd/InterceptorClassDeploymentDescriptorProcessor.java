/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.dd;

import jakarta.interceptor.InvocationContext;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.DeploymentDescriptorEnvironment;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.component.InterceptorEnvironment;
import org.jboss.as.ee.component.interceptors.InterceptorClassDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.invocation.proxy.MethodIdentifier;
import org.jboss.metadata.ejb.spec.AroundInvokeMetaData;
import org.jboss.metadata.ejb.spec.AroundInvokesMetaData;
import org.jboss.metadata.ejb.spec.AroundTimeoutMetaData;
import org.jboss.metadata.ejb.spec.AroundTimeoutsMetaData;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.InterceptorMetaData;
import org.jboss.metadata.javaee.spec.LifecycleCallbackMetaData;
import org.jboss.metadata.javaee.spec.LifecycleCallbacksMetaData;

/**
 * Processor that handles the &lt;interceptor\&gt; element of an ejb-jar.xml file.
 *
 * @author Stuart Douglas
 */
public class InterceptorClassDeploymentDescriptorProcessor implements DeploymentUnitProcessor {
    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EjbJarMetaData metaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        final EEModuleDescription eeModuleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        if (metaData == null) {
            return;
        }

        if (metaData.getInterceptors() == null) {
            return;
        }

        for (InterceptorMetaData interceptor : metaData.getInterceptors()) {
            String interceptorClassName = interceptor.getInterceptorClass();
            AroundInvokesMetaData aroundInvokes = interceptor.getAroundInvokes();
            if (aroundInvokes != null) {
                for (AroundInvokeMetaData aroundInvoke : aroundInvokes) {
                    final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                    String methodName = aroundInvoke.getMethodName();
                    MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifier(Object.class, methodName, InvocationContext.class);
                    builder.setAroundInvoke(methodIdentifier);
                    if (aroundInvoke.getClassName() == null || aroundInvoke.getClassName().isEmpty()) {
                        eeModuleDescription.addInterceptorMethodOverride(interceptorClassName, builder.build());
                    } else {
                        eeModuleDescription.addInterceptorMethodOverride(aroundInvoke.getClassName(), builder.build());
                    }
                }
            }

            AroundTimeoutsMetaData aroundTimeouts = interceptor.getAroundTimeouts();
            if (aroundTimeouts != null) {
                for (AroundTimeoutMetaData aroundTimeout : aroundTimeouts) {
                    final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                    String methodName = aroundTimeout.getMethodName();
                    MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifier(Object.class, methodName, InvocationContext.class);
                    builder.setAroundTimeout(methodIdentifier);
                    if (aroundTimeout.getClassName() == null || aroundTimeout.getClassName().isEmpty()) {
                        eeModuleDescription.addInterceptorMethodOverride(interceptorClassName, builder.build());
                    } else {
                        eeModuleDescription.addInterceptorMethodOverride(aroundTimeout.getClassName(), builder.build());
                    }
                }
            }

            // post-construct(s) of the interceptor configured (if any) in the deployment descriptor
            LifecycleCallbacksMetaData postConstructs = interceptor.getPostConstructs();
            if (postConstructs != null) {
                for (LifecycleCallbackMetaData postConstruct : postConstructs) {
                    final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                    String methodName = postConstruct.getMethodName();
                    MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifier(void.class, methodName, InvocationContext.class);
                    builder.setPostConstruct(methodIdentifier);
                    if (postConstruct.getClassName() == null || postConstruct.getClassName().isEmpty()) {
                        eeModuleDescription.addInterceptorMethodOverride(interceptorClassName, builder.build());
                    } else {
                        eeModuleDescription.addInterceptorMethodOverride(postConstruct.getClassName(), builder.build());
                    }
                }
            }

            // pre-destroy(s) of the interceptor configured (if any) in the deployment descriptor
            LifecycleCallbacksMetaData preDestroys = interceptor.getPreDestroys();
            if (preDestroys != null) {
                for (LifecycleCallbackMetaData preDestroy : preDestroys) {
                    final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                    String methodName = preDestroy.getMethodName();
                    MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifier(void.class, methodName, InvocationContext.class);
                    builder.setPreDestroy(methodIdentifier);
                    if (preDestroy.getClassName() == null || preDestroy.getClassName().isEmpty()) {
                        eeModuleDescription.addInterceptorMethodOverride(interceptorClassName, builder.build());
                    } else {
                        eeModuleDescription.addInterceptorMethodOverride(preDestroy.getClassName(), builder.build());
                    }
                }
            }

            // pre-passivates(s) of the interceptor configured (if any) in the deployment descriptor
            LifecycleCallbacksMetaData prePassivates = interceptor.getPrePassivates();
            if (prePassivates != null) {
                for (LifecycleCallbackMetaData prePassivate : prePassivates) {
                    final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                    String methodName = prePassivate.getMethodName();
                    MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifier(void.class, methodName, InvocationContext.class);
                    builder.setPrePassivate(methodIdentifier);
                    if (prePassivate.getClassName() == null || prePassivate.getClassName().isEmpty()) {
                        eeModuleDescription.addInterceptorMethodOverride(interceptorClassName, builder.build());
                    } else {
                        eeModuleDescription.addInterceptorMethodOverride(prePassivate.getClassName(), builder.build());
                    }
                }
            }

            // pre-passivates(s) of the interceptor configured (if any) in the deployment descriptor
            LifecycleCallbacksMetaData postActivates = interceptor.getPostActivates();
            if (postActivates != null) {
                for (LifecycleCallbackMetaData postActivate : postActivates) {
                    final InterceptorClassDescription.Builder builder = InterceptorClassDescription.builder();
                    String methodName = postActivate.getMethodName();
                    MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifier(void.class, methodName, InvocationContext.class);
                    builder.setPostActivate(methodIdentifier);
                    if (postActivate.getClassName() == null || postActivate.getClassName().isEmpty()) {
                        eeModuleDescription.addInterceptorMethodOverride(interceptorClassName, builder.build());
                    } else {
                        eeModuleDescription.addInterceptorMethodOverride(postActivate.getClassName(), builder.build());
                    }
                }
            }

            if(interceptor.getJndiEnvironmentRefsGroup() != null) {
                final DeploymentDescriptorEnvironment environment = new DeploymentDescriptorEnvironment("java:comp/env", interceptor.getJndiEnvironmentRefsGroup());
                eeModuleDescription.addInterceptorEnvironment(interceptor.getInterceptorClass(), new InterceptorEnvironment(environment));
            }
        }

    }
}
