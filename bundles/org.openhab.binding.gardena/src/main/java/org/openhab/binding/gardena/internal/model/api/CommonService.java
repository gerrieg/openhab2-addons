package org.openhab.binding.gardena.internal.model.api;

public class CommonService {
    public UserDefinedNameWrapper name;
    public TimestampedIntegerValue batteryLevel;
    public TimestampedStringValue batteryState;
    public TimestampedIntegerValue rfLinkLevel;
    public StringValue serial;
    public StringValue modelType;
    public TimestampedStringValue rfLinkState;
}
