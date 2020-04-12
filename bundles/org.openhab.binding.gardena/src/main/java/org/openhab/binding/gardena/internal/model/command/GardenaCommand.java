package org.openhab.binding.gardena.internal.model.command;

public abstract class GardenaCommand {
    public String id;
    public String type;
    public GardenaCommandAttributes attributes;
}
