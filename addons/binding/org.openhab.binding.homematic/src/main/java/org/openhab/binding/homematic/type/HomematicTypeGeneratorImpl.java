/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.type;

import static org.openhab.binding.homematic.HomematicBindingConstants.*;
import static org.openhab.binding.homematic.internal.misc.HomematicConstants.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ChannelDefinition;
import org.eclipse.smarthome.core.thing.type.ChannelType;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.types.StateDescription;
import org.eclipse.smarthome.core.types.StateOption;
import org.openhab.binding.homematic.internal.model.HmChannel;
import org.openhab.binding.homematic.internal.model.HmDatapoint;
import org.openhab.binding.homematic.internal.model.HmDevice;
import org.openhab.binding.homematic.internal.model.HmParamsetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates ThingTypes based on metadata from a Homematic gateway.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class HomematicTypeGeneratorImpl implements HomematicTypeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(HomematicTypeGeneratorImpl.class);
    private static URI configDescriptionUriChannel;

    private static ResourceBundle deviceNames;

    private Map<String, ChannelDefinition> channelDefinitionsByID = new HashMap<String, ChannelDefinition>();
    private HomematicThingTypeProvider thingTypeProvider;
    private HomematicChannelTypeProvider channelTypeProvider;
    private Map<String, Set<String>> firmwaresByType = new HashMap<String, Set<String>>();

    static {
        // loads all Homematic device names
        deviceNames = ResourceBundle.getBundle("homematic/deviceNames", Locale.getDefault());
    }

    public HomematicTypeGeneratorImpl() {
        try {
            configDescriptionUriChannel = new URI(CONFIG_DESCRIPTION_URI_CHANNEL);
        } catch (Exception ex) {
            logger.warn("Can't create ConfigDescription URI '{}', ConfigDescription for channels not avilable!",
                    CONFIG_DESCRIPTION_URI_CHANNEL);
        }
    }

    protected void setThingTypeProvider(HomematicThingTypeProvider thingTypeProvider) {
        this.thingTypeProvider = thingTypeProvider;
    }

    protected void unsetThingTypeProvider(HomematicThingTypeProvider thingTypeProvider) {
        this.thingTypeProvider = null;
    }

    protected void setChannelTypeProvider(HomematicChannelTypeProvider channelTypeProvider) {
        this.channelTypeProvider = channelTypeProvider;
    }

    protected void unsetChannelTypeProvider(HomematicChannelTypeProvider channelTypeProvider) {
        this.channelTypeProvider = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generate(HmDevice device) {
        ThingTypeUID thingTypeUID = UidUtils.generateThingTypeUID(device);
        ThingType tt = thingTypeProvider.getThingType(thingTypeUID, Locale.getDefault());
        if (tt == null || device.isGatewayExtras()) {
            logger.debug("Generating ThingType for device '{}' with {} datapoints", device.getType(),
                    device.getDatapointCount());

            List<ChannelDefinition> channelDefinitions = new ArrayList<ChannelDefinition>();
            for (HmChannel channel : device.getChannels()) {

                for (HmDatapoint dp : channel.getDatapoints().values()) {
                    String dpTypeId = UidUtils.generateDatapointTypeId(dp);
                    ChannelDefinition cd = channelDefinitionsByID.get(dpTypeId);
                    if (cd == null) {
                        cd = createChannelDefinition(dp);
                        channelDefinitionsByID.put(dpTypeId, cd);
                    }
                    channelDefinitions.add(cd);
                }
            }
            tt = createThingType(device, channelDefinitions);
            if (logger.isTraceEnabled()) {
                dumpThingType(tt);
            }
            thingTypeProvider.addThingType(tt);
        }
        addFirmware(device);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFirmwares() {
        for (String deviceType : firmwaresByType.keySet()) {
            Set<String> firmwares = firmwaresByType.get(deviceType);
            if (firmwares.size() > 1) {
                logger.info(
                        "Multiple firmware versions for device type '{}' found ({}). "
                                + "Make sure, all devices of the same type have the same firmware version, "
                                + "otherwise you MAY have channel and/or datapoint errors in the logfile",
                        deviceType, StringUtils.join(firmwares, ", "));
            }
        }
    }

    /**
     * Adds the firmware version for validation.
     */
    private void addFirmware(HmDevice device) {
        if (!StringUtils.equals(device.getFirmware(), "?") && !DEVICE_TYPE_VIRTUAL.equals(device.getType())
                && !DEVICE_TYPE_VIRTUAL_WIRED.equals(device.getType())) {
            Set<String> firmwares = firmwaresByType.get(device.getType());
            if (firmwares == null) {
                firmwares = new HashSet<String>();
                firmwaresByType.put(device.getType(), firmwares);
            }
            firmwares.add(device.getFirmware());
        }
    }

    /**
     * Returns the device name by the device type.
     */
    private static String getDeviceName(HmDevice device) {
        try {
            if (device.getType().endsWith("-Team")) {
                String type = StringUtils.remove(device.getType(), "-Team");
                return deviceNames.getString(type) + " Team";
            } else if (device.isGatewayExtras()) {
                return deviceNames.getString(HmDevice.TYPE_GATEWAY_EXTRAS);
            }
            return deviceNames.getString(device.getType());
        } catch (MissingResourceException ex) {
            return "No name defined for this device type";
        }
    }

    /**
     * Creates the ThingType for the given device.
     */
    private ThingType createThingType(HmDevice device, List<ChannelDefinition> channelDefinitions) {
        String label = getDeviceName(device);
        String description = String.format("%s (%s)", label, device.getType());

        List<String> supportedBridgeTypeUids = new ArrayList<String>();
        supportedBridgeTypeUids.add(THING_TYPE_BRIDGE.toString());
        ThingTypeUID thingTypeUID = UidUtils.generateThingTypeUID(device);

        Map<String, String> properties = new HashMap<String, String>();
        properties.put(Thing.PROPERTY_VENDOR, PROPERTY_VENDOR_NAME);
        properties.put(Thing.PROPERTY_MODEL_ID, device.getType());

        return new ThingType(thingTypeUID, supportedBridgeTypeUids, label, description, channelDefinitions, null,
                properties, null);
    }

    /**
     * Creates the ChannelDefinition for the given datapoint.
     */
    private ChannelDefinition createChannelDefinition(HmDatapoint dp) {
        ChannelTypeUID channelTypeUID = UidUtils.generateChannelTypeUID(dp);
        String itemType = getItemType(dp);
        String category = getCategory(dp, itemType);
        String label = WordUtils.capitalizeFully(StringUtils.replace(dp.getName(), "_", " "));

        List<StateOption> options = null;
        if (dp.isEnumType()) {
            options = new ArrayList<StateOption>();
            for (int i = 0; i < dp.getOptions().length; i++) {
                options.add(new StateOption(String.valueOf(i), dp.getOptions()[i]));
            }
        }

        String pattern = null;
        if (dp.getUnit() != null) {
            String unit = StringUtils.replace(dp.getUnit(), "100%", "%");
            unit = StringUtils.replace(unit, "%", "%%");
            if (dp.isFloatType()) {
                pattern = "%.2f " + unit;
            } else if (dp.isNumberType()) {
                pattern = "%d " + unit;
            }
        }

        StateDescription state = null;
        if (dp.isNumberType() || dp.isEnumType()) {
            BigDecimal min = createBigDecimal(dp.getMinValue());
            BigDecimal max = createBigDecimal(dp.getMaxValue());
            BigDecimal step = createBigDecimal(dp.isFloatType() ? new Float(0.1) : 1L);
            state = new StateDescription(min, max, step, pattern, dp.isReadOnly(), options);
        } else {
            state = new StateDescription(null, null, null, pattern, dp.isReadOnly(), options);
        }

        boolean advanced = (!dp.getChannel().getDevice().isGatewayExtras() && dp.getChannel().getNumber() == 0)
                || dp.getParamsetType() == HmParamsetType.MASTER || DATAPOINT_NAME_INSTALL_TEST.equals(dp.getName())
                || dp.getChannel().getNumber() == 18
                        && dp.getChannel().getDevice().getType().startsWith(DEVICE_TYPE_19_REMOTE_CONTROL);

        ChannelType channelType = new ChannelType(channelTypeUID, advanced, itemType, label, null, category, null,
                state, configDescriptionUriChannel);

        channelTypeProvider.addChannelType(channelType);
        return new ChannelDefinition(channelTypeUID.getId(), channelTypeUID);
    }

    /**
     * Helper method for creating a BigDecimal.
     */
    private BigDecimal createBigDecimal(Number number) {
        try {
            return new BigDecimal(number.toString());
        } catch (Exception ex) {
            logger.warn("Can't create BigDecimal for number: {}", number.toString());
            return null;
        }
    }

    /**
     * Determines the itemType for the given datapoint.
     */
    private String getItemType(HmDatapoint dp) {
        String dpName = dp.getName();
        String channelType = StringUtils.defaultString(dp.getChannel().getType());

        if (dp.isBooleanType()) {
            if ((dpName.equals(DATAPOINT_NAME_STATE) && channelType.equals(CHANNEL_TYPE_SHUTTER_CONTACT))
                    || (dpName.equals(DATAPOINT_NAME_SENSOR) && channelType.equals(CHANNEL_TYPE_SENSOR))) {
                return ITEM_TYPE_CONTACT;
            } else {
                return ITEM_TYPE_SWITCH;
            }
        } else if (dp.isNumberType()) {
            if (dpName.startsWith(DATAPOINT_NAME_LEVEL) && channelType.equals(CHANNEL_TYPE_BLIND)) {
                return ITEM_TYPE_ROLLERSHUTTER;
            } else if (dpName.startsWith(DATAPOINT_NAME_LEVEL) && !channelType.equals(CHANNEL_TYPE_WINMATIC)
                    && !channelType.equals(CHANNEL_TYPE_AKKU)) {
                return ITEM_TYPE_DIMMER;
            } else {
                return ITEM_TYPE_NUMBER;
            }
        } else {
            return ITEM_TYPE_STRING;
        }
    }

    /**
     * Determines the category for the given datapoint.
     */
    private String getCategory(HmDatapoint dp, String itemType) {
        String dpName = dp.getName();
        String channelType = StringUtils.defaultString(dp.getChannel().getType());

        if (dpName.equals(DATAPOINT_NAME_BATTERY_TYPE) || dpName.equals(DATAPOINT_NAME_LOWBAT)) {
            return CATEGORY_BATTERY;
        } else if (dpName.equals(DATAPOINT_NAME_STATE) && channelType.equals(CHANNEL_TYPE_ALARMACTUATOR)) {
            return CATEGORY_ALARM;
        } else if (dpName.equals(DATAPOINT_NAME_HUMIDITY)) {
            return CATEGORY_HUMIDITY;
        } else if (dpName.contains(DATAPOINT_NAME_TEMPERATURE)) {
            return CATEGORY_TEMPERATURE;
        } else if (dpName.equals(DATAPOINT_NAME_MOTION)) {
            return CATEGORY_MOTION;
        } else if (dpName.equals(DATAPOINT_NAME_AIR_PRESSURE)) {
            return CATEGORY_PRESSURE;
        } else if (dpName.equals(DATAPOINT_NAME_STATE) && channelType.equals(CHANNEL_TYPE_SMOKE_DETECTOR)) {
            return CATEGORY_SMOKE;
        } else if (dpName.equals(DATAPOINT_NAME_STATE) && channelType.equals(CHANNEL_TYPE_WATERDETECTIONSENSOR)) {
            return CATEGORY_WATER;
        } else if (dpName.equals(DATAPOINT_NAME_WIND_SPEED)) {
            return CATEGORY_WIND;
        } else if (dpName.startsWith(DATAPOINT_NAME_RAIN)
                || dpName.equals(DATAPOINT_NAME_STATE) && channelType.equals(CHANNEL_TYPE_RAINDETECTOR)) {
            return CATEGORY_RAIN;
        } else if (channelType.equals(CHANNEL_TYPE_POWERMETER) && !dpName.equals(DATAPOINT_NAME_BOOT)
                && !dpName.equals(DATAPOINT_NAME_FREQUENCY)) {
            return CATEGORY_ENERGY;
        } else if (itemType.equals(ITEM_TYPE_ROLLERSHUTTER)) {
            return CATEGORY_BLINDS;
        } else if (itemType.equals(ITEM_TYPE_CONTACT)) {
            return CATEGORY_CONTACT;
        } else if (itemType.equals(ITEM_TYPE_DIMMER)) {
            return CATEGORY_DIMMABLE_LIGHT;
        } else if (itemType.equals(ITEM_TYPE_SWITCH)) {
            return CATEGORY_SWITCH;
        } else {
            return null;
        }
    }

    /**
     * Helper method for troubleshooting, dumps the ThingType.
     */
    private void dumpThingType(ThingType thingType) {
        logger.trace("ThingType {}", thingTypeToString(thingType));
        for (ChannelDefinition cd : thingType.getChannelDefinitions()) {
            logger.trace("  {}", channelDefinitionToString(cd));
        }
    }

    private String thingTypeToString(ThingType tt) {
        return new ToStringBuilder(tt, ToStringStyle.SHORT_PREFIX_STYLE).append("uid", tt.getUID())
                .append("label", tt.getLabel()).append("description", tt.getDescription()).toString();
    }

    private String channelDefinitionToString(ChannelDefinition cd) {
        ToStringBuilder cdBuilder = new ToStringBuilder(cd, ToStringStyle.SHORT_PREFIX_STYLE);
        cdBuilder.append("id", cd.getId());

        ChannelType type = channelTypeProvider.getChannelType(cd.getChannelTypeUID(), Locale.getDefault());
        ToStringBuilder typeBuilder = new ToStringBuilder(type, ToStringStyle.SHORT_PREFIX_STYLE);
        typeBuilder.append("uid", type.getUID()).append("category", type.getCategory())
                .append("itemType", type.getItemType()).append("stateDescription", type.getState())
                .append("advanced", type.isAdvanced()).append("label", type.getLabel())
                .append("description", type.getDescription());

        cdBuilder.append("type", typeBuilder);
        return cdBuilder.toString();
    }

}
