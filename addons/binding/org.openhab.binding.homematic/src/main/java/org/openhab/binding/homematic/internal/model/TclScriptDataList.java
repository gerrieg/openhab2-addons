/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.internal.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Simple class with the JAXB mapping for a list of entries returned from a TclRega script.
 *
 * @author Gerhard Riegler - Initial contribution
 */
@XmlRootElement(name = "list")
@XmlAccessorType(XmlAccessType.FIELD)
public class TclScriptDataList {

    @XmlElement(name = "entry")
    private List<TclScriptDataEntry> entries = new ArrayList<TclScriptDataEntry>();

    /**
     * Returns all entries.
     */
    public List<TclScriptDataEntry> getEntries() {
        return entries;
    }

}
