/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.security.runas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.Principal;
import java.util.concurrent.Callable;

import org.jboss.logging.Logger;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBAccessException;
import javax.naming.InitialContext;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.ejb.security.EjbSecurityDomainSetup;
import org.jboss.as.test.integration.ejb.security.Entry;
import org.jboss.as.test.integration.ejb.security.WhoAmI;
import org.jboss.as.test.integration.ejb.security.base.WhoAmIBean;
import org.jboss.as.test.integration.security.common.AbstractSecurityDomainSetup;
import org.jboss.as.test.shared.integration.ejb.security.Util;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Test case to test the requirements related to the handling of a RunAs identity.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(Arquillian.class)
@ServerSetup({EjbSecurityDomainSetup.class})
@Category(CommonCriteria.class)
public class RunAsTestCase {

    private static final Logger log = Logger.getLogger(RunAsTestCase.class.getName());

    @ArquillianResource
    InitialContext ctx;

    @EJB(mappedName = "java:global/ejb3security/WhoAmIBean!org.jboss.as.test.integration.ejb.security.WhoAmI")
    private WhoAmI whoAmIBean;

    @EJB(mappedName = "java:global/ejb3security/EntryBean!org.jboss.as.test.integration.ejb.security.runas.EntryBean")
    private EntryBean entryBean;

    /*
     * isCallerInRole Scenarios with @RunAs Defined
     *
     * EJB 3.1 FR 17.2.5.2 isCallerInRole tests the principal that represents the caller of the enterprise bean, not the
     * principal that corresponds to the run-as security identity for the bean.
     */
    @Deployment
    public static Archive<?> runAsDeployment() {
        final Package currentPackage = RunAsTestCase.class.getPackage();
        // using JavaArchive doesn't work, because of a bug in Arquillian, it only deploys wars properly
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "ejb3security.war")
                .addPackage(WhoAmIBean.class.getPackage()).addPackage(EntryBean.class.getPackage())
                .addPackage(HttpRequest.class.getPackage()).addClass(WhoAmI.class).addClass(Util.class).addClass(Entry.class)
                .addClasses(AbstractSecurityDomainSetup.class, EjbSecurityDomainSetup.class)
                .addAsResource(currentPackage, "users.properties", "users.properties")
                .addAsResource(currentPackage, "roles.properties", "roles.properties")
                .addAsWebInfResource(currentPackage, "web.xml", "web.xml")
                .addAsWebInfResource(currentPackage, "jboss-web.xml", "jboss-web.xml")
                .addAsWebInfResource(currentPackage, "jboss-ejb3.xml", "jboss-ejb3.xml")
                .addAsManifestResource(new StringAsset("Manifest-Version: 1.0\nDependencies: org.jboss.as.controller-client,org.jboss.dmr\n"), "MANIFEST.MF")
                .addAsManifestResource(currentPackage, "permissions.xml", "permissions.xml");
        war.addPackage(CommonCriteria.class.getPackage());
        return war;
    }

    @Test
    public void testAuthentication_TwoBeans() throws Exception {
        final Callable<Void> callable = () -> {
            String[] response = entryBean.doubleWhoAmI();
            assertEquals("user1", response[0]);
            assertEquals("anonymous", response[1]); //Unless a run-as-principal configuration has been done, you cannot expect a principal
            return null;
        };
        Util.switchIdentity("user1", "password1", callable);
    }

    @Test
    public void testRunAsICIR_TwoBeans() throws Exception {
        Callable<Void> callable = () -> {
            // TODO - Enable once auth checks are working.
            /*
             * try { whoAmIBean.getCallerPrincipal(); fail("Expected call to whoAmIBean to fail"); } catch (Exception expected)
             * { }
             */

            boolean[] response;
            response = entryBean.doubleDoIHaveRole("Users");
            assertTrue(response[0]);
            assertFalse(response[1]);

            response = entryBean.doubleDoIHaveRole("Role1");
            assertTrue(response[0]);
            assertFalse(response[1]);

            response = entryBean.doubleDoIHaveRole("Role2");
            assertFalse(response[0]);
            assertTrue(response[1]);
            return null;
        };
        Util.switchIdentity("user1", "password1", callable);

        callable = () -> {
            // Verify the call now passes.
            Principal user = whoAmIBean.getCallerPrincipal();
            assertNotNull(user);

            boolean[] response;
            response = entryBean.doubleDoIHaveRole("Users");
            assertTrue(response[0]);
            assertFalse(response[1]);

            response = entryBean.doubleDoIHaveRole("Role1");
            assertFalse(response[0]);
            assertFalse(response[1]);

            response = entryBean.doubleDoIHaveRole("Role2");
            assertTrue(response[0]);
            assertTrue(response[1]);
            return null;
        };
        Util.switchIdentity("user2", "password2", callable);
    }

    @Test
    public void testOnlyRole1() {
        try {
            entryBean.callOnlyRole1();
            fail("Expected EJBAccessException");
        } catch (EJBAccessException e) {
            // good
        }
    }

    /**
     * Migration test from EJB Testsuite (security/TimerRunAs) to AS7 [JBQA-5483].
     */
    @Test
    public void testTimerNoSecurityAssociationPrincipal() throws Exception {
        final Callable<Void> callable = () -> {
            TimerTester test = (TimerTester) ctx.lookup("java:module/" + TimerTesterBean.class.getSimpleName());

            assertNotNull(test);
            test.startTimer(150);
            Assert.assertTrue(TimerTesterBean.awaitTimerCall());

            Assert.assertEquals("user2", TimerTesterBean.calleeCallerPrincipal.iterator().next().getName());
            return null;
        };
        Util.switchIdentity("user1", "password1", callable);
    }
}
