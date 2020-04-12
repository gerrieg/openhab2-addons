package org.openhab.binding.gardena.internal.model;

import org.openhab.binding.gardena.internal.model.api.*;

public class DataItemFactory {
    public static Class<? extends DataItem> create(String type) {
        switch (type) {
            case "LOCATION":
                return LocationDataItem.class;
            case "DEVICE":
                return DeviceDataItem.class;
            case "COMMON":
                return CommonServiceDataItem.class;
            case "MOWER":
                return MowerServiceDataItem.class;
            case "POWER_SOCKET":
                return PowerSocketServiceDataItem.class;
            case "VALVE":
                return ValveServiceDataItem.class;
            case "VALVE_SET":
                return ValveSetServiceDataItem.class;
            case "SENSOR":
                return SensorServiceDataItem.class;
            default:
                throw new RuntimeException("Unknown DataItem type: " + type);
        }
    }
}
