/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.resourceadapters;

import java.util.List;
import java.util.Map;

import org.jboss.jca.common.api.metadata.common.TransactionSupportEnum;
import org.jboss.jca.common.api.metadata.resourceadapter.AdminObject;
import org.jboss.jca.common.api.metadata.resourceadapter.ConnectionDefinition;
import org.jboss.jca.common.api.metadata.resourceadapter.WorkManager;
import org.jboss.jca.common.metadata.resourceadapter.ActivationImpl;
import org.jboss.msc.service.ServiceName;


public class ModifiableResourceAdapter extends ActivationImpl {

    private volatile ServiceName raXmlDeploymentServiceName = null;

    public ModifiableResourceAdapter(String id, String archive, TransactionSupportEnum transactionSupport, List<ConnectionDefinition> connectionDefinitions,
                                     List<AdminObject> adminObjects, Map<String, String> configProperties, List<String> beanValidationGroups,
                                     String bootstrapContext, WorkManager workmanager) {
        super(id, archive, transactionSupport, connectionDefinitions, adminObjects, configProperties, beanValidationGroups, bootstrapContext, workmanager);
    }

    public synchronized void addConfigProperty(String name, String value) {
        configProperties.put(name, value);
    }

    public synchronized void addConnectionDefinition(ConnectionDefinition value) {
        connectionDefinitions.add(value);
    }

    public synchronized int connectionDefinitionSize() {
            return connectionDefinitions.size();
    }

    public synchronized void addAdminObject(AdminObject value) {
        adminObjects.add(value);
    }

    public synchronized int adminObjectSize() {
        return adminObjects.size();
    }

    public ServiceName getRaXmlDeploymentServiceName() {
        return raXmlDeploymentServiceName;
    }

    public void setRaXmlDeploymentServiceName(ServiceName raXmlDeploymentServiceName) {
        this.raXmlDeploymentServiceName = raXmlDeploymentServiceName;
    }

}

