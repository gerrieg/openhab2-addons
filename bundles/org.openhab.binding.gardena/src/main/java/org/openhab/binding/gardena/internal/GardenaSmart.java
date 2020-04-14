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

import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.smarthome.io.net.http.HttpClientFactory;
import org.eclipse.smarthome.io.net.http.WebSocketFactory;
import org.openhab.binding.gardena.internal.config.GardenaConfig;
import org.openhab.binding.gardena.internal.exception.GardenaDeviceNotFoundException;
import org.openhab.binding.gardena.internal.exception.GardenaException;
import org.openhab.binding.gardena.internal.model.Device;
import org.openhab.binding.gardena.internal.model.api.DataItem;
import org.openhab.binding.gardena.internal.model.command.GardenaCommand;

/**
 * Describes the methods required for the communication with Gardens Smart Home.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public interface GardenaSmart {

    /**
     * Initializes Gardena Smart Home.
     */
    public void init(String id, GardenaConfig config, GardenaSmartEventListener eventListener,
            ScheduledExecutorService scheduler, HttpClientFactory httpClientFactory, WebSocketFactory webSocketFactory)
            throws GardenaException;

    /**
     * Disposes Gardena Smart Home.
     */
    public void dispose();

    /**
     * Returns all devices from all locations.
     */
    public Collection<Device> getAllDevices();

    /**
     * Returns a device with the given id.
     */
    public Device getDevice(String deviceId) throws GardenaDeviceNotFoundException;

    /**
     * Sends a command to Gardena Smart Home.
     */
    public void sendCommand(DataItem dataItem, GardenaCommand gardenaCommand) throws GardenaException;

    /**
     * Returns the id.
     */
    public String getId();
}
