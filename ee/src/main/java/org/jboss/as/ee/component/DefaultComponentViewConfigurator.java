/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.component;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.ee.logging.EeLogger;
import org.jboss.as.ee.component.serialization.WriteReplaceInterface;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.ModuleClassFactory;
import org.jboss.as.server.deployment.reflect.ProxyMetadataSource;
import org.jboss.invocation.proxy.ProxyConfiguration;
import org.jboss.invocation.proxy.ProxyFactory;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class DefaultComponentViewConfigurator extends AbstractComponentConfigurator implements ComponentConfigurator {

    private static final AtomicInteger PROXY_ID = new AtomicInteger(0);

    public void configure(final DeploymentPhaseContext context, final ComponentDescription description, final ComponentConfiguration configuration) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = context.getDeploymentUnit();
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        final ProxyMetadataSource proxyReflectionIndex = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.PROXY_REFLECTION_INDEX);

        //views
        for (ViewDescription view : description.getViews()) {
            Class<?> viewClass;
            try {
                viewClass = module.getClassLoader().loadClass(view.getViewClassName());
            } catch (ClassNotFoundException e) {
                throw EeLogger.ROOT_LOGGER.cannotLoadViewClass(e, view.getViewClassName(), configuration);
            }
            final ViewConfiguration viewConfiguration;

            final ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
            if (viewClass.getName().startsWith("java.")) {
                proxyConfiguration.setProxyName("org.jboss.proxy.java.lang." + viewClass.getSimpleName() + "$$$view" + PROXY_ID.incrementAndGet());
            } else {
                proxyConfiguration.setProxyName(viewClass.getName() + "$$$view" + PROXY_ID.incrementAndGet());
            }
            proxyConfiguration.setClassLoader(module.getClassLoader());
            proxyConfiguration.setClassFactory(ModuleClassFactory.INSTANCE);
            proxyConfiguration.setProtectionDomain(viewClass.getProtectionDomain());
            proxyConfiguration.setMetadataSource(proxyReflectionIndex);
            if (view.isSerializable()) {
                proxyConfiguration.addAdditionalInterface(Serializable.class);
                if (view.isUseWriteReplace()) {
                    proxyConfiguration.addAdditionalInterface(WriteReplaceInterface.class);
                }
            }

            Class<?> markupClass;
            try {
                if (view.getMarkupClassName() != null) {
                    markupClass = module.getClassLoader().loadClass(view.getMarkupClassName());
                    proxyConfiguration.addAdditionalInterface(markupClass);
                }
            } catch (ClassNotFoundException e) {
                throw EeLogger.ROOT_LOGGER.cannotLoadViewClass(e, view.getMarkupClassName(), configuration);
            }

            //we define it in the modules class loader to prevent permgen leaks
            if (viewClass.isInterface()) {
                final Class<?> componentClass = configuration.getComponentClass();
                Constructor<?> defaultConstructor = null;
                try {
                    defaultConstructor = componentClass.getConstructor();
                } catch (Exception e) {
                    //ignore
                }
                proxyConfiguration.setSuperClass(!view.requiresSuperclassInProxy() || defaultConstructor == null ? Object.class : componentClass);
                proxyConfiguration.addAdditionalInterface(viewClass);
                viewConfiguration = view.createViewConfiguration(viewClass, configuration, new ProxyFactory(proxyConfiguration));
            } else {
                proxyConfiguration.setSuperClass(viewClass);
                viewConfiguration = view.createViewConfiguration(viewClass, configuration, new ProxyFactory(proxyConfiguration));
            }
            for (final ViewConfigurator configurator : view.getConfigurators()) {
                configurator.configure(context, configuration, view, viewConfiguration);
            }
            configuration.getViews().add(viewConfiguration);
        }

        configuration.getStartDependencies().add(new DependencyConfigurator<ComponentStartService>() {
            @Override
            public void configureDependency(final ServiceBuilder<?> serviceBuilder, ComponentStartService service) {
                for (ServiceName dependencyName : description.getDependencies()) {
                    serviceBuilder.requires(dependencyName);
                }
            }
        });
    }
}
