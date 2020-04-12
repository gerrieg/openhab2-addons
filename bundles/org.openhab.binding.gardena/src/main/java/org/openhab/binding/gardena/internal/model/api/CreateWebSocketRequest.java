package org.openhab.binding.gardena.internal.model.api;

public class CreateWebSocketRequest {
    public CreateWebSocketDataItem data;

    public CreateWebSocketRequest(String locationId) {
        data = new CreateWebSocketDataItem();
        data.id = "wsreq-" + locationId;
        data.type = "WEBSOCKET";
        data.attributes = new CreateWebSocket();
        data.attributes.locationId = locationId;
    }
}
