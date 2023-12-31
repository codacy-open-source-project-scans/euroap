/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.sessioncontext;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that bean implementing the {@link jakarta.ejb.SessionBean} interface has its
 * {@link jakarta.ejb.SessionBean#setSessionContext(jakarta.ejb.SessionContext)} method invoked.
 * <p/>
 * User: Jaikiran Pai
 */
@RunWith(Arquillian.class)
public class SetSessionContextMethodInvocationTestCase {

    @ArquillianResource
    InitialContext ctx;

    @Deployment
    public static JavaArchive createDeployment() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class);
        jar.addPackage(SLSBImplementingSessionBean.class.getPackage());

        return jar;
    }

    private<T> T lookup(Class<T> beanType) throws NamingException {
        return beanType.cast(ctx.lookup("java:module/" + beanType.getSimpleName() + "!" + beanType.getName()));
    }

    /**
     * Tests that {@link jakarta.ejb.SessionBean#setSessionContext(jakarta.ejb.SessionContext)} was invoked on a stateless
     * session bean, implementing the {@link jakarta.ejb.SessionBean} interface
     *
     * @throws Exception
     */
    @Test
    public void testSetSessionContextOnSLSB() throws Exception {
        final SLSBImplementingSessionBean slsb = lookup(SLSBImplementingSessionBean.class);
        Assert.assertTrue("setSessionContext(SessionContext) method was not invoked on a stateless bean implementing jakarta.ejb.SessionBean",
                slsb.wasSetSessionContextMethodInvoked());
    }

    /**
     * Tests that {@link jakarta.ejb.SessionBean#setSessionContext(jakarta.ejb.SessionContext)} was invoked on a stateful
     * session bean, implementing the {@link jakarta.ejb.SessionBean} interface
     *
     * @throws Exception
     */
    @Test
    public void testSetSessionContextOnSFSB() throws Exception {
        final SFSBImplementingSessionBean sfsb = lookup(SFSBImplementingSessionBean.class);
        Assert.assertTrue("setSessionContext(SessionContext) method was not invoked on a stateful bean implementing jakarta.ejb.SessionBean",
                sfsb.wasSetSessionContextMethodInvoked());
    }


    /**
     * Tests that a {@link jakarta.ejb.SessionContext} is injected into a stateless bean, via the @Resource annotation
     *
     * @throws Exception
     */
    @Test
    public void testSessionContextInjectionOnSLSB() throws Exception {
        final SLSBImplementingSessionBean slsb = lookup(SLSBImplementingSessionBean.class);
        Assert.assertTrue("SessionContext was not injectd in stateless bean", slsb.wasSessionContextInjected());
    }

    /**
     * Tests that a {@link jakarta.ejb.SessionContext} is injected into a stateful bean, via the @Resource annotation
     *
     * @throws Exception
     */
    @Test
    public void testSessionContextInjectionOnSFSB() throws Exception {
        final SFSBImplementingSessionBean sfsb = lookup(SFSBImplementingSessionBean.class);
        Assert.assertTrue("SessionContext was not injectd in stateful bean", sfsb.wasSessionContextInjected());
    }

    /**
     * Testing whether correct exception is called on {@link jakarta.ejb.SessionContext#wasCancelCalled()} is returned. Supposing IllegalStateException.
     */
    @Test
    public void testWasCancelledCalled() throws Exception {
        final SLSBImplementingSessionBean slsb = lookup(SLSBImplementingSessionBean.class);
        try {
            slsb.wasCanceledCalled();
        } catch (Exception e) {
            Assert.assertEquals("The SessionContext.wasCanceledCalled() method was called on not asynchronous method. We supposing " + IllegalStateException.class.getName(),
                    IllegalStateException.class, e.getCause().getClass());
            return; // we are fine with this
        }
        Assert.fail("Expecting: " + IllegalStateException.class.getName() + " and it was not thrown.");
    }

}
