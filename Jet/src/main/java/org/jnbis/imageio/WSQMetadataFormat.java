/*
 * Copyright 2014 The MITRE Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jnbis.imageio;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataFormat;
import javax.imageio.metadata.IIOMetadataFormatImpl;

/**
 * Specified the native metadata for WSQ Images. It is a simple list of key-value pairs that can be mapped almost directly to NISTCOM comments.
 * <p>
 * The tree will look like this:
 * <pre>{@code
 <org.jmrtd.imageio.WSQMetadata_1.0>
 <property name="PPI" value="500.0"/>
 <property name="WSQ_BITRATE" value="1.5"/>
 </org.jmrtd.imageio.WSQMetadata_1.0>
 }</pre>
 * 
 * @author Paulo Costa <me@paulo.costa.nom.br>
 */
public class WSQMetadataFormat extends IIOMetadataFormatImpl {
    public static final IIOMetadataFormat instance = new WSQMetadataFormat();

    public static final String nativeMetadataFormatName = "org.jmrtd.imageio.WSQMetadata_1.0";

    public WSQMetadataFormat() {
        super(
                nativeMetadataFormatName, 
                CHILD_POLICY_SEQUENCE);        

        addElement(
                "property",
                nativeMetadataFormatName,
                CHILD_POLICY_EMPTY);
        addAttribute("property", "name",
                DATATYPE_STRING, 
                true, //Required 
                null);
        addAttribute("property", "value",
                DATATYPE_STRING, 
                false, //Value==null => Remove the key 
                null);

        addElement(
                "comment",
                nativeMetadataFormatName,
                CHILD_POLICY_EMPTY);
        addAttribute("comment", "value",
                DATATYPE_STRING, 
                false, //Value==null => Remove the key 
                null);
    }

    @Override
    public boolean canNodeAppear(final String elementName, final ImageTypeSpecifier imageType) {
        return true;
    }

    public static IIOMetadataFormat getInstance() {
        return instance;
    }
}
