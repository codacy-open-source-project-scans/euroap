/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.remote;

import java.util.function.Supplier;

import org.jboss.as.ee.component.InjectionSource;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * Injection source for Jakarta Enterprise Beans remote views.
 *
 * @author Stuart Douglas
 */
public class RemoteViewInjectionSource extends InjectionSource {

    private final ServiceName serviceName;
    private final String appName;
    private final String moduleName;
    private final String distinctName;
    private final String beanName;
    private final String viewClass;
    private final boolean stateful;
    private final Supplier<ClassLoader> viewClassLoader;
    private final boolean appclient;

    public RemoteViewInjectionSource(final ServiceName serviceName, final String appName, final String moduleName, final String distinctName, final String beanName, final String viewClass, final boolean stateful, final Supplier<ClassLoader> viewClassLoader, boolean appclient) {
        this.serviceName = serviceName;
        this.appName = appName;
        this.moduleName = moduleName;
        this.distinctName = distinctName;
        this.beanName = beanName;
        this.viewClass = viewClass;
        this.stateful = stateful;
        this.viewClassLoader = viewClassLoader;
        this.appclient = appclient;
    }

    /**
     * {@inheritDoc}
     */
    public void getResourceValue(final ResolutionContext resolutionContext, final ServiceBuilder<?> serviceBuilder, final DeploymentPhaseContext phaseContext, final Injector<ManagedReferenceFactory> injector) {
        if(serviceName != null) {
            serviceBuilder.requires(serviceName);
        }
        final RemoteViewManagedReferenceFactory factory = new RemoteViewManagedReferenceFactory(appName, moduleName, distinctName, beanName, viewClass, stateful, viewClassLoader, appclient);
        injector.inject(factory);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RemoteViewInjectionSource that = (RemoteViewInjectionSource) o;

        if (stateful != that.stateful) return false;
        if (appName != null ? !appName.equals(that.appName) : that.appName != null) return false;
        if (beanName != null ? !beanName.equals(that.beanName) : that.beanName != null) return false;
        if (distinctName != null ? !distinctName.equals(that.distinctName) : that.distinctName != null) return false;
        if (moduleName != null ? !moduleName.equals(that.moduleName) : that.moduleName != null) return false;
        if (viewClass != null ? !viewClass.equals(that.viewClass) : that.viewClass != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = appName != null ? appName.hashCode() : 0;
        result = 31 * result + (moduleName != null ? moduleName.hashCode() : 0);
        result = 31 * result + (distinctName != null ? distinctName.hashCode() : 0);
        result = 31 * result + (beanName != null ? beanName.hashCode() : 0);
        result = 31 * result + (viewClass != null ? viewClass.hashCode() : 0);
        result = 31 * result + (stateful ? 1 : 0);
        return result;
    }
}
