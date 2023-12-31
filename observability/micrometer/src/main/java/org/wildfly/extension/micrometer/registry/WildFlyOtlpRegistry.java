/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.micrometer.registry;

import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.wildfly.extension.micrometer.WildFlyMicrometerConfig;

public class WildFlyOtlpRegistry extends OtlpMeterRegistry implements WildFlyRegistry {

    public WildFlyOtlpRegistry(WildFlyMicrometerConfig config) {
        super(config, Clock.SYSTEM);
    }
}
