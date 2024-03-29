/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jca.moduledeployment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import jakarta.resource.spi.ActivationSpec;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.test.integration.jca.beanvalidation.NegativeValidationTestCase;
import org.jboss.as.test.integration.jca.beanvalidation.ra.ValidConnectionFactory;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.core.spi.mdr.MetadataRepository;
import org.jboss.jca.core.spi.rar.Endpoint;
import org.jboss.jca.core.spi.rar.MessageListener;
import org.jboss.jca.core.spi.rar.ResourceAdapterRepository;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * AS7-5768 -Support for RA module deployment
 *
 * @author <a href="vrastsel@redhat.com">Vladimir Rastseluev</a>
 *         <p>
 *         Tests for module deployment of resource adapter archive in
 *         uncompressed form with classes in flat form (under package structure)
 *         <p>
 *         Structure of module is:
 *         modulename
 *         modulename/main
 *         modulename/main/module.xml
 *         modulename/main/META-INF
 *         modulename/main/META-INF/ra.xml
 *         modulename/main/org
 *         modulename/main/org/jboss/
 *         modulename/main/org/jboss/package/
 *         modulename/main/org/jboss/package/First.class
 *         modulename/main/org/jboss/package/Second.class ...
 */
@RunWith(Arquillian.class)
@ServerSetup(InflowFlatTestCase.ModuleAcDeploymentTestCaseSetup.class)
public class InflowFlatTestCase extends AbstractModuleDeploymentTestCase {

    static class ModuleAcDeploymentTestCaseSetup extends
            AbstractModuleDeploymentTestCaseSetup {

        @Override
        public void doSetup(ManagementClient managementClient) throws Exception {

            super.doSetup(managementClient);
            fillModuleWithFlatClasses("ra3.xml");
            setConfiguration("inflow.xml");

        }

        @Override
        protected String getSlot() {
            return InflowFlatTestCase.class.getSimpleName().toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * Define the deployment
     *
     * @return The deployment archive
     */
    @Deployment
    public static Archive<?> createRarDeployment() throws Exception {
        JavaArchive ja = createDeployment(false);

        ResourceAdapterArchive raa = ShrinkWrap.create(
                ResourceAdapterArchive.class, "inflow.rar");
        ja.addPackage(ValidConnectionFactory.class.getPackage());
        raa.addAsLibrary(ja);
        raa.addAsManifestResource(
                NegativeValidationTestCase.class.getPackage(), "ra.xml",
                "ra.xml")
                .addAsManifestResource(
                        new StringAsset(
                                "Dependencies: javax.inject.api,org.jboss.as.connector,org.jboss.as.controller-client,org.jboss.dmr\n"),
                        "MANIFEST.MF");

        return raa;
    }

    @ArquillianResource
    ServiceContainer serviceContainer;

    /**
     * Test configuration
     *
     * @throws Throwable Thrown if case of an error
     */
    @Test
    public void testRegistryConfiguration() throws Throwable {
        assertNotNull(serviceContainer);
        ServiceController<?> controller = serviceContainer
                .getService(ConnectorServices.RA_REPOSITORY_SERVICE);
        assertNotNull(controller);
        ResourceAdapterRepository repository = (ResourceAdapterRepository) controller
                .getValue();
        assertNotNull(repository);
        Set<String> ids = repository.getResourceAdapters();

        assertNotNull(ids);

        String piId = getElementContaining(ids, "MultipleResourceAdapter");
        assertNotNull(piId);

        Endpoint endpoint = repository.getEndpoint(piId);
        assertNotNull(endpoint);

        List<MessageListener> listeners = repository.getMessageListeners(piId);
        assertNotNull(listeners);
        assertEquals(1, listeners.size());

        MessageListener listener = listeners.get(0);

        ActivationSpec as = listener.getActivation().createInstance();
        assertNotNull(as);
        assertNotNull(as.getResourceAdapter());
    }

    /**
     * Tests metadata configuration
     *
     * @throws Throwable
     */
    @Test
    public void testMetadataConfiguration() throws Throwable {
        ServiceController<?> controller = serviceContainer
                .getService(ConnectorServices.IRONJACAMAR_MDR);
        assertNotNull(controller);
        MetadataRepository repository = (MetadataRepository) controller
                .getValue();
        assertNotNull(repository);
        Set<String> ids = repository.getResourceAdapters();

        assertNotNull(ids);

        String piId = getElementContaining(ids, "inflow2");
        assertNotNull(piId);
        assertNotNull(repository.getResourceAdapter(piId));
    }

    @Override
    protected ModelNode getAddress() {
        return ModuleAcDeploymentTestCaseSetup.getAddress();
    }

}
