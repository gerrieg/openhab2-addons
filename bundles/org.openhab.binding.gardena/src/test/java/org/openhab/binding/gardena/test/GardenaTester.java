package org.openhab.binding.gardena.test;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.smarthome.io.net.http.HttpClientFactory;
import org.eclipse.smarthome.io.net.http.WebSocketFactory;
import org.junit.Test;
import org.openhab.binding.gardena.internal.GardenaSmart;
import org.openhab.binding.gardena.internal.GardenaSmartImpl;
import org.openhab.binding.gardena.internal.GardenaSmartEventListener;
import org.openhab.binding.gardena.internal.config.GardenaConfig;
import org.openhab.binding.gardena.internal.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;


public class GardenaTester implements GardenaSmartEventListener {
    private static Logger logger = LoggerFactory.getLogger(GardenaTester.class);

    @Test
    public void testApiImplementation() {
        GardenaConfig config = new GardenaConfig();
        config.setEmail("");
        config.setPassword("");
        config.setApiKey("");

        GardenaSmart gs = new GardenaSmartImpl();

        try {
            gs.init("myId", config, this, Executors.newScheduledThreadPool(10), new HttpClientFactory() {
                @Override
                public HttpClient createHttpClient(String s, String s1) {
                    return new HttpClient();
                }

                @Override
                public HttpClient createHttpClient(String s) {
                    return new HttpClient();
                }

                @Override
                public HttpClient getCommonHttpClient() {
                    return new HttpClient();
                }
            }, new WebSocketFactory() {
                @Override
                public WebSocketClient createWebSocketClient(String s, String s1) {
                    return new WebSocketClient();
                }

                @Override
                public WebSocketClient createWebSocketClient(String s) {
                    return new WebSocketClient();
                }

                @Override
                public WebSocketClient getCommonWebSocketClient() {
                    return new WebSocketClient();
                }
            });

            // sleep for 5 minutes to get events
            Thread.currentThread().sleep(60000 * 1);
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
    public void onConnectionLost() {

    }

    @Override
    public void onConnectionResumed() {

    }

}
