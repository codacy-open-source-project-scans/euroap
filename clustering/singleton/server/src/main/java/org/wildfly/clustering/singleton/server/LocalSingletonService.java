/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.singleton.server;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.clustering.group.Group;
import org.wildfly.clustering.group.Node;
import org.wildfly.clustering.singleton.SingletonElectionListener;
import org.wildfly.clustering.singleton.service.SingletonService;

/**
 * Local {@link SingletonService} implementation created using JBoss MSC 1.4.x service installation.
 * @author Paul Ferraro
 */
public class LocalSingletonService implements SingletonService {

    private final Service service;
    private final Supplier<Group> group;
    private final SingletonElectionListener listener;

    public LocalSingletonService(Service service, LocalSingletonServiceContext context) {
        this.service = service;
        this.group = context.getGroup();
        this.listener = context.getElectionListener();
    }

    @Override
    public void start(StartContext context) throws StartException {
        this.service.start(context);
        Node localMember = this.group.get().getLocalMember();
        try {
            this.listener.elected(Collections.singletonList(localMember), localMember);
        } catch (Throwable e) {
            SingletonLogger.ROOT_LOGGER.warn(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void stop(StopContext context) {
        this.service.stop(context);
    }

    @Override
    public boolean isPrimary() {
        // A local singleton is always primary
        return true;
    }

    @Override
    public Node getPrimaryProvider() {
        return this.group.get().getLocalMember();
    }

    @Override
    public Set<Node> getProviders() {
        return Collections.singleton(this.group.get().getLocalMember());
    }
}
