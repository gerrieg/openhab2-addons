package org.openhab.binding.gardena.internal.model.api;

public class ValveService {
    public UserDefinedNameWrapper name;
    public TimestampedStringValue activity;
    public TimestampedStringValue state;
    public TimestampedStringValue lastErrorCode;
    public TimestampedIntegerValue duration;
}
