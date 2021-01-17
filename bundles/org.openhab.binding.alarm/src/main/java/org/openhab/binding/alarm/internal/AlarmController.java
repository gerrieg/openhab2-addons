/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.alarm.internal;

import static org.openhab.binding.alarm.internal.model.AlarmStatus.*;
import static org.openhab.binding.alarm.internal.model.AlarmZoneType.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.openhab.binding.alarm.internal.Countdown.CountdownCallback;
import org.openhab.binding.alarm.internal.config.AlarmControllerConfig;
import org.openhab.binding.alarm.internal.model.AlarmCommand;
import org.openhab.binding.alarm.internal.model.AlarmStatus;
import org.openhab.binding.alarm.internal.model.AlarmZone;
import org.openhab.binding.alarm.internal.model.AlarmZoneType;
import org.openhab.core.common.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for all alarm functions.
 *
 * @author Gerhard Riegler - Initial contribution
 */

public class AlarmController {
    private final Logger logger = LoggerFactory.getLogger(AlarmController.class);

    private Map<String, AlarmZone> alarmZones = new HashMap<>();
    private AlarmListener listener;
    private AlarmControllerConfig config;
    private AlarmStatus status = DISARMED;
    private Countdown countDown = new Countdown();
    private Boolean isReadyToArmInternally;
    private Boolean isReadyToArmExternally;
    private Boolean isReadyToPassthrough;

    private ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool("alarmController");
    private Map<String, TempDisabledZoneInfo> tempDisabledZones = new HashMap<>();

    /**
     * Creates a new alarm controller with the given configuration and listener.
     */
    public AlarmController(AlarmControllerConfig config, AlarmListener listener) {
        this.config = config;
        this.listener = listener;
    }

    /**
     * Adds an alarm zone to the controller.
     */
    public void addOrUpdateAlarmZone(AlarmZone alarmZone) {
        AlarmZone currentAlarmZone = alarmZones.get(alarmZone.getId());
        if (currentAlarmZone != null) {
            // preserve state of alarm zone
            alarmZone.setClosed(currentAlarmZone.isClosed());
        }
        alarmZones.put(alarmZone.getId(), alarmZone);
        validate();
    }

    /**
     * Removes an alarm zone from the controller.
     */
    public void removeAlarmZone(String id) {
        alarmZones.remove(id);
        validate();
    }

    /**
     * Returns the alarm zone with the given id.
     */
    public AlarmZone getAlarmZone(String id) throws AlarmException {
        if (!alarmZones.containsKey(id)) {
            throw new AlarmException("Alarm zone with id '" + id + "' does not exist");
        }
        return alarmZones.get(id);
    }

    /**
     * Informs the controller of a change in an alarm zone.
     */
    public void alarmZoneChanged(String id, boolean contactClosed) throws AlarmException {
        AlarmZone alarmZone = alarmZones.get(id);
        if (alarmZone == null) {
            throw new AlarmException("Alarm zone with id '" + id + "' not found");
        }
        alarmZone.setClosed(contactClosed);

        if (!alarmZone.isClosed()) {
            if (isType(alarmZone, SABOTAGE)) {
                startCountdown(config.getAlarmDelay(), PREALARM, SABOTAGE_ALARM);
            } else if (isType(alarmZone, ALWAYS)) {
                startCountdown(config.getAlarmDelay(), PREALARM, ALARM);
            } else if (isType(alarmZone, ALWAYS_IMMEDIATELY)) {
                setStatus(ALARM);
            } else if (isStatus(INTERNALLY_ARMED, EXTERNALLY_ARMED, EXIT, ENTRY) && isType(alarmZone, IMMEDIATELY)) {
                setStatus(ALARM);
            } else if (isStatus(INTERNALLY_ARMED) && isType(alarmZone, INTERN_ACTIVE, EXIT_ENTRY, INTERN_MOTION)) {
                startCountdown(config.getAlarmDelay(), PREALARM, ALARM);
            } else if (isStatus(EXTERNALLY_ARMED) && isType(alarmZone, ACTIVE, INTERN_ACTIVE, MOTION, INTERN_MOTION)) {
                startCountdown(config.getAlarmDelay(), PREALARM, ALARM);
            } else if (isStatus(EXTERNALLY_ARMED, ALARM) && isType(alarmZone, EXIT_ENTRY)) {
                startCountdown(config.getEntryTime(), ENTRY, ALARM);
            } else if (isStatus(EXIT, ENTRY) && isType(alarmZone, ACTIVE, INTERN_ACTIVE)) {
                startCountdown(config.getAlarmDelay(), PREALARM, ALARM);
            } else if (isStatus(PASSTHROUGH) && isType(alarmZone, INTERN_ACTIVE)) {
                startCountdown(config.getAlarmDelay(), PREALARM, ALARM);
            }
        }

        boolean isMotionWhenDisarmed = isStatus(DISARMED) && isType(alarmZone, MOTION, INTERN_MOTION);
        if (!isMotionWhenDisarmed) {
            validate();
        }
    }

