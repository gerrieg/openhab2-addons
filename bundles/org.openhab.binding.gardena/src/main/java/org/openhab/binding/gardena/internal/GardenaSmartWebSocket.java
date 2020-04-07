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

import java.net.URI;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.gardena.internal.config.GardenaConfig;
import org.openhab.binding.gardena.internal.model.ApiWebsocketInfo;
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

    private Session session;
    private WebSocketClient webSocketClient;
    private boolean closing;

    /**
     * Constructs the {@link GardenaSmartWebSocket}.
     *
     */
    public GardenaSmartWebSocket(GardenaSmartWebSocketListener socketEventListener, ApiWebsocketInfo apiWebsocketInfo, GardenaConfig config) throws Exception {
        this.socketEventListener = socketEventListener;

        if (webSocketClient == null || webSocketClient.isStopped()) {
            webSocketClient = new WebSocketClient(new SslContextFactory(true));
            webSocketClient.setConnectTimeout(config.getConnectionTimeout() * 1000L);
            webSocketClient.setMaxIdleTimeout(config.getWebsocketIdleTimeout() * 1000L);
            webSocketClient.start();
        }

        if (session != null) {
            session.close();
        }

        logger.debug("Connecting to Gardena WebSocket");
        session = webSocketClient.connect(this, new URI(apiWebsocketInfo.websocketUrl)).get();
    }

    /**
     * Stops the {@link GardenaSmartWebSocket}.
     */
    public synchronized void stop() {
        closing = true;
        if (isRunning()) {
            logger.debug("Closing session");
            session.close();
            session = null;
            try {
                webSocketClient.stop();
            } catch (Exception ex) {
                // ignore
            }
        } else {
            session = null;
        }
    }

    /**
     * Return true, if the websocket is running.
     *
     * @return
     */
    public synchronized boolean isRunning() {
        return session != null && session.isOpen();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        closing = false;
        logger.info("Connected to Gardena Webservice");
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        if (!closing) {
            if (statusCode == StatusCode.NORMAL) {
                logger.info("Connection to Gardena Webservice was closed normally");
            } else {
                logger.info("Connection to Gardena Webservice was closed abnormally (code: {}). Reason: {}", statusCode, reason);
                socketEventListener.connectionClosed();
            }
        }
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        if (!closing) {
            logger.info("Gardena WebSocket error: {}", cause.getMessage());
            socketEventListener.onError(cause);
        }
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        logger.debug("Gardena WebSocket event: {}", msg);
        if (closing) {
            logger.debug("Gardena WebSocket event ignored, WebSocket is closing");
        } else {
            socketEventListener.onEvent(msg);
        }
    }
}
