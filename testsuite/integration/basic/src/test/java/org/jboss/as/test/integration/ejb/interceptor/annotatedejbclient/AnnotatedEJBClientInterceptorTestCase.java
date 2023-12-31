/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.interceptor.annotatedejbclient;

import java.net.URL;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.shared.integration.ejb.security.Util;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * AnnotatedEJBClientInterceptorTest
 * For more information visit <a href="issues.jboss.org/browse/JBEAP-14246">https://issues.jboss.org/browse/JBEAP-14246</a>
 * @author Petr Adamec
 */
@RunWith(Arquillian.class)
@RunAsClient
public class AnnotatedEJBClientInterceptorTestCase {
    private static final String JAR_FILE_NAME = "remote-interface-test-annotations-standalone";
    @Deployment
    public static JavaArchive getDeployment() {
        return ShrinkWrap.create(JavaArchive.class, JAR_FILE_NAME+".jar")
                .addClasses(ClientInterceptor.class, TestRemote.class, TestSLSB.class);
    }

    public static Context getInitialContext(String host, Integer port, String username, String password) throws Exception {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY,  "org.wildfly.naming.client.WildFlyInitialContextFactory");
        props.put(Context.PROVIDER_URL, String.format("%s://%s:%d", "remote+http", host, port));
        if(username != null && password != null) {
            props.put(Context.SECURITY_PRINCIPAL, username);
            props.put(Context.SECURITY_CREDENTIALS, password);
        }
        return new InitialContext(props);
    }
    @Test
    public void testConfiguration(@ArquillianResource URL url) throws Throwable {
        TestRemote bean = lookup(url, TestRemote.class, TestSLSB.class, JAR_FILE_NAME);
        if(bean.invoke("Test-" + System.currentTimeMillis()) > 1){
            Assert.fail("Method was invoked more than once.");
        }
    }

    private <T> T lookup(URL url, final Class<T> remoteClass, final Class<?> beanClass, final String archiveName) throws NamingException, Throwable {
        String myContext = Util.createRemoteEjbJndiContext(
                "",
                archiveName,
                "",
                beanClass.getSimpleName(),
                remoteClass.getName(),
                false);

        return remoteClass.cast(getInitialContext(url.getHost(), url.getPort(), null, null).lookup(myContext));
    }

}