    /**
     * Temporary disable alarm zone for maximum the configured tempDisableTime.
     */
    public void temporaryDisableZone(final String id) throws AlarmException {
        synchronized (this) {
            if (tempDisabledZones.containsKey(id)) {
                throw new AlarmException("Alarm zone with id '" + id + "' already disabled");
            }

            AlarmZone alarmZone = getAlarmZone(id);
            TempDisabledZoneInfo zoneInfo = new TempDisabledZoneInfo();
            zoneInfo.type = alarmZone.getType();
            zoneInfo.future = scheduler.schedule(() -> {
                try {
                    zoneInfo.future.cancel(false);
                    zoneInfo.future = null;
                    enableTemporaryDisabledZone(id);
                } catch (AlarmException ex) {
                    logger.warn(ex.getMessage());
                }
            }, config.getTempDisableTime(), TimeUnit.SECONDS);
            tempDisabledZones.put(id, zoneInfo);
            alarmZone.setType(DISABLED);
            logger.info("Temporary disabled alarm zone '{}' for max. {} seconds", id, config.getTempDisableTime());
        }
    }

    /**
     * Enables a temporary disabled alarm zone.
     */
    public synchronized void enableTemporaryDisabledZone(final String id) throws AlarmException {
        synchronized (this) {
            TempDisabledZoneInfo zoneInfo = tempDisabledZones.get(id);
            if (zoneInfo == null) {
                throw new AlarmException("Temporary disabled alarm zone '" + id + "' not found");
            }
            AlarmZone alarmZone = getAlarmZone(id);
            alarmZone.setType(zoneInfo.type);
            if (zoneInfo.future != null) {
                zoneInfo.future.cancel(false);
            }
            tempDisabledZones.remove(id);
            logger.info("Enabled temporary disabled alarm zone '{}'", id);
            alarmZoneChanged(id, alarmZone.isClosed());
        }
    }

    /**
     * Return the alarm controller configuration.
     */
    public AlarmControllerConfig getConfig() {
        return config;
    }

    /**
     * Returns the current status of the alarm controller.
     */
    public AlarmStatus getStatus() {
        return status;
    }

    /**
     * Sets the status of the alarm controller.
     */
    private void setStatus(AlarmStatus newStatus) {
        if (countDown.isActive()) {
            countDown.stop();
            if (listener != null) {
                listener.alarmCountdownChanged(0);
            }
        }
        status = newStatus;
        if (listener != null) {
            listener.alarmStatusChanged(status);
        }
        validate();
    }

    /**
     * Returns true, if the alarm controller can be switched to internal armed mode.
     */
    public boolean isReadyToArmInternally() {
        return isReadyToArmInternally == null ? false : isReadyToArmInternally;
    }

    /**
     * Returns true, if the alarm controller can be switched to external armed mode.
     */
    public boolean isReadyToArmExternally() {
        return isReadyToArmExternally == null ? false : isReadyToArmExternally;
    }

    /**
     * Returns true, if the alarm controller can be switched to passthrough mode.
     */
    public boolean isReadyToPassthrough() {
        return isReadyToPassthrough == null ? false : isReadyToPassthrough;
    }

    /**
     * Handles the commands of this alarm controller.
     */
    public void doCommand(AlarmCommand command) throws AlarmException {
        switch (command) {
            case ARM_INTERNALLY:
                if (!isStatus(DISARMED)) {
                    throw new AlarmException(
                            "Arming not possible, Alarm Controller in status " + status + ", must be " + DISARMED);
                }
                if (!isReadyToArmInternally) {
                    throw new AlarmException(
                            "Arming not possible, either Alarmzone(s) open or no Alarmzone of type " + INTERN_ACTIVE);
                }
                setStatus(INTERNALLY_ARMED);
                break;
            case ARM_EXTERNALLY:
                if (!isStatus(DISARMED)) {
                    throw new AlarmException(
                            "Arming not possible, Alarm Controller in status " + status + ", must be " + DISARMED);
                }
                if (!isReadyToArmExternally) {
                    throw new AlarmException("Arming not possible, either Alarmzone(s) open or no enabled Alarmzone");
                }
                startCountdown(config.getExitTime(), EXIT, EXTERNALLY_ARMED);
                break;
            case PASSTHROUGH:
                if (!isReadyToPassthrough) {
                    throw new AlarmException("Passthrough not possible, Alarm Controller in status " + status
                            + ", must be " + INTERNALLY_ARMED);
                }
                startCountdown(config.getPassthroughTime(), PASSTHROUGH, INTERNALLY_ARMED);
                break;
            case FORCE_ALARM:
                setStatus(ALARM);
                break;
            case DISARM:
                setStatus(DISARMED);
        }
    }

