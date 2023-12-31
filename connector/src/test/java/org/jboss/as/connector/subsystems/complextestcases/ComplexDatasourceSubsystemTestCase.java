/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.connector.subsystems.complextestcases;

import java.util.Properties;

import org.jboss.as.connector.subsystems.datasources.DataSourcesExtension;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="vrastsel@redhat.com">Vladimir Rastseluev</a>
 */
//@Ignore
public class ComplexDatasourceSubsystemTestCase extends AbstractComplexSubsystemTestCase {

    public ComplexDatasourceSubsystemTestCase() {
        super(DataSourcesExtension.SUBSYSTEM_NAME, new DataSourcesExtension());
    }

    @Test
    public void testDatasource() throws Exception{
        ModelNode model = getModel("datasource.xml",false,null);
       //Check model..
        final String complexDs = "complexDs";
        final String complexDsJndi = "java:jboss/datasources/" + complexDs;
        Properties params= ParseUtils.nonXaDsProperties(complexDsJndi);
        ModelNode modelDs=model.get("subsystem", "datasources","data-source",complexDs+"_Pool");
        ParseUtils.checkModelParams(modelDs, params);
        Assert.assertEquals(modelDs.asString(),"UTF-8",modelDs.get("connection-properties","char.encoding","value").asString());
        Assert.assertEquals(modelDs.asString(),"Property2",modelDs.get("valid-connection-checker-properties","name").asString());
        Assert.assertEquals(modelDs.asString(),"Property4",modelDs.get("exception-sorter-properties","name").asString());
        Assert.assertEquals(modelDs.asString(),"Property3",modelDs.get("stale-connection-checker-properties","name").asString());
        Assert.assertEquals(modelDs.asString(),"Property1",modelDs.get("reauth-plugin-properties","name").asString());

        final String complexXaDs = "complexXaDs";
        final String complexXaDsJndi = "java:jboss/xa-datasources/" + complexXaDs;
        params= ParseUtils.xaDsProperties(complexXaDsJndi);
        ModelNode modelXaDs=model.get("subsystem", "datasources","xa-data-source",complexXaDs+"_Pool");
        ParseUtils.checkModelParams(modelXaDs, params);
        Assert.assertEquals(modelXaDs.asString(),"jdbc:h2:mem:test",modelXaDs.get("xa-datasource-properties","URL","value").asString());
        Assert.assertEquals(modelXaDs.asString(),"Property2",modelXaDs.get("valid-connection-checker-properties","name").asString());
        Assert.assertEquals(modelXaDs.asString(),"Property4",modelXaDs.get("exception-sorter-properties","name").asString());
        Assert.assertEquals(modelXaDs.asString(),"Property3",modelXaDs.get("stale-connection-checker-properties","name").asString());
        Assert.assertEquals(modelXaDs.asString(),"Property1",modelXaDs.get("reauth-plugin-properties","name").asString());
        Assert.assertEquals(modelXaDs.asString(),"Property5",modelXaDs.get("recovery-plugin-properties","name").asString());
        Assert.assertEquals(modelXaDs.asString(),"Property6",modelXaDs.get("recovery-plugin-properties","name1").asString());


    }

    @Test
    public void testJDBCDriver() throws Exception{
        ModelNode model = getModel("datasource.xml",false,null);
        ModelNode h2MainModuleDriver = model.get("subsystem", "datasources", "jdbc-driver", "h2");
        ModelNode h2TestModuleDriver = model.get("subsystem", "datasources", "jdbc-driver", "h2test");
        Assert.assertEquals(h2MainModuleDriver.asString(),"com.h2database.h2",h2MainModuleDriver.get("driver-module-name").asString());
        Assert.assertEquals(h2MainModuleDriver.asString(),"org.h2.jdbcx.JdbcDataSource",h2MainModuleDriver.get("driver-xa-datasource-class-name").asString());
        Assert.assertFalse(h2MainModuleDriver.get("module-slot").isDefined());

        Assert.assertEquals(h2TestModuleDriver.asString(),"com.h2database.h2",h2TestModuleDriver.get("driver-module-name").asString());
        Assert.assertEquals(h2TestModuleDriver.asString(),"org.h2.jdbcx.JdbcDataSource",h2TestModuleDriver.get("driver-xa-datasource-class-name").asString());
        Assert.assertEquals(h2TestModuleDriver.asString(),"test",h2TestModuleDriver.get("module-slot").asString());
    }
}
