/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.undertow.deployment;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import org.jboss.common.beans.property.BeanUtils;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.security.manager.WildFlySecurityManager;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Properties;

/**
 * Handler wrapper that create a new instance of the specified {@link HandlerWrapper} or
 * {@link HttpHandler} class, and configures it via the specified properties.
 *
 * @author Stuart Douglas
 */
public class ConfiguredHandlerWrapper implements HandlerWrapper {

    private final Class<?> handlerClass;
    private final Map<String, String> properties;

    public ConfiguredHandlerWrapper(Class<?> handlerClass, Map<String, String> properties) {
        this.handlerClass = handlerClass;
        this.properties = properties;
    }

    @Override
    public HttpHandler wrap(HttpHandler handler) {
        try {
            final Object instance;
            if (HttpHandler.class.isAssignableFrom(handlerClass)) {
                final Constructor<?> ctor = handlerClass.getConstructor(HttpHandler.class);
                // instantiate the handler with the TCCL as the handler class' classloader
                final ClassLoader prevCL = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(handlerClass);
                try {
                    instance = ctor.newInstance(handler);
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
                }
            } else if (HandlerWrapper.class.isAssignableFrom(handlerClass)) {
                // instantiate the handler with the TCCL as the handler class' classloader
                final ClassLoader prevCL = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(handlerClass);
                try {
                    instance = handlerClass.newInstance();
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
                }
            } else {
                throw UndertowLogger.ROOT_LOGGER.handlerWasNotAHandlerOrWrapper(handlerClass);
            }
            Properties p = new Properties();
            p.putAll(properties);

            ClassLoader oldCl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
            try {
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(BeanUtils.class);
                BeanUtils.mapJavaBeanProperties(instance, p);
            } finally {
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldCl);
            }
            if (HttpHandler.class.isAssignableFrom(handlerClass)) {
                return (HttpHandler) instance;
            } else {
                return ((HandlerWrapper) instance).wrap(handler);
            }
        } catch (Exception e) {
            throw UndertowLogger.ROOT_LOGGER.failedToConfigureHandler(handlerClass, e);
        }
    }
}
