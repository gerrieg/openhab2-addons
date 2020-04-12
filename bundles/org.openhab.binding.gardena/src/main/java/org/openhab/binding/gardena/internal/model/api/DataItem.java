package org.openhab.binding.gardena.internal.model.api;

import org.apache.commons.lang.StringUtils;

public class DataItem {
    public String id;
    public String type;

    public String getDeviceId() {
        return StringUtils.substringBeforeLast(id, ":");
    }
}
