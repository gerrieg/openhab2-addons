package org.openhab.binding.gardena.test;

import org.junit.Test;
import org.openhab.binding.gardena.internal.GardenaSmart;
import org.openhab.binding.gardena.internal.GardenaSmartEventListener;
import org.openhab.binding.gardena.internal.GardenaSmartImpl;
import org.openhab.binding.gardena.internal.config.GardenaConfig;
import org.openhab.binding.gardena.internal.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;


public class GardenaTester implements GardenaSmartEventListener {
    private static Logger logger = LoggerFactory.getLogger(GardenaTester.class);

    @Test
    public void go() {
        GardenaConfig config = new GardenaConfig();
        config.setEmail("");
        config.setPassword("");
        config.setApiKey("");

        GardenaSmart gs = new GardenaSmartImpl();

        try {
            gs.init("myId", config, this, Executors.newScheduledThreadPool(10));

            // sleep for 5 minutes to get events
            Thread.currentThread().sleep(60000 * 5);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        } finally {
            gs.dispose();
        }
    }

    @Override
    public void onDeviceUpdated(Device device) {

    }

    @Override
    public void onNewDevice(Device device) {

    }

    @Override
    public void onDeviceDeleted(Device device) {

    }

    @Override
    public void onConnectionLost() {

    }

    @Override
    public void onConnectionResumed() {

    }

    @Override
    public void scheduleReinitialize() {

    }
}
