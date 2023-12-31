/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.ejb;

import org.jboss.as.controller.PathAddress;
import org.wildfly.clustering.ejb.remote.ClientMappingsRegistryProvider;

/**
 * Service configurator for local client mappings registry provider.
 *
 * @author Paul Ferraro
 * @author Richard Achmatowicz
 */
public class LocalClientMappingsRegistryProviderServiceConfigurator extends ClientMappingsRegistryProviderServiceConfigurator {

    public LocalClientMappingsRegistryProviderServiceConfigurator(PathAddress address) {
        super(address);
    }

    @Override
    public ClientMappingsRegistryProvider get() {
        return new LocalClientMappingsRegistryProvider();
    }
}
