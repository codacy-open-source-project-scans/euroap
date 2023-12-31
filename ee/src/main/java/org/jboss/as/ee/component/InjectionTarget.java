/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.component;

import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.msc.value.Value;

/**
 * An injection target field or method in a class.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Eduardo Martins
 */
public abstract class InjectionTarget {
    private final String className;
    private final String name;
    private final String declaredValueClassName;

    /**
     *
     * @param className The class to inject into
     * @param name The target name
     * @param declaredValueClassName The type of injection
     */
    protected InjectionTarget(final String className, final String name, final String declaredValueClassName) {
        this.className = className;
        this.name = name;
        this.declaredValueClassName = declaredValueClassName;
    }

    /**
     * Get the name of the target property.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the name of the target class.
     *
     * @return the class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Get the class name of the field or the parameter type declared for the target method.
     *
     * @return the class name
     */
    public String getDeclaredValueClassName() {
        return declaredValueClassName;
    }

    /**
     * Get an interceptor factory which will carry out injection into this target.
     *
     *
     * @param targetContextKey the interceptor context key for the target
     * @param valueContextKey  the interceptor context key for the value
     * @param factoryValue     the value to inject
     * @param deploymentUnit   the deployment unit
     * @param optional         If this is an optional injection
     * @return the interceptor factory
     * @throws DeploymentUnitProcessingException
     *          if an error occurs
     */
    public abstract InterceptorFactory createInjectionInterceptorFactory(final Object targetContextKey, final Object valueContextKey, final Value<ManagedReferenceFactory> factoryValue, final DeploymentUnit deploymentUnit, final boolean optional) throws DeploymentUnitProcessingException;

    /**
     * Indicates if the target has the staic modifier.
     *
     * @param deploymentUnit   the deployment unit
     * @return true
     * @throws DeploymentUnitProcessingException if an error occurs
     */
    public abstract boolean isStatic(final DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException;

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final InjectionTarget that = (InjectionTarget) o;

        if (className != null ? !className.equals(that.className) : that.className != null) return false;
        if (declaredValueClassName != null ? !declaredValueClassName.equals(that.declaredValueClassName) : that.declaredValueClassName != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (declaredValueClassName != null ? declaredValueClassName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return new StringBuilder("InjectionTarget[className=").append(className).append(",name=").append(name).append(",declaredValueClassName=").append(declaredValueClassName).append("]").toString();
    }
}
