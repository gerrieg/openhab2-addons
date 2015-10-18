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
 * Simple class with the JAXB mapping for a list of TclRega scripts. Used to load the resource
 * homematic/tclrega-scripts.xml.
 * 
 * @author Gerhard Riegler - Initial contribution
 */
@XmlRootElement(name = "scripts")
@XmlAccessorType(XmlAccessType.FIELD)
public class TclScriptList {

    @XmlElement(name = "script")
    private List<TclScript> scripts = new ArrayList<TclScript>();

    /**
     * Returns all scripts.
     */
    public List<TclScript> getScripts() {
        return scripts;
    }

}
