/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.smoke.deployment.rar.configproperty;

import java.io.Serializable;
import javax.naming.NamingException;
import javax.naming.Reference;
import jakarta.resource.Referenceable;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterAssociation;

/**
 * ConfigPropertyAdminObjectImpl
 *
 * @version $Revision: $
 */
public class ConfigPropertyAdminObjectImpl implements ConfigPropertyAdminObjectInterface,
        ResourceAdapterAssociation, Referenceable, Serializable {
    /**
     * Serial version uid
     */
    private static final long serialVersionUID = 1L;

    /**
     * The resource adapter
     */
    private ResourceAdapter ra;

    /**
     * Reference
     */
    private Reference reference;

    /**
     * property
     */
    private String property;

    /**
     * Default constructor
     */
    public ConfigPropertyAdminObjectImpl() {

    }

    /**
     * Set property
     *
     * @param property The value
     */
    public void setProperty(String property) {
        this.property = property;
    }

    /**
     * Get property
     *
     * @return The value
     */
    public String getProperty() {
        return property;
    }

    /**
     * Get the resource adapter
     *
     * @return The handle
     */
    public ResourceAdapter getResourceAdapter() {
        return ra;
    }

    /**
     * Set the resource adapter
     *
     * @param ra The handle
     */
    public void setResourceAdapter(ResourceAdapter ra) {
        this.ra = ra;
    }

    /**
     * Get the Reference instance.
     *
     * @return Reference instance
     * @throws javax.naming.NamingException Thrown if a reference can't be obtained
     */
    @Override
    public Reference getReference() throws NamingException {
        return reference;
    }

    /**
     * Set the Reference instance.
     *
     * @param reference A Reference instance
     */
    @Override
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        int result = 17;
        if (property != null) { result += 31 * result + 7 * property.hashCode(); } else { result += 31 * result + 7; }
        return result;
    }

    /**
     * Indicates whether some other object is equal to this one.
     *
     * @param other The reference object with which to compare.
     * @return true if this object is the same as the obj argument, false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) { return false; }
        if (other == this) { return true; }
        if (!(other instanceof ConfigPropertyAdminObjectImpl)) { return false; }
        ConfigPropertyAdminObjectImpl obj = (ConfigPropertyAdminObjectImpl) other;
        boolean result = true;
        if (result) {
            if (property == null) { result = obj.getProperty() == null; } else { result = property.equals(obj.getProperty()); }
        }
        return result;
    }


}
