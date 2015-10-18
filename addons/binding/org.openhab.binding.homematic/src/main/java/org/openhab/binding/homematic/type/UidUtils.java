/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.type;

import static org.openhab.binding.homematic.HomematicBindingConstants.BINDING_ID;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.openhab.binding.homematic.internal.model.HmChannel;
import org.openhab.binding.homematic.internal.model.HmDatapoint;
import org.openhab.binding.homematic.internal.model.HmDatapointInfo;
import org.openhab.binding.homematic.internal.model.HmDevice;
import org.openhab.binding.homematic.internal.model.HmGatewayInfo;
import org.openhab.binding.homematic.internal.model.HmParamsetType;

/**
 * Utility class for generating some UIDs.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class UidUtils {

    /**
     * Generates a UID for the given channel.
     */
    public static String generateChannelId(HmChannel channel) {
        return String.format("%s:%s:%s", channel.getDevice().getType(), channel.getDevice().getFirmware(),
                channel.getNumber());
    }

    /**
     * Generates a UID by device type for the given datapoint.
     */
    public static String generateDatapointTypeId(HmDatapoint dp) {
        return String.format("%s:%s", dp.getChannel().getDevice().getType(), generateHmDatapointUID(dp));
    }

    /**
     * Generates the ThingTypeUID for the given device. If it's a Homegear device, add a prefix because a Homegear
     * device has more datapoints.
     */
    public static ThingTypeUID generateThingTypeUID(HmDevice device) {
        if (!device.isGatewayExtras() && device.getGatewayId().equals(HmGatewayInfo.ID_HOMEGEAR)) {
            return new ThingTypeUID(BINDING_ID, String.format("HG-%s", device.getType()));
        } else {
            return new ThingTypeUID(BINDING_ID, String.format(device.getType()));
        }
    }

    /**
     * Generates the ChannelTypeUID for the given datapoint.
     */
    public static ChannelTypeUID generateChannelTypeUID(HmDatapoint dp) {
        return new ChannelTypeUID(BINDING_ID, generateHmDatapointUID(dp));
    }

    /**
     * Generates the ThingUID for the given device in the given bridge.
     */
    public static ThingUID generateThingUID(HmDevice device, Bridge bridge) {
        ThingTypeUID thingTypeUID = generateThingTypeUID(device);
        return new ThingUID(thingTypeUID, bridge.getUID(), device.getAddress());
    }

    /**
     * Generates the ChannelUID for the given datapoint.
     */
    public static ChannelUID generateChannelUID(HmDatapoint dp, ThingUID thingUID) {
        return new ChannelUID(thingUID, generateHmDatapointUID(dp));
    }

    /**
     * Generates the HmDatapointInfo for the given thing and channelUID.
     */
    public static HmDatapointInfo createHmDatapointInfo(ThingUID thingUID, ChannelUID channelUID) {
        String[] segments = StringUtils.split(channelUID.getId(), "_", 3);
        return new HmDatapointInfo(thingUID.getId(), HmParamsetType.parse(segments[0]), NumberUtils.toInt(segments[1]),
                segments[2]);
    }

    /**
     * Returns the address of the Homematic device from the given thing.
     */
    public static String getHomematicAddress(Thing thing) {
        return thing.getUID().getId();
    }

    private static String generateHmDatapointUID(HmDatapoint dp) {
        return String.format("%s_%s_%s", dp.getParamsetType().getId(), dp.getChannel().getNumber(), dp.getName());
    }

}
