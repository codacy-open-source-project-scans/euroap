/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.resourceadapters;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.core.api.workmanager.DistributedWorkManager;
import org.jboss.jca.core.api.workmanager.WorkManager;


public class WorkManagerRuntimeAttributeWriteHandler extends AbstractWriteAttributeHandler<Void> {

    private final WorkManager wm;
    private final boolean distributed;

    public WorkManagerRuntimeAttributeWriteHandler(final WorkManager wm, boolean distributed, final AttributeDefinition... definitions) {
        super(definitions);
        this.distributed = distributed;
        this.wm = wm;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> voidHandback) {
        switch (attributeName) {
            case ModelDescriptionConstants.STATISTICS_ENABLED: {
                if (distributed) {
                    ((DistributedWorkManager) wm).setDistributedStatisticsEnabled(resolvedValue.asBoolean());
                } else {
                    wm.setStatisticsEnabled(resolvedValue.asBoolean());
                }
                break;
            }
            case Constants.WORKMANAGER_STATISTICS_ENABLED_NAME: {
                wm.setStatisticsEnabled(resolvedValue.asBoolean());
                break;
            }
            case Constants.DISTRIBUTED_STATISTICS_ENABLED_NAME: {
                ((DistributedWorkManager) wm).setDistributedStatisticsEnabled(resolvedValue.asBoolean());
                break;
            }
            case Constants.DOWORK_DISTRIBUTION_ENABLED_NAME: {
                ((DistributedWorkManager) wm).setDoWorkDistributionEnabled(resolvedValue.asBoolean());
                break;
            }
            case Constants.STARTWORK_DISTRIBUTION_ENABLED_NAME: {
                ((DistributedWorkManager) wm).setStartWorkDistributionEnabled(resolvedValue.asBoolean());
                break;
            }
            case Constants.SCHEDULEWORK_DISTRIBUTION_ENABLED_NAME: {
                ((DistributedWorkManager) wm).setScheduleWorkDistributionEnabled(resolvedValue.asBoolean());
                break;
            }

        }
        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, Void handback) {
        // no-op
    }
}
