/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.singleton.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.clustering.service.AsyncServiceConfigurator;
import org.wildfly.clustering.service.Builder;

/**
 * Builder for asynchronously started/stopped services.
 * @author Paul Ferraro
 * @param <T> the type of value provided by services built by this builder
 * @deprecated Replaced by {@link AsyncServiceConfigurator}.
 */
@Deprecated
public class AsynchronousServiceBuilder<T> implements Builder<T>, Service<T>, Supplier<Service<T>> {

    private static final ServiceName EXECUTOR_SERVICE_NAME = ServiceName.JBOSS.append("as", "server-executor");

    private final InjectedValue<ExecutorService> executor = new InjectedValue<>();
    private final Service<T> service;
    private final ServiceName name;
    private volatile boolean startAsynchronously = true;
    private volatile boolean stopAsynchronously = true;

    /**
     * Constructs a new builder for building asynchronous service
     * @param name the target service name
     * @param service the target service
     */
    public AsynchronousServiceBuilder(ServiceName name, Service<T> service) {
        this.name = name;
        this.service = service;
    }

    @Override
    public ServiceName getServiceName() {
        return this.name;
    }

    @Override
    public ServiceBuilder<T> build(ServiceTarget target) {
        return target.addService(this.name, this).addDependency(EXECUTOR_SERVICE_NAME, ExecutorService.class, this.executor);
    }

    /**
     * Return the underlying service for this builder
     * @return an MSC service
     */
    @Override
    public Service<T> get() {
        return this.service;
    }

    /**
     * Indicates that this service should *not* be started asynchronously.
     * @return a reference to this builder
     */
    public AsynchronousServiceBuilder<T> startSynchronously() {
        this.startAsynchronously = false;
        return this;
    }

    /**
     * Indicates that this service should *not* be stopped asynchronously.
     * @return a reference to this builder
     */
    public AsynchronousServiceBuilder<T> stopSynchronously() {
        this.stopAsynchronously = false;
        return this;
    }

    @Override
    public T getValue() {
        return this.service.getValue();
    }

    @Override
    public void start(final StartContext context) throws StartException {
        if (this.startAsynchronously) {
            Runnable task = () -> {
                try {
                    this.service.start(context);
                    context.complete();
                } catch (StartException e) {
                    context.failed(e);
                } catch (Throwable e) {
                    context.failed(new StartException(e));
                }
            };
            try {
                this.executor.getValue().execute(task);
            } catch (RejectedExecutionException e) {
                task.run();
            } finally {
                context.asynchronous();
            }
        } else {
            this.service.start(context);
        }
    }

    @Override
    public void stop(final StopContext context) {
        if (this.stopAsynchronously) {
            Runnable task = () -> {
                try {
                    this.service.stop(context);
                } finally {
                    context.complete();
                }
            };
            try {
                this.executor.getValue().execute(task);
            } catch (RejectedExecutionException e) {
                task.run();
            } finally {
                context.asynchronous();
            }
        } else {
            this.service.stop(context);
        }
    }
}
