/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.web.websocket;

import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

/**
 * @author Stuart Douglas
 */
@ClientEndpoint
public class AnnotatedClient {

    private final BlockingDeque<String> queue = new LinkedBlockingDeque<>();

    @OnOpen
    public void open(final Session session) throws IOException {
        session.getBasicRemote().sendText("Hello");
    }

    @OnMessage
    public void message(final String message) {
        queue.add(message);
    }

    public String getMessage() throws InterruptedException {
        return queue.poll(5, TimeUnit.SECONDS);
    }

}
