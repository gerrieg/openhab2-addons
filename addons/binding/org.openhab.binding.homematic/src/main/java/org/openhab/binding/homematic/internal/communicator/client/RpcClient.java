/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.internal.communicator.client;

import java.io.IOException;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.homematic.internal.common.HomematicConfig;
import org.openhab.binding.homematic.internal.communicator.message.BinRpcMessage;
import org.openhab.binding.homematic.internal.communicator.parser.GetAllScriptsParser;
import org.openhab.binding.homematic.internal.communicator.parser.GetAllSystemVariablesParser;
import org.openhab.binding.homematic.internal.communicator.parser.GetDeviceDescriptionParser;
import org.openhab.binding.homematic.internal.communicator.parser.GetParamsetDescriptionParser;
import org.openhab.binding.homematic.internal.communicator.parser.GetParamsetParser;
import org.openhab.binding.homematic.internal.communicator.parser.GetValueParser;
import org.openhab.binding.homematic.internal.communicator.parser.HomegearLoadDeviceNamesParser;
import org.openhab.binding.homematic.internal.communicator.parser.ListBidcosInterfacesParser;
import org.openhab.binding.homematic.internal.communicator.parser.ListDevicesParser;
import org.openhab.binding.homematic.internal.communicator.parser.RpcResponseParser;
import org.openhab.binding.homematic.internal.model.HmChannel;
import org.openhab.binding.homematic.internal.model.HmDatapoint;
import org.openhab.binding.homematic.internal.model.HmDevice;
import org.openhab.binding.homematic.internal.model.HmGatewayInfo;
import org.openhab.binding.homematic.internal.model.HmInterface;
import org.openhab.binding.homematic.internal.model.HmParamsetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client implementation for sending messages via BIN-RPC to a Homematic gateway.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class RpcClient {
    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);
    private static final boolean TRACE_ENABLED = logger.isTraceEnabled();
    private static final int MAX_SOCKET_RETRY = 1;

    private HomematicConfig config;
    private SocketHandler socketHandler;

    public RpcClient(HomematicConfig config) {
        this.config = config;
        socketHandler = new SocketHandler(config);
    }

    public void dispose() {
        socketHandler.flush();
    }

    /**
     * Register a callback for the specified interface where the Homematic gateway can send its events.
     */
    public void init(HmInterface hmInterface, String clientId) throws IOException {
        BinRpcMessage request = new BinRpcMessage("init", config.getEncoding());
        request.addArg(config.getBinRpcCallbackUrl());
        request.addArg(clientId);
        sendMessage(config.getRpcPort(hmInterface), request);
        socketHandler.removeSocket(config.getRpcPort(hmInterface));
    }

    /**
     * Release a callback for the specified interface.
     */
    public void release(HmInterface hmInterface) throws IOException {
        BinRpcMessage request = new BinRpcMessage("init", config.getEncoding());
        request.addArg(config.getBinRpcCallbackUrl());
        sendMessage(config.getRpcPort(hmInterface), request);
    }

    /**
     * Returns all variable metadata and values from a Homegear gateway.
     */
    public void getAllSystemVariables(HmChannel channel) throws IOException {
        BinRpcMessage request = new BinRpcMessage("getAllSystemVariables", config.getEncoding());
        new GetAllSystemVariablesParser(channel).parse(sendMessage(config.getRpcPort(channel), request));
    }

    /**
     * Loads all device names from a Homegear gateway.
     */
    public void loadDeviceNames(HmInterface hmInterface, Collection<HmDevice> devices) throws IOException {
        BinRpcMessage request = new BinRpcMessage("getDeviceInfo", config.getEncoding());
        new HomegearLoadDeviceNamesParser(devices).parse(sendMessage(config.getRpcPort(hmInterface), request));
    }

    /**
     * Returns true, if the interface is available on the gateway.
     */
    public void checkInterface(HmInterface hmInterface) throws IOException {
        BinRpcMessage request = new BinRpcMessage("init", config.getEncoding());
        request.addArg("binary://openhab.validation:1000");
        sendMessage(config.getRpcPort(hmInterface), request);
    }

    /**
     * Validates the connection to the interface by calling the listBidcosInterfaces method.
     */
    public void validateConnection(HmInterface hmInterface) throws IOException {
        BinRpcMessage request = new BinRpcMessage("listBidcosInterfaces", config.getEncoding());
        sendMessage(config.getRpcPort(hmInterface), request);
    }

    /**
     * Returns all script metadata from a Homegear gateway.
     */
    public void getAllScripts(HmChannel channel) throws IOException {
        BinRpcMessage request = new BinRpcMessage("getAllScripts", config.getEncoding());
        new GetAllScriptsParser(channel).parse(sendMessage(config.getRpcPort(channel), request));
    }

    /**
     * Returns all device and channel metadata.
     */
    public Collection<HmDevice> listDevices(HmInterface hmInterface) throws IOException {
        BinRpcMessage request = new BinRpcMessage("listDevices", config.getEncoding());
        return new ListDevicesParser(hmInterface, config).parse(sendMessage(config.getRpcPort(hmInterface), request));
    }

    /**
     * Loads all datapoint metadata into the given channel.
     */
    public void addChannelDatapoints(HmChannel channel, HmParamsetType paramsetType) throws IOException {
        BinRpcMessage request = new BinRpcMessage("getParamsetDescription", config.getEncoding());
        request.addArg(getRpcAddress(channel.getDevice().getAddress()) + ":" + channel.getNumber());
        request.addArg(paramsetType.toString());
        new GetParamsetDescriptionParser(channel, paramsetType).parse(sendMessage(config.getRpcPort(channel), request));
    }

    /**
     * Sets all datapoint values for the given channel.
     */
    public void setChannelDatapointValues(HmChannel channel, HmParamsetType paramsetType) throws IOException {
        BinRpcMessage request = new BinRpcMessage("getParamset", config.getEncoding());
        request.addArg(getRpcAddress(channel.getDevice().getAddress()) + ":" + channel.getNumber());
        request.addArg(paramsetType.toString());
        if (channel.getDevice().getHmInterface() == HmInterface.CUXD && paramsetType == HmParamsetType.VALUES) {
            setChannelDatapointValues(channel);
        } else {
            try {
                new GetParamsetParser(channel, paramsetType).parse(sendMessage(config.getRpcPort(channel), request));
            } catch (UnknownRpcFailureException ex) {
                if (paramsetType == HmParamsetType.VALUES) {
                    logger.debug(
                            "BinRpcMessage unknown RPC failure (-1 Failure), fetching values with another API method for device '{}'",
                            channel.getDevice().getAddress());
                    setChannelDatapointValues(channel);
                } else {
                    throw ex;
                }
            }
        }
    }

    /**
     * Reads all VALUES datapoints individually, fallback method if setChannelDatapointValues throws a -1 Failure
     * exception.
     */
    private void setChannelDatapointValues(HmChannel channel) throws IOException {
        for (HmDatapoint dp : channel.getDatapoints().values()) {
            if (dp.isReadable() && !dp.isVirtual() && dp.getParamsetType() == HmParamsetType.VALUES) {
                BinRpcMessage request = new BinRpcMessage("getValue", config.getEncoding());
                request.addArg(getRpcAddress(channel.getDevice().getAddress()) + ":" + channel.getNumber());
                request.addArg(dp.getName());
                new GetValueParser(dp).parse(sendMessage(config.getRpcPort(channel), request));
            }
        }
    }

    /**
     * Tries to identify the gateway and returns the GatewayInfo.
     */
    public HmGatewayInfo getGatewayInfo() throws IOException {
        BinRpcMessage request = new BinRpcMessage("getDeviceDescription", config.getEncoding());
        request.addArg("BidCoS-RF");
        GetDeviceDescriptionParser ddParser = new GetDeviceDescriptionParser();
        ddParser.parse(sendMessage(config.getRpcPort(HmInterface.RF), request));

        boolean isHomegear = StringUtils.equalsIgnoreCase(ddParser.getType(), "Homegear");

        request = new BinRpcMessage("listBidcosInterfaces", config.getEncoding());
        ListBidcosInterfacesParser biParser = new ListBidcosInterfacesParser(ddParser.getDeviceInterface(), isHomegear);
        biParser.parse(sendMessage(config.getRpcPort(HmInterface.RF), request));

        HmGatewayInfo gatewayInfo = new HmGatewayInfo();
        gatewayInfo.setAddress(biParser.getGatewayAddress());
        if (isHomegear) {
            gatewayInfo.setId(HmGatewayInfo.ID_HOMEGEAR);
            gatewayInfo.setType(ddParser.getType());
            gatewayInfo.setFirmware(ddParser.getFirmware());
        } else if (StringUtils.startsWithIgnoreCase(biParser.getType(), "CCU")
                || config.getGatewayType().equalsIgnoreCase(HomematicConfig.GATEWAY_TYPE_CCU)) {
            gatewayInfo.setId(HmGatewayInfo.ID_CCU);
            String type = StringUtils.isBlank(biParser.getType()) ? "CCU" : biParser.getType();
            gatewayInfo.setType(type);
            gatewayInfo.setFirmware(ddParser.getFirmware());
        } else {
            gatewayInfo.setId(HmGatewayInfo.ID_DEFAULT);
            gatewayInfo.setType(biParser.getType());
            gatewayInfo.setFirmware(biParser.getFirmware());
        }

        return gatewayInfo;
    }

    /**
     * Sets the value of the datapoint.
     */
    public void setDatapointValue(HmDatapoint dp, Object value) throws IOException {
        if (dp.isIntegerType() && value instanceof Double) {
            value = ((Number) value).intValue();
        }

        BinRpcMessage request;
        if (HmParamsetType.VALUES == dp.getParamsetType()) {
            request = new BinRpcMessage("setValue", config.getEncoding());
            request.addArg(getRpcAddress(dp.getChannel().getDevice().getAddress()) + ":" + dp.getChannel().getNumber());
            request.addArg(dp.getName());
            request.addArg(value);
        } else {
            request = new BinRpcMessage("putParamset", config.getEncoding());
            request.addArg(getRpcAddress(dp.getChannel().getDevice().getAddress()) + ":" + dp.getChannel().getNumber());
            request.addArg(HmParamsetType.MASTER.toString());
            Map<String, Object> paramSet = new HashMap<String, Object>();
            paramSet.put(dp.getName(), value);
            request.addArg(paramSet);
        }
        sendMessage(config.getRpcPort(dp.getChannel()), request);
    }

    /**
     * Sets the value of a system variable on a Homegear gateway.
     */
    public void setSystemVariable(HmDatapoint dp, Object value) throws IOException {
        BinRpcMessage request = new BinRpcMessage("setSystemVariable", config.getEncoding());
        request.addArg(dp.getInfo());
        request.addArg(value);
        sendMessage(config.getRpcPort(dp.getChannel()), request);
    }

    /**
     * Executes a script on the Homegear gateway.
     */
    public void executeScript(HmDatapoint dp) throws IOException {
        BinRpcMessage request = new BinRpcMessage("runScript", config.getEncoding());
        request.addArg(dp.getInfo());
        sendMessage(config.getRpcPort(dp.getChannel()), request);
    }

    /**
     * Enables/disables the install mode for given seconds.
     */
    public void setInstallMode(HmInterface hmInterface, boolean enable, int seconds) throws IOException {
        BinRpcMessage request = new BinRpcMessage("setInstallMode", config.getEncoding());
        request.addArg(enable);
        request.addArg(seconds);
        request.addArg(1);
        sendMessage(config.getRpcPort(hmInterface), request);
    }

    /**
     * Deletes the device from the gateway.
     */
    public void deleteDevice(HmDevice device, int flags) throws IOException {
        BinRpcMessage request = new BinRpcMessage("deleteDevice", config.getEncoding());
        request.addArg(device.getAddress());
        request.addArg(flags);
        sendMessage(config.getRpcPort(device.getHmInterface()), request);
    }

    /**
     * Returns the rpc address from a device address, correctly handling groups.
     */
    private String getRpcAddress(String address) {
        if (address != null && address.startsWith("T-")) {
            address = "*" + address.substring(2);
        }
        return address;
    }

    /**
     * Sends a BIN-RPC message and parses the response to see if there was an error.
     */
    private synchronized Object[] sendMessage(int port, BinRpcMessage request) throws IOException {
        if (TRACE_ENABLED) {
            logger.trace("Client request BinRpcMessage {}", request);
        }
        return sendSocketMessage(port, request, 0);
    }

    /**
     * Sends the message, retries if there was an error.
     */
    private Object[] sendSocketMessage(int port, BinRpcMessage request, int socketRetryCounter) throws IOException {
        BinRpcMessage resp = null;
        try {
            Socket socket = socketHandler.getSocket(port);
            socket.getOutputStream().write(request.getBinRpcData());
            resp = new BinRpcMessage(socket.getInputStream(), false, config.getEncoding());
            return new RpcResponseParser(request).parse(resp.getMessageData());
        } catch (UnknownRpcFailureException rpcEx) {
            // throw immediately, don't retry the message
            throw rpcEx;
        } catch (IOException ioEx) {
            if ("init".equals(request.getMethodName()) || socketRetryCounter >= MAX_SOCKET_RETRY) {
                throw ioEx;
            } else {
                socketRetryCounter++;
                logger.debug("BinRpcMessage socket failure, sending message again {}/{}", socketRetryCounter,
                        MAX_SOCKET_RETRY);
                socketHandler.removeSocket(port);
                return sendSocketMessage(port, request, socketRetryCounter);
            }
        } finally {
            if (TRACE_ENABLED) {
                logger.trace("Client response BinRpcMessage: {}", resp == null ? "null" : resp.toString());
            }
        }
    }

}
