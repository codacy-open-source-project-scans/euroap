/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.mdb.dynamic.impl;

import jakarta.resource.spi.endpoint.MessageEndpoint;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * @version $Revision$ $Date$
 */
public class Cmd {

    private final String name;

    private final Method method;

    public Cmd(String name, Method method) {
        this.name = name;
        this.method = method;
    }

    public String getName() {
        return name;
    }

    public void exec(Object impl, String[] args, PrintStream out) throws Throwable {
        try {

            if (impl instanceof MessageEndpoint) {
                MessageEndpoint endpoint = (MessageEndpoint) impl;
                endpoint.beforeDelivery(method);
            }

            final Object result = method.invoke(impl, toParams(args));
            if (result != null) {
                final String text = result.toString().replaceAll("\n*$", "");
                out.println(text);
                out.println();
            }
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } finally {
            if (impl instanceof MessageEndpoint) {
                MessageEndpoint endpoint = (MessageEndpoint) impl;
                endpoint.afterDelivery();
            }
        }
    }

    private Object[] toParams(String[] args) {
        final Class<?>[] expected = method.getParameterTypes();
        final Object[] converted = new Object[expected.length];

        for (int i = 0; i < expected.length; i++) {
            if (args.length <= i) {
                converted[i] = null;
            } else {
                converted[i] = convert(expected[i], args[i]);
            }
        }
        return converted;
    }

    private static Object convert(Class<?> type, String text) {
        final PropertyEditor editor = PropertyEditorManager.findEditor(type);

        if (editor != null) {
            editor.setAsText(text);
            return editor.getValue();
        } else {
            try {
                final Constructor<?> constructor = type.getConstructor(String.class);
                return constructor.newInstance(text);
            } catch (NoSuchMethodException e) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public Method getMethod() {
        return method;
    }

    static {
        PropertyEditorManager.registerEditor(Pattern.class, PatternEditor.class);
    }

    public static class PatternEditor extends java.beans.PropertyEditorSupport {

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            setValue(Pattern.compile(text));
        }
    }
}
