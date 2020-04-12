package org.openhab.binding.gardena.internal.model.api;

public class MowerService {
    public TimestampedStringValue state;
    public TimestampedStringValue activity;
    public TimestampedStringValue lastErrorCode;
    public IntegerValue operatingHours;
}
