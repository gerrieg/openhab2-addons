/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.internal.communicator.server;

import java.io.IOException;

import org.openhab.binding.homematic.internal.common.HomematicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server implementation for receiving messages via BIN-RPC from a Homematic gateway.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private Thread networkServiceThread;
    private RpcNetworkService networkService;

    /**
     * Initializes and starts the server threads.
     */
    public RpcServer(RpcEventListener listener, HomematicConfig config) throws IOException {
        logger.debug("Initializing RPC server at port {}", config.getCallbackPort());

        networkService = new RpcNetworkService(listener, config.getCallbackPort(), config.getEncoding());
        networkServiceThread = new Thread(networkService);
        networkServiceThread.setName("HomematicRpcServer");
        networkServiceThread.start();
    }

    /**
     * Stops the server.
     */
    public void dispose() {
        if (networkService != null) {
            logger.debug("Disposing RPC server");
            try {
                if (networkServiceThread != null) {
                    networkServiceThread.interrupt();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            networkService.shutdown();
            networkService = null;
        }
    }

}
