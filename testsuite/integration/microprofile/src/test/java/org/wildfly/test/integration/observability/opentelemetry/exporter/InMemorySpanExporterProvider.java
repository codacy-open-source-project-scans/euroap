/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.opentelemetry.exporter;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.enterprise.inject.spi.CDI;

public class InMemorySpanExporterProvider implements ConfigurableSpanExporterProvider {
    @Override
    public SpanExporter createExporter(final ConfigProperties config) {
        return CDI.current().select(InMemorySpanExporter.class).get();
    }

    @Override
    public String getName() {
        return "in-memory";
    }
}
