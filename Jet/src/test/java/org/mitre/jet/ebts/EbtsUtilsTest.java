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
import org.mitre.jet.ebts.field.Occurrence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class EbtsUtilsTest {
    private static final Logger log = LoggerFactory.getLogger(EbtsUtilsTest.class);
    @Test
    public void testTransforms() throws Exception {

        List<String> strings = new ArrayList<String>();
        strings.add("Test1");
        strings.add("Test2");
        strings.add("Test3");
        List<Occurrence> occs = EbtsUtils.convertStringList(strings);
        occs.add(new Occurrence("HI"));
        occs.remove(new Occurrence("HI"));

    }

    @Test
    public void testByteArrayToInt() throws  Exception {
        byte[] data = new byte[4];
        data[0] = 0x00;
        data[1] = 0x00;
        data[2] = (byte) 0x98;
        data[3] = (byte) 0xF2;

        int val = EbtsUtils.byteArrayToInt(data);
        assertEquals(39154,val);

        log.debug("{}",val);

        data = new byte[1];
        data[0] = (byte) 0xFF;

        val = EbtsUtils.byteArrayToInt(data);

        log.debug("{}",val);

        data = new byte[2];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xFF;

        val = EbtsUtils.byteArrayToInt(data);

        log.debug("{}",val);

    }

    @Test
    public void bitFun() throws Exception {
        System.out.println((int)((0xF0 & 0xFF) << 8 | (0x01 & 0xFF)));
        System.out.println((int)((0x01 & 0xFF) | (0xF0 & 0xFF) << 8));
    }
}
