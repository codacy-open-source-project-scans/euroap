/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld.services;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.as.weld.logging.WeldLogger;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.weld.bootstrap.api.Singleton;
import org.jboss.weld.bootstrap.api.SingletonProvider;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Singleton Provider that uses the TCCL to figure out the current application.
 *
 * @author Stuart Douglas
 */
public class ModuleGroupSingletonProvider extends SingletonProvider {

    /**
     * Map of the top level class loader to all class loaders in a deployment
     */
    public static final Map<ClassLoader, Set<ClassLoader>> deploymentClassLoaders = new ConcurrentHashMap<ClassLoader, Set<ClassLoader>>();

    /**
     * Maps a top level class loader to all CL's in the deployment
     */
    public static void addClassLoaders(ClassLoader topLevel, Set<ClassLoader> allClassLoaders) {
        deploymentClassLoaders.put(topLevel, allClassLoaders);
    }

    /**
     * Removes the class loader mapping
     */
    public static void removeClassLoader(ClassLoader topLevel) {
        deploymentClassLoaders.remove(topLevel);
    }

    @Override
    public <T> Singleton<T> create(Class<? extends T> type) {
        return new TCCLSingleton<T>();
    }

    @SuppressWarnings("unused")
    private static class TCCLSingleton<T> implements Singleton<T> {

        private volatile Map<ClassLoader, T> store = Collections.emptyMap();
        private volatile Map<String, T> contextIdStore = Collections.emptyMap();

        public T get() {
            T instance = store.get(findParentModuleCl(WildFlySecurityManager.getCurrentContextClassLoaderPrivileged()));
            if (instance == null) {
                throw WeldLogger.ROOT_LOGGER.singletonNotSet(WildFlySecurityManager.getCurrentContextClassLoaderPrivileged());
            }
            return instance;
        }

        public synchronized void set(T object) {
            final Map<ClassLoader, T> store = new IdentityHashMap<ClassLoader, T>(this.store);
            ClassLoader classLoader = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
            store.put(classLoader, object);
            if (deploymentClassLoaders.containsKey(classLoader)) {
                for (ClassLoader cl : deploymentClassLoaders.get(classLoader)) {
                    store.put(cl, object);
                }
            }
            this.store = store;
        }

        public synchronized void clear() {
            ClassLoader classLoader = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
            final Map<ClassLoader, T> store = new IdentityHashMap<ClassLoader, T>(this.store);
            store.remove(classLoader);
            if (deploymentClassLoaders.containsKey(classLoader)) {
                for (ClassLoader cl : deploymentClassLoaders.get(classLoader)) {
                    store.remove(cl);
                }
            }
            this.store = store;
        }

        public boolean isSet() {
            return store.containsKey(findParentModuleCl(WildFlySecurityManager.getCurrentContextClassLoaderPrivileged()));
        }

        /**
         * If a custom CL is in use we want to get the module CL it delegates to
         * @param classLoader The current CL
         * @returnThe corresponding module CL
         */
        private ClassLoader findParentModuleCl(ClassLoader classLoader) {
            ClassLoader c = classLoader;
            while (c != null && !(c instanceof ModuleClassLoader)) {
                c = c.getParent();
            }
            return c;
        }

        /*
         * Default implementation of the Weld 1.2 API
         *
         * This provides forward binary compatibility with Weld 1.2 (OSGi integration).
         * It is OK to ignore the id parameter as TCCL is still used to identify the singleton in Jakarta EE.
         */
        public T get(String id) {
            T val = contextIdStore.get(id);
            if(val != null) {
                return val;
            }
            return get();
        }

        public boolean isSet(String id) {
            T val = contextIdStore.get(id);
            if(val != null) {
                return true;
            }
            return isSet();
        }

        public synchronized void set(String id, T object) {
            set(object);
            final HashMap<String, T> store = new HashMap<String, T>(this.contextIdStore);
            store.put(id, object);
            this.contextIdStore = store;
        }

        public synchronized void clear(String id) {
            clear();
            final HashMap<String, T> store = new HashMap<String, T>(this.contextIdStore);
            store.remove(id);
            this.contextIdStore = store;
        }
    }
}
