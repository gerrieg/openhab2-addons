package org.openhab.binding.gardena.internal;

import com.google.gson.*;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.binding.gardena.internal.config.GardenaConfig;
import org.openhab.binding.gardena.internal.exception.GardenaException;
import org.openhab.binding.gardena.internal.exception.GardenaUnauthorizedException;
import org.openhab.binding.gardena.internal.model.*;
import org.openhab.binding.gardena.internal.model.deser.DateDeserializer;
import org.openhab.binding.gardena.internal.model.deser.PropertyValueDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GardenaSmartApiImpl implements GardenaSmartWebSocketListener {
    private final Logger logger = LoggerFactory.getLogger(GardenaSmartApiImpl.class);

    private static final long RESTART_DELAY_SECONDS = 10;

    private Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new DateDeserializer())
            .registerTypeAdapter(PropertyValue.class, new PropertyValueDeserializer()).create();

    private static final String URL_API_HUSQUARNA = "https://api.authentication.husqvarnagroup.dev/v1";
    private static final String URL_API_GARDENA = "https://api.smart.gardena.dev/v1";
    private static final String URL_API_LOGIN = URL_API_HUSQUARNA + "/oauth2/token";
    private static final String URL_API_WEBSOCKET = URL_API_GARDENA + "/websocket";

    private String id;
    private GardenaConfig config;
    private ScheduledExecutorService scheduler;

    private Map<String, Device> allDevicesById = new HashMap<>();
    private Set<Location> allLocations = new HashSet<>();
    private GardenaSmartEventListener eventListener;

    private HttpClient httpClient;
    private List<GardenaSmartWebSocket> webSockets = new ArrayList<>();
    private ApiToken token;
    private boolean restarting = false;
    private ScheduledFuture<?> restartScheduledFuture;

    public GardenaSmartApiImpl(String id, GardenaConfig config, Set<Location> allLocations, Map<String, Device> allDevicesById,
                     GardenaSmartEventListener eventListener, ScheduledExecutorService scheduler) throws GardenaException {
        this.id = id;
        this.config = config;
        this.allLocations = allLocations;
        this.allDevicesById = allDevicesById;
        this.eventListener = eventListener;
        this.scheduler = scheduler;
    }

    public void start() throws GardenaException {
        logger.debug("Starting GardenaSmartApi");
        try {
            httpClient = new HttpClient(new SslContextFactory(true));
            httpClient.setConnectTimeout(config.getConnectionTimeout() * 1000L);
            httpClient.setIdleTimeout(httpClient.getConnectTimeout());
            httpClient.start();

            token = getToken();
            for(Location location : allLocations) {
                ApiWebsocketInfo apiWebsocketInfo = getWebsocketInfo(location.getId());
                webSockets.add(new GardenaSmartWebSocket(this, apiWebsocketInfo, config));
            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
            throw new GardenaException(ex.getMessage(), ex);
        }
    }

    private <T> T executeRequest(HttpMethod method, String url, RequestExecutor<T> requestExecutor) throws GardenaException {
        try {
            Request request = httpClient.newRequest(url).method(method)
                    .header(HttpHeader.CONTENT_TYPE, requestExecutor.getContentType())
                    .header(HttpHeader.ACCEPT, "application/vnd.api+json")
                    .header(HttpHeader.ACCEPT_ENCODING, "gzip");

            if (!URL_API_LOGIN.equals(url)) {
                request.header("Authorization", token.tokenType + " " + token.accessToken);
                request.header("Authorization-provider", token.provider);
                request.header("X-Api-Key", config.getApiKey());
            }

            requestExecutor.beforeRequest(request);
            ContentResponse contentResponse = request.send();
            int status = contentResponse.getStatus();
            if (logger.isTraceEnabled()) {
                logger.trace("Response: status:{}, {}", status, contentResponse.getContentAsString());
            }

            if (status != 200 && status != 204 && status != 201) {
                throw new GardenaException(String.format("Error %s %s", status, contentResponse.getReason()));
            }

            return requestExecutor.afterRequest(contentResponse);

        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof HttpResponseException) {
                HttpResponseException responseException = (HttpResponseException) ex.getCause();
                int status = responseException.getResponse().getStatus();
                if (status == 401) {
                    throw new GardenaUnauthorizedException(ex.getCause());
                }
            }
            throw new GardenaException(ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new GardenaException(ex.getMessage(), ex);
        }
    }

    public ApiToken getToken() throws GardenaException {
        return executeRequest(HttpMethod.POST, URL_API_LOGIN, new RequestExecutor<ApiToken>() {
            @Override
            public String getContentType() {
                return "application/x-www-form-urlencoded";
            }

            @Override
            public void beforeRequest(Request request) {
                Fields fields = new Fields();
                if (token != null && StringUtils.trimToNull(token.refreshToken) != null) {
                    logger.debug("Gardena API login using refreshToken");
                    fields.add("grant_type","refresh_token");
                    fields.add("refresh_token", token.refreshToken);
                }
                else {
                    logger.debug("Gardena API login using password");
                    fields.add("grant_type","password");
                    fields.add("username", config.getEmail());
                    fields.add("password", config.getPassword());
                }

                fields.add("client_id", config.getApiKey());
                request.content(new FormContentProvider(fields));
            }

            @Override
            public ApiToken afterRequest(ContentResponse contentResponse) {
                return gson.fromJson(contentResponse.getContentAsString(), ApiToken.class);
            }
        });
    }

    private ApiWebsocketInfo getWebsocketInfo(String locationId) throws GardenaException {
        return executeRequest(HttpMethod.POST, URL_API_WEBSOCKET, new RequestExecutor<ApiWebsocketInfo>() {
            @Override
            public String getContentType() {
                return "application/vnd.api+json";
            }

            @Override
            public void beforeRequest(Request request) {
                final String WEBSOCKET_REQUEST_DATA = "{'data': {'id': '{ID}','type': 'WEBSOCKET','attributes': {'locationId': '{LOCATION_ID}'}}}";
                StringContentProvider content = new StringContentProvider(WEBSOCKET_REQUEST_DATA.replace("'", "\"").replace("{ID}", id).replace("{LOCATION_ID}", locationId));
                request.content(content);

                logger.trace("request: {}", WEBSOCKET_REQUEST_DATA.replace("'","\"").replace("{ID}", id).replace("{LOCATION_ID}", locationId));
            }

            @Override
            public ApiWebsocketInfo afterRequest(ContentResponse contentResponse) {
                ReadContext ctx = JsonPath.parse(contentResponse.getContentAsString());
                ApiWebsocketInfo apiWebsocketInfo = new ApiWebsocketInfo();
                apiWebsocketInfo.websocketUrl = ctx.read("$.data.attributes.url");
                apiWebsocketInfo.websocketValidity = ctx.read("$.data.attributes.validity");
                return apiWebsocketInfo;
            }
        });
    }

    public void dispose() {
        logger.debug("Disposing GardenaSmartApi");
        if (restartScheduledFuture != null) {
            restartScheduledFuture.cancel(true);
        }
        restarting = false;
        if (httpClient != null) {
            try {
                httpClient.stop();
            } catch (Exception e) {
                // ignore
            }
            httpClient.destroy();
        }
        for(GardenaSmartWebSocket webSocket : webSockets) {
            webSocket.stop();
        }
    }

    private synchronized void restart() {
        if (!restarting) {
            restarting = true;
            logger.debug("Restarting GardenaSmartApi");
            dispose();
            try {
                start();
                restarting = false;
            } catch (GardenaException ex) {
                logger.warn("Restarting GardenaSmartApi failed: {}, try restart in {} seconds", ex.getMessage(), RESTART_DELAY_SECONDS);
                restartScheduledFuture = scheduler.schedule(() -> {
                    restart();
                }, RESTART_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void connectionClosed() {
        restart();
    }

    @Override
    public void onError(Throwable cause) {
        restart();
    }

    @Override
    public void onEvent(String msg) {
        ReadContext ctx = JsonPath.parse(msg);

        String type = ctx.read("$.type");
        if (!"LOCATION".equals(type) && !"DEVICE".equals(type)) {
            Device device = allDevicesById.get(StringUtils.substringBefore(ctx.read("id"), ":"));
            if (device != null) {
                mapEventToDevice(ctx, type, device);
            }
            else {
                logger.warn("Event for an unknown device: {}", msg);
            }
        }
    }

    /**
     * The polling data and the event data are completely different, so unfortunately we have to map the events to the polling data.
     */
    private void mapEventToDevice(ReadContext ctx, String type, Device device) {
        if ("SENSOR".equals(type)) {
            logger.debug("Sensor:Sensor data");
            setValue(device,"humidity","humidity", ctx.read("$.attributes.soilHumidity.value").toString());
            setValue(device,"soil_temperature", "temperature", ctx.read("$.attributes.soilTemperature.value").toString());
            setValue(device,"ambient_temperature", "temperature", ctx.read("$.attributes.ambientTemperature.value").toString());
            setValue(device,"light", "light", ctx.read("$.attributes.lightIntensity.value").toString());
        }
        else if ("MOWER".equals(type)) {
            String status = ctx.read("$.attributes.activity.value");
            if (isPathValid(ctx, "$.attributes.state.lastErrorCode")) {
                status = ctx.read("$.attributes.state.lastErrorCode");
            }
            setValue(device, "device_info", "connection_status", status);
        }
        else if ("watering_computer".equals(device.getCategory()) && "VALVE".equals(type)) {
            String activity = ctx.read("$.attributes.activity.value");
            setValue(device, "outlet","valve_open", "CLOSED".equals(activity) ? "off" : "on");
            if (isPathValid(ctx, "$.attributes.duration.value")) {
                setValue(device, "outlet","duration", "$.attributes.duration.value".toString());
            }
        }
        // TODO: check category for pressure pump
        else if ("???".equals(device.getCategory()) && "VALVE".equals(type)) {

        }
        // TODO: check category for irrigation control
        else if ("ic24".equals(device.getCategory()) && "VALVE".equals(type)) {
            String valveNumber = StringUtils.substringAfter(ctx.read("id"), ":");
            String activity = ctx.read("$.attributes.activity.value");
            if ("CLOSED".equals(activity)) {
                setValue(device, "watering","watering_timer_" + valveNumber, "off");
            }
            if (isPathValid(ctx, "$.attributes.duration.value")) {
                setValue(device, "watering","watering_timer_" + valveNumber, "$.attributes.duration.value".toString());
            }
        }
        else if ("POWER_SOCKET".equals(type)) {
            setValue(device, "power","power_timer", "$.attributes.activity.value".toLowerCase());
        }
        else if ("COMMON".equals(type)) {
            String batteryState = ctx.read("$.attributes.batteryState.value");
            if (!"NO_BATTERY".equals(batteryState)) {
                setValue(device, "battery", "level", ctx.read("$.attributes.batteryLevel.value").toString());
            }
            setValue(device, "radio", "quality", ctx.read("$.attributes.rfLinkLevel.value").toString());

            String connectionStatus = ctx.read("$.attributes.rfLinkState.value");
            if ("ONLINE".equals(connectionStatus)) {
                connectionStatus = "status_device_alive";
            }
            else if ("OFFLINE".equals(connectionStatus)) {
                connectionStatus = "status_device_unreachable";
            }
            else {
                connectionStatus = "unknown";
            }
            setValue(device, "device_info", "connection_status", connectionStatus);
        }
        eventListener.onDeviceUpdated(device);
    }

    private void setValue(Device device, String ability, String property, String value) {
        try {
            device.getAbility(ability).getProperty(property).setValue(new PropertyValue(value));
        } catch (GardenaException ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }

    private boolean isPathValid(ReadContext ctx, String path) {
        try {
            ctx.read(path);
            return true;
        } catch(PathNotFoundException e) {
            return false;
        }
    }

    private interface RequestExecutor<T> {
        String getContentType();
        void beforeRequest(Request request);
        T afterRequest(ContentResponse contentResponse);
    }
}
