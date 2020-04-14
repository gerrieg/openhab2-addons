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
package org.openhab.binding.gardena.internal.handler;

import static org.openhab.binding.gardena.internal.GardenaBindingConstants.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.*;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.gardena.internal.GardenaSmart;
import org.openhab.binding.gardena.internal.exception.GardenaDeviceNotFoundException;
import org.openhab.binding.gardena.internal.exception.GardenaException;
import org.openhab.binding.gardena.internal.model.Device;
import org.openhab.binding.gardena.internal.model.api.DataItem;
import org.openhab.binding.gardena.internal.model.command.*;
import org.openhab.binding.gardena.internal.util.PropertyUtils;
import org.openhab.binding.gardena.internal.util.UidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.openhab.binding.gardena.internal.model.command.ValveCommand.ValveControl;
import static org.openhab.binding.gardena.internal.model.command.ValveSetCommand.ValveSetControl;
import static org.openhab.binding.gardena.internal.model.command.MowerCommand.MowerControl;
import static org.openhab.binding.gardena.internal.model.command.PowerSocketCommand.PowerSocketControl;
/**
 * The {@link GardenaThingHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class GardenaThingHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(GardenaThingHandler.class);

    public GardenaThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        try {
            Device device = getDevice();
            updateProperties(device);
            updateStatus(device);
        } catch (GardenaException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, ex.getMessage());
        } catch (AccountHandlerNotAvailableException ex) {
            // ignore
        }
    }

    /**
     * Updates the thing properties from the Gardena device.
     */
    protected void updateProperties(Device device) throws GardenaException {
        Map<String, String> properties = editProperties();
        properties.put(PROPERTY_SERIALNUMBER, PropertyUtils.getPropertyValue(device,"common.attributes.serial.value", String.class));
        properties.put(PROPERTY_MODELTYPE, PropertyUtils.getPropertyValue(device,"common.attributes.modelType.value", String.class));
        updateProperties(properties);
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        try {
            updateChannel(channelUID);
        } catch (GardenaDeviceNotFoundException | AccountHandlerNotAvailableException ex) {
            logger.debug("{}", ex.getMessage(), ex);
        } catch (GardenaException ex) {
            logger.error("{}", ex.getMessage(), ex);
        }
    }

    /**
     * Updates the channel from the Gardena device.
     */
    protected void updateChannel(ChannelUID channelUID) throws GardenaException, AccountHandlerNotAvailableException {
        if (!channelUID.getGroupId().endsWith("_commands")) {
            Device device = getDevice();
            State state = convertToState(device, channelUID);
            if (state != null) {
                updateState(channelUID, state);
            }
        }
    }

    /**
     * Converts a Gardena property value to a openHAB state.
     */
    private State convertToState(Device device, ChannelUID channelUID) throws GardenaException {
        String propertyPath = channelUID.getGroupId() + ".attributes.";
        String propertyName = channelUID.getIdWithoutGroup();
        if (propertyName.endsWith("_timestamp")) {
            propertyPath += propertyName.replace("_",".");
        }
        else {
            propertyPath += propertyName + ".value";
        }

        String acceptedItemType = StringUtils.substringBefore(getThing().getChannel(channelUID.getId()).getAcceptedItemType(), ":");

        try {
            if (PropertyUtils.isNull(device, propertyPath)) {
                return UnDefType.NULL;
            }
            switch (acceptedItemType) {
                case "String":
                    return new StringType(PropertyUtils.getPropertyValue(device, propertyPath, String.class));
                case "Number":
                    long value = PropertyUtils.getPropertyValue(device, propertyPath, Number.class).longValue();
                    // convert duration from seconds to minutes (MUST be positive multiple of 60)
                    if ("duration".equals(propertyName)) {
                        value = Math.round(value / 60.0);
                    }
                    return new DecimalType(value);
                case "DateTime":
                    Date date = PropertyUtils.getPropertyValue(device, propertyPath, Date.class);
                    ZonedDateTime zdt = ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
                    return new DateTimeType(zdt);
            }
        } catch (GardenaException e) {
            logger.warn("Channel '{}' cannot be updated as device does not contain propertyPath '{}'", channelUID,
                    propertyPath);
        } catch (ClassCastException ex) {
            logger.warn("Value of propertyPath '{}' can not be casted to {}: {}", propertyPath, acceptedItemType, ex.getMessage());
        }
        return null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            boolean isOnCommand = command instanceof OnOffType && ((OnOffType) command) == OnOffType.ON;
            if (RefreshType.REFRESH == command) {
                logger.debug("Refreshing channel '{}'", channelUID);
                updateChannel(channelUID);
            }
            else if (isOnCommand || command instanceof QuantityType) {
                GardenaCommand gardenaCommand = getGardenaCommand(channelUID, command);
                if (gardenaCommand != null) {
                    logger.debug("Received Gardena command: {}, {}", gardenaCommand.getClass().getSimpleName(), gardenaCommand.attributes.command);

                    String dataItemProperty = StringUtils.substringBeforeLast(channelUID.getGroupId(), "_");
                    DataItem dataItem = PropertyUtils.getPropertyValue(getDevice(), dataItemProperty, DataItem.class);
                    getGardenaSmart().sendCommand(dataItem, gardenaCommand);

                    if (isOnCommand){
                        scheduler.schedule(() -> {
                            updateState(channelUID, OnOffType.OFF);
                        }, 3, TimeUnit.SECONDS);
                    }
                }
            }
        } catch (AccountHandlerNotAvailableException | GardenaDeviceNotFoundException ex) {
            // ignore
        } catch (Exception ex) {
            logger.warn("{}", ex.getMessage(), ex);
        }
    }

    private GardenaCommand getGardenaCommand(ChannelUID channelUID, Command command) throws GardenaException {
        String commandName = channelUID.getIdWithoutGroup().toUpperCase();
        Integer duration = null;

        if ("START_SECONDS_TO_OVERRIDE".equals(commandName)) {
            QuantityType<?> quantityType = (QuantityType<?>) command;
            duration = quantityType.intValue() * 60;
        }

        if (StringUtils.startsWith(channelUID.getGroupId(), "valve_") &&
                StringUtils.endsWith(channelUID.getGroupId(), "_commands")) {
            return new ValveCommand(ValveControl.valueOf(commandName), duration);
        }
        else if ("mower_commands".equals(channelUID.getGroupId())) {
            return new MowerCommand(MowerControl.valueOf(commandName), duration);
        }
        else if ("valveSet_commands".equals(channelUID.getGroupId())) {
            return new ValveSetCommand(ValveSetControl.valueOf(commandName));
        }
        else if ("powerSocket_commands".equals(channelUID.getGroupId())) {
            return new PowerSocketCommand(PowerSocketControl.valueOf(commandName), duration);
        }
        throw new GardenaException("Command " + channelUID.getId() + " not found");
    }

    /**
     * Updates the thing status based on the Gardena device status.
     */
    protected void updateStatus(Device device) {
        ThingStatus oldStatus = thing.getStatus();
        ThingStatus newStatus = ThingStatus.ONLINE;
        ThingStatusDetail newDetail = ThingStatusDetail.NONE;

        if (!CONNECTION_STATUS_ONLINE.equals(device.common.attributes.rfLinkState.value)) {
            newStatus = ThingStatus.OFFLINE;
            newDetail = ThingStatusDetail.COMMUNICATION_ERROR;
        }

        if (oldStatus != newStatus || thing.getStatusInfo().getStatusDetail() != newDetail) {
            updateStatus(newStatus, newDetail);
        }
    }

    /**
     * Returns the Gardena device for this ThingHandler.
     */
    private Device getDevice() throws GardenaException, AccountHandlerNotAvailableException {
        return getGardenaSmart().getDevice(UidUtils.getGardenaDeviceId(getThing()));
    }

    /**
     * Returns the Gardena smart system implementation if the bridge is available.
     */
    private GardenaSmart getGardenaSmart() throws AccountHandlerNotAvailableException {
        if (getBridge() == null || getBridge().getHandler() == null
                || ((GardenaAccountHandler) getBridge().getHandler()).getGardenaSmart() == null) {
            if (thing.getStatus() != ThingStatus.INITIALIZING) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_MISSING_ERROR);
            }
            throw new AccountHandlerNotAvailableException("Gardena AccountHandler not yet available!");
        }

        return ((GardenaAccountHandler) getBridge().getHandler()).getGardenaSmart();
    }

}
