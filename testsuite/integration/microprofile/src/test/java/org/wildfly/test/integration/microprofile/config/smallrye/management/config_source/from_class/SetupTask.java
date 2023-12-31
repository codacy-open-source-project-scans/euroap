/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.microprofile.config.smallrye.management.config_source.from_class;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * Add a config-source with a custom class in the microprofile-config subsystem.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2017 Red Hat inc.
 */
public class SetupTask implements ServerSetupTask {

    private static final String TEST_MODULE_NAME = "test.custom-config-source";
    private static final String TEST_MODULE_NAME_SERVICE_LOADER = TEST_MODULE_NAME + "-service-loader";

    private static Set<TestModule> testModules = new HashSet<>();

    @Override
    public void setup(ManagementClient managementClient, String s) throws Exception {
        URL url = ConfigSourceFromClassTestCase.class.getResource("module.xml");
        File moduleXmlFile = new File(url.toURI());
        TestModule testModule = new TestModule(TEST_MODULE_NAME, moduleXmlFile);
        testModule.addResource("config-source.jar")
                .addClass(CustomConfigSource.class);
        testModule.create();
        addConfigSource(managementClient.getControllerClient());
        testModules.add(testModule);

        final File archiveDir = new File("target/archives");
        archiveDir.mkdirs();
        File moduleFile = new File(archiveDir, "config-source-service-loader.jar");
        JavaArchive configSourceServiceLoad = ShrinkWrap.create(JavaArchive.class, "config-source-service-loader.jar")
                .addClass(CustomConfigSourceServiceLoader.class)
                .addClass(CustomConfigSourceAServiceLoader.class)
                .addAsServiceProvider(ConfigSource.class,
                        CustomConfigSourceAServiceLoader.class, CustomConfigSourceServiceLoader.class);
        try(FileOutputStream target = new FileOutputStream(moduleFile)) {
            configSourceServiceLoad.as(ZipExporter.class).exportTo(target);
        }
        url = ConfigSourceFromClassTestCase.class.getResource("module_service_loader.xml");
        moduleXmlFile = new File(url.toURI());
        testModule = new TestModule(TEST_MODULE_NAME_SERVICE_LOADER, moduleXmlFile);
        testModule.addJavaArchive(moduleFile);
        testModule.create();
        testModules.add(testModule);
        ServerReload.reloadIfRequired(managementClient);
    }

    @Override
    public void tearDown(ManagementClient managementClient, String s) throws Exception {
        removeConfigSource(managementClient.getControllerClient());
        for (TestModule testModule : testModules) {
            testModule.remove();
        }
        final File archiveDir = new File("target/archives");
        cleanFile(archiveDir);
        ServerReload.reloadIfRequired(managementClient);
    }

    private void addConfigSource(ModelControllerClient client) throws IOException {
        ModelNode op;
        op = new ModelNode();
        op.get(OP_ADDR).add(SUBSYSTEM, "microprofile-config-smallrye");
        op.get(OP_ADDR).add("config-source", "cs-from-class");
        op.get(OP).set(ADD);
        op.get("class").get("module").set(TEST_MODULE_NAME);
        op.get("class").get("name").set(CustomConfigSource.class.getName());
        client.execute(op);
    }

    private void removeConfigSource(ModelControllerClient client) throws IOException {
        ModelNode op;
        op = new ModelNode();
        op.get(OP_ADDR).add(SUBSYSTEM, "microprofile-config-smallrye");
        op.get(OP_ADDR).add("config-source", "cs-from-class");
        op.get(OP).set(REMOVE);
        client.execute(op);
    }

    private static void cleanFile(File toClean) {
        if (toClean.exists()) {
            if (toClean.isDirectory()) {
                for (File child : toClean.listFiles()) {
                    cleanFile(child);
                }
            }
            toClean.delete();
        }
    }

}
