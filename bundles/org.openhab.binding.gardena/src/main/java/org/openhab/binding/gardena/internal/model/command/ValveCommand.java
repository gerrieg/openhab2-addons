package org.openhab.binding.gardena.internal.model.command;

public class ValveCommand extends GardenaCommand {
    private static final String COMMAND_TYPE = "VALVE_CONTROL";

    public static enum ValveControl {
        START_SECONDS_TO_OVERRIDE,
        STOP_UNTIL_NEXT_TASK,
        PAUSE,
        UNPAUSE
    }

    public ValveCommand(ValveControl valveControl, Integer seconds) {
        this.id = "vcid";
        this.type = COMMAND_TYPE;
        this.attributes = new GardenaCommandAttributes(valveControl.name(), seconds);
    }
}
