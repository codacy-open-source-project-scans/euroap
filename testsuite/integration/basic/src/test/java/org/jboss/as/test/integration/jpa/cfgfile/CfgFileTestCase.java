/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jpa.cfgfile;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test Hibernate configuration in hibernate.cfg.xml file
 *
 * @author Zbynek Roubalik
 */
@RunWith(Arquillian.class)
public class CfgFileTestCase {

    private static final String ARCHIVE_NAME = "jpa_cfgfile";

    private static final String hibernate_cfg_xml =
            "<?xml version='1.0' encoding='utf-8'?>\n " +
                    "<!DOCTYPE hibernate-configuration>\n" +
                    "<hibernate-configuration>\n" +
                    "<session-factory>\n" +
                    "    <property name=\"show_sql\">true</property>\n" +
                    "    <property name=\"hibernate.default_batch_fetch_size\">5</property>\n" +
                    "    <property name=\"dialect\">org.hibernate.dialect.HSQLDialect</property>\n" +
                    "    <property name=\"hibernate.hbm2ddl.auto\">create-drop</property>\n" +
                    "  </session-factory>\n" +
                    "</hibernate-configuration>";


    @Deployment
    public static Archive<?> deploy() {

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar");
        jar.addClasses(CfgFileTestCase.class,
                Employee.class,
                SFSB1.class
        );
        jar.addAsManifestResource(CfgFileTestCase.class.getPackage(), "persistence.xml", "persistence.xml");
        jar.addAsResource(new StringAsset(hibernate_cfg_xml), "hibernate.cfg.xml");
        return jar;

    }

    @ArquillianResource
    private InitialContext iniCtx;


    protected <T> T lookup(String beanName, Class<T> interfaceType) throws NamingException {
        return interfaceType.cast(iniCtx.lookup("java:global/" + ARCHIVE_NAME + "/" + beanName + "!" + interfaceType.getName()));
    }

    protected <T> T rawLookup(String name, Class<T> interfaceType) throws NamingException {
        return interfaceType.cast(iniCtx.lookup(name));
    }

    @Test
    public void testEntityManagerInvocation() throws Exception {
        SFSB1 sfsb1 = lookup("SFSB1", SFSB1.class);
        sfsb1.getEmployeeNoTX(1);
    }
}
