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

package org.mitre.jet.ebts;

import org.mitre.jet.common.ByteBufferUtils;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;

/**
 * User: aday
 */
public class ImageUtils {

    //TODO: Store the byte array data in its raw format to improve performance

    public static int getWsqImagePosition(final ByteBuffer bb) {
        final byte[] header = DatatypeConverter.parseHexBinary("FFA0FF");
        return ByteBufferUtils.findIndex(bb, header);
    }

    public static int getJp2ImagePosition(final ByteBuffer bb) {
        final byte[] header =  DatatypeConverter.parseHexBinary("0000000C6A5020200D0A870A");
        return ByteBufferUtils.findIndex(bb, header);
    }

    public static int getJpgImagePosition(final ByteBuffer bb) {
        final byte[] header =  DatatypeConverter.parseHexBinary("FFD8FF");
        return ByteBufferUtils.findIndex(bb, header);
    }

    public static int getPngImagePosition(final ByteBuffer bb) {
        final byte[] header =  DatatypeConverter.parseHexBinary("89504E470D0A1A0A");
        return ByteBufferUtils.findIndex(bb, header);
    }
}
