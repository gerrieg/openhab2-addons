/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.gardena.internal.config;

import com.google.gson.annotations.SerializedName;

/**
 * Gardena attribute wrapper for valid Gardena JSON serialization.
 *
 * @author Gerhard Riegler - Initial contribution
 */

public class GardenaAttributeWrapper {
    @SerializedName("attributes")
    private Object attributes;

    @SerializedName("type")
    private String type = "token";

    @SerializedName("id")
    private String id;

    public void setType(String type) {
        this.type = type;
    }

    public void setId(String id) {
        this.id = id;
    }

    public GardenaAttributeWrapper() {
    }

    public GardenaAttributeWrapper(Object attributes) {
        this.attributes = attributes;
    }

}
