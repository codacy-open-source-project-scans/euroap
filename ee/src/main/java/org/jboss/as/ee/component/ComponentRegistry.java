/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.component;

import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.value.InjectedValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that can be used to create a fully injected class instance. If there is an appropriate component regiestered
 * an instance of the component will be created. Otherwise the default class introspector will be used to create an instance.
 * <p/>
 * This can be problematic in theory, as it is possible to have multiple components for a single class, however it does
 * not seem to be an issue in practice.
 * <p/>
 * This registry only contains simple component types that have at most 1 view
 *
 * @author Stuart Douglas
 */
public class ComponentRegistry {

    private static ServiceName SERVICE_NAME = ServiceName.of("ee", "ComponentRegistry");

    private final Map<Class<?>, ComponentManagedReferenceFactory> componentsByClass = new ConcurrentHashMap<Class<?>, ComponentManagedReferenceFactory>();
    private final ServiceRegistry serviceRegistry;
    private final InjectedValue<EEClassIntrospector> classIntrospectorInjectedValue = new InjectedValue<>();

    public static ServiceName serviceName(final DeploymentUnit deploymentUnit) {
        return deploymentUnit.getServiceName().append(SERVICE_NAME);
    }

    public ComponentRegistry(final ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    public void addComponent(final ComponentConfiguration componentConfiguration) {
        if(componentConfiguration.getViews().size() < 2) {
            if(componentConfiguration.getViews().isEmpty()) {
                componentsByClass.put(componentConfiguration.getComponentClass(), new ComponentManagedReferenceFactory(componentConfiguration.getComponentDescription().getStartServiceName(), null));
            } else {
                componentsByClass.put(componentConfiguration.getComponentClass(), new ComponentManagedReferenceFactory(componentConfiguration.getComponentDescription().getStartServiceName(), componentConfiguration.getViews().get(0).getViewServiceName()));
            }
        }
    }

    public ManagedReferenceFactory createInstanceFactory(final Class<?> componentClass) {
        return createInstanceFactory(componentClass, false);
    }

    public ManagedReferenceFactory createInstanceFactory(final Class<?> componentClass, final boolean optional) {
        final ManagedReferenceFactory factory = componentsByClass.get(componentClass);
        if (factory == null) {
            EEClassIntrospector introspector = optional ? classIntrospectorInjectedValue.getOptionalValue() : classIntrospectorInjectedValue.getValue();
            return introspector != null ? introspector.createFactory(componentClass) : null;
        }
        return factory;
    }

    public ManagedReference createInstance(final Object instance) {

        final ComponentManagedReferenceFactory factory = componentsByClass.get(instance.getClass());
        if (factory == null) {
            return classIntrospectorInjectedValue.getValue().createInstance(instance);
        }
        return factory.createReference(instance);
    }

    public ManagedReference getInstance(final Object instance) {

        final ComponentManagedReferenceFactory factory = componentsByClass.get(instance.getClass());
        if (factory == null) {
            return classIntrospectorInjectedValue.getValue().getInstance(instance);
        }
        return factory.getReference(instance);
    }

    public InjectedValue<EEClassIntrospector> getClassIntrospectorInjectedValue() {
        return classIntrospectorInjectedValue;
    }

    private static class ComponentManagedReference implements ManagedReference {

        private final ComponentInstance instance;
        private boolean destroyed;

        ComponentManagedReference(final ComponentInstance component) {
            instance = component;
        }

        @Override
        public synchronized void release() {
            if (!destroyed) {
                instance.destroy();
                destroyed = true;
            }
        }

        @Override
        public Object getInstance() {
            return instance.getInstance();
        }
    }

    public class ComponentManagedReferenceFactory implements ManagedReferenceFactory {

        private final ServiceName serviceName;
        private final ServiceName viewServiceName;
        private volatile ServiceController<Component> component;
        private volatile ServiceController<ViewService.View> view;

        private ComponentManagedReferenceFactory(final ServiceName serviceName, ServiceName viewServiceName) {
            this.serviceName = serviceName;
            this.viewServiceName = viewServiceName;
        }

        @Override
        public ManagedReference getReference() {
            if (component == null) {
                synchronized (this) {
                    if (component == null) {
                        component = (ServiceController<Component>) serviceRegistry.getService(serviceName);
                    }
                }
            }
            if (view == null && viewServiceName != null) {
                synchronized (this) {
                    if (view == null) {
                        view = (ServiceController<ViewService.View>) serviceRegistry.getService(viewServiceName);
                    }
                }
            }
            if (component == null) {
                return null;
            }
            if(view == null) {
                return new ComponentManagedReference(component.getValue().createInstance());
            } else {
                try {
                    return view.getValue().createInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public ManagedReference createReference(final Object instance) {
            if (component == null) {
                synchronized (this) {
                    if (component == null) {
                        component = (ServiceController<Component>) serviceRegistry.getService(serviceName);
                    }
                }
            }
            if (component == null) {
                return null;
            }
            return new ComponentManagedReference(component.getValue().createInstance(instance));
        }

        ManagedReference getReference(final Object instance) {
            if (component == null) {
                synchronized (this) {
                    if (component == null) {
                        component = (ServiceController<Component>) serviceRegistry.getService(serviceName);
                    }
                }
            }
            if (component == null) {
                return null;
            }
            return new ComponentManagedReference(component.getValue().getInstance(instance));
        }

        public ServiceName getServiceName() {
            return serviceName;
        }
    }
}
