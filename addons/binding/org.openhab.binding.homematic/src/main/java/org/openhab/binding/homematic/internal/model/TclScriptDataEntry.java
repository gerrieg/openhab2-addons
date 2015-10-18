/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.internal.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Simple class with the JAXB mapping for a data entry returned from a TclRega script.
 *
 * @author Gerhard Riegler - Initial contribution
 */
@XmlRootElement(name = "entry")
@XmlAccessorType(XmlAccessType.FIELD)
public class TclScriptDataEntry {

    @XmlAttribute(name = "name", required = true)
    public String name;

    @XmlAttribute(name = "description")
    public String description;

    @XmlAttribute(name = "value")
    public String value;

    @XmlAttribute(name = "valueType", required = true)
    public String valueType;

    @XmlAttribute(name = "readOnly")
    public boolean readOnly;

    @XmlAttribute(name = "options")
    public String options;

    @XmlAttribute(name = "min")
    public String minValue;

    @XmlAttribute(name = "max")
    public String maxValue;

    @XmlAttribute(name = "unit")
    public String unit;

}
