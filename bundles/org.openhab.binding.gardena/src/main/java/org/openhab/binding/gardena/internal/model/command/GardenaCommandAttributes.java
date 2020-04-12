package org.openhab.binding.gardena.internal.model.command;

public class GardenaCommandAttributes {
    public String command;
    public Integer seconds;

    public GardenaCommandAttributes(String command, Integer seconds) {
        this.command = command;
        this.seconds = seconds;
    }
}
