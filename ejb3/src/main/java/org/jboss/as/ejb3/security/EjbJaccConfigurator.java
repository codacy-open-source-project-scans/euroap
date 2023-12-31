/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.security;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.Permission;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.security.jacc.EJBMethodPermission;
import jakarta.security.jacc.EJBRoleRefPermission;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentConfigurator;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.ViewConfiguration;
import org.jboss.as.ee.component.serialization.WriteReplaceInterface;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.component.EJBViewConfiguration;
import org.jboss.as.ejb3.component.session.SessionBeanComponentDescription;
import org.jboss.as.ejb3.deployment.ApplicableMethodInformation;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndexUtil;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.metadata.ejb.spec.MethodInterfaceType;
import org.jboss.metadata.javaee.spec.SecurityRoleMetaData;
import org.jboss.metadata.javaee.spec.SecurityRolesMetaData;

/**
 * Component configurator the calculates JACC roles
 *
 * @author Stuart Douglas
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class EjbJaccConfigurator implements ComponentConfigurator {

    private static final String ANY_AUTHENTICATED_USER_ROLE = "**";

    @Override
    public void configure(final DeploymentPhaseContext context, final ComponentDescription description, final ComponentConfiguration configuration) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = context.getDeploymentUnit();
        final DeploymentReflectionIndex reflectionIndex = deploymentUnit.getAttachment(Attachments.REFLECTION_INDEX);
        final EJBComponentDescription ejbComponentDescription = EJBComponentDescription.class.cast(description);
        final EjbJaccConfig ejbJaccConfig = new EjbJaccConfig();
        context.getDeploymentUnit().addToAttachmentList(EjbDeploymentAttachmentKeys.JACC_PERMISSIONS, ejbJaccConfig);

        // process the method permissions.
        for (final ViewConfiguration viewConfiguration : configuration.getViews()) {
            final List<Method> viewMethods = viewConfiguration.getProxyFactory().getCachedMethods();
            for (final Method viewMethod : viewMethods) {
                if (!Modifier.isPublic(viewMethod.getModifiers()) || viewMethod.getDeclaringClass() == WriteReplaceInterface.class) {
                    continue;
                }
                final EJBViewConfiguration ejbViewConfiguration = EJBViewConfiguration.class.cast(viewConfiguration);
                // try to create permissions using the descriptor metadata first.
                ApplicableMethodInformation<EJBMethodSecurityAttribute> permissions = ejbComponentDescription.getDescriptorMethodPermissions();
                boolean createdPerms = this.createPermissions(ejbJaccConfig, ejbComponentDescription, ejbViewConfiguration, viewMethod, reflectionIndex, permissions);
                // no permissions created using the descriptor metadata - try to use annotation metadata.
                if (!createdPerms) {
                    permissions = ejbComponentDescription.getAnnotationMethodPermissions();
                    createPermissions(ejbJaccConfig, ejbComponentDescription, ejbViewConfiguration, viewMethod, reflectionIndex, permissions);
                }
            }
        }

        Set<String> securityRoles = new HashSet<String>();
        // get all roles from the deployments descriptor (assembly descriptor roles)
        SecurityRolesMetaData secRolesMetaData = ejbComponentDescription.getSecurityRoles();
        if (secRolesMetaData != null) {
            for (SecurityRoleMetaData secRoleMetaData : secRolesMetaData) {
                securityRoles.add(secRoleMetaData.getRoleName());
            }
        }

        // at this point any roles specified via RolesAllowed annotation have been mapped to EJBMethodPermissions, so
        // going through the permissions allows us to retrieve these roles.
        // TODO there might be a better way to retrieve just annotated roles without going through all processed permissions
        List<Map.Entry<String, Permission>> processedRoles = ejbJaccConfig.getRoles();
        for (Map.Entry<String, Permission> entry : processedRoles) {
            securityRoles.add(entry.getKey());
        }

        securityRoles.add(ANY_AUTHENTICATED_USER_ROLE);

        // process the security-role-ref from the deployment descriptor.
        Map<String, Collection<String>> securityRoleRefs = ejbComponentDescription.getSecurityRoleLinks();
        for (Map.Entry<String, Collection<String>> entry : securityRoleRefs.entrySet()) {
            String roleName = entry.getKey();
            for (String roleLink : entry.getValue()) {
                EJBRoleRefPermission p = new EJBRoleRefPermission(ejbComponentDescription.getEJBName(), roleName);
                ejbJaccConfig.addRole(roleLink, p);
            }
            securityRoles.remove(roleName);
        }

        // process remaining annotated declared roles that were not overridden in the descriptor.
        Set<String> declaredRoles = ejbComponentDescription.getDeclaredRoles();
        for (String role : declaredRoles) {
            if (!securityRoleRefs.containsKey(role)) {
                EJBRoleRefPermission p = new EJBRoleRefPermission(ejbComponentDescription.getEJBName(), role);
                ejbJaccConfig.addRole(role, p);
            }
            securityRoles.remove(role);
        }

        // an EJBRoleRefPermission must be created for each declared role that does not appear in the security-role-ref.
        for (String role : securityRoles) {
            EJBRoleRefPermission p = new EJBRoleRefPermission(ejbComponentDescription.getEJBName(), role);
            ejbJaccConfig.addRole(role, p);
        }

        // special handling of stateful session bean getEJBObject due how the stateful session handles acquire the
        // proxy by sending an invocation to the ejb container.
        if (ejbComponentDescription instanceof SessionBeanComponentDescription) {
            SessionBeanComponentDescription session = SessionBeanComponentDescription.class.cast(ejbComponentDescription);
            if (session.isStateful()) {
                EJBMethodPermission p = new EJBMethodPermission(ejbComponentDescription.getEJBName(), "getEJBObject", "Home", null);
                ejbJaccConfig.addPermit(p);
            }
        }
    }

    protected boolean createPermissions(final EjbJaccConfig ejbJaccConfig, final EJBComponentDescription description, final EJBViewConfiguration ejbViewConfiguration,
                                     final Method viewMethod, final DeploymentReflectionIndex index, final ApplicableMethodInformation<EJBMethodSecurityAttribute> permissions) {

        EJBMethodSecurityAttribute ejbMethodSecurityMetaData = permissions.getViewAttribute(ejbViewConfiguration.getMethodIntf(), viewMethod);
        //if this is null we try with the corresponding bean method.
        if (ejbMethodSecurityMetaData == null) {
            ejbMethodSecurityMetaData = permissions.getViewAttribute(MethodInterfaceType.Bean, viewMethod);
        }

        final Method classMethod = ClassReflectionIndexUtil.findMethod(index, ejbViewConfiguration.getComponentConfiguration().getComponentClass(), viewMethod);
        if (ejbMethodSecurityMetaData == null
                && classMethod != null) {
            // if this is null we try with the corresponding bean method.
            ejbMethodSecurityMetaData = permissions.getAttribute(ejbViewConfiguration.getMethodIntf(), classMethod);
            if (ejbMethodSecurityMetaData == null) {
                ejbMethodSecurityMetaData = permissions.getAttribute(MethodInterfaceType.Bean, classMethod);

            }
        }

        // check if any security metadata was defined for the method.
        if (ejbMethodSecurityMetaData != null) {
            final MethodInterfaceType interfaceType = ejbViewConfiguration.getMethodIntf();
            final EJBMethodPermission permission = new EJBMethodPermission(description.getEJBName(), interfaceType.name(), viewMethod);

            if (ejbMethodSecurityMetaData.isPermitAll()) {
                ejbJaccConfig.addPermit(permission);
            }

            if (ejbMethodSecurityMetaData.isDenyAll()) {
                ejbJaccConfig.addDeny(permission);
            }

            for (String role : ejbMethodSecurityMetaData.getRolesAllowed()) {
                ejbJaccConfig.addRole(role, permission);
            }
            return true;
        }
        return false;
    }
}
