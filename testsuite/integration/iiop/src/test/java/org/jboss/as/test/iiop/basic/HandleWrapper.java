/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.iiop.basic;

import java.io.Serializable;

import jakarta.ejb.Handle;

/**
 * @author Stuart Douglas
 */
public class HandleWrapper implements Serializable{

    private static final long serialVersionUID = 1L;
    private final Handle handle;

    public HandleWrapper(Handle handle) {
        this.handle = handle;
    }

    public Handle getHandle() {
        return handle;
    }
}
