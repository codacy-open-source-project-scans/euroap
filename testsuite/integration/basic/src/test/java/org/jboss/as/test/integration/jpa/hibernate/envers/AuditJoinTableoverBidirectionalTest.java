/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jpa.hibernate.envers;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test @AuditJoinTable over Bidirectional Relationship
 *
 * @author Madhumita Sadhukhan
 */
@RunWith(Arquillian.class)
public class AuditJoinTableoverBidirectionalTest {
    private static final String ARCHIVE_NAME = "jpa_AuditJoinTableoverBidirectionalTest";

    @ArquillianResource
    private static InitialContext iniCtx;

    @BeforeClass
    public static void beforeClass() throws NamingException {

        iniCtx = new InitialContext();
    }

    @Deployment
    public static Archive<?> deploy() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar");
        jar.addClasses(CustomerMO.class, PhoneMO.class, SLSBAuditMO.class);
        jar.addAsManifestResource(AuditJoinTableoverBidirectionalTest.class.getPackage(), "persistence.xml", "persistence.xml");
        return jar;
    }

    protected static <T> T lookup(String beanName, Class<T> interfaceType) throws NamingException {
        return interfaceType
                .cast(iniCtx.lookup("java:global/" + ARCHIVE_NAME + "/" + beanName + "!" + interfaceType.getName()));
    }

    @Test
    public void testRevisionsforValidityStrategyoverManytoOne() throws Exception {

        SLSBAuditMO slsbAudit = lookup("SLSBAuditMO", SLSBAuditMO.class);

        CustomerMO c1 = slsbAudit.createCustomer("MADHUMITA", "SADHUKHAN", "WORK", "+420", "543789654");
        PhoneMO p1 = c1.getPhones().get(0);
        p1.setType("Emergency");
        p1.setCustomer(c1);
        slsbAudit.updatePhone(p1);
        c1.setFirstname("Madhu");
        slsbAudit.updateCustomer(c1);

        int c = slsbAudit.retrieveOldPhoneListSizeFromCustomer(c1.getId());

        Assert.assertEquals(2, c);

        String phoneType = slsbAudit.retrieveOldPhoneListVersionFromCustomer(c1.getId());

        // check that updating Phone updates audit information fetched from Customer
        Assert.assertEquals("WORK", phoneType);

        PhoneMO p3 = slsbAudit.createPhone("WORK", "+420", "543789654");
        p3.setCustomer(c1);
        slsbAudit.updatePhone(p3);
        c1.getPhones().add(p3);

        slsbAudit.updateCustomer(c1);

        PhoneMO p4 = slsbAudit.createPhone("WORK", "+420", "88899912");
        slsbAudit.updatePhone(p4);
        c1.getPhones().add(p4);
        //System.out.println("PhoneList size::" + c1.getPhones().size());
        slsbAudit.updateCustomer(c1);

        int check = slsbAudit.retrieveOldPhoneListSizeFromCustomer(c1.getId());
        Assert.assertEquals(4, check);

    }

}
