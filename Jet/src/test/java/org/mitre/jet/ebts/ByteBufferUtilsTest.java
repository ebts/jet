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

import org.junit.Test;
import org.mitre.jet.common.ByteBufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class ByteBufferUtilsTest {
    private static final Logger log = LoggerFactory.getLogger(ByteBufferUtilsTest.class);


    @Test
    public void testFindByteArray() throws Exception {

        byte[] data = "0101023adsnaifbasdfsafsaf saffr45df".getBytes();

        int index = ByteBufferUtils.findIndex(ByteBuffer.wrap(data),"ad".getBytes());
        assertEquals(7,index);

        //Test a second time to ensure we aren't moving our buffer
        index = ByteBufferUtils.findIndex(ByteBuffer.wrap(data),"ad".getBytes());
        assertEquals(7,index);

        index = ByteBufferUtils.findIndex(ByteBuffer.wrap(data),"zz".getBytes());
        assertEquals(-1,index);

        data = "".getBytes();
        index = ByteBufferUtils.findIndex(ByteBuffer.wrap(data),"ad".getBytes());
        assertEquals(-1,index);

        data = "".getBytes();
        index = ByteBufferUtils.findIndex(ByteBuffer.wrap(data),"".getBytes());
        assertEquals(-1,index);

    }

}
