/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.view.basic;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;

/**
 * @author: Jaikiran Pai
 */
@Stateless
@Remote
public class MultipleRemoteViewBean implements One, Two, Three, Serializable, Externalizable {
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }
}
