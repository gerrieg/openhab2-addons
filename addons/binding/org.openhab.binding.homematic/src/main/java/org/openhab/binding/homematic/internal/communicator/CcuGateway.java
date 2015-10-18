/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.internal.communicator;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.openhab.binding.homematic.internal.common.HomematicConfig;
import org.openhab.binding.homematic.internal.communicator.client.UnknownRpcFailureException;
import org.openhab.binding.homematic.internal.communicator.parser.CcuLoadDeviceNamesParser;
import org.openhab.binding.homematic.internal.communicator.parser.CcuValueParser;
import org.openhab.binding.homematic.internal.communicator.parser.CcuVariablesAndScriptsParser;
import org.openhab.binding.homematic.internal.model.HmChannel;
import org.openhab.binding.homematic.internal.model.HmDatapoint;
import org.openhab.binding.homematic.internal.model.HmDevice;
import org.openhab.binding.homematic.internal.model.HmParamsetType;
import org.openhab.binding.homematic.internal.model.HmResult;
import org.openhab.binding.homematic.internal.model.TclScript;
import org.openhab.binding.homematic.internal.model.TclScriptDataList;
import org.openhab.binding.homematic.internal.model.TclScriptList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HomematicGateway implementation for a CCU.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public class CcuGateway extends AbstractHomematicGateway {
    private static final Logger logger = LoggerFactory.getLogger(CcuGateway.class);
    private static final boolean TRACE_ENABLED = logger.isTraceEnabled();

    private Map<String, String> tclregaScripts;
    private HttpClient httpClient;

    protected CcuGateway(String id, HomematicConfig config, HomematicGatewayListener eventListener) {
        super(id, config, eventListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void startClient() throws IOException {
        super.startClient();

        tclregaScripts = loadTclRegaScripts();

        httpClient = new HttpClient(new SimpleHttpConnectionManager(true));
        HttpClientParams params = httpClient.getParams();
        Long timeout = config.getTimeout() * 1000L;
        params.setConnectionManagerTimeout(timeout);
        params.setSoTimeout(timeout.intValue());
        params.setContentCharset(config.getEncoding());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void stopClient() {
        super.stopClient();
        tclregaScripts = null;
        httpClient = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadVariables(HmChannel channel) throws IOException {
        TclScriptDataList resultList = sendScriptByName("getAllVariables", TclScriptDataList.class);
        new CcuVariablesAndScriptsParser(channel).parse(resultList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadScripts(HmChannel channel) throws IOException {
        TclScriptDataList resultList = sendScriptByName("getAllPrograms", TclScriptDataList.class);
        new CcuVariablesAndScriptsParser(channel).parse(resultList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadDeviceNames(Collection<HmDevice> devices) throws IOException {
        TclScriptDataList resultList = sendScriptByName("getAllDeviceNames", TclScriptDataList.class);
        new CcuLoadDeviceNamesParser(devices).parse(resultList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadChannelValues(HmChannel channel) throws IOException {
        try {
            super.loadChannelValues(channel);
        } catch (UnknownRpcFailureException ex) {
            logger.debug(
                    "BinRpcMessage unknown RPC failure (-1 Failure), fetching values with TclRega script for device '{}'",
                    channel.getDevice().getAddress());

            Collection<String> dpNames = new ArrayList<String>();
            for (HmDatapoint dp : channel.getDatapoints().values()) {
                if (!dp.isVirtual() && dp.isReadable() && dp.getParamsetType() == HmParamsetType.VALUES) {
                    dpNames.add(dp.getName());
                }
            }
            if (dpNames.size() > 0) {
                HmDevice device = channel.getDevice();
                String channelName = String.format("%s.%s:%s.", device.getHmInterface().getName(), device.getAddress(),
                        channel.getNumber());
                String datapointNames = StringUtils.join(dpNames.toArray(), "\\t");
                TclScriptDataList resultList = sendScriptByName("getAllChannelValues", TclScriptDataList.class,
                        new String[] { "channel_name", "datapoint_names" },
                        new String[] { channelName, datapointNames });
                new CcuValueParser(channel).parse(resultList);
                channel.setInitialized(true);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setVariable(HmDatapoint dp, Object value) throws IOException {
        String strValue = StringUtils.replace(ObjectUtils.toString(value), "\"", "\\\"");
        if (dp.isStringType()) {
            strValue = "\"" + strValue + "\"";
        }
        HmResult result = sendScriptByName("setVariable", HmResult.class,
                new String[] { "variable_name", "variable_state" }, new String[] { dp.getInfo(), strValue });
        if (!result.isValid()) {
            throw new IOException("Unable to set CCU variable " + dp.getInfo());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void executeScript(HmDatapoint dp) throws IOException {
        HmResult result = sendScriptByName("executeProgram", HmResult.class, new String[] { "program_name" },
                new String[] { dp.getInfo() });
        if (!result.isValid()) {
            throw new IOException("Unable to start CCU program: " + dp.getInfo());
        }
    }

    /**
     * Sends a TclRega script to the CCU.
     */
    private <T> T sendScriptByName(String scriptName, Class<T> clazz) throws IOException {
        return sendScriptByName(scriptName, clazz, new String[] {}, null);
    }

    /**
     * Sends a TclRega script with the specified variables to the CCU.
     */
    private <T> T sendScriptByName(String scriptName, Class<T> clazz, String[] variableNames, String[] values)
            throws IOException {
        String script = tclregaScripts.get(scriptName);
        for (int i = 0; i < variableNames.length; i++) {
            script = StringUtils.replace(script, "{" + variableNames[i] + "}", values[i]);
        }
        return sendScript(script, clazz);
    }

    /**
     * Main method for sending a TclRega script and parsing the XML result.
     */
    @SuppressWarnings("unchecked")
    private synchronized <T> T sendScript(String script, Class<T> clazz) throws IOException {
        PostMethod post = null;
        try {
            script = StringUtils.trim(script);
            if (StringUtils.isEmpty(script)) {
                throw new RuntimeException("Homematic TclRegaScript is empty!");
            }
            if (TRACE_ENABLED) {
                logger.trace("TclRegaScript: {}", script);
            }

            post = new PostMethod(config.getTclRegaUrl());
            RequestEntity re = new ByteArrayRequestEntity(script.getBytes(config.getEncoding()));
            post.setRequestEntity(re);
            httpClient.executeMethod(post);

            String result = post.getResponseBodyAsString();
            result = StringUtils.substringBeforeLast(result, "<xml><exec>");
            if (TRACE_ENABLED) {
                logger.trace("Result TclRegaScript: {}", result);
            }

            Unmarshaller um = JAXBContext.newInstance(clazz).createUnmarshaller();
            return (T) um.unmarshal(new StringReader(result));
        } catch (Exception ex) {
            throw new IOException(ex.getMessage(), ex);
        } finally {
            if (post != null) {
                post.releaseConnection();
            }
        }
    }

    /**
     * Load predefined scripts from an XML file.
     */
    private Map<String, String> loadTclRegaScripts() throws IOException {
        try {
            Unmarshaller um = JAXBContext.newInstance(TclScriptList.class).createUnmarshaller();
            InputStream stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("homematic/tclrega-scripts.xml");
            TclScriptList scriptList = (TclScriptList) um.unmarshal(stream);

            Map<String, String> result = new HashMap<String, String>();
            for (TclScript script : scriptList.getScripts()) {
                result.put(script.name, StringUtils.trimToNull(script.data));
            }
            return result;
        } catch (JAXBException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

}
