/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.beanvalidation.hibernate.validator;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.hibernate.validator.HibernateValidatorPermission;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that hibernate validator works correctly for WAR(Web Applications)
 *
 * @author Madhumita Sadhukhan
 */
@RunWith(Arquillian.class)
public class ConstraintValidationTestCase {

    @Deployment
    public static Archive<?> deploy() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "testconstraintvalidation.war");
        war.addPackage(ConstraintValidationTestCase.class.getPackage());
        war.addAsManifestResource(createPermissionsXmlAsset(
                HibernateValidatorPermission.ACCESS_PRIVATE_MEMBERS
        ), "permissions.xml");
        return war;
    }

    @Test
    public void testConstraintValidation() throws NamingException {
        Validator validator = (Validator) new InitialContext().lookup("java:comp/Validator");
        UserBean user1 = new UserBean("MADHUMITA", "");
        user1.setEmail("madhumita_gmail");
        user1.setAddress("");
        final Set<ConstraintViolation<UserBean>> result = validator.validate(user1);

        Iterator<ConstraintViolation<UserBean>> it = result.iterator();
        String message = "";

        while (it.hasNext()) {
            ConstraintViolation<UserBean> cts = it.next();
            String mess = cts.getMessage();

            if (mess.contains("Please get a valid address")) { message = mess; }
        }

        assertEquals(3, result.size());
        assertTrue(message.contains("Please get a valid address"));
    }

    @Test
    public void testObjectGraphValidation() throws NamingException {
        Validator validator = (Validator) new InitialContext().lookup("java:comp/Validator");

        // create first passenger
        UserBean user1 = new UserBean("MADHUMITA", "SADHUKHAN");
        user1.setEmail("madhu@gmail.com");
        user1.setAddress("REDHAT Brno");

        // create second passenger
        UserBean user2 = new UserBean("Mickey", "Mouse");
        user2.setAddress("");

        List<UserBean> passengers = new ArrayList<UserBean>();
        passengers.add(user1);
        passengers.add(user2);

        // Create a Car
        Car car = new Car("CET5678", passengers);
        car.setModel("SKODA Octavia");

        final Set<ConstraintViolation<Car>> errorresult = validator.validate(car);

        Iterator<ConstraintViolation<Car>> it1 = errorresult.iterator();
        String message = "";

        while (it1.hasNext()) {
            ConstraintViolation<Car> cts = it1.next();
            String mess = cts.getMessage();

            if (mess.contains("Please get a valid address")) { message = mess; }
        }

        assertEquals(2, errorresult.size());
        assertTrue(message.contains("Please get a valid address"));
    }
}
