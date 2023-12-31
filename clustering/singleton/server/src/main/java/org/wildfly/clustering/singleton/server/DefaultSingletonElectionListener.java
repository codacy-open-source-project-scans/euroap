/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.singleton.server;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.jboss.msc.service.ServiceName;
import org.wildfly.clustering.group.Group;
import org.wildfly.clustering.group.Node;
import org.wildfly.clustering.singleton.SingletonElectionListener;

/**
 * Default singleton election listener that logs the results of the singleton election.
 * @author Paul Ferraro
 */
public class DefaultSingletonElectionListener implements SingletonElectionListener {

    private final ServiceName name;
    private final Supplier<Group> group;
    private final AtomicReference<Node> primaryMember = new AtomicReference<>();

    public DefaultSingletonElectionListener(ServiceName name, Supplier<Group> group) {
        this.name = name;
        this.group = group;
    }

    @Override
    public void elected(List<Node> candidateMembers, Node electedMember) {
        Node localMember = this.group.get().getLocalMember();
        Node previousElectedMember = this.primaryMember.getAndSet(electedMember);

        if (electedMember != null) {
            SingletonLogger.ROOT_LOGGER.elected(electedMember.getName(), this.name.getCanonicalName());
        } else {
            SingletonLogger.ROOT_LOGGER.noPrimaryElected(this.name.getCanonicalName());
        }
        if (localMember.equals(electedMember)) {
            SingletonLogger.ROOT_LOGGER.startSingleton(this.name.getCanonicalName());
        } else if (localMember.equals(previousElectedMember)) {
            SingletonLogger.ROOT_LOGGER.stopSingleton(this.name.getCanonicalName());
        }
    }
}
