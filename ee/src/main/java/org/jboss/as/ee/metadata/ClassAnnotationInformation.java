/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ee.metadata;

import org.jboss.invocation.proxy.MethodIdentifier;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Class level information about the annotations present on a particular class.
 *
 * @param <A> The annotation type
 * @param <T> The data type that is used to store the annotation information internally
 * @author Stuart Douglas
 */
public final class ClassAnnotationInformation<A extends Annotation, T> {

    private final Class<A> annotationType;
    private final List<T> classLevelAnnotations;
    private final Map<MethodIdentifier, List<T>> methodLevelAnnotations;
    private final Map<String, List<T>> fieldLevelAnnotations;

    ClassAnnotationInformation(final Class<A> annotationType, final List<T> classLevelAnnotations, final Map<MethodIdentifier, List<T>> methodLevelAnnotations, final Map<String, List<T>> fieldLevelAnnotations) {
        this.annotationType = annotationType;
        this.classLevelAnnotations = Collections.unmodifiableList(classLevelAnnotations);
        this.methodLevelAnnotations = Collections.unmodifiableMap(methodLevelAnnotations);
        this.fieldLevelAnnotations = Collections.unmodifiableMap(fieldLevelAnnotations);
    }

    public Class<A> getAnnotationType() {
        return annotationType;
    }

    public List<T> getClassLevelAnnotations() {
        return classLevelAnnotations;
    }

    public Map<String, List<T>> getFieldLevelAnnotations() {
        return fieldLevelAnnotations;
    }

    public Map<MethodIdentifier, List<T>> getMethodLevelAnnotations() {
        return methodLevelAnnotations;
    }
}
