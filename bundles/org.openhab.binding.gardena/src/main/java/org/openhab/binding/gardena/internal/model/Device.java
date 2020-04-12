package org.openhab.binding.gardena.internal.model;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.openhab.binding.gardena.internal.exception.GardenaException;
import org.openhab.binding.gardena.internal.model.api.*;

public class Device {
    public String id;
    public String deviceType;
    public String location;
    public CommonServiceDataItem common;
    public MowerServiceDataItem mower;
    public PowerSocketServiceDataItem powerSocket;
    public SensorServiceDataItem sensor;
    public ValveServiceDataItem valve;
    public ValveServiceDataItem valve_1;
    public ValveServiceDataItem valve_2;
    public ValveServiceDataItem valve_3;
    public ValveServiceDataItem valve_4;
    public ValveServiceDataItem valve_5;
    public ValveServiceDataItem valve_6;
    public ValveSetServiceDataItem valveSet;

    public Device(String id) {
        this.id = id;
    }

    public void setDataItem(DataItem dataItem) throws GardenaException {
        if (dataItem instanceof DeviceDataItem) {
            deviceType = ((DeviceDataItem) dataItem).getDeviceType();
        }
        else if (dataItem instanceof LocationDataItem) {
            LocationDataItem locationDataItem = (LocationDataItem) dataItem;
            if (locationDataItem.attributes != null) {
                location = locationDataItem.attributes.name;
            }
        }
        else if (dataItem instanceof CommonServiceDataItem) {
            common = (CommonServiceDataItem) dataItem;
        }
        else if (dataItem instanceof MowerServiceDataItem) {
            mower = (MowerServiceDataItem) dataItem;
        }
        else if (dataItem instanceof PowerSocketServiceDataItem) {
            powerSocket = (PowerSocketServiceDataItem) dataItem;
        }
        else if (dataItem instanceof SensorServiceDataItem) {
            sensor = (SensorServiceDataItem) dataItem;
        }
        else if (dataItem instanceof ValveSetServiceDataItem) {
            valveSet = (ValveSetServiceDataItem) dataItem;
        }
        else if (dataItem instanceof ValveServiceDataItem) {
            String valveNumber = StringUtils.substringAfterLast(dataItem.id, ":");
            if (valveNumber.equals("") || valveNumber.equals("wc") || valveNumber.equals("0")) {
                valve = (ValveServiceDataItem) dataItem;
            }
            else if ("1".equals(valveNumber)) {
                valve_1 = (ValveServiceDataItem) dataItem;
            }
            else if ("2".equals(valveNumber)) {
                valve_2 = (ValveServiceDataItem) dataItem;
            }
            else if ("3".equals(valveNumber)) {
                valve_3 = (ValveServiceDataItem) dataItem;
            }
            else if ("4".equals(valveNumber)) {
                valve_4 = (ValveServiceDataItem) dataItem;
            }
            else if ("5".equals(valveNumber)) {
                valve_5 = (ValveServiceDataItem) dataItem;
            }
            else if ("6".equals(valveNumber)) {
                valve_6 = (ValveServiceDataItem) dataItem;
            }
            else {
                throw new GardenaException("Unknown valveNumber in dataItem with id: " + dataItem.id);
            }
        }
        else {
            throw new GardenaException("Unknown dataItem with id: " + dataItem.id);
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Device)) {
            return false;
        }
        Device comp = (Device) obj;
        return new EqualsBuilder().append(comp.id, id).isEquals();
    }

}

