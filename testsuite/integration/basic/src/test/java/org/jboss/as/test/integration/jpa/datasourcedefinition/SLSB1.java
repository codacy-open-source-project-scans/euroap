/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jpa.datasourcedefinition;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import jakarta.annotation.Resource;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import javax.naming.Context;
import javax.naming.InitialContext;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import jakarta.transaction.UserTransaction;

/**
 * @author Zbynek Roubalik
 */
@DataSourceDefinition(
        name = "java:app/DataSource",
        user = "sa",
        password = "sa",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"

)
@Stateless
@TransactionManagement(TransactionManagementType.BEAN)
public class SLSB1 {
    @PersistenceContext(unitName = "mypc")
    EntityManager em;

    @Resource
    UserTransaction tx;

    public Employee createEmployee(String name, String address, int id) {

        Employee emp = new Employee();
        emp.setId(id);
        emp.setAddress(address);
        emp.setName(name);
        return emp;
    }

    /**
     * Makes two DAO calls, the transaction fails during the first DAO call. The
     * Jakarta Transactions transaction is rolled back and no database changes should occur.
     */
    public String failInFirstCall() throws Exception {
        int[] initialList = getEmployeeIDsNoEM();

        try {
            tx.begin();
            performFailCall();
            em.persist(createEmployee("Tony", "Butcher", 101));
            tx.commit();

            return "Transaction was performed, but shouldn't";
        } catch (EntityExistsException e) {
            tx.rollback();

            int[] newList = getEmployeeIDsNoEM();
            if (Arrays.equals(initialList, newList)) {
                return "success";
            } else {
                return "Database changed.";
            }
        }

    }

    /**
     * Makes two DAO calls, the transaction fails during the second DAO call.
     * The Jakarta Transactions transaction is rolled back and no database changes should occur.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public String failInSecondCall() throws Exception {
        int[] initialList = getEmployeeIDsNoEM();

        try {
            tx.begin();
            em.persist(createEmployee("Jane", "Butcher", 102));
            performFailCall();
            tx.commit();

            return "Transaction was performed, but shouldn't";
        } catch (EntityExistsException e) {
            tx.rollback();

            int[] newList = getEmployeeIDsNoEM();
            if (Arrays.equals(initialList, newList)) {
                return "success";
            } else {
                return "Database changed.";
            }
        } catch (Exception unexpected) {
            return unexpected.getMessage();
        }

    }

    /**
     * Makes two DAO calls, the transaction fails after the DAO calls. The Jakarta Transactions
     * transaction is rolled back and no database changes should occur.
     */
    public String failAfterCalls() throws Exception {
        int[] initialList = getEmployeeIDsNoEM();

        try {
            tx.begin();
            em.persist(createEmployee("Peter", "Butcher", 103));
            em.persist(createEmployee("John", "Butcher", 104));

            int n = 100 / 0; // this should throw exception: division by zero
            n = n + 20;
            tx.commit();

            return "Transaction was performed, but shouldn't";
        } catch (Exception e) {
            tx.rollback();

            int[] newList = getEmployeeIDsNoEM();
            if (Arrays.equals(initialList, newList)) {
                return "success";
            } else {
                return "Database changed.";
            }
        }

    }

    /**
     * Persisting existing entity, should throws EntityExistsException
     */
    public void performFailCall() {
        Employee emp = em.find(Employee.class, 1212);

        if (emp == null) {
            emp = createEmployee("Mr. Problem", "Brno ", 1212);
            em.persist(emp);
            em.flush();
        }

        em.persist(createEmployee(emp.getName(), emp.getAddress(), emp.getId()));
    }

    /**
     * Returns array of Employee IDs in DB using raw connection to the
     * DataSource
     */
    public int[] getEmployeeIDsNoEM() throws Exception {

        int[] idList = null;

        Context ctx = new InitialContext();
        DataSource ds = (DataSource) ctx
                .lookup("java:jboss/datasources/ExampleDS");
        Connection conn = ds.getConnection();
        try {
            ResultSet rs = conn.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY).executeQuery(
                    "SELECT e.id FROM Employee e ORDER BY e.id");

            rs.last();
            int rowCount = rs.getRow();
            idList = new int[rowCount];
            rs.first();

            int i = 0;
            while (rs.next()) {
                idList[i] = rs.getInt(1);
                i++;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            conn.close();
        }

        return idList;

    }

    /**
     * Adds new Employee
     */
    public void addEmployee() throws Exception {

        try {
            tx.begin();
            em.persist(createEmployee("John", "Wayne", 100));
            tx.commit();
        } catch (Exception e) {
            throw new Exception("Couldn't add an Employee.");
        }
    }

}
