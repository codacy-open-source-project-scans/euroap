/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.deployment.classloading.transformer;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.integration.deployment.classloading.ear.TestAA;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Marius Bogoevici
 */
@RunWith(Arquillian.class)
public class WarJbossStructureClassFileTransformerTestCase {

    public static final String CLASS_NAME = "org.jboss.as.test.integration.deployment.classloading.ear.TestAA";

    public static final String TRANSFORMER1_CLASS_NAME_CANONICAL = "org/jboss/as/testsuite/integration/deployment/classloading/ear/DummyClassFileTransformer1";

    public static final String TRANSFORMER2_CLASS_NAME_CANONICAL = "org/jboss/as/testsuite/integration/deployment/classloading/ear/DummyClassFileTransformer2";

    @Deployment
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");
        war.addClasses(TestAA.class, DummyClassFileTransformer1.class, DummyClassFileTransformer2.class, WarJbossStructureClassFileTransformerTestCase.class);

        war.addAsManifestResource(new StringAsset(
                "<jboss-deployment-structure xmlns=\"urn:jboss:deployment-structure:1.0\">\n" +
                        "  <deployment>\n" +
                        "     <transformers>\n" +
                        "         <transformer class=\"" + DummyClassFileTransformer1.class.getName() + "\"/>\n" +
                        "         <transformer class=\"" + DummyClassFileTransformer2.class.getName() + "\"/>\n" +
                        "     </transformers>\n" +
                        "  </deployment>\n" +
                        "</jboss-deployment-structure>"), "jboss-deployment-structure.xml");
        return war;
    }

    @Test
    public void testTransformerApplied() throws ClassNotFoundException {
        loadClass(CLASS_NAME, getClass().getClassLoader());
        Assert.assertTrue(DummyClassFileTransformer1.wasActive);
        Assert.assertTrue(DummyClassFileTransformer2.wasActive);
        String canonicalClassName = CLASS_NAME.replace(".", "/");
        Assert.assertTrue(DummyClassFileTransformer1.transformedClassNames.contains(canonicalClassName));
        Assert.assertTrue(DummyClassFileTransformer2.transformedClassNames.contains(canonicalClassName));
        Assert.assertFalse(DummyClassFileTransformer1.transformedClassNames.contains(TRANSFORMER2_CLASS_NAME_CANONICAL));
        Assert.assertFalse(DummyClassFileTransformer2.transformedClassNames.contains(TRANSFORMER1_CLASS_NAME_CANONICAL));
    }

    private static Class<?> loadClass(String name, ClassLoader cl) throws ClassNotFoundException {
        if (cl != null) {
            return Class.forName(name, false, cl);
        } else
            return Class.forName(name);
    }
}
