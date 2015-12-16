/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.handler;

import static org.openhab.binding.homematic.internal.misc.HomematicConstants.*;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.homematic.converter.ConverterException;
import org.openhab.binding.homematic.converter.ConverterFactory;
import org.openhab.binding.homematic.converter.TypeConverter;
import org.openhab.binding.homematic.internal.communicator.HomematicGateway;
import org.openhab.binding.homematic.internal.misc.HomematicClientException;
import org.openhab.binding.homematic.internal.model.HmDatapoint;
import org.openhab.binding.homematic.internal.model.HmDatapointConfig;
import org.openhab.binding.homematic.internal.model.HmDatapointInfo;
import org.openhab.binding.homematic.internal.model.HmDevice;
import org.openhab.binding.homematic.type.UidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HomematicThingHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class HomematicThingHandler extends BaseThingHandler {
    private Logger logger = LoggerFactory.getLogger(HomematicThingHandler.class);

    private static final String[] STATUS_DATAPOINT_NAMES = new String[] { DATAPOINT_NAME_UNREACH,
            DATAPOINT_NAME_CONFIG_PENDING, DATAPOINT_NAME_DEVICE_IN_BOOTLOADER };

    public HomematicThingHandler(Thing thing) {
        super(thing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        if (getThing().getChannels().size() == 0) {
            updateStatus(ThingStatus.INITIALIZING);
        } else {
            try {
                HomematicGateway gateway = getHomematicGateway();
                HmDevice device = gateway.getDevice(UidUtils.getHomematicAddress(getThing()));
                updateStatus(device);
                if (!device.isOffline()) {
                    logger.debug("Initializing {} channels of thing '{}' from gateway '{}'",
                            getThing().getChannels().size(), getThing().getUID(), gateway.getId());

                    for (Channel channel : getThing().getChannels()) {
                        updateChannelState(channel.getUID());
                    }
                }
            } catch (HomematicClientException ex) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, ex.getMessage());
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, ex.getMessage());
            } catch (BridgeHandlerNotAvailableException ex) {
                // ignore
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void channelLinked(ChannelUID channelUID) {
        logger.debug("Channel linked '{}' from thing id '{}'", channelUID, getThing().getUID().getId());
        try {
            updateChannelState(channelUID);
        } catch (Exception ex) {
            logger.warn(ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command '{}' for channel '{}'", command, channelUID);
        HmDatapoint dp = null;
        try {
            HomematicGateway gateway = getHomematicGateway();
            Channel channel = getThing().getChannel(channelUID.getId());
            if (channel == null) {
                logger.warn("Channel '{}' not found in thing '{}' on gateway '{}'", channelUID, getThing().getUID(),
                        gateway.getId());
            } else {
                HmDatapointInfo dpInfo = UidUtils.createHmDatapointInfo(getThing().getUID(), channelUID);
                if (RefreshType.REFRESH == command) {
                    logger.debug("Refreshing {}", dpInfo);
                    updateChannelState(channelUID);
                } else if (StopMoveType.STOP == command && DATAPOINT_NAME_LEVEL.equals(dpInfo.getName())) {
                    // special case with stop type (rollershutter)
                    dpInfo.setName(DATAPOINT_NAME_STOP);
                    HmDatapoint stopDp = gateway.getDatapoint(dpInfo);
                    ChannelUID stopChannelUID = UidUtils.generateChannelUID(stopDp, getThing().getUID());
                    handleCommand(stopChannelUID, OnOffType.ON);
                } else {
                    dp = gateway.getDatapoint(dpInfo);
                    TypeConverter<?> converter = ConverterFactory.createConverter(channel.getAcceptedItemType());
                    Object newValue = converter.convertToBinding(command, dp);
                    HmDatapointConfig config = getChannelConfig(channel, dp);
                    gateway.sendDatapoint(dp, config, newValue);
                }
            }
        } catch (HomematicClientException | BridgeHandlerNotAvailableException ex) {
            logger.warn(ex.getMessage());
        } catch (IOException ex) {
            if (dp != null && dp.getChannel().getDevice().isOffline()) {
                logger.warn("Device '{}' is OFFLINE, can't send command '{}' for channel '{}'",
                        dp.getChannel().getDevice().getAddress(), command, channelUID);
                logger.trace(ex.getMessage(), ex);
            } else {
                logger.error(ex.getMessage(), ex);
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    /**
     * Evaluates the channel and datapoint for this channelUID and updates the state of the channel.
     */
    private void updateChannelState(ChannelUID channelUID)
            throws BridgeHandlerNotAvailableException, HomematicClientException, IOException, ConverterException {
        HomematicGateway gateway = getHomematicGateway();
        HmDatapointInfo dpInfo = UidUtils.createHmDatapointInfo(getThing().getUID(), channelUID);
        HmDatapoint dp = gateway.getDatapoint(dpInfo);
        Channel channel = getThing().getChannel(channelUID.getId());
        updateChannelState(dp, channel);
    }

    /**
     * Evaluates the channel for this datapoint and updates the state of the channel.
     */
    protected void updateChannelState(HmDatapoint dp) {
        ChannelUID channelUID = UidUtils.generateChannelUID(dp, thing.getUID());
        Channel channel = thing.getChannel(channelUID.getId());
        if (channel != null) {
            try {
                updateChannelState(dp, channel);
            } catch (BridgeHandlerNotAvailableException ex) {
                // ignore
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
        } else {
            logger.warn("Channel not found for datapoint '{}'", new HmDatapointInfo(dp));
        }
    }

    /**
     * Converts the value of the datapoint to a State, updates the channel and also sets the thing status if necessary.
     */
    private void updateChannelState(final HmDatapoint dp, Channel channel)
            throws IOException, BridgeHandlerNotAvailableException, ConverterException {

        boolean isChannelLinked = isLinked(channel);
        if (!dp.getChannel().isInitialized() && (isChannelLinked || dp.getChannel().getNumber() == 0)) {
            synchronized (this) {
                if (!dp.getChannel().isInitialized()) {
                    try {
                        getHomematicGateway().loadChannelValues(dp.getChannel());
                    } catch (IOException ex) {
                        if (dp.getChannel().getDevice().isOffline()) {
                            logger.warn("Device '{}' is OFFLINE, can't update channel '{}'",
                                    dp.getChannel().getDevice().getAddress(), channel.getUID());
                        } else {
                            throw ex;
                        }
                    }
                }
            }
        }

        boolean isStatusDatapoint = StringUtils.indexOfAny(dp.getName(), STATUS_DATAPOINT_NAMES) != -1;
        if (isStatusDatapoint) {
            updateStatus(dp.getChannel().getDevice());
        }

        if (isChannelLinked) {
            TypeConverter<?> converter = ConverterFactory.createConverter(channel.getAcceptedItemType());
            State state = converter.convertFromBinding(dp);
            updateState(channel.getUID(), state);
        }
    }

    /**
     * Updates the thing status based on device status.
     */
    private void updateStatus(HmDevice device) {
        ThingStatus oldStatus = thing.getStatus();
        ThingStatus newStatus = ThingStatus.ONLINE;
        ThingStatusDetail newDetail = ThingStatusDetail.NONE;

        if (device.isFirmwareUpdating()) {
            newStatus = ThingStatus.OFFLINE;
            newDetail = ThingStatusDetail.FIRMWARE_UPDATING;
        } else if (device.isUnreach()) {
            newStatus = ThingStatus.OFFLINE;
            newDetail = ThingStatusDetail.COMMUNICATION_ERROR;
        } else if (device.isConfigPending()) {
            newStatus = thing.getStatus();
            newDetail = ThingStatusDetail.CONFIGURATION_PENDING;
        }

        if (thing.getStatus() != newStatus || thing.getStatusInfo().getStatusDetail() != newDetail) {
            updateStatus(newStatus, newDetail);
        }
        if (oldStatus == ThingStatus.OFFLINE && newStatus == ThingStatus.ONLINE) {
            initialize();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    /**
     * Returns true, if the channel is linked at least to one item.
     */
    private boolean isLinked(Channel channel) {
        return channel != null && super.isLinked(channel.getUID().getId());
    }

    /**
     * Returns the config for a channel.
     */
    private HmDatapointConfig getChannelConfig(Channel channel, HmDatapoint dp) {
        HmDatapointConfig dpConfig = channel.getConfiguration().as(HmDatapointConfig.class);
        if (DATAPOINT_NAME_STOP.equals(dp.getName()) && CHANNEL_TYPE_BLIND.equals(dp.getChannel().getType())) {
            dpConfig.setForceUpdate(true);
        }
        return dpConfig;
    }

    /**
     * Returns the Homematic gateway if the bridge is available.
     */
    private HomematicGateway getHomematicGateway() throws BridgeHandlerNotAvailableException {
        if (getBridge() == null || getBridge().getHandler() == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_MISSING_ERROR);
            throw new BridgeHandlerNotAvailableException("BridgeHandler not yet available!");
        }

        return ((HomematicBridgeHandler) getBridge().getHandler()).getGateway();
    }

}
