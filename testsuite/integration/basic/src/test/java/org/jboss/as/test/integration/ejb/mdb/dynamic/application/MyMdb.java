/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.mdb.dynamic.application;

import org.jboss.as.test.integration.ejb.mdb.dynamic.api.Command;
import org.jboss.as.test.integration.ejb.mdb.dynamic.api.Option;
import org.jboss.as.test.integration.ejb.mdb.dynamic.api.TelnetListener;
import org.jboss.ejb3.annotation.ResourceAdapter;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "beanClass", propertyValue = "org.jboss.as.test.integration.ejb.mdb.dynamic.application.MyMdb"),
        @ActivationConfigProperty(propertyName = "prompt", propertyValue = "pronto>")
})
@ResourceAdapter("ear-with-rar.ear#telnet-ra.rar")
public class MyMdb implements TelnetListener {
    private final Properties properties = new Properties();

    @Command("get")
    public String doGet(@Option("key") String key) {
        return properties.getProperty(key);
    }

    @Command("set")
    public String doSet(@Option("key") String key, @Option("value") String value) {

        final Object old = properties.setProperty(key, value);
        final StringBuilder sb = new StringBuilder();
        sb.append("set ").append(key).append(" to ").append(value);
        sb.append("\n");
        if (old != null) {
            sb.append("old value: ").append(old);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Command("list")
    public String doList(@Option("pattern") Pattern pattern) {

        if (pattern == null) pattern = Pattern.compile(".*");
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            final String key = entry.getKey().toString();
            if (pattern.matcher(key).matches()) {
                sb.append(key).append(" = ").append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }
}
