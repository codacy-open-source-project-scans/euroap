/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

import io.undertow.UndertowOptions;
import io.undertow.server.OpenListener;
import io.undertow.server.protocol.ajp.AjpOpenListener;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.network.NetworkUtils;
import org.jboss.msc.service.StartContext;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class AjpListenerService extends ListenerService {

    private volatile AcceptingChannel<StreamConnection> server;
    private final String scheme;
    private final PathAddress address;

    public AjpListenerService(Consumer<ListenerService> serviceConsumer, final PathAddress address, final String scheme, OptionMap listenerOptions, OptionMap socketOptions) {
        super(serviceConsumer, address.getLastElement().getValue(), listenerOptions, socketOptions, false);
        this.address = address;
        this.scheme = scheme;
    }

    @Override
    protected OpenListener createOpenListener() {
        AjpOpenListener ajpOpenListener = new AjpOpenListener(getBufferPool().get(), OptionMap.builder().addAll(commonOptions).addAll(listenerOptions).set(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, getUndertowService().isStatisticsEnabled()).getMap());
        ajpOpenListener.setScheme(scheme);
        return ajpOpenListener;
    }

    @Override
    void startListening(XnioWorker worker, InetSocketAddress socketAddress, ChannelListener<AcceptingChannel<StreamConnection>> acceptListener) throws IOException {
        server = worker.createStreamConnectionServer(socketAddress, acceptListener, OptionMap.builder().addAll(commonOptions).addAll(socketOptions).getMap());
        server.resumeAccepts();
        final InetSocketAddress boundAddress = server.getLocalAddress(InetSocketAddress.class);
        UndertowLogger.ROOT_LOGGER.listenerStarted("AJP", getName(), NetworkUtils.formatIPAddressForURI(boundAddress.getAddress()), boundAddress.getPort());
    }

    @Override
    protected void cleanFailedStart() {
        //noting to do
    }

    @Override
    void stopListening() {
        final InetSocketAddress boundAddress = server.getLocalAddress(InetSocketAddress.class);
        server.suspendAccepts();
        UndertowLogger.ROOT_LOGGER.listenerSuspend("AJP", getName());
        IoUtils.safeClose(server);
        UndertowLogger.ROOT_LOGGER.listenerStopped("AJP", getName(), NetworkUtils.formatIPAddressForURI(boundAddress.getAddress()), boundAddress.getPort());
    }

    @Override
    public AjpListenerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public boolean isSecure() {
        return scheme != null && scheme.equals("https");
    }

    @Override
    protected void preStart(final StartContext context) {

    }

    @Override
    public String getProtocol() {
        return "ajp";
    }
}
