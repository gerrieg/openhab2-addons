package org.openhab.binding.gardena.internal.model.command;

public class ValveSetCommand extends GardenaCommand {
    private static final String COMMAND_TYPE = "VALVE_SET_CONTROL";

    public enum ValveSetControl {
        STOP_UNTIL_NEXT_TASK
    }

    public ValveSetCommand(ValveSetControl valveSetControl) {
        this.id = "vscid";
        this.type = COMMAND_TYPE;
        this.attributes = new GardenaCommandAttributes(valveSetControl.name(), null);
    }
}
