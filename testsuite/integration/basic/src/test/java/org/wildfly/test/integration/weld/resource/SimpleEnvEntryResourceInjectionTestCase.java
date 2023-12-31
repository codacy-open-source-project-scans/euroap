/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.weld.resource;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of behavior of {@code SimpleEnvEntryCdiResourceInjectionProcessor}.
 */
@RunWith(Arquillian.class)
public class SimpleEnvEntryResourceInjectionTestCase {

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, "SimpleEnvEntryResourceInjectionTestCase.war")
                .addClasses(SimpleEnvEntryResourceInjectionTestCase.class, EnvEntryInjectionBean.class)
                .addAsWebInfResource(SimpleEnvEntryResourceInjectionTestCase.class.getPackage(), "jboss-web.xml", "jboss-web.xml")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    private static EnvEntryInjectionBean bean;

    static void setBean(EnvEntryInjectionBean initialized) {
        bean = initialized;
    }

    EnvEntryInjectionBean getBean() {
        Assert.assertNotNull(EnvEntryInjectionBean.class.getSimpleName() + " was not initialized", bean);
        return bean;
    }

    @AfterClass
    public static void afterClass() {
        bean = null;
    }

    @Test
    public void testUnmapped() {
        org.junit.Assert.assertNotNull("Null should not be injected into unmappedEnvEntry", getBean().getUnmappedEnvEntry());
        org.junit.Assert.assertEquals("unmappedEnvEntry should have its initial value",1, getBean().getUnmappedEnvEntry().intValue());
    }

    @Test
    public void testMapped() {
        org.junit.Assert.assertEquals("mappedEnvEntry should have its deployment descriptor value",1, getBean().getMappedEnvEntry().intValue());
    }

    @Test
    public void testUnnamedMapped() {
        org.junit.Assert.assertEquals("unnamedMappedEnvEntry should have its deployment descriptor value",1, getBean().getUnnamedMappedEnvEntry().intValue());
    }

    @Test
    public void testUnannotatedString() {
        org.junit.Assert.assertEquals("unannotatedString should have its deployment descriptor value","injected", getBean().getUnannotatedString());
    }

    @Test
    public void testUnannotatedBoolean() {
        org.junit.Assert.assertTrue("unannotatedBoolean should have its deployment descriptor value", getBean().isUnannotatedBoolean());
    }

    @Test
    public void testUnannotatedChar() {
        org.junit.Assert.assertEquals("unannotatedChar should have its deployment descriptor value", Character.valueOf('b'), Character.valueOf(getBean().getUnannotatedChar()));
    }

    @Test
    public void testUnannotatedByte() {
        org.junit.Assert.assertEquals("unannotatedByte should have its deployment descriptor value", Byte.valueOf((byte)1), Byte.valueOf(getBean().getUnannotatedByte()));
    }

    @Test
    public void testUnannotatedShort() {
        org.junit.Assert.assertEquals("unannotatedShort should have its deployment descriptor value", 1, getBean().getUnannotatedShort());
    }

    @Test
    public void testUnannotatedInt() {
        org.junit.Assert.assertEquals("unannotatedInt should have its deployment descriptor value", 1, getBean().getUnannotatedInt());
    }

    @Test
    public void testUnannotatedLong() {
        org.junit.Assert.assertEquals("unannotatedLong should have its deployment descriptor value", 1L, getBean().getUnannotatedLong());
    }

    @Test
    public void testUnannotatedFloat() {
        org.junit.Assert.assertEquals("unannotatedFloat should have its deployment descriptor value", (float) 1, getBean().getUnannotatedFloat(), 0.1);
    }

    @Test
    public void testUnannotatedDouble() {
        org.junit.Assert.assertEquals("unannotatedDouble should have its deployment descriptor value", (double) 1, getBean().getUnannotatedDouble(), 0.1);
    }

    @Test
    public void testUnannotatedStringProperty() {
        org.junit.Assert.assertEquals("unannotatedStringProperty should have its deployment descriptor value","injected", getBean().getUnannotatedStringProperty());
    }

    @Test
    public void testUnannotatedBooleanProperty() {
        org.junit.Assert.assertTrue("unannotatedBooleanProperty should have its deployment descriptor value", getBean().isUnannotatedBooleanProperty());
    }

    @Test
    public void testUnannotatedIntProperty() {
        org.junit.Assert.assertEquals("unannotatedIntProperty should have its deployment descriptor value", 1, getBean().getUnannotatedIntProperty());
    }

    @Test
    public void testUnannotatedIntLookup() {
        org.junit.Assert.assertEquals("unannotatedIntLookup should have its deployment descriptor value", 1, getBean().getUnannotatedIntLookup());
    }
}