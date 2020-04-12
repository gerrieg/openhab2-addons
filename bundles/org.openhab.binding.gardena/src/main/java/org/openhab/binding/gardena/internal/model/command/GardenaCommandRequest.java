package org.openhab.binding.gardena.internal.model.command;

public class GardenaCommandRequest {
    public GardenaCommand data;

    public GardenaCommandRequest(GardenaCommand gardenaCommand) {
        this.data = gardenaCommand;
    }
}
