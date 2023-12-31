/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ejb3.logging.EjbLogger;

/**
 * User: jpai
 */
public class EjbJarDescription {

    private final EEModuleDescription eeModuleDescription;

    private final Set<String> applicationLevelSecurityRoles = new HashSet<String>();

    /**
     * True if this represents EJB's packaged in a war
     */
    private final boolean war;

    public EjbJarDescription(EEModuleDescription eeModuleDescription, boolean war) {
        this.war = war;
        if (eeModuleDescription == null) {
            throw EjbLogger.ROOT_LOGGER.paramCannotBeNull("EE module description");
        }
        this.eeModuleDescription = eeModuleDescription;
    }

    public void addSecurityRole(final String role) {
        if (role == null || role.trim().isEmpty()) {
            throw EjbLogger.ROOT_LOGGER.stringParamCannotBeNullOrEmpty("Security role");
        }
        this.applicationLevelSecurityRoles.add(role);
    }

    /**
     * Returns the security roles that have been defined at the application level (via security-role elements in the
     * ejb-jar.xml). Note that this set does *not* include the roles that have been defined at each individual component
     * level (via @DeclareRoles, @RolesAllowed annotations or security-role-ref element)
     * <p/>
     * Returns an empty set if no roles have been defined at the application level.
     *
     * @return
     */
    public Set<String> getSecurityRoles() {
        return Collections.unmodifiableSet(this.applicationLevelSecurityRoles);
    }


    public boolean hasComponent(String componentName) {
        return eeModuleDescription.hasComponent(componentName);
    }

    public EEModuleDescription getEEModuleDescription() {
        return this.eeModuleDescription;
    }


    public boolean isWar() {
        return war;
    }

}
