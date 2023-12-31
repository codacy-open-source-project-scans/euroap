/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import static org.jboss.as.clustering.jgroups.subsystem.AbstractProtocolResourceDefinition.Attribute.MODULE;
import static org.jboss.as.clustering.jgroups.subsystem.AbstractProtocolResourceDefinition.Attribute.PROPERTIES;
import static org.jboss.as.clustering.jgroups.subsystem.AbstractProtocolResourceDefinition.Attribute.STATISTICS_ENABLED;

import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.clustering.controller.ResourceServiceConfigurator;
import org.jboss.as.clustering.jgroups.ProtocolDefaults;
import org.jboss.as.clustering.jgroups.logging.JGroupsLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jgroups.Global;
import org.jgroups.stack.Configurator;
import org.jgroups.stack.Protocol;
import org.jgroups.util.StackType;
import org.jgroups.util.Util;
import org.wildfly.clustering.jgroups.spi.ProtocolConfiguration;
import org.wildfly.clustering.jgroups.spi.ProtocolStackConfiguration;
import org.wildfly.clustering.service.Dependency;
import org.wildfly.clustering.service.FunctionalService;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author Paul Ferraro
 */
public abstract class AbstractProtocolConfigurationServiceConfigurator<P extends Protocol, C extends ProtocolConfiguration<P>> implements ResourceServiceConfigurator, ProtocolConfiguration<P>, Consumer<P>, Supplier<C>, Dependency {

    private final String name;
    private final Map<String, String> properties = new HashMap<>();

    private volatile Supplier<ModuleLoader> loader;
    private volatile Supplier<ProtocolDefaults> defaults;
    private volatile String moduleName;
    private volatile Boolean statisticsEnabled;

    protected AbstractProtocolConfigurationServiceConfigurator(String name) {
        this.name = name;
    }

    @Override
    public final ServiceBuilder<?> build(ServiceTarget target) {
        ServiceBuilder<?> builder = target.addService(this.getServiceName());
        Consumer<C> configuration = this.register(builder).provides(this.getServiceName());
        Service service = new FunctionalService<>(configuration, Function.identity(), this);
        return builder.setInstance(service).setInitialMode(ServiceController.Mode.ON_DEMAND);
    }

    @Override
    public <T> ServiceBuilder<T> register(ServiceBuilder<T> builder) {
        this.loader = builder.requires(Services.JBOSS_SERVICE_MODULE_LOADER);
        this.defaults = builder.requires(ProtocolDefaultsServiceConfigurator.SERVICE_NAME);
        return builder;
    }

    @Override
    public ServiceConfigurator configure(OperationContext context, ModelNode model) throws OperationFailedException {
        this.moduleName = MODULE.resolveModelAttribute(context, model).asString();
        this.properties.clear();
        for (Property property : PROPERTIES.resolveModelAttribute(context, model).asPropertyListOrEmpty()) {
            this.properties.put(property.getName(), property.getValue().asString());
        }
        this.statisticsEnabled = STATISTICS_ENABLED.resolveModelAttribute(context, model).asBooleanOrNull();
        return this;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public final P createProtocol(ProtocolStackConfiguration stackConfiguration) {
        String protocolName = this.name;
        String moduleName = this.moduleName;
        // A "native" protocol is one that is not specified as a class name
        boolean nativeProtocol = moduleName.equals(AbstractProtocolResourceDefinition.Attribute.MODULE.getDefinition().getDefaultValue().asString()) && !protocolName.startsWith(Global.PREFIX);
        String className = nativeProtocol ? (Global.PREFIX + protocolName) : protocolName;
        try {
            Module module = this.loader.get().loadModule(moduleName);
            Class<? extends Protocol> protocolClass = module.getClassLoader().loadClass(className).asSubclass(Protocol.class);
            Map<String, String> properties = new HashMap<>(this.defaults.get().getProperties(protocolClass));
            properties.putAll(this.properties);
            PrivilegedExceptionAction<Protocol> action = new PrivilegedExceptionAction<>() {
                @Override
                public Protocol run() throws Exception {
                    try {
                        Protocol protocol = protocolClass.getConstructor().newInstance();
                        // These Configurator methods are destructive, so make a defensive copy
                        Map<String, String> copy = new HashMap<>(properties);
                        StackType type = Util.getIpStackType();
                        Configurator.resolveAndAssignFields(protocol, copy, type);
                        Configurator.resolveAndInvokePropertyMethods(protocol, copy, type);
                        List<Object> objects = protocol.getComponents();
                        if (objects != null) {
                            for (Object object : objects) {
                                Configurator.resolveAndAssignFields(object, copy, type);
                                Configurator.resolveAndInvokePropertyMethods(object, copy, type);
                            }
                        }
                        if (!copy.isEmpty()) {
                            for (String property : copy.keySet()) {
                                JGroupsLogger.ROOT_LOGGER.unrecognizedProtocolProperty(protocolName, property);
                            }
                        }
                        return protocol;
                    } catch (InstantiationException | IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
            @SuppressWarnings("unchecked")
            P protocol = (P) WildFlySecurityManager.doUnchecked(action);
            this.accept(protocol);
            protocol.enableStats(this.statisticsEnabled != null ? this.statisticsEnabled : stackConfiguration.isStatisticsEnabled());
            return protocol;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    void setValue(P protocol, String propertyName, Object propertyValue) {
        PrivilegedAction<P> action = new PrivilegedAction<>() {
            @Override
            public P run() {
                return protocol.setValue(propertyName, propertyValue);
            }
        };
        WildFlySecurityManager.doUnchecked(action);
    }
}
