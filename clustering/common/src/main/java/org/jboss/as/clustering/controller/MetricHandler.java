/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.controller;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Generic {@link org.jboss.as.controller.OperationStepHandler} for runtime metrics.
 * @author Paul Ferraro
 */
public class MetricHandler<C> extends AbstractRuntimeOnlyHandler implements ManagementRegistrar<ManagementResourceRegistration> {

    private final Collection<? extends Metric<C>> metrics;
    private final Map<String, Metric<C>> executables = new HashMap<>();
    private final Executor<C, Metric<C>> executor;

    public <M extends Enum<M> & Metric<C>> MetricHandler(MetricExecutor<C> executor, Class<M> metricClass) {
        this(executor, EnumSet.allOf(metricClass));
    }

    public MetricHandler(MetricExecutor<C> executor, Metric<C>[] metrics) {
        this(executor, Arrays.asList(metrics));
    }

    public MetricHandler(MetricExecutor<C> executor, Collection<? extends Metric<C>> metrics) {
        this.executor = executor;
        for (Metric<C> executable : metrics) {
            this.executables.put(executable.getName(), executable);
        }
        this.metrics = metrics;
    }

    @Override
    public void register(ManagementResourceRegistration registration) {
        for (Metric<C> metric : this.metrics) {
            registration.registerMetric(metric.getDefinition(), this);
        }
    }

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) {
        String name = operation.get(ModelDescriptionConstants.NAME).asString();
        Metric<C> executable = this.executables.get(name);
        try {
            ModelNode result = this.executor.execute(context, executable);
            if (result != null) {
                context.getResult().set(result);
            }
        } catch (OperationFailedException e) {
            context.getFailureDescription().set(e.getLocalizedMessage());
        }
        context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
    }
}
