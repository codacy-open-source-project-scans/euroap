/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jpa.management;

import static org.jboss.as.jpa.messages.JpaLogger.ROOT_LOGGER;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.jpa.messages.JpaLogger;
import org.jboss.dmr.ModelNode;
import org.jipijapa.management.spi.Statistics;

/**
 * Resource representing a Jakarta Persistence PersistenceUnit (from a persistence.xml) deployment.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Scott Marlow
 */
public class DynamicManagementStatisticsResource extends PlaceholderResource.PlaceholderResourceEntry {

    private final String puName;
    private final ModelNode model = new ModelNode();
    private final Statistics statistics;
    private final String identificationLabel;
    private final EntityManagerFactoryLookup entityManagerFactoryLookup;

    public DynamicManagementStatisticsResource(
            final Statistics statistics,
            final String puName,
            final String identificationLabel,
            final EntityManagerFactoryLookup entityManagerFactoryLookup) {
        super(identificationLabel, puName);
        this.puName = puName;
        this.statistics = statistics;
        this.identificationLabel = identificationLabel;
        this.entityManagerFactoryLookup = entityManagerFactoryLookup;
    }

    @Override
    public ModelNode getModel() {
        return model;
    }

    @Override
    public boolean isModelDefined() {
        return model.isDefined();
    }

    @Override
    public boolean hasChild(PathElement element) {
        try {
            Statistics statistics = getStatistics();
            // if element key matches, check if element value also matches
            if (statistics.getChildrenNames().contains(element.getKey())) {
                Statistics childStatistics = statistics.getChild(element.getKey());
                return childStatistics != null && childStatistics.getDynamicChildrenNames(entityManagerFactoryLookup, PathWrapper.path(puName)).contains(element.getValue());
            } else {
                return super.hasChild(element);
            }
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
            return false;
        }

    }

    @Override
    public Resource getChild(PathElement element) {
        try {
            Statistics statistics = getStatistics();
            if (statistics.getChildrenNames().contains(element.getKey())) {
                Statistics childStatistics = statistics.getChild(element.getKey());
                return childStatistics != null && childStatistics.getDynamicChildrenNames(entityManagerFactoryLookup, PathWrapper.path(puName)).contains(element.getValue())
                        ? PlaceholderResource.INSTANCE : null;
            } else {
                return super.getChild(element);
            }
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
            return super.getChild(element);
        }
    }

    @Override
    public Resource requireChild(PathElement element) {
        try {
            Statistics statistics = getStatistics();
            if (statistics.getChildrenNames().contains(element.getKey())) {
                Statistics childStatistics = statistics.getChild(element.getKey());
                if (childStatistics != null && childStatistics.getDynamicChildrenNames(entityManagerFactoryLookup, PathWrapper.path(puName)).contains(element.getValue())) {
                    return PlaceholderResource.INSTANCE;
                }
                throw new NoSuchResourceException(element);
            } else {
                return super.requireChild(element);
            }
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
            return super.requireChild(element);
        }
    }

    @Override
    public boolean hasChildren(String childType) {
        try {
            Statistics statistics = getStatistics();
            if (statistics.getChildrenNames().contains(childType)) {
                Statistics childStatistics = statistics.getChild(childType);
                return childStatistics != null && !childStatistics.getNames().isEmpty();
            } else {
                return super.hasChildren(childType);
            }
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
            return false;
        }
    }

    @Override
    public Resource navigate(PathAddress address) {
        Statistics statistics = getStatistics();
        if (address.size() > 0 && statistics.getChildrenNames().contains(address.getElement(0).getKey())) {
            if (address.size() > 1) {
                throw new NoSuchResourceException(address.getElement(1));
            }
            return PlaceholderResource.INSTANCE;
        } else {
            return super.navigate(address);
        }
    }

    @Override
    public Set<String> getChildTypes() {
        try {
            Set<String> result = new HashSet<String>(super.getChildTypes());
            Statistics statistics = getStatistics();
            result.addAll(statistics.getChildrenNames());
            return result;
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
            return Collections.emptySet();
        }
    }

    @Override
    public Set<String> getChildrenNames(String childType) {
        try {
            Statistics statistics = getStatistics();
            if (statistics.getChildrenNames().contains(childType)) {
                Statistics childStatistics = statistics.getChild(childType);
                Set<String>result = new HashSet<String>();
                for(String name:childStatistics.getDynamicChildrenNames(entityManagerFactoryLookup, PathWrapper.path(puName))) {
                    result.add(name);
                }
                return result;
            } else {
                return super.getChildrenNames(childType);
            }
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
            return Collections.emptySet();
        }
    }

    @Override
    public Set<ResourceEntry> getChildren(String childType) {
        try {
            Statistics statistics = getStatistics();
            if (statistics.getChildrenNames().contains(childType)) {
                Set<ResourceEntry> result = new HashSet<ResourceEntry>();
                Statistics childStatistics = statistics.getChild(childType);
                for (String name : childStatistics.getDynamicChildrenNames(entityManagerFactoryLookup, PathWrapper.path(puName))) {
                    result.add(new PlaceholderResource.PlaceholderResourceEntry(childType, name));
                }
                return result;
            } else {
                return super.getChildren(childType);
            }
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
            return Collections.emptySet();
        }
    }

    @Override
    public void registerChild(PathElement address, Resource resource) {
        try {
            Statistics statistics = getStatistics();
            if (statistics.getChildrenNames().contains(address.getKey())) {
                throw JpaLogger.ROOT_LOGGER.resourcesOfTypeCannotBeRegistered(address.getKey());
            } else {
                super.registerChild(address, resource);
            }
        }
        catch (RuntimeException e) {
            // WFLY-2436 ignore unexpected exceptions (e.g. JIPI-27 may throw an IllegalStateException)
            // WFLY-10413 : also make sure to catch HibernateExceptions
            ROOT_LOGGER.unexpectedStatisticsProblem(e);
        }
    }

    @Override
    public Resource removeChild(PathElement address) {
        Statistics statistics = getStatistics();
        if (statistics.getChildrenNames().contains(address.getKey())) {
            throw JpaLogger.ROOT_LOGGER.resourcesOfTypeCannotBeRemoved(address.getKey());
        } else {
            return super.removeChild(address);
        }
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public DynamicManagementStatisticsResource clone() {
        return new DynamicManagementStatisticsResource(statistics, puName, identificationLabel, entityManagerFactoryLookup);
    }

    private Statistics getStatistics() {
        return statistics;
    }
}
