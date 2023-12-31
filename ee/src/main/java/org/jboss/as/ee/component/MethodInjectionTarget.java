/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Iterator;

import org.jboss.as.ee.logging.EeLogger;
import org.jboss.as.ee.utils.ClassLoadingUtils;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndex;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndexUtil;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.msc.value.Value;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Eduardo Martins
 */
public final class MethodInjectionTarget extends InjectionTarget {

    public MethodInjectionTarget(final String className, final String name, final String paramType) {
        super(className, name, paramType);
    }

    @Override
    public boolean isStatic(DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException {
        return Modifier.isStatic(getMethod(deploymentUnit).getModifiers());
    }

    public InterceptorFactory createInjectionInterceptorFactory(final Object targetContextKey, final Object valueContextKey, final Value<ManagedReferenceFactory> factoryValue, final DeploymentUnit deploymentUnit, final boolean optional) throws DeploymentUnitProcessingException {
        return new ManagedReferenceMethodInjectionInterceptorFactory(targetContextKey, valueContextKey, factoryValue, getMethod(deploymentUnit), optional);
    }

    public Method getMethod(final DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException {
        final String name = getName();
        final String className = getClassName();
        final String paramType = getDeclaredValueClassName();
        final DeploymentReflectionIndex reflectionIndex = deploymentUnit.getAttachment(Attachments.REFLECTION_INDEX);
        final Class<?> clazz;
        try {
             clazz = ClassLoadingUtils.loadClass(className, deploymentUnit);
        } catch (ClassNotFoundException e) {
            throw new DeploymentUnitProcessingException(e);
        }
        Method method = getMethod(reflectionIndex, clazz);
        return method;
    }

    public Method getMethod(final DeploymentReflectionIndex reflectionIndex, final Class<?> clazz) throws DeploymentUnitProcessingException {
        final ClassReflectionIndex classIndex = reflectionIndex.getClassIndex(clazz);
        Collection<Method> methods = null;
        final String paramType = getDeclaredValueClassName();
        final String name = getName();
        final String className = getClassName();
        if (paramType != null) {
            // find the methods with the specific name and the param types
            methods = ClassReflectionIndexUtil.findMethods(reflectionIndex, classIndex, name, paramType);
        }
        // either paramType is not set, or we may need to find autoboxing methods
        // e.g. setMyBoolean(boolean) for a Boolean
        if (methods == null || methods.isEmpty()) {
            // find all the methods with the specific name and which accept just 1 parameter.
            methods = ClassReflectionIndexUtil.findAllMethods(reflectionIndex, classIndex, name, 1);
        }
        Iterator<Method> iterator = methods.iterator();
        if (!iterator.hasNext()) {
            throw EeLogger.ROOT_LOGGER.methodNotFound(name, paramType, className);
        }
        Method method = iterator.next();
        if (iterator.hasNext()) {
            throw EeLogger.ROOT_LOGGER.multipleMethodsFound(name, paramType, className);
        }
        return method;
    }
}
