/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.beanvalidation.hibernate.validator;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
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
 * Tests GroupSequenceProvider feature in Hibernate Validator.
 *
 * @author Madhumita Sadhukhan
 */
@RunWith(Arquillian.class)
public class GroupandGroupSequenceValidationTestCase {

    @Deployment
    public static Archive<?> deploy() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "testgroupvalidation.war");
        war.addPackage(GroupandGroupSequenceValidationTestCase.class.getPackage());
        war.addAsManifestResource(createPermissionsXmlAsset(
                HibernateValidatorPermission.ACCESS_PRIVATE_MEMBERS
        ),"permissions.xml");

        return war;
    }

    @Test
    public void testGroupValidation() throws NamingException {
        Validator validator = (Validator) new InitialContext().lookup("java:comp/Validator");

        // create first passenger
        UserBean user1 = new UserBean("MADHUMITA", "SADHUKHAN");
        user1.setEmail("madhu@gmail.com");
        user1.setAddress("REDHAT Brno");

        // create second passenger
        UserBean user2 = new UserBean("Mickey", "Mouse");
        user2.setEmail("mickey@gmail.com");
        user2.setAddress("DISNEY CA USA");

        List<UserBean> passengers = new ArrayList<UserBean>();
        passengers.add(user1);
        passengers.add(user2);

        // Create a Car
        Car car = new Car("CET5678", passengers);
        car.setModel("SKODA Octavia");

        // validate Car with default group as per GroupSequenceProvider
        Set<ConstraintViolation<Car>> result = validator.validate(car);
        assertEquals(1, result.size());
        assertEquals("The Car has to pass the fuel test and inspection test before being driven", result.iterator().next()
                .getMessage());

        Driver driver = new Driver("Sebastian", "Vettel");
        driver.setAge(25);
        driver.setAddress("ABC");

        result = validator.validate(car);
        assertEquals(1, result.size());
        assertEquals("The Car has to pass the fuel test and inspection test before being driven", result.iterator().next()
                .getMessage());
        car.setPassedVehicleInspection(true);

        result = validator.validate(car);
        // New group set in defaultsequence for Car as per CarGroupSequenceProvider should be implemented now
        assertEquals(2, result.size());

        car.setDriver(driver);
        // implementing validation for group associated with associated objects of Car(in this case Driver)
        Set<ConstraintViolation<Car>> driverResult = validator.validate(car, DriverChecks.class);
        assertEquals(1, driverResult.size());
        driver.setHasValidDrivingLicense(true);
        assertEquals(0, validator.validate(car, DriverChecks.class).size());

        car.setSeats(5);
        car.setHasBeenPaid(true);

        assertEquals(0, validator.validate(car).size());

    }
}
