/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.services.workmanager.statistics;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.connector.dynamicresource.ClearWorkManagerStatisticsHandler;
import org.jboss.as.connector.subsystems.resourceadapters.CommonAttributes;
import org.jboss.as.connector.subsystems.resourceadapters.Constants;
import org.jboss.as.connector.subsystems.resourceadapters.WorkManagerRuntimeAttributeReadHandler;
import org.jboss.as.connector.subsystems.resourceadapters.WorkManagerRuntimeAttributeWriteHandler;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.core.api.workmanager.DistributedWorkManager;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class DistributedWorkManagerStatisticsService implements Service<ManagementResourceRegistration> {

    private final ManagementResourceRegistration overrideRegistration;
    private final boolean statsEnabled;

    protected final InjectedValue<DistributedWorkManager> distributedWorkManager = new InjectedValue<>();


    /**
     * create an instance *
     */
    public DistributedWorkManagerStatisticsService(final ManagementResourceRegistration registration,
                                                   final String name,
                                                   final boolean statsEnabled) {
        super();
        if (registration.isAllowsOverride()) {
            overrideRegistration = registration.registerOverrideModel(name, new OverrideDescriptionProvider() {
                @Override
                public Map<String, ModelNode> getAttributeOverrideDescriptions(Locale locale) {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, ModelNode> getChildTypeOverrideDescriptions(Locale locale) {
                    return Collections.emptyMap();
                }

            });
        } else {
            overrideRegistration = registration;
        }
        this.statsEnabled = statsEnabled;

    }


    @Override
    public void start(StartContext context) throws StartException {
        synchronized (this) {
            DistributedWorkManager dwm = distributedWorkManager.getValue();
            dwm.setDistributedStatisticsEnabled(statsEnabled);

            if (dwm.getDistributedStatistics() != null) {
                PathElement peDistributedWm = PathElement.pathElement(Constants.STATISTICS_NAME, "distributed");

                ResourceBuilder resourceBuilder = ResourceBuilder.Factory.create(peDistributedWm,
                        new StandardResourceDescriptionResolver(Constants.STATISTICS_NAME + "." + Constants.WORKMANAGER_NAME, CommonAttributes.RESOURCE_NAME, CommonAttributes.class.getClassLoader()));

                ManagementResourceRegistration dwmSubRegistration = overrideRegistration.registerSubModel(resourceBuilder.build());

                OperationStepHandler metricHandler = new WorkManagerRuntimeAttributeReadHandler(dwm, dwm.getDistributedStatistics(), false);
                for (SimpleAttributeDefinition metric : Constants.WORKMANAGER_METRICS) {
                    dwmSubRegistration.registerMetric(metric, metricHandler);
                }

                OperationStepHandler readHandler = new WorkManagerRuntimeAttributeReadHandler(dwm, dwm.getDistributedStatistics(), false);
                OperationStepHandler writeHandler = new WorkManagerRuntimeAttributeWriteHandler(dwm, false, Constants.DISTRIBUTED_WORKMANAGER_RW_ATTRIBUTES);

                for (SimpleAttributeDefinition attribute : Constants.DISTRIBUTED_WORKMANAGER_RW_ATTRIBUTES) {
                    dwmSubRegistration.registerReadWriteAttribute(attribute, readHandler, writeHandler);
                }


                dwmSubRegistration.registerOperationHandler(ClearWorkManagerStatisticsHandler.DEFINITION, new ClearWorkManagerStatisticsHandler(dwm));

            }
        }
    }


    @Override
    public void stop(StopContext context) {

        PathElement peDistributedWm = PathElement.pathElement(Constants.STATISTICS_NAME, "distributed");
        if (overrideRegistration.getSubModel(PathAddress.pathAddress(peDistributedWm)) != null)
            overrideRegistration.unregisterSubModel(peDistributedWm);

    }


    @Override
    public ManagementResourceRegistration getValue() throws IllegalStateException, IllegalArgumentException {
        return overrideRegistration;
    }


    public Injector<DistributedWorkManager> getDistributedWorkManagerInjector() {
        return distributedWorkManager;
    }
}
