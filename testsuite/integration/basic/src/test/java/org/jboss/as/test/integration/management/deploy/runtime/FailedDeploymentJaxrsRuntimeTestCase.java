/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.deploy.runtime;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBDEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.integration.management.deploy.runtime.jaxrs.HelloApplication;
import org.jboss.as.test.integration.management.deploy.runtime.jaxrs.HelloResource;
import org.jboss.as.test.integration.management.deploy.runtime.jaxrs.PureProxyApiService;
import org.jboss.as.test.integration.management.deploy.runtime.jaxrs.PureProxyEndPoint;
import org.jboss.as.test.integration.management.deploy.runtime.jaxrs.SubHelloResource;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class FailedDeploymentJaxrsRuntimeTestCase extends AbstractFailedDeploymentRuntimeTestCase {

    private static final String DEPLOYMENT_NAME = "failed-jaxrs.ear";
    private static final String SUBDEPLOYMENT_NAME = "failed-jaxrs.war";

    @BeforeClass
    public static void setup() throws Exception {
        WebArchive war = ShrinkWrap.create(WebArchive.class, SUBDEPLOYMENT_NAME);
        war.addClass(HelloApplication.class);
        war.addClass(HelloResource.class);
        war.addClass(SubHelloResource.class);
        war.addClass(PureProxyApiService.class);
        war.addClass(PureProxyEndPoint.class);
        setup(DEPLOYMENT_NAME, war);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tearDown(DEPLOYMENT_NAME);
    }

    @Override
    void validateReadResourceResponse(ModelNode response) {
        Assert.assertTrue(response.toString(), response.hasDefined(RESULT, SUBDEPLOYMENT, getSubdeploymentName(),
                SUBSYSTEM, "jaxrs", "rest-resource", HelloResource.class.getCanonicalName()));
    }

    @Override
    String getDeploymentName() {
        return DEPLOYMENT_NAME;
    }

    @Override
    String getSubdeploymentName() {
        return SUBDEPLOYMENT_NAME;
    }
}
