/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.naming;

import java.security.BasicPermission;

/**
 * <p>
 * This class is for WildFly Naming's permissions. A permission
 * contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission
 * or you don't.
 * </p>
 *
 * <p>
 * The naming convention follows the hierarchical property naming convention.
 * An asterisk may appear by itself, or if immediately preceded by a "."
 * may appear at the end of the name, to signify a wildcard match.
 * </p>
 *
 * <p>
 * The target name is the name of the permission. The following table lists all the possible permission target names,
 * and for each provides a description of what the permission allows.
 * </p>
 *
 * <p>
 * <table border=1 cellpadding=5 summary="permission target name,
 *  what the target allows">
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * </tr>
 *
 * <tr>
 *   <td>setActiveNamingStore</td>
 *   <td>Set the active {@link org.jboss.as.naming.NamingStore}</td>
 * </tr>
 *
 *  </table>
 * </p>
 * @author Eduardo Martins
 */
public class NamingPermission extends BasicPermission {
    /**
     * Creates a new permission with the specified name.
     * The name is the symbolic name of the permission, such as
     * "setActiveNamingStore".
     *
     * @param name the name of the permission.
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */
    public NamingPermission(String name) {
        super(name);
    }

    /**
     * Creates a new permission object with the specified name.
     * The name is the symbolic name of the permission, and the
     * actions String is currently unused and should be null.
     *
     * @param name the name of the permission.
     * @param actions should be null.
     *
     * @throws NullPointerException if <code>name</code> is <code>null</code>.
     * @throws IllegalArgumentException if <code>name</code> is empty.
     */
    public NamingPermission(String name, String actions) {
        super(name, actions);
    }
}
