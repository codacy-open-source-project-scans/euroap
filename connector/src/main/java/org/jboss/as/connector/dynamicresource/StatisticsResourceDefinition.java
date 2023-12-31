/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.dynamicresource;


import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.connector.subsystems.common.pool.PoolMetrics;
import org.jboss.as.connector.subsystems.common.pool.PoolStatisticsRuntimeAttributeReadHandler;
import org.jboss.as.connector.subsystems.common.pool.PoolStatisticsRuntimeAttributeWriteHandler;
import org.jboss.as.connector.subsystems.resourceadapters.ResourceAdaptersExtension;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;
import org.jboss.jca.core.spi.statistics.StatisticsPlugin;


/**
 * @author Tomaz Cerar
 */
public class StatisticsResourceDefinition extends SimpleResourceDefinition {

    private final StatisticsPlugin plugin;

    /**
     * Constructor for the {@link org.jboss.as.controller.descriptions.OverrideDescriptionProvider} case. Internationalization support is not provided.
     *
     * @param bundleName name to pass to {@link ResourceBundle#getBundle(String)}
     * @param plugin     the statistics plugins
     */
    public StatisticsResourceDefinition(final PathElement path, final String bundleName, final StatisticsPlugin plugin) {
        super(new Parameters(path, getResolver("statistics", bundleName, plugin)).setRuntime());
        this.plugin = plugin;
    }

    private static ResourceDescriptionResolver getResolver(final String keyPrefix, final String bundleName, final StatisticsPlugin plugin) {
        return new StandardResourceDescriptionResolver(keyPrefix, bundleName, ResourceAdaptersExtension.class.getClassLoader(), true, false){
            @Override
            public String getResourceAttributeDescription(String attributeName, Locale locale, ResourceBundle bundle) {
                if (bundle.containsKey(keyPrefix + "." + attributeName)){
                    return super.getResourceAttributeDescription(attributeName, locale, bundle);
                }
                return plugin.getDescription(attributeName);
            }
        };
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        for (AttributeDefinition attribute : getAttributesFromPlugin(plugin)) {
            resourceRegistration.registerMetric(attribute, new PoolMetrics.ParametrizedPoolMetricsHandler(plugin));
        }
        //adding enable/disable for pool stats
        OperationStepHandler readHandler = new PoolStatisticsRuntimeAttributeReadHandler(plugin);
        OperationStepHandler writeHandler = new PoolStatisticsRuntimeAttributeWriteHandler(plugin);
        resourceRegistration.registerReadWriteAttribute(org.jboss.as.connector.subsystems.common.pool.Constants.POOL_STATISTICS_ENABLED, readHandler, writeHandler);

    }

    public static List<AttributeDefinition> getAttributesFromPlugin(StatisticsPlugin plugin) {
        LinkedList<AttributeDefinition> result = new LinkedList<>();
        for (String name : plugin.getNames()) {
            ModelType modelType = ModelType.STRING;
            if (plugin.getType(name) == int.class) {
                modelType = ModelType.INT;
            }
            if (plugin.getType(name) == long.class) {
                modelType = ModelType.LONG;
            }
            SimpleAttributeDefinition attribute = new SimpleAttributeDefinitionBuilder(name, modelType)
                    .setRequired(false)
                    .setStorageRuntime()
                    .build();
            result.add(attribute);
        }
        return result;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(ClearStatisticsHandler.DEFINITION, new ClearStatisticsHandler(plugin));
    }


}
