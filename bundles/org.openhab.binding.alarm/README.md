# Alarm Binding

This is the OH3 binding for an alarm controller inspired by [Aritech](https://aritech-security.de) and [Abus](https://www.abus.com)  

The binding allows you to create alarm controllers with a configurable amount of alarm zones. Each alarm zone has a type like ACTIVE, SABOTAGE, EXIT_ENTRY, ... and you can bind a (Window/Motiondetector/...) Switch to each alarm zone. You can also send some commands to the controller for arming and disarming and also temporary disable alarm zones.

## Supported Bridges

None, this binding does not need a bridge

## Supported Things

This binding supports only one Thing: An alarm controller

## Discovery

On discovery, the binding creates a default alarm controller

## Thing Configuration

One alarm controller is created automatically, you can handle it in MainUI.  

You can also configure one or multiple controllers (things) manually:

```java
alarm:controller:home [alarmZones=10, entryTime=30, exitTime=30, passthroughTime=30, alarmDelay=30] {
    Channels:
        Type alarmZone : alarmZone_1 "My alarm zone"    [ type = "ACTIVE" ]
        Type alarmZone : alarmZone_2 "My sabotage zone" [ type = "SABOTAGE" ]
}
```

| Thing config    | Description                                                                    | 
|-----------------|--------------------------------------------------------------------------------|
| alarmZones      | Required, the number of alarm zones the controller should handle, max 1000     |
| entryTime       | The time in seconds until alarm at entry                                       |
| exitTime        | The time in seconds until arming at exit                                       |
| passthroughTime | The time in seconds to passthrough a exit/entry alarm zone on internally armed |
| alarmDelay      | The time in seconds the alarm is delayed                                       |
| tempDisableTime | The time in seconds, that an alarm zone remains temporary disabled             |

All alarm zone channels have a type. With the alarm zone types you can define the behaviour of the individual alarm zone:

| Alarm zone types         | Description                                                                                                       |
|--------------------------|-------------------------------------------------------------------------------------------------------------------|
| ```DISABLED```           | Ignored by the controller                                                                                         |
| ```ACTIVE```             | Default type, active on external arming                                                                           |
| ```MOTION```             | Alarm zone for motion detectors, disabled on ```DISARMED```, ```EXIT```, ```ENTRY``` and ```PASSTHROUGH```        |
| ```INTERN_MOTION```      | Same as ```MOTION```, but is active on internal **AND** external arming                                           |
| ```INTERN_ACTIVE```      | Alarm zone is active on internal **AND** external arming                                                          |
| ```EXIT_ENTRY```         | Set this type to an alarm zone, where you enter and leave the secured area, e.g door(s)                           |
| ```IMMEDIATELY```        | Activates the alarm if armed, ignoring the configured delays                                                      |
| ```SABOTAGE```           | Tags the alarm zone as sabotage zone, triggers a ```SABOTAGE_ALARM```, even when the controller is ```DISARMED``` |
| ```ALWAYS```             | Always triggers an alarm, even when the controller is ```DISARMED```                                              |
| ```ALWAYS_IMMEDIATELY``` | Same as ```ALWAYS``` but ignores the configured delays                                                            |


You can send these commands to the controller:

| Commands             | Description                                                                                                                                                                    |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ```ARM_INTERNALLY``` | Activates the internal armed mode                                                                                                                                              |
| ```ARM_EXTERNALLY``` | Activates the external armed mode                                                                                                                                              |
| ```PASSTHROUGH```    | Activates the passthrough mode. If the controller is internal armed and you activate pasthrough, you can open alarm zones of type ```EXIT_ENTRY``` without triggering an alarm |
| ```FORCE_ALARM```    | Immediately triggers an alarm                                                                                                                                                  |
| ```DISARM```         | Disarmes the controller                                                                                                                                                        |

Available status:

| Status                 | Description                                                                                             |
|------------------------|---------------------------------------------------------------------------------------------------------|
| ```DISARMED```         | Disarmed, watching only alarm zones with type ```SABOTAGE```, ```ALWAYS``` and ```ALWAYS_IMMEDIATELY``` |
| ```INTERNALLY_ARMED``` | Watches also all ```INTERN_ACTIVE``` alarm zones                                                        |
| ```EXTERNALLY_ARMED``` | Watches all alarm zones                                                                                 |
| ```ENTRY```            | Someone opens a ```ENTRY_EXIT``` alarm zone in external armed mode and a entry time has been configured |
| ```EXIT```             | After activating external armed mode and a exit time has been configured                                |
| ```PASSTHROUGH```      | After activating passthrough and a passthrough time has been configured                                 |
| ```PREALARM```         | Before an alarm when a alarm delay has been configured                                                  |
| ```ALARM```            | An alarm has been triggered                                                                             |
| ```SABOTAGE_ALARM```   | A sabotage alarm has been triggered                                                                     |


## Item examples

You must use a Switch for the alarm_Zones. If the Switch is ```ON```, the alarm zone is closed, ```OFF``` triggers an action when armed.

```java
String  Alarm_Status     "Status"              { channel = "alarm:controller:home:status" }
String  Alarm_Command    "Command"             { channel = "alarm:controller:home:command" }
Number  Alarm_Countdown  "Countdown [%d]"      { channel = "alarm:controller:home:countdown" }
Switch  Can_Arm_Internal "Can Arm Internal"    { channel = "alarm:controller:home:internalArmingPossible" }
Switch  Can_Arm_External "Can Arm External"    { channel = "alarm:controller:home:externalArmingPossible" }
Switch  Can_Passthrough  "Can Passthrough"     { channel = "alarm:controller:home:passthroughPossible" }
Number  Temp_Disable_Zone "Temp Disable Zone"  { channel = "alarm:controller:home:tempDisableZone" }
Number  Temp_Enable_Zone  "Temp Enable Zone"   { channel = "alarm:controller:home:tempEnableZone" }

Switch Alarmzone_1      "Alarmzone_1"      { channel = "alarm:controller:home:alarmZone_1" }
Switch Alarmzone_2      "Alarmzone_2"      { channel = "alarm:controller:home:alarmZone_2" }
```

If you bind the alarm zones this way, you have to feed them manually in rules. You can also bind them directly to real switches.  
Let's say you have a window Switch and you like to map this switch directly to alarm zone one:

```java
Switch Kitchen_Window "Kitchen" { channel=".....", channel="alarm:controller:home:alarmZone_1" [profile="follow"] }
```

You can map other types to:

| Type         | Alarm Zone Mapping                                                                                             |
|--------------|---------------------------------------|
| ```String``` | "OPEN" -> ```OFF```, else -> ```ON``` |
| ```Number``` | 0 -> ```OFF```, else ```ON```         |

## Commands

You can send commands to the alarm controller:

```java
// arming
sendCommand(Alarm_Command, "ARM_INTERNALLY")
// or
sendCommand(Alarm_Command, "ARM_EXTERNALLY")

// disarming
sendCommand(Alarm_Command, "DISARM")
```

## Temporary disable alarm zones

What's this for? Suppose you have a cleaning robot that starts when you are not at home and the alarm controller is externally armed. If you have motion detectors, the cleaning robot may be detected and an alarm triggered.

Therefore you can temporary disable an alarm zone (e.g of the motion detector) and when the robot has finished its work, you can enable it again. If you 'forget' to enable the alarm zone, it will be automatically enabled after the configured ```tempDisableTime``` of the controller.

```java
// disable alarm zone 1
sendCommand(Temp_Disable_Zone, 1)

// enable alarm zone 1
sendCommand(Temp_Enable_Zone, 1)
```
After sending the command, the item will reset to NULL again. In the logfile you can see the disable/enable message.

## Debugging

```java
// show alarm zone status
log:set DEBUG org.openhab.binding.alarm.handler
```
