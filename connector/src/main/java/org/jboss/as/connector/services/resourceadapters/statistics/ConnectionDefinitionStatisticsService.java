/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.services.resourceadapters.statistics;

import static org.jboss.as.connector.logging.ConnectorLogger.ROOT_LOGGER;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.connector.dynamicresource.StatisticsResourceDefinition;
import org.jboss.as.connector.metadata.deployment.ResourceAdapterDeployment;
import org.jboss.as.connector.subsystems.common.jndi.Util;
import org.jboss.as.connector.subsystems.resourceadapters.CommonAttributes;
import org.jboss.as.connector.subsystems.resourceadapters.Constants;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.core.api.bootstrap.CloneableBootstrapContext;
import org.jboss.jca.core.api.management.ConnectionFactory;
import org.jboss.jca.core.connectionmanager.ConnectionManager;
import org.jboss.jca.core.spi.statistics.StatisticsPlugin;
import org.jboss.jca.deployers.common.CommonDeployment;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class ConnectionDefinitionStatisticsService implements Service<ManagementResourceRegistration> {


    private final ManagementResourceRegistration overrideRegistration;
    private final boolean statsEnabled;
    private final String jndiName;

    protected final InjectedValue<ResourceAdapterDeployment> deployment = new InjectedValue<>();
    protected final InjectedValue<CloneableBootstrapContext> bootstrapContext = new InjectedValue<>();


    /**
     * create an instance *
     */
    public ConnectionDefinitionStatisticsService(final ManagementResourceRegistration registration,
                                                 final String jndiName,
                                                 final boolean useJavaContext,
                                                 final String poolName,
                                                 final boolean statsEnabled) {
        super();
        this.jndiName = Util.cleanJndiName(jndiName, useJavaContext);
        if (registration.isAllowsOverride()) {
            overrideRegistration = registration.registerOverrideModel(poolName, new OverrideDescriptionProvider() {
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
        ROOT_LOGGER.debugf("Starting ConnectionDefinitionStatisticsService %s", jndiName);
        synchronized (this) {
            final CommonDeployment deploymentMD = deployment.getValue().getDeployment();
            PathElement pePoolStats = PathElement.pathElement(Constants.STATISTICS_NAME, "pool");
            PathElement peExtendedStats = PathElement.pathElement(Constants.STATISTICS_NAME, "extended");

            if (deploymentMD.getConnector() != null && deploymentMD.getConnector().getConnectionFactories() != null) {
                for (ConnectionFactory cf : deploymentMD.getConnector().getConnectionFactories()) {
                    if (cf.getManagedConnectionFactory() != null && cf.getManagedConnectionFactory().getStatistics() != null) {
                        StatisticsPlugin extendStats = cf.getManagedConnectionFactory().getStatistics();
                        extendStats.setEnabled(statsEnabled);
                        if (!extendStats.getNames().isEmpty()
                                && overrideRegistration.getSubModel(PathAddress.pathAddress(peExtendedStats)) == null) {
                            overrideRegistration.registerSubModel(new StatisticsResourceDefinition(peExtendedStats,
                                    CommonAttributes.RESOURCE_NAME, extendStats));
                        }
                    }
                }
            }

            if (deploymentMD.getConnectionManagers() != null) {
                for (ConnectionManager cm : deploymentMD.getConnectionManagers()) {
                    if (cm.getPool() != null && cm.getJndiName() != null && cm.getJndiName().equals(jndiName)) {
                        StatisticsPlugin poolStats = cm.getPool().getStatistics();
                        poolStats.setEnabled(statsEnabled);
                        if (!poolStats.getNames().isEmpty()
                                && overrideRegistration.getSubModel(PathAddress.pathAddress(pePoolStats)) == null) {
                            overrideRegistration.registerSubModel(
                                    new StatisticsResourceDefinition(pePoolStats, CommonAttributes.RESOURCE_NAME, poolStats));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void stop(StopContext context) {
        PathElement peCD = PathElement.pathElement(Constants.STATISTICS_NAME, "pool");
        if (overrideRegistration.getSubModel(PathAddress.pathAddress(peCD)) != null) {
            overrideRegistration.unregisterSubModel(peCD);
        }
        PathElement peExtended = PathElement.pathElement(Constants.STATISTICS_NAME, "extended");
        if (overrideRegistration.getSubModel(PathAddress.pathAddress(peExtended)) != null) {
            overrideRegistration.unregisterSubModel(peExtended);
        }


    }


    @Override
    public ManagementResourceRegistration getValue() throws IllegalStateException, IllegalArgumentException {
        return overrideRegistration;
    }

    public Injector<ResourceAdapterDeployment> getResourceAdapterDeploymentInjector() {
        return deployment;
    }

    public Injector<CloneableBootstrapContext> getBootstrapContextInjector() {
        return bootstrapContext;
    }
}