    public void dispose() {
        countDown.stop();

        for (TempDisabledZoneInfo zoneInfo : tempDisabledZones.values()) {
            if (zoneInfo.future != null) {
                zoneInfo.future.cancel(true);
            }
        }
        tempDisabledZones.clear();
    }

    /**
     * Validates the states of the alarm controller and calls the listener if something changes.
     */
    private void validate() {
        boolean currentIsReadyToArmInternally = isReadyToArm(true, false);
        boolean currentIsReadyToArmExternally = isReadyToArm(false, false);
        boolean currentIsReadyToPassthrough = isStatus(INTERNALLY_ARMED);

        if (isReadyToArmInternally == null || currentIsReadyToArmInternally != isReadyToArmInternally) {
            isReadyToArmInternally = currentIsReadyToArmInternally;
            if (listener != null) {
                listener.readyToArmInternallyChanged(isReadyToArmInternally);
            }
        }

        if (isReadyToArmExternally == null || currentIsReadyToArmExternally != isReadyToArmExternally) {
            isReadyToArmExternally = currentIsReadyToArmExternally;
            if (listener != null) {
                listener.readyToArmExternallyChanged(isReadyToArmExternally);
            }
        }

        if (isReadyToPassthrough == null || currentIsReadyToPassthrough != isReadyToPassthrough) {
            isReadyToPassthrough = currentIsReadyToPassthrough;
            if (listener != null) {
                listener.readyToPassthroughChanged(isReadyToPassthrough);
            }
        }
    }

    /**
     * Returns true, if the alarm controller is ready to arm.
     */
    private boolean isReadyToArm(boolean internally, boolean checkExternalExitEntry) {
        if (!isStatus(DISARMED) && !isStatus(EXIT)) {
            return false;
        }
        boolean atLeaseOneActiveZoneType = false;
        for (AlarmZone alarmZone : alarmZones.values()) {
            atLeaseOneActiveZoneType |= internally ? isType(alarmZone, INTERN_ACTIVE) : !isType(alarmZone, DISABLED);

            if (!alarmZone.isClosed() && !isType(alarmZone, DISABLED)) {
                if (internally && isType(alarmZone, EXIT_ENTRY)) {
                    return false;
                } else if ((!internally && isType(alarmZone, ACTIVE)) || isType(alarmZone, INTERN_ACTIVE)) {
                    return false;
                } else if (checkExternalExitEntry && isType(alarmZone, EXIT_ENTRY)) {
                    return false;
                } else if (isType(alarmZone, IMMEDIATELY, ALWAYS, ALWAYS_IMMEDIATELY, SABOTAGE)) {
                    return false;
                }
            } else if (!checkExternalExitEntry && isStatus(EXIT)) {
                return false;
            }
        }
        return atLeaseOneActiveZoneType;
    }

    /**
     * Returns true, if the alarm zone is of a given type.
     */
    private boolean isType(AlarmZone alarmZone, AlarmZoneType... types) {
        for (AlarmZoneType type : types) {
            if (alarmZone.getType() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true, if the current status is of a given status.
     */
    private boolean isStatus(AlarmStatus... status) {
        for (AlarmStatus alarmStatus : status) {
            if (this.status == alarmStatus) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts a countdown if configured.
     */
    private void startCountdown(int startCountdownFrom, AlarmStatus startStatus, AlarmStatus targetStatus) {
        if (startCountdownFrom > 0) {
            setStatus(startStatus);
            countDown.start(startCountdownFrom, new CountdownCallback() {

                @Override
                public void finished() {
                    if (listener != null) {
                        listener.alarmCountdownChanged(0);
                    }
                    if (targetStatus == EXTERNALLY_ARMED && !isReadyToArm(false, true)) {
                        setStatus(ALARM);
                    } else {
                        setStatus(targetStatus);
                    }
                }

                @Override
                public void countdownChanged(int value) {
                    if (listener != null) {
                        listener.alarmCountdownChanged(value);
                    }
                }
            });
        } else {
            setStatus(targetStatus);
        }
    }

    private class TempDisabledZoneInfo {
        public AlarmZoneType type;
        public ScheduledFuture<?> future;
    }
}
