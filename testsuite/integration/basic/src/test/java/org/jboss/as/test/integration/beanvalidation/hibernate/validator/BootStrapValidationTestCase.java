/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.beanvalidation.hibernate.validator;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Set;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.HibernateValidatorFactory;
import org.hibernate.validator.HibernateValidatorPermission;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that bootstrapping works correctly for Hibernate Validator.
 *
 * @author Madhumita Sadhukhan
 */
@RunWith(Arquillian.class)
public class BootStrapValidationTestCase {

    @Deployment
    public static Archive<?> deploy() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, "testbootstrapvalidation.war");
        war.addPackage(BootStrapValidationTestCase.class.getPackage());
        war.addAsManifestResource(createPermissionsXmlAsset(
                HibernateValidatorPermission.ACCESS_PRIVATE_MEMBERS
        ), "permissions.xml");
        return war;
    }

    @Test
    public void testBootstrapAsServiceDefault() {
        HibernateValidatorFactory factory = (HibernateValidatorFactory) Validation.buildDefaultValidatorFactory();
        assertNotNull(factory);
    }

    @Test
    public void testCustomConstraintValidatorFactory() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        assertNotNull(configuration);

        ValidatorFactory factory = configuration.buildValidatorFactory();
        Validator validator = factory.getValidator();

        Employee emp = new Employee();
        // create employee
        emp.setEmpId("M1234");
        emp.setFirstName("MADHUMITA");
        emp.setLastName("SADHUKHAN");
        emp.setEmail("madhu@redhat.com");

        Set<ConstraintViolation<Employee>> constraintViolations = validator.validate(emp);
        assertEquals("Wrong number of constraints", constraintViolations.size(), 1);
        assertEquals("Created by default factory", constraintViolations.iterator().next().getMessage());

        // get a new factory using a custom configuration
        configuration.constraintValidatorFactory(new CustomConstraintValidatorFactory(configuration
                .getDefaultConstraintValidatorFactory()));

        factory = configuration.buildValidatorFactory();
        validator = factory.getValidator();
        constraintViolations = validator.validate(emp);
        assertEquals("Wrong number of constraints", constraintViolations.size(), 1);
        assertEquals("Created by custom factory", constraintViolations.iterator().next().getMessage());
    }

    /**
     * A custom constraint validator factory.
     */
    private static class CustomConstraintValidatorFactory implements ConstraintValidatorFactory {

        private final ConstraintValidatorFactory delegate;

        CustomConstraintValidatorFactory(ConstraintValidatorFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key == CustomConstraint.Validator.class) {
                return (T) new CustomConstraint.Validator("Created by custom factory");
            }

            return delegate.getInstance(key);
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
            delegate.releaseInstance(instance);
        }
    }
}
