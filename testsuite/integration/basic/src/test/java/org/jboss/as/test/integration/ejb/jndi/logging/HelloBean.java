/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.jndi.logging;

import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;

@Stateless(name="Hello")
@Remote(Hello.class)
public class HelloBean implements Hello {
}
