/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld.util;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


/**
 *
 * @author Stuart Douglas
 *
 */
public class Reflections {

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object obj) {
        return (T) obj;
    }

    public static <T> T newInstance(String className, ClassLoader classLoader) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            return (T) clazz.newInstance();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isAccessible(String className, ClassLoader classLoader) {
        return loadClass(className, classLoader) != null;
    }

    public static <T> Class<T> loadClass(String className, ClassLoader classLoader) {
        try {
            return cast(classLoader.loadClass(className));
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * A simple implementation of the {@link org.jboss.weld.resources.spi.AnnotationDiscovery#containsAnnotation(Class, Class)} contract.
     * This implementation uses reflection.
     *
     * @see org.jboss.as.weld.discovery.WeldAnnotationDiscovery
     */
    public static boolean containsAnnotation(Class<?> javaClass, Class<? extends Annotation> requiredAnnotation) {
        for (Class<?> clazz = javaClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            // class level annotations
            if ((clazz == javaClass || requiredAnnotation.isAnnotationPresent(Inherited.class))
                    && containsAnnotations(clazz.getAnnotations(), requiredAnnotation)) {
                    return true;
            }
            // fields
            for (Field field : clazz.getDeclaredFields()) {
                if (containsAnnotations(field.getAnnotations(), requiredAnnotation)) {
                    return true;
                }
            }
            // constructors
            for (Constructor<?> constructor : clazz.getConstructors()) {
                if (containsAnnotations(constructor.getAnnotations(), requiredAnnotation)) {
                    return true;
                }
                for (Annotation[] parameterAnnotations : constructor.getParameterAnnotations()) {
                    if (containsAnnotations(parameterAnnotations, requiredAnnotation)) {
                        return true;
                    }
                }
            }
            // methods
            for (Method method : clazz.getDeclaredMethods()) {
                if (containsAnnotations(method.getAnnotations(), requiredAnnotation)) {
                    return true;
                }
                for (Annotation[] parameterAnnotations : method.getParameterAnnotations()) {
                    if (containsAnnotations(parameterAnnotations, requiredAnnotation)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean containsAnnotations(Annotation[] annotations, Class<? extends Annotation> requiredAnnotation) {
        return containsAnnotation(annotations, requiredAnnotation, true);
    }

    private static boolean containsAnnotation(Annotation[] annotations, Class<? extends Annotation> requiredAnnotation, boolean checkMetaAnnotations) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (requiredAnnotation.equals(annotationType)) {
                return true;
            }
            if (checkMetaAnnotations && containsAnnotation(annotationType.getAnnotations(), requiredAnnotation, false)) {
                return true;
            }
        }
        return false;
    }

}
