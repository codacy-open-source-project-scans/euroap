/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.jsp;

import org.hamcrest.MatcherAssert;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.hamcrest.CoreMatchers;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.integration.security.common.AbstractSystemPropertiesServerSetupTask;

/**
 * Tests EL evaluation in JSPs
 *
 * @author rmartinc
 */
@RunWith(Arquillian.class)
@ServerSetup({JspELDisableImportedClassELResolverTestCase.SystemPropertiesSetup.class})
@RunAsClient
public class JspELDisableImportedClassELResolverTestCase {

    /**
     * Setup the system property to disable the disableImportedClassELResolver
     * and act exactly as the specification says delegating in .
     */
    static class SystemPropertiesSetup extends AbstractSystemPropertiesServerSetupTask {

        @Override
        protected SystemProperty[] getSystemProperties() {
            return new SystemProperty[] {
                new DefaultSystemProperty("org.wildfly.extension.undertow.deployment.disableImportedClassELResolver", "true")
            };
        }
    }

    static final String POSSIBLE_ISSUES_LINKS = "Might be caused by: https://issues.jboss.org/browse/WFLY-6939 or" +
            " https://issues.jboss.org/browse/WFLY-11065 or https://issues.jboss.org/browse/WFLY-11086 or" +
            " https://issues.jboss.org/browse/WFLY-12650";

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(DummyConstants.class, DummyEnum.class, DummyBean.class)
                .addAsWebResource(JspELDisableImportedClassELResolverTestCase.class.getResource("jsp-with-el-static-class.jsp"), "index.jsp");
    }

    /**
     * Test that for web application using default version of servlet spec, EL expressions that use implicitly imported
     * classes from <code>java.lang</code> package are evaluated correctly, and the bean is resolved OK as per specification.
     *
     * @param url
     * @throws Exception
     */
    @Test
    public void testStaticImportSameName(@ArquillianResource URL url) throws Exception {
        commonTestPart(url, POSSIBLE_ISSUES_LINKS);
        commonTestPart(url, POSSIBLE_ISSUES_LINKS);
    }

    private void commonTestPart(final URL url, final String possibleCausingIssues) throws Exception {
        final String responseBody = HttpRequest.get(url + "index.jsp", 10, TimeUnit.SECONDS);
        MatcherAssert.assertThat("Unexpected EL evaluation for ${Boolean.TRUE}; " + possibleCausingIssues, responseBody,
                CoreMatchers.containsString("Boolean.TRUE: --- " + Boolean.TRUE + " ---"));
        MatcherAssert.assertThat("Unexpected EL evaluation for ${Integer.MAX_VALUE}; " + possibleCausingIssues, responseBody,
                CoreMatchers.containsString("Integer.MAX_VALUE: --- " + Integer.MAX_VALUE + " ---"));
        MatcherAssert.assertThat("Unexpected EL evaluation for ${DummyConstants.FOO};  " + possibleCausingIssues, responseBody,
                CoreMatchers.containsString("DummyConstants.FOO: --- " + DummyConstants.FOO + " ---"));
        MatcherAssert.assertThat("Unexpected EL evaluation for ${DummyEnum.VALUE}; " + possibleCausingIssues, responseBody,
                CoreMatchers.containsString("DummyEnum.VALUE: --- " + DummyEnum.VALUE + " ---"));
        MatcherAssert.assertThat("Unexpected EL evaluation for ${DummyBean.test}; " + possibleCausingIssues, responseBody,
                CoreMatchers.containsString("DummyBean.test: --- " + DummyBean.DEFAULT_VALUE + " ---"));
    }
}
