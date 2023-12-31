/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.inflow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
abstract class AbstractInvocationHandler implements InvocationHandler {
    protected abstract boolean doEquals(Object obj);

    protected abstract Object doInvoke(Object proxy, Method method, Object[] args) throws Throwable;

    @Override
    public final boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (obj == null)
            return false;

        if (Proxy.isProxyClass(obj.getClass()))
            return equals(Proxy.getInvocationHandler(obj));

        // It might not be a JDK proxy that's handling this, so here we do a little trick.
        // Normally you would do:
        //      if(!(obj instanceof EndpointInvocationHandler))
        //         return false;
        if (!(obj instanceof AbstractInvocationHandler))
            return obj.equals(this);

        return doEquals(obj);
    }

    protected Object handle(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(this, args);
        }
        catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    public abstract int hashCode();

    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass().equals(Object.class))
            return handle(method, args);
        return doInvoke(proxy, method, args);
    }
}
