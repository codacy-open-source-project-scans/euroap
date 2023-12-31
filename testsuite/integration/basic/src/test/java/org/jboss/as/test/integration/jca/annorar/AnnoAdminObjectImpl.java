/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jca.annorar;

import javax.naming.NamingException;
import javax.naming.Reference;
import jakarta.resource.spi.AdministeredObject;
import jakarta.resource.spi.ConfigProperty;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterAssociation;

/**
 * AnnoAdminObjectImpl
 *
 * @version $Revision: $
 */
@AdministeredObject(adminObjectInterfaces = {AnnoAdminObject.class})
public class AnnoAdminObjectImpl implements AnnoAdminObject,
        ResourceAdapterAssociation {
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
     * first
     */
    @ConfigProperty(defaultValue = "12345", description = {"1st", "first"}, ignore = true, supportsDynamicUpdates = false, confidential = true)
    private Long first;

    /**
     * second
     */
    private Boolean second;

    /**
     * Default constructor
     */
    public AnnoAdminObjectImpl() {

    }

    /**
     * Set first
     *
     * @param first The value
     */
    public void setFirst(Long first) {
        this.first = first;
    }

    /**
     * Get first
     *
     * @return The value
     */
    public Long getFirst() {
        return first;
    }

    /**
     * Set second
     *
     * @param second The value
     */
    @ConfigProperty(defaultValue = "false", description = {"2nd", "second"}, ignore = false, supportsDynamicUpdates = true, confidential = false)
    public void setSecond(Boolean second) {
        this.second = second;
    }

    /**
     * Get second
     *
     * @return The value
     */
    public Boolean getSecond() {
        return second;
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
     * @throws NamingException Thrown if a reference can't be obtained
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
        if (first != null) { result += 31 * result + 7 * first.hashCode(); } else { result += 31 * result + 7; }
        if (second != null) { result += 31 * result + 7 * second.hashCode(); } else { result += 31 * result + 7; }
        return result;
    }

    /**
     * Indicates whether some other object is equal to this one.
     *
     * @param other The reference object with which to compare.
     * @return true if this object is the same as the obj argument, false
     * otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) { return false; }
        if (other == this) { return true; }
        if (!(other instanceof AnnoAdminObjectImpl)) { return false; }
        boolean result = true;
        AnnoAdminObjectImpl obj = (AnnoAdminObjectImpl) other;
        if (result) {
            if (first == null) { result = obj.getFirst() == null; } else { result = first.equals(obj.getFirst()); }
        }
        if (result) {
            if (second == null) { result = obj.getSecond() == null; } else { result = second.equals(obj.getSecond()); }
        }
        return result;
    }

}
