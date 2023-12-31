/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.metadata.javaee.spec.SecurityRolesMetaData;

/**
 * Holds the Jakarta Enterprise Beans component level security metadata.
 * <p/>
 * For per method specific security metadata, take a look at {@link EJBMethodSecurityAttribute}
 * <p/>
 * User: Jaikiran Pai
 */
public class EJBSecurityMetaData {

    /**
     * The security domain for this Jakarta Enterprise Beans component
     */
    private final String securityDomainName;

    /**
     * The run-as role (if any) for this Jakarta Enterprise Beans component
     */
    private final String runAsRole;

    /**
     * The roles declared (via @DeclareRoles) on this Jakarta Enterprise Beans component
     */
    private final Set<String> declaredRoles;

    /**
     * The run-as principal (if any) for this Jakarta Enterprise Beans component
     */
    private final String runAsPrincipal;

    /**
     * Roles mapped with security-role
     */
    private final SecurityRolesMetaData securityRoles;

    /**
     * Security role links. The key is the "from" role name and the value is a collection of "to" role names of the link.
     */
    private final Map<String, Collection<String>> securityRoleLinks;

    /**
     * @param componentConfiguration Component configuration of the Jakarta Enterprise Beans component
     */
    public EJBSecurityMetaData(final ComponentConfiguration componentConfiguration) {
        if (componentConfiguration.getComponentDescription() instanceof EJBComponentDescription == false) {
            throw EjbLogger.ROOT_LOGGER.invalidComponentConfiguration(componentConfiguration.getComponentName());
        }
        final EJBComponentDescription ejbComponentDescription = (EJBComponentDescription) componentConfiguration.getComponentDescription();
        this.runAsRole = ejbComponentDescription.getRunAs();
        this.securityDomainName = ejbComponentDescription.getResolvedSecurityDomain();
        this.runAsPrincipal = ejbComponentDescription.getRunAsPrincipal();
        this.securityRoles = ejbComponentDescription.getSecurityRoles();
        final Map<String, Collection<String>> links = ejbComponentDescription.getSecurityRoleLinks();
        // security role links configured via <security-role-ref>
        this.securityRoleLinks = links == null ? Collections.<String, Collection<String>>emptyMap() : Collections.unmodifiableMap(links);
        // @DeclareRoles
        final Set<String> roles = ejbComponentDescription.getDeclaredRoles();
        this.declaredRoles = roles == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(roles);
    }

    /**
     * Returns the roles that have been declared by the bean. Returns an empty set if there are no declared roles.
     *
     * @return
     */
    public Set<String> getDeclaredRoles() {
        return declaredRoles;
    }

    /**
     * Returns the run-as role associated with this bean. Returns null if there's no run-as role associated.
     *
     * @return
     */
    public String getRunAs() {
        return this.runAsRole;
    }

    /**
     * Returns the security domain associated with the bean
     *
     * @return
     */
    public String getSecurityDomainName() {
        return this.securityDomainName;
    }

    /**
     * Returns the run-as principal associated with this bean. Returns 'anonymous' if no principal was set.
     *
     * @return
     */
    public String getRunAsPrincipal() {
        return runAsPrincipal;
    }

    /**
     * Returns the security-role mapping.
     *
     * @return
     */
    public SecurityRolesMetaData getSecurityRoles() {
        return securityRoles;
    }

    /**
     * Returns the security role links (configured via <security-role-ref>) applicable for the
     * Jakarta Enterprise Beans. Returns an empty map if no role links are configured
     *
     * @return
     */
    public Map<String, Collection<String>> getSecurityRoleLinks() {
        return this.securityRoleLinks;
    }
}
