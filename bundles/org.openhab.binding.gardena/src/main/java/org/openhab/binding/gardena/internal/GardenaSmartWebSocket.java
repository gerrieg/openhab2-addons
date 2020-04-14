/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.gardena.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.frames.PongFrame;
import org.eclipse.smarthome.io.net.http.WebSocketFactory;
import org.openhab.binding.gardena.internal.config.GardenaConfig;
import org.openhab.binding.gardena.internal.model.api.WebSocketCreatedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GardenaSmartWebSocket} implements the websocket for receiving constant updates
 * from the GardenaSmart web service.
 *
 * @author Gerhard Riegler - Initial contribution
 */
@WebSocket
public class GardenaSmartWebSocket {
    private final Logger logger = LoggerFactory.getLogger(GardenaSmartWebSocket.class);
    private final GardenaSmartWebSocketListener socketEventListener;
    private final static long WEBSOCKET_IDLE_TIMEOUT = 300;

    private Session session;
    private WebSocketClient webSocketClient;
    private boolean closing;
    private Instant lastPong;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture pingFuture;
    private ByteBuffer pingPayload = ByteBuffer.wrap("ping".getBytes());

    /**
     * Constructs the {@link GardenaSmartWebSocket}.
     */
    public GardenaSmartWebSocket(GardenaSmartWebSocketListener socketEventListener,
            WebSocketCreatedResponse webSocketCreatedResponse, GardenaConfig config, ScheduledExecutorService scheduler,
            WebSocketFactory webSocketFactory) throws Exception {
        this.socketEventListener = socketEventListener;
        this.scheduler = scheduler;

        webSocketClient = webSocketFactory.createWebSocketClient(String.valueOf(this.getClass().hashCode()));
        webSocketClient.setConnectTimeout(config.getConnectionTimeout() * 1000L);
        webSocketClient.start();

        logger.debug("Connecting to Gardena Webservice");
        session = webSocketClient.connect(this, new URI(webSocketCreatedResponse.data.attributes.url)).get();
    }

    /**
     * Stops the {@link GardenaSmartWebSocket}.
     */
    public synchronized void stop() {
        closing = true;
        if (pingFuture != null) {
            pingFuture.cancel(true);
        }
        if (isRunning()) {
            logger.debug("Closing Gardens Webservice session");
            session.close();
            try {
                webSocketClient.stop();
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    /**
     * Return true, if the websocket is running.
     */
    public synchronized boolean isRunning() {
        return session != null && session.isOpen();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        closing = false;
        logger.debug("Connected to Gardena Webservice");

        pingFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.trace("Sending ping");
                session.getRemote().sendPing(pingPayload);

                if (lastPong != null
                        && Instant.now().getEpochSecond() - lastPong.getEpochSecond() > WEBSOCKET_IDLE_TIMEOUT) {
                    session.close(1000, "Timeout manually closing dead connection");
                }
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }

        }, 2, 2, TimeUnit.MINUTES);
    }

    @OnWebSocketFrame
    public void onFrame(Frame pong) {
        if (pong instanceof PongFrame) {
            lastPong = Instant.now();
            logger.trace("Pong received");
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        if (!closing) {
            logger.debug("Connection to Gardena Webservice was closed: code: {}, reason: {}", statusCode, reason);
            socketEventListener.onWebSocketClose();
        }
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        if (!closing) {
            logger.warn("Gardena Webservice error: {}, restarting", cause.getMessage());
            logger.debug(cause.getMessage(), cause);
            socketEventListener.onWebSocketError(cause);
        }
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        if (!closing) {
            logger.trace("<<< event: {}", msg);
            socketEventListener.onWebSocketMessage(msg);
        }
    }
}
