/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.ModelUtil;
import org.jboss.as.test.integration.management.util.SimpleServlet;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests both automated and manual configuration model persistence snapshot generation.
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class ModelPersistenceTestCase {

    enum Host { PRIMARY, SECONDARY }

    private class CfgFileDescription {

        CfgFileDescription(int version, File file, long hash) {
            this.version = version;
            this.file = file;
            this.hash = hash;
        }
        public int version;
        public File file;
        public long hash;
    }
    private static DomainTestSupport domainSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;
    private static final String DOMAIN_HISTORY_DIR = "domain_xml_history";
    private static final String HOST_HISTORY_DIR = "host_xml_history";
    private static final String CONFIG_DIR = "configuration";
    private static final String CURRENT_DIR = "current";
    private static final String PRIMARY_DIR = "primary";
    private static final String SECONDARY_DIR = "secondary";
    private static final String DOMAIN_NAME = "testing-domain-standard";
    private static final String PRIMARY_NAME = "testing-host-primary";
    private static final String SECONDARY_NAME = "testing-host-secondary";
    private static File domainCurrentCfgDir;
    private static File primaryCurrentCfgDir;
    private static File secondaryCurrentCfgDir;
    private static File domainLastCfgFile;
    private static File primaryLastCfgFile;
    private static File secondaryLastCfgFile;

    @BeforeClass
    public static void initDomain() throws Exception {
        domainSupport = DomainTestSuite.createSupport(ModelPersistenceTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = domainSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = domainSupport.getDomainSecondaryLifecycleUtil();

        File primaryDir = new File(domainSupport.getDomainPrimaryConfiguration().getDomainDirectory());
        File secondaryDir = new File(domainSupport.getDomainSecondaryConfiguration().getDomainDirectory());
        domainCurrentCfgDir = new File(primaryDir, CONFIG_DIR
                + File.separator + DOMAIN_HISTORY_DIR + File.separator + CURRENT_DIR);
        primaryCurrentCfgDir = new File(primaryDir,  CONFIG_DIR
                + File.separator + HOST_HISTORY_DIR + File.separator + CURRENT_DIR);
        secondaryCurrentCfgDir = new File(secondaryDir, CONFIG_DIR
                + File.separator + HOST_HISTORY_DIR + File.separator + CURRENT_DIR);
        domainLastCfgFile = new File(primaryDir, CONFIG_DIR + File.separator
                + DOMAIN_HISTORY_DIR + File.separator + DOMAIN_NAME + ".last.xml");
        primaryLastCfgFile = new File(primaryDir, CONFIG_DIR + File.separator
                + HOST_HISTORY_DIR + File.separator + PRIMARY_NAME + ".last.xml");
        secondaryLastCfgFile = new File(secondaryDir, CONFIG_DIR + File.separator
                + HOST_HISTORY_DIR + File.separator + SECONDARY_NAME + ".last.xml");
    }

    @AfterClass
    public static void shutdownDomain() {
        domainSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testSimpleDomainOperation() throws Exception {
        ModelNode op = ModelUtil.createOpNode("profile=default/subsystem=ee", WRITE_ATTRIBUTE_OPERATION);
        op.get(NAME).set("ear-subdeployments-isolated");
        op.get(VALUE).set(true);
        testDomainOperation(op);
        op.get(VALUE).set(false);
        testDomainOperation(op);
    }

    @Test
    public void testCompositeDomainOperation() throws Exception {
        ModelNode[] steps = new ModelNode[2];
        steps[0] = ModelUtil.createOpNode("profile=default/subsystem=ee", WRITE_ATTRIBUTE_OPERATION);
        steps[0].get(NAME).set("ear-subdeployments-isolated");
        steps[0].get(VALUE).set(true);

        steps[1] = ModelUtil.createOpNode("system-property=model-persistence-test", ADD);
        steps[1].get(VALUE).set("test");

        testDomainOperation(ModelUtil.createCompositeNode(steps));
        steps[0].get(VALUE).set(false);
        steps[1] = ModelUtil.createOpNode("system-property=model-persistence-test", REMOVE);
        testDomainOperation(ModelUtil.createCompositeNode(steps));
    }

    private void testDomainOperation(ModelNode operation) throws Exception {

        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();
        CfgFileDescription lastBackupDesc = getLatestBackup(domainCurrentCfgDir);
        CfgFileDescription lastPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
        CfgFileDescription lastSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);
        long lastFileHash = domainLastCfgFile.exists() ? FileUtils.checksumCRC32(domainLastCfgFile) : -1;

        // execute operation so the model gets updated
        executeOperation(client, operation);

        // check that the automated snapshot of the domain has been generated
        CfgFileDescription newBackupDesc = getLatestBackup(domainCurrentCfgDir);
        Assert.assertNotNull("Model snapshot not found.", newBackupDesc);
        // check that the version is incremented by one
        Assert.assertTrue(lastBackupDesc.version == newBackupDesc.version - 1);

        // check that the both primary and secondary host snapshot have not been generated
        CfgFileDescription newPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
        CfgFileDescription newSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);
        Assert.assertTrue(lastPrimaryBackupDesc.version == newPrimaryBackupDesc.version);
        Assert.assertTrue(lastSecondaryBackupDesc.version == newSecondaryBackupDesc.version);

        // check that the last cfg file has changed
        Assert.assertTrue(lastFileHash != FileUtils.checksumCRC32(domainLastCfgFile));
    }

    @Test
    public void testDomainOperationRollback() throws Exception {

        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        CfgFileDescription lastDomainBackupDesc = getLatestBackup(domainCurrentCfgDir);
        CfgFileDescription lastPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
        CfgFileDescription lastSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);

        // execute operation so the model gets updated
        ModelNode op = ModelUtil.createOpNode("system-property=model-persistence-test", "add");
        op.get(VALUE).set("test");
        executeAndRollbackOperation(client, op);

        // check that the model has not been updated
        CfgFileDescription newDomainBackupDesc = getLatestBackup(domainCurrentCfgDir);
        CfgFileDescription newPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
        CfgFileDescription newSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);

        // check that the configs did not change
        Assert.assertTrue(lastDomainBackupDesc.version == newDomainBackupDesc.version);
        Assert.assertTrue(lastPrimaryBackupDesc.version == newPrimaryBackupDesc.version);
        Assert.assertTrue(lastSecondaryBackupDesc.version == newSecondaryBackupDesc.version);
    }

    @Test
    public void testSimpleHostOperation() throws Exception {

        // using primary DC
        ModelNode op = ModelUtil.createOpNode("host=primary/system-property=model-persistence-test", ADD);
        op.get(VALUE).set("test");
        testHostOperation(op, Host.PRIMARY, Host.PRIMARY);
        op = ModelUtil.createOpNode("host=primary/system-property=model-persistence-test", REMOVE);
        testHostOperation(op, Host.PRIMARY, Host.PRIMARY);

        op = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", ADD);
        op.get(VALUE).set("test");
        testHostOperation(op, Host.PRIMARY, Host.SECONDARY);
        op = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", REMOVE);
        testHostOperation(op, Host.PRIMARY, Host.SECONDARY);

        // using secondary HC
        op = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", ADD);
        op.get(VALUE).set("test");
        testHostOperation(op, Host.SECONDARY, Host.SECONDARY);
        op = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", REMOVE);
        testHostOperation(op, Host.SECONDARY, Host.SECONDARY);
    }

    @Test
    public void testCompositeHostOperation() throws Exception {

        // test op on primary using primary controller
        ModelNode[] steps = new ModelNode[2];
        steps[0] = ModelUtil.createOpNode("host=primary/system-property=model-persistence-test", ADD);
        steps[0].get(VALUE).set("test");
        steps[1] = ModelUtil.createOpNode("host=primary/system-property=model-persistence-test", "write-attribute");
        steps[1].get(NAME).set("value");
        steps[1].get(VALUE).set("test2");
        testHostOperation(ModelUtil.createCompositeNode(steps),Host.PRIMARY, Host.PRIMARY);

        ModelNode op = ModelUtil.createOpNode("host=primary/system-property=model-persistence-test", REMOVE);
        testHostOperation(op,Host.PRIMARY,  Host.PRIMARY);

        // test op on secondary using primary controller
        steps[0] = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", ADD);
        steps[0].get(VALUE).set("test");
        steps[1] = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", "write-attribute");
        steps[1].get(NAME).set("value");
        steps[1].get(VALUE).set("test2");
        testHostOperation(ModelUtil.createCompositeNode(steps),Host.PRIMARY,  Host.SECONDARY);

        op = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", REMOVE);
        testHostOperation(op,Host.PRIMARY,  Host.SECONDARY);

        // test op on secondary using secondary controller
        steps[0] = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", ADD);
        steps[0].get(VALUE).set("test");
        steps[1] = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", "write-attribute");
        steps[1].get(NAME).set("value");
        steps[1].get(VALUE).set("test2");
        testHostOperation(ModelUtil.createCompositeNode(steps), Host.SECONDARY,  Host.SECONDARY);

        op = ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", REMOVE);
        testHostOperation(op, Host.SECONDARY,  Host.SECONDARY);

    }

    @Test
    public void testHostOperationRollback() throws Exception {

        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        for (Host host : Host.values()) {

            CfgFileDescription lastDomainBackupDesc = getLatestBackup(domainCurrentCfgDir);
            CfgFileDescription lastPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
            CfgFileDescription lastSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);

            // execute operation so the model gets updated
            ModelNode op = host.equals(Host.PRIMARY) ?
                    ModelUtil.createOpNode("host=primary/system-property=model-persistence-test", "add") :
                    ModelUtil.createOpNode("host=secondary/system-property=model-persistence-test", "add") ;
            op.get(VALUE).set("test");
            executeAndRollbackOperation(client, op);

            // check that the model has not been updated
            CfgFileDescription newDomainBackupDesc = getLatestBackup(domainCurrentCfgDir);
            CfgFileDescription newPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
            CfgFileDescription newSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);

            // check that the configs did not change
            Assert.assertTrue(lastDomainBackupDesc.version == newDomainBackupDesc.version);
            Assert.assertTrue(lastPrimaryBackupDesc.version == newPrimaryBackupDesc.version);
            Assert.assertTrue(lastSecondaryBackupDesc.version == newSecondaryBackupDesc.version);

        }
    }

    private void testHostOperation(ModelNode operation, Host controller, Host target) throws Exception {

        DomainClient client = controller.equals(Host.PRIMARY) ?
                domainPrimaryLifecycleUtil.getDomainClient() : domainSecondaryLifecycleUtil.getDomainClient();

        CfgFileDescription lastDomainBackupDesc = getLatestBackup(domainCurrentCfgDir);
        CfgFileDescription lastPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
        CfgFileDescription lastSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);
        long lastDomainFileHash = domainLastCfgFile.exists() ? FileUtils.checksumCRC32(domainLastCfgFile) : -1;
        long lastPrimaryFileHash = primaryLastCfgFile.exists() ? FileUtils.checksumCRC32(primaryLastCfgFile) : -1;
        long lastSecondaryFileHash = secondaryLastCfgFile.exists() ? FileUtils.checksumCRC32(secondaryLastCfgFile) : -1;

        // execute operation so the model gets updated
        executeOperation(client, operation);

        // check that the automated snapshot of the domain has not been generated
        CfgFileDescription newDomainBackupDesc = getLatestBackup(domainCurrentCfgDir);
        Assert.assertTrue(lastDomainBackupDesc.version == newDomainBackupDesc.version);

        // check that only the appropriate host snapshot has been generated
        CfgFileDescription newPrimaryBackupDesc = getLatestBackup(primaryCurrentCfgDir);
        CfgFileDescription newSecondaryBackupDesc = getLatestBackup(secondaryCurrentCfgDir);
        if (target == Host.PRIMARY) {
            Assert.assertTrue(lastPrimaryBackupDesc.version == newPrimaryBackupDesc.version - 1);
            Assert.assertTrue(lastSecondaryBackupDesc.version == newSecondaryBackupDesc.version);
            Assert.assertTrue(lastPrimaryFileHash != FileUtils.checksumCRC32(primaryLastCfgFile));
            Assert.assertTrue(lastSecondaryFileHash == FileUtils.checksumCRC32(secondaryLastCfgFile));
        } else {
            Assert.assertTrue(lastPrimaryBackupDesc.version == newPrimaryBackupDesc.version);
            Assert.assertTrue(lastSecondaryBackupDesc.version == newSecondaryBackupDesc.version - 1);
            Assert.assertTrue(lastPrimaryFileHash == FileUtils.checksumCRC32(primaryLastCfgFile));
            Assert.assertTrue(lastSecondaryFileHash != FileUtils.checksumCRC32(secondaryLastCfgFile));
        }
        Assert.assertTrue(lastDomainBackupDesc.version == newDomainBackupDesc.version);
        Assert.assertTrue(lastDomainFileHash == FileUtils.checksumCRC32(domainLastCfgFile));

    }

    @Test
    public void testTakeAndDeleteSnapshot() throws Exception {

        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();

        // take snapshot
        ModelNode op = ModelUtil.createOpNode(null, "take-snapshot");
        ModelNode result = executeOperation(client, op);

        // check that the snapshot file exists
        String snapshotFileName = result.asString();
        File snapshotFile = new File(snapshotFileName);
        Assert.assertTrue(snapshotFile.exists());

        // compare with current cfg
        long snapshotHash = FileUtils.checksumCRC32(snapshotFile);
        long lastHash = FileUtils.checksumCRC32(domainLastCfgFile);
        Assert.assertTrue(snapshotHash == lastHash);

        // delete snapshot
        op = ModelUtil.createOpNode(null, "delete-snapshot");
        op.get("name").set(snapshotFile.getName());
        executeOperation(client, op);

        // check that the file is deleted
        Assert.assertFalse("Snapshot file still exists.", snapshotFile.exists());


    }

    private CfgFileDescription getLatestBackup(File dir) throws IOException {

        int lastVersion = 0;
        File lastFile = null;

        File[] children;
        if (dir.isDirectory() && (children = dir.listFiles()) != null) {
            for (File file : children) {

                String fileName = file.getName();
                String[] nameParts = fileName.split("\\.");
                if (! (nameParts[0].contains(DOMAIN_NAME) || nameParts[0].contains(PRIMARY_NAME) || nameParts[0].contains(SECONDARY_NAME) )) {
                    continue;
                }
                if (!nameParts[2].equals("xml")) {
                    continue;
                }
                int version = Integer.valueOf(nameParts[1].substring(1));
                if (version > lastVersion) {
                    lastVersion = version;
                    lastFile = file;
                }
            }
        }
        return new CfgFileDescription(lastVersion, lastFile, (lastFile != null) ? FileUtils.checksumCRC32(lastFile) : 0);
    }

    protected ModelNode executeOperation(DomainClient client, final ModelNode op) throws IOException, MgmtOperationException {
        return executeOperation(client, op, true);
    }

    protected ModelNode executeOperation(DomainClient client, final ModelNode op, boolean unwrapResult) throws IOException, MgmtOperationException {
        ModelNode ret = client.execute(op);
        if (!unwrapResult) {
            return ret;
        }

        if (!SUCCESS.equals(ret.get(OUTCOME).asString())) {
            throw new MgmtOperationException("Management operation failed: " + ret.get(FAILURE_DESCRIPTION), op, ret);
        }
        return ret.get(RESULT);
    }

    protected void executeAndRollbackOperation(DomainClient client, final ModelNode op) throws IOException, OperationFormatException {

        ModelNode addDeploymentOp = ModelUtil.createOpNode("deployment=malformedDeployment.war", "add");
        addDeploymentOp.get("content").get(0).get("input-stream-index").set(0);

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName("deploy");
        builder.addNode("deployment", "malformedDeployment.war");


        ModelNode[] steps = new ModelNode[3];
        steps[0] = op;
        steps[1] = addDeploymentOp;
        steps[2] = builder.buildRequest();
        ModelNode compositeOp = ModelUtil.createCompositeNode(steps);

        OperationBuilder ob = new OperationBuilder(compositeOp, true);
        ob.addInputStream(new FileInputStream(getBrokenWar()));

        ModelNode ret =  client.execute(ob.build());
        Assert.assertFalse(SUCCESS.equals(ret.get(OUTCOME).asString()));
    }

    private static File getBrokenWar() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "malformedDeployment.war");
        war.addClass(SimpleServlet.class);
        war.addAsWebInfResource(new StringAsset("Malformed"), "web.xml");
        File brokenWar = new File(System.getProperty("java.io.tmpdir") + File.separator + "malformedDeployment.war");
        brokenWar.deleteOnExit();
        new ZipExporterImpl(war).exportTo(brokenWar, true);
        return brokenWar;
    }
}
