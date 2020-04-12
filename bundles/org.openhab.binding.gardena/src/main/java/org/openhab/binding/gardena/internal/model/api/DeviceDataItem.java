package org.openhab.binding.gardena.internal.model.api;

public class DeviceDataItem extends DataItem {
    public Relationships relationships;

    public String getDeviceType() {
        if (relationships != null) {
            if (relationships.services != null) {
                if (relationships.services.data != null) {
                    int valveCounter = 0;
                    int valveSetCounter = 0;
                    for (ServiceLink serviceLink: relationships.services.data) {
                        if ("MOWER".equals(serviceLink.type)) {
                            return "mower";
                        }
                        else if ("SENSOR".equals(serviceLink.type)) {
                            return "sensor";
                        }
                        else if ("POWER_SOCKET".equals(serviceLink.type)) {
                            return "power_socket";
                        }
                        else if ("VALVE".equals(serviceLink.type)) {
                            valveCounter++;
                        }
                        else if ("VALVE_SET".equals(serviceLink.type)) {
                            valveSetCounter++;
                        }
                    }
                    if (valveCounter == 1 && valveSetCounter == 0) {
                        return "electronic_pressure_pump";
                    }
                    else if (valveCounter == 1 && valveSetCounter == 1) {
                        return "watering_computer";
                    }
                    else if (valveCounter == 6 && valveSetCounter == 1) {
                        return "irrigation_control";
                    }
                }
            }
        }
        return null;
    }
}
