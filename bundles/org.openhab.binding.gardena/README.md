# Gardena Binding

This is the binding for [Gardena smart system](https://www.gardena.com/smart).
This binding allows you to integrate, view and control Gardena smart system devices in the openHAB environment.

## Supported Things

Devices connected to Gardena smart system, currently:

| Thing type               | Name                                               |
|--------------------------|----------------------------------------------------|
| bridge                   | smart Gateway                                      |
| mower                    | smart SILENO(+), SILENO city, SILENO life Mower    |
| watering_computer        | smart Water Control                                |
| sensor                   | smart Sensor                                       |
| electronic_pressure_pump | smart Pressure Pump                                |
| power_socket             | smart Power Adapter                                |
| irrigation_control       | smart Irrigation Control                           |

## Discovery

An account must be specified, all things for an account are discovered automatically.

## Account Configuration

There are several settings for an account:

| Name                  | Required | Description                                                                                         |
|-----------------------|----------|-----------------------------------------------------------------------------------------------------|
| **email**             | yes      | The email address for logging into the Gardena smart system                                         |
| **password**          | yes      | The password for logging into the Gardena smart system                                              |
| **apiKey**            | yes      | The The Gardena smart system integration API key                                                    |
| **connectionTimeout** | no       | The timeout in seconds for connections to Gardena smart system integration API (default = 10)       |

### Obtaining your API Key
1. Goto https://developer.1689.cloud/, sign in using your GARDENA smart system account and accept the terms of use
2. Create and save a new application via the 'Create application' button
3. Connect both _Authentication API_ and _GARDENA smart system API_ to your application via the 'Connect new API' button
4. Copy the application key to use with this binding as _apiKey_

## Examples

### Things

Minimal Thing configuration:

```java
Bridge gardena:account:home [ email="...", password="...", apiKey="..." ]
```

Configuration of multiple bridges:

```java
Bridge gardena:account:home1 [ email="...", password="...", apiKey="..." ]
Bridge gardena:account:home2 [ email="...", password="...", apiKey="..." ]
```

Once a connection to an account is established, connected Things are discovered automatically.

Alternatively, you can manually configure Things:

```java
Bridge gardena:account:home [ email="...", password="...", apiKey="..." ]
{
  Thing mower myMower [ deviceId="c81ad682-6e45-42ce-bed1-6b4eff5620c8" ]
  Thing watering_computer myWateringComputer [ deviceId="c81ad682-6e45-42ce-bed1-6b4eff5620c8" ]
  Thing sensor mySensor [ deviceId="c81ad682-6e45-42ce-bed1-6b4eff5620c8" ]
  Thing electronic_pressure_pump myEPP [ deviceId="c81ad682-6e45-42ce-bed1-6b4eff5620c8" ]
  Thing power_socket myPowerSocket [ deviceId="c81ad682-6e45-42ce-bed1-6b4eff5620c8" ]
  Thing irrigation_control myIrrigationControl [ deviceId="c81ad682-6e45-42ce-bed1-6b4eff5620c8" ]
}
```

### Items

In the items file, you can link items to channels of your Things:

```java
Number Mower_Battery_Level "Battery [%d %%]" {channel="gardena:mower:home:myMower:common#batteryLevel"}
```

### Sensor refresh

Sensor refresh commands are not yet supported by the Gardena smart system integration API.

### Example configuration

```
// smart Water Control
String  WC_Valve_Activity                 "Valve Activity" { channel="gardena:watering_computer:home:myWateringComputer:valve#activity" }
Number  WC_Valve_Duration                 "Last Watering Duration [%d min]" { channel="gardena:watering_computer:home:myWateringComputer:valve#duration" }
Number  WC_Valve_cmd_OpenWithDuration     "Watering Timer [%d min]" { channel="gardena:watering_computer:home:myWateringComputer:valve_commands#start_seconds_to_override" }
Switch  WC_Valve_cmd_CloseValve           "Stop Switch" { channel="gardena:watering_computer:home:myWateringComputer:valve_commands#stop_until_next_task" }
```

```
smarthome:status WC_Valve_Duration // returns the duration of the last watering request if still active, or NULL
smarthome:status WC_Valve_Activity // returns the current valve activity  (CLOSED|MANUAL_WATERING|SCHEDULED_WATERING)

smarthome:send WC_Valve_cmd_OpenWithDuration.sendCommand(10) // start watering for 10min
smarthome:send WC_Valve_cmd_CloseValve.sendCommand(ON) // stop any active watering

```

### Debugging and Tracing

If you want to see what's going on in the binding, switch the loglevel to TRACE in the Karaf console

```
log:set TRACE org.openhab.binding.gardena
```

Set the logging back to normal

```
log:set INFO org.openhab.binding.gardena
```

**Notes and known limitations:** 
When the binding sends a command to a device, it communicates only with the Gardena smart system integration API.
It has no control over whether the command is sent from the online service via your gateway to the device.
It is the same as if you send the command in the Gardena App.

Schedules, sensor-refresh commands, irrigation control master valve configuration etc. are not supported.
This binding relies on the GARDENA smart system integration API.
Further API documentation: https://developer.1689.cloud/apis/GARDENA+smart+system+API
