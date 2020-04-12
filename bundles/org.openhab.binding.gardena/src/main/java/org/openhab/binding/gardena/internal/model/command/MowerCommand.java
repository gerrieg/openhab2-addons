package org.openhab.binding.gardena.internal.model.command;

public class MowerCommand extends GardenaCommand {
    private static final String COMMAND_TYPE = "MOWER_CONTROL";

    public enum MowerControl {
        START_SECONDS_TO_OVERRIDE,
        START_DONT_OVERRIDE,
        PARK_UNTIL_NEXT_TASK,
        PARK_UNTIL_FURTHER_NOTICE
    }

    public MowerCommand(MowerControl mowerControl, Integer seconds) {
        this.id = "mcid";
        this.type = COMMAND_TYPE;
        this.attributes = new GardenaCommandAttributes(mowerControl.name(), seconds);
    }
}
