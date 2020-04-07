package org.openhab.binding.gardena.internal;

/**
 * The {@link GardenaSmartWebSocketListener} is called by the {@link GardenaSmartWebSocket} on new Events and if the {@link GardenaSmartWebSocket}
 * closed the connection.
 *
 * @author Gerhard Riegler - Initial contribution
 */

public interface GardenaSmartWebSocketListener {
    /**
     * This method is called, when the evenRunner stops abnormally (statuscode <> 1000).
     */
    void connectionClosed();

    /**
     * This method is called when the Gardena websocket services throws an onError.
     *
     * @param cause
     */
    void onError(Throwable cause);
    /**
     * This method is called, whenever a new event comes from the Gardena service.
     *
     * @param msg
     */
    void onEvent(String msg);

}
