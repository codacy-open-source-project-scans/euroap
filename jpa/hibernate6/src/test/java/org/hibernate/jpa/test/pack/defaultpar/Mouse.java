/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hibernate.jpa.test.pack.defaultpar;

import jakarta.persistence.ExcludeDefaultListeners;

/**
 * @author Emmanuel Bernard
 */
@ExcludeDefaultListeners
public class Mouse {
    private Integer id;
    private String name;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
