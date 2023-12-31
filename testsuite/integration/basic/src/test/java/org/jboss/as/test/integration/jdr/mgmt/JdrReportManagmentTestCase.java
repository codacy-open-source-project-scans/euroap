/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jdr.mgmt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the JDR Report subsystem management interfaces.
 *
 * @author Mike M. Clark
 */
@RunAsClient
@RunWith(Arquillian.class)
public class JdrReportManagmentTestCase {

    @ContainerResource
    private ManagementClient managementClient;

    @Test
    public void generateStandaloneJdrReport() throws Exception {
        // Create the generate-jdr-report operation
        final ModelNode address = new ModelNode();
        address.add("subsystem", "jdr");
        ModelNode operation = Util.getEmptyOperation("generate-jdr-report", address);

        // Execute generate-jdr-report operation
        ModelNode response = managementClient.getControllerClient().execute(operation);
        String outcome = response.get("outcome").asString();
        Assert.assertEquals("JDR Generation failed. Failed response: " + response.asString(), "success", outcome);

        ModelNode result = response.get("result");
        validateJdrTimeStamps(result);

        String location = result.get("report-location").asString();
        Assert.assertNotNull("JDR report location was null", location);

        // Validate report itself.
        File reportFile = new File(location);
        assertTrue("JDR report missing, not located at " + location, reportFile.exists());
        validateJdrReportContents(reportFile);

        // Clean up report file
        reportFile.delete();
    }

    /**
     * Tests if jdr.* script files are present in the WFLY_HOME/bin directory.
     * The default server copy, see ts.copy-wildfly, does not copy these resources, so we only
     * execute this test when we are testing galleon layers, hence ts.layers enabled.
     */
    @Test
    public void ensureShellScriptExists() {
        Assume.assumeTrue("This test is only executed if we are testing Galleon Layers", Boolean.getBoolean("ts.layers"));

        Path binPath = Paths.get(System.getProperty("jboss.home"), "bin");

        assertTrue("jdr.sh was not found in " + binPath.toString(), binPath.resolve("jdr.sh").toFile().exists());
        assertTrue("jdr.bat was not found in " + binPath.toString(), binPath.resolve("jdr.bat").toFile().exists());
        assertTrue("jdr.ps1 was not found in " + binPath.toString(), binPath.resolve("jdr.ps1").toFile().exists());
    }

    private void validateJdrReportContents(File reportFile) {
        String reportName = reportFile.getName().replace(".zip", "");

        ZipFile reportZip = null;
        try {
            reportZip = new ZipFile(reportFile);
            Enumeration<ZipEntry> e = (Enumeration<ZipEntry>) reportZip.entries();
            Set<String> fileNames = new HashSet<>();
            while (e.hasMoreElements()) {
                String fileName = e.nextElement().toString();
                fileNames.add(fileName);
            }
            validateReportEntries(fileNames, reportName, reportZip);
        } catch (Exception e) {
            throw new RuntimeException("Unable to validate JDR report: " + reportFile.getName(), e);
        } finally {
            if (reportZip != null) {
                try {
                    reportZip.close();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to close JDR report: " + reportFile.getName(), e);
                }
            }
        }
    }

    private void validateReportEntries(Set<String> fileNames, String reportName, ZipFile reportZip) throws IOException {
        ZipEntry zipENtry = validateEntryNotEmpty("product.txt", fileNames, reportName, reportZip);
        String productName = readProductDirectory(reportZip.getInputStream(zipENtry));

        validateEntryNotEmpty("version.txt", fileNames, reportName, reportZip);
        validateEntryNotEmpty("JBOSS_HOME/standalone/configuration/standalone(.*).xml$", "JBOSS_HOME\\\\standalone\\\\configuration\\\\standalone(.*).xml$",
                fileNames, reportName, reportZip);

        validateEntryNotEmpty("sos_strings/" + productName + "/tree.txt", fileNames, reportName, reportZip);
        validateEntryNotEmpty("sos_strings/" + productName + "/jarcheck.txt", fileNames,
                reportName, reportZip);
        validateEntryNotEmpty("sos_strings/" + productName + "/dump-services.json", fileNames,
                reportName, reportZip);
        validateEntryNotEmpty("sos_strings/" + productName + "/configuration.json", fileNames,
                reportName, reportZip);
        validateEntryNotEmpty("sos_strings/" + productName + "/cluster-proxies-configuration.json", fileNames,
                reportName, reportZip);
        validateEntryPresent("sos_strings/" + productName + "/deployment-dependencies.txt", fileNames,
                reportName, reportZip);
        validateEntryNotEmpty("sos_strings/" + productName + "/jndi-view.json", fileNames,
                reportName, reportZip);
        validateEntryNotEmpty("sos_strings/" + productName + "/local-module-dependencies.txt", fileNames,
                reportName, reportZip);
        validateEntryNotEmpty("sos_strings/" + productName + "/system-properties.txt", fileNames,
                reportName, reportZip);
        validateEmptyEntry("sos_logs/skips.log", fileNames, reportName, reportZip);
    }

