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
package org.openhab.binding.gardena.internal.discovery;

import static org.openhab.binding.gardena.internal.GardenaBindingConstants.BINDING_ID;

import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.openhab.binding.gardena.internal.GardenaSmart;
import org.openhab.binding.gardena.internal.exception.GardenaException;
import org.openhab.binding.gardena.internal.handler.GardenaAccountHandler;
import org.openhab.binding.gardena.internal.model.Device;
import org.openhab.binding.gardena.internal.util.PropertyUtils;
import org.openhab.binding.gardena.internal.util.UidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GardenaDeviceDiscoveryService} is used to discover devices that are connected to Gardena smart system.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class GardenaDeviceDiscoveryService extends AbstractDiscoveryService
        implements DiscoveryService, ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(GardenaDeviceDiscoveryService.class);
    private static final int DISCOVER_TIMEOUT_SECONDS = 5;

    private @NonNullByDefault({}) GardenaAccountHandler accountHandler;
    private Future<?> scanFuture;

    public GardenaDeviceDiscoveryService() {
        super(Collections.unmodifiableSet(Stream.of(new ThingTypeUID(BINDING_ID, "-")).collect(Collectors.toSet())),
                DISCOVER_TIMEOUT_SECONDS, false);
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof GardenaAccountHandler) {
            this.accountHandler = (GardenaAccountHandler) handler;
            this.accountHandler.setDiscoveryService(this);
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return accountHandler;
    }

    /**
     * Called on component activation.
     */
    @Override
    public void activate() {
        super.activate(null);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startScan() {
        logger.debug("Starting Gardena discovery scan");
        loadDevices();
    }

    @Override
    public void stopScan() {
        logger.debug("Stopping Gardena discovery scan");
        if (scanFuture != null) {
            scanFuture.cancel(true);
        }
        super.stopScan();
    }

    /**
     * Starts a thread which loads all Gardena devices registered in the account
     */
    private void loadDevices() {
        if (scanFuture == null) {
            scanFuture = scheduler.submit(() -> {
                GardenaSmart gardena = accountHandler.getGardenaSmart();
                for (Device device : gardena.getAllDevices()) {
                    deviceDiscovered(device);
                }

                for (Thing thing : accountHandler.getThing().getThings()) {
                    try {
                        gardena.getDevice(UidUtils.getGardenaDeviceId(thing));
                    } catch (GardenaException ex) {
                        thingRemoved(thing.getUID());
                    }
                }

                logger.debug("Finished Gardena device discovery scan on gateway '{}'",
                        accountHandler.getGardenaSmart().getId());
                scanFuture = null;
                removeOlderResults(getTimestampOfLastScan());
            });
        } else {
            logger.debug("Gardena device discovery scan in progress");
        }
    }

    /**
     * Waits for the discovery scan to finish and then returns.
     */
    public void waitForScanFinishing() {
        if (scanFuture != null) {
            logger.debug("Waiting for finishing Gardena device discovery scan");
            try {
                scanFuture.get();
                logger.debug("Gardena device discovery scan finished");
            } catch (CancellationException ex) {
                // ignore
            } catch (Exception ex) {
                logger.error("Error waiting for device discovery scan: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Generates the DiscoveryResult from a Gardena device.
     */
    public void deviceDiscovered(Device device) {
        ThingUID accountUID = accountHandler.getThing().getUID();
        ThingUID thingUID = UidUtils.generateThingUID(device, accountHandler.getThing());

        try {
            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withBridge(accountUID)
                    .withLabel(PropertyUtils.getPropertyValue(device, "common.attributes.name.value", String.class))
                    .build();
            thingDiscovered(discoveryResult);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
}
