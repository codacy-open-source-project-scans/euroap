/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import io.undertow.UndertowOptions;
import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.OpenListener;
import io.undertow.server.protocol.http.AlpnOpenListener;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.server.protocol.http2.Http2OpenListener;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.network.NetworkUtils;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.security.ssl.CipherSuiteSelector;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

/**
 * An extension of {@see HttpListenerService} to add SSL.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class HttpsListenerService extends HttpListenerService {

    private Supplier<SSLContext> sslContextSupplier;
    private volatile AcceptingChannel<SslConnection> sslServer;
    static final String PROTOCOL = "https";
    private final String cipherSuites;
    private final boolean proxyProtocol;

    public HttpsListenerService(final Consumer<ListenerService> serviceConsumer, final PathAddress address, String serverName, OptionMap listenerOptions, String cipherSuites, OptionMap socketOptions, boolean proxyProtocol) {
        this(serviceConsumer, address, serverName, listenerOptions, cipherSuites, socketOptions, false, false, proxyProtocol);
    }

    HttpsListenerService(final Consumer<ListenerService> serviceConsumer, final PathAddress address, String serverName, OptionMap listenerOptions, String cipherSuites, OptionMap socketOptions, boolean certificateForwarding, boolean proxyAddressForwarding, boolean proxyProtocol) {
        super(serviceConsumer, address, serverName, listenerOptions, socketOptions, certificateForwarding, proxyAddressForwarding, proxyProtocol);
        this.cipherSuites = cipherSuites;
        this.proxyProtocol = proxyProtocol;
    }

    void setSSLContextSupplier(Supplier<SSLContext> sslContextSupplier) {
        this.sslContextSupplier = sslContextSupplier;
    }

    @Override
    protected UndertowXnioSsl getSsl() {
        SSLContext sslContext = sslContextSupplier.get();
        OptionMap combined = getSSLOptions(sslContext);

        return new UndertowXnioSsl(worker.get().getXnio(), combined, sslContext);
    }

    protected OptionMap getSSLOptions(SSLContext sslContext) {
        Builder builder = OptionMap.builder().addAll(commonOptions);
        builder.addAll(socketOptions);
        builder.set(Options.USE_DIRECT_BUFFERS, true);

        if (cipherSuites != null) {
            String[] cipherList = CipherSuiteSelector.fromString(cipherSuites).evaluate(sslContext.getSupportedSSLParameters().getCipherSuites());
            builder.setSequence((Option<Sequence<String>>) HttpsListenerResourceDefinition.ENABLED_CIPHER_SUITES.getOption(), cipherList);
        }

        return builder.getMap();
    }

    @Override
    protected OpenListener createOpenListener() {
        if(listenerOptions.get(UndertowOptions.ENABLE_HTTP2, false)) {
            try {
                return createAlpnOpenListener();
            } catch (Throwable e) {
                UndertowLogger.ROOT_LOGGER.alpnNotFound(getName());
                UndertowLogger.ROOT_LOGGER.debug("Exception creating ALPN listener", e);
                return super.createOpenListener();
            }
        } else {
            return super.createOpenListener();
        }
    }

    private OpenListener createAlpnOpenListener() {
        OptionMap undertowOptions = OptionMap.builder().addAll(commonOptions).addAll(listenerOptions).set(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, getUndertowService().isStatisticsEnabled()).getMap();
        ByteBufferPool bufferPool = getBufferPool().get();
        HttpOpenListener http =  new HttpOpenListener(bufferPool, undertowOptions);
        AlpnOpenListener alpn = new AlpnOpenListener(bufferPool, undertowOptions, http);

        if(listenerOptions.get(UndertowOptions.ENABLE_HTTP2, false)) {
            Http2OpenListener http2 = new Http2OpenListener(bufferPool, undertowOptions, "h2");
            alpn.addProtocol(Http2OpenListener.HTTP2, http2, 10);
            Http2OpenListener http2_14 = new Http2OpenListener(bufferPool, undertowOptions, "h2-14");
            alpn.addProtocol(Http2OpenListener.HTTP2_14, http2_14, 9);
        }
        return alpn;
    }



    @Override
    protected void startListening(XnioWorker worker, InetSocketAddress socketAddress, ChannelListener<AcceptingChannel<StreamConnection>> acceptListener) throws IOException {
        if(proxyProtocol) {
            sslServer = worker.createStreamConnectionServer(socketAddress, (ChannelListener) acceptListener, getSSLOptions(sslContextSupplier.get()));
        } else {
            XnioSsl ssl = getSsl();
            sslServer = ssl.createSslConnectionServer(worker, socketAddress, (ChannelListener) acceptListener, getSSLOptions(sslContextSupplier.get()));
        }
        sslServer.resumeAccepts();

        final InetSocketAddress boundAddress = sslServer.getLocalAddress(InetSocketAddress.class);
        UndertowLogger.ROOT_LOGGER.listenerStarted("HTTPS", getName(), NetworkUtils.formatIPAddressForURI(boundAddress.getAddress()), boundAddress.getPort());
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    protected void stopListening() {
        final InetSocketAddress boundAddress = sslServer.getLocalAddress(InetSocketAddress.class);
        sslServer.suspendAccepts();
        UndertowLogger.ROOT_LOGGER.listenerSuspend("HTTPS", getName());
        IoUtils.safeClose(sslServer);
        sslServer = null;
        UndertowLogger.ROOT_LOGGER.listenerStopped("HTTPS", getName(), NetworkUtils.formatIPAddressForURI(boundAddress.getAddress()), boundAddress.getPort());
        httpListenerRegistry.get().removeListener(getName());
    }

    @Override
    public String getProtocol() {
        return PROTOCOL;
    }
}