    /**
     * Check if entry (reportname/filename) is presented in reportZip file and is not empty
     *
     * @param fileName   Name of file inside report
     * @param reportZip  Report zip file
     * @param reportName Report root folder name
     * @return The entry found
     */
    private ZipEntry validateEntryNotEmpty(String fileName, Set<String> fileNames,
                                       String reportName, ZipFile reportZip) {
       return validateEntryNotEmpty(fileName, null, fileNames, reportName, reportZip);
    }

    /**
     * Check if entry (reportname/filename) is presented in reportZip file and is not empty
     *
     * @param fileName         Name of file inside report
     * @param fileNameOptional Optional name of file inside report
     * @param reportZip        Report zip file
     * @param reportName       Report root folder name
     * @return The entry found
     */
    private ZipEntry validateEntryNotEmpty(String fileName, String fileNameOptional, Set<String> fileNames,
                                       String reportName, ZipFile reportZip) {
        ZipEntry zipENtry = getZipEntry(reportZip, fileName, fileNameOptional, fileNames, reportName);
        assertTrue("Report entry " + fileName + " was empty or could not be determined", zipENtry.getSize() > 0);

        return zipENtry;
    }

    /**
     * Check if entry (reportname/filename) is presented in reportZip file, either empty or not empty.
     *
     * @param fileName   Name of file inside report
     * @param reportZip  Report zip file
     * @param reportName Report root folder name
     */
    private void validateEntryPresent(String fileName, Set<String> fileNames,
                                       String reportName, ZipFile reportZip) {
        ZipEntry zipENtry = getZipEntry(reportZip, fileName, null, fileNames, reportName);
        assertNotNull("Report entry " + fileName + " was not present", zipENtry);
    }

    private ZipEntry getZipEntry(ZipFile reportZip, String fileName, String fileNameOptional, Set<String> fileNames, String reportName) {
        String entryInZip = reportName + "/" + fileName;

        Pattern p = Pattern.compile(entryInZip);
        Optional<String> realFileName = fileNames.stream().filter(name -> p.matcher(name).find()).findFirst();
        if (!realFileName.isPresent() && fileNameOptional != null) {
            entryInZip = reportName + "/" + fileNameOptional;
            Pattern p2 = Pattern.compile(entryInZip);
            realFileName = fileNames.stream().filter(name -> p2.matcher(name).find()).findFirst();
        }

        assertTrue("Report entry " + fileName + " missing from JDR report "
                + reportName, realFileName.isPresent());
        return reportZip.getEntry(realFileName.get());
    }

    private void validateJdrTimeStamps(ModelNode result) {
        // TODO: Validate time structures beyond just not null.
        Assert.assertNotNull("JDR start time was null.", result.get("start-time").asString());
        Assert.assertNotNull("JDR end time was null.", result.get("end-time").asString());
    }

    /**
     * Check if entry (reportname/filename) is presented in reportZip file and is empty
     *
     * @param fileName   Name of file inside report
     * @param reportZip  Report zip file
     * @param reportName Report root folder name
     */
    private void validateEmptyEntry(String fileName, Set<String> fileNames, String reportName, ZipFile reportZip) {
        ZipEntry zipEntry = getZipEntry(reportZip, fileName, null, fileNames, reportName);
        assertFalse("Report entry " + fileName + " should be empty", zipEntry.getSize() > 0);
    }

    private String readProductDirectory(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(in, Charset.forName(StandardCharsets.UTF_8.name())))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        return sb.toString();
    }
}
