/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.security;

import java.util.concurrent.Callable;

import jakarta.ejb.EJBAccessException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.security.common.AbstractSecurityDomainSetup;
import org.jboss.as.test.shared.integration.ejb.security.Util;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * User: jpai
 */
@RunWith(Arquillian.class)
@Category(CommonCriteria.class)
@ServerSetup({EjbSecurityDomainSetup.class})
public class EJBInWarDefaultSecurityDomainTestCase {
    @ArquillianResource
    private Context ctx;

    @Deployment
    public static WebArchive createDeployment() {
        final Package currentPackage = EJBInWarDefaultSecurityDomainTestCase.class.getPackage();
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "ejb-security-test.war")
                .addClasses(BeanWithoutExplicitSecurityDomain.class, Restriction.class)
                .addClasses(FullAccess.class, EjbSecurityDomainSetup.class, Util.class)
                .addAsWebInfResource(currentPackage, "jboss-web.xml", "jboss-web.xml")
                .addPackage(CommonCriteria.class.getPackage())
                .addPackage(AbstractSecurityDomainSetup.class.getPackage())
                .addAsManifestResource(currentPackage, "permissions.xml", "permissions.xml");
        return war;
    }

    private <T> T lookup(final Class<?> beanClass, final Class<T> viewClass) throws NamingException {
        return viewClass.cast(ctx.lookup("java:module/" + beanClass.getSimpleName() + "!" + viewClass.getName()));
    }

    /**
     * Tests that a bean which doesn't explicitly have a security domain configured, but still has EJB security related
     * annotations on it, is still considered secured and the security annotations are honoured
     *
     * @throws Exception
     */
    @Test
    public void testSecurityOnBeanInAbsenceOfExplicitSecurityDomain() throws Exception {
        final Context ctx = new InitialContext();
        // lookup the bean which doesn't explicitly have any security domain configured
        final Restriction restrictedBean = (Restriction) ctx.lookup("java:module/" + BeanWithoutExplicitSecurityDomain.class.getSimpleName() + "!" + Restriction.class.getName());
        try {
            // try invoking a method annotated @DenyAll (expected to fail)
            restrictedBean.restrictedMethod();
            Assert.fail("Call to restrictedMethod() method was expected to fail");
        } catch (EJBAccessException ejbae) {
            // expected
        }

        // lookup the bean which doesn't explicitly have any security domain configured
        final FullAccess fullAccessBean = (FullAccess) ctx.lookup("java:module/" + BeanWithoutExplicitSecurityDomain.class.getSimpleName() + "!" + FullAccess.class.getName());
        // invoke a @PermitAll method
        fullAccessBean.doAnything();

        // lookup the bean which doesn't explicitly have any security domain configured
        final BeanWithoutExplicitSecurityDomain specificRoleAccessBean = (BeanWithoutExplicitSecurityDomain) ctx.lookup("java:module/" + BeanWithoutExplicitSecurityDomain.class.getSimpleName() + "!" + BeanWithoutExplicitSecurityDomain.class.getName());
        try {
            // invoke a method which only a specific role can access.
            // this is expected to fail since we haven't logged in as any user
            specificRoleAccessBean.allowOnlyRoleTwoToAccess();
            Assert.fail("Invocation was expected to fail since only a specific role was expected to be allowed to access the bean method");
        } catch (EJBAccessException ejbae) {
            // expected
        }


        // login as user1 and test
        Callable<Void> callable = () -> {
            // expected to pass since user1 belongs to Role1
            specificRoleAccessBean.allowOnlyRoleOneToAccess();

            // expected to fail since user1 *doesn't* belong to Role2
            try {
                specificRoleAccessBean.allowOnlyRoleTwoToAccess();
                Assert.fail("Call to toBeInvokedByRole2() was expected to fail");
            } catch (EJBAccessException ejbae) {
                // expected
            }
            return null;
        };
        Util.switchIdentity("user1", "password1", callable);

        // login as user2 and test
        callable = () -> {
            // expected to pass since user2 belongs to Role2
            specificRoleAccessBean.allowOnlyRoleTwoToAccess();

            // expected to fail since user2 *doesn't* belong to Role1
            try {
                specificRoleAccessBean.allowOnlyRoleOneToAccess();
                Assert.fail("Call to toBeInvokedOnlyByRole1() was expected to fail");
            } catch (EJBAccessException ejbae) {
                // expected
            }
            return null;
        };
        Util.switchIdentity("user2", "password2", callable);


    }
}
