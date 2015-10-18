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
import javax.xml.bind.annotation.XmlElement;

/**
 * Simple class with the JAXB mapping for a TclRega script.
 *
 * @author Gerhard Riegler - Initial contribution
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class TclScript {

    @XmlAttribute(name = "name", required = true)
    public String name;

    @XmlElement(name = "data", required = true)
    public String data;

}
