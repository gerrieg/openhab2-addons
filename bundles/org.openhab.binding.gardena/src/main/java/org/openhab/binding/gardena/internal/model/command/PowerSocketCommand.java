package org.openhab.binding.gardena.internal.model.command;

public class PowerSocketCommand extends GardenaCommand {
    private static final String COMMAND_TYPE = "POWER_SOCKET_CONTROL";

    public enum PowerSocketControl {
        START_SECONDS_TO_OVERRIDE,
        START_OVERRIDE,
        STOP_UNTIL_NEXT_TASK,
        PAUSE,
        UNPAUSE
    }

    public PowerSocketCommand(PowerSocketControl powerSocketControl, Integer seconds) {
        this.id = "pscid";
        this.type = COMMAND_TYPE;
        this.attributes = new GardenaCommandAttributes(powerSocketControl.name(), seconds);
    }
}
