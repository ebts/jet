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

import com.google.common.io.Files;
import org.junit.Test;
import org.mitre.jet.common.ByteBufferUtils;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.ebts.field.Occurrence;
import org.mitre.jet.ebts.records.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// TODO: Auto-generated Javadoc
/**
 * The Class EbtsBuilderTests.
 */
public class EbtsBuilderTests {

    private static final Logger log = LoggerFactory.getLogger(EbtsParserTests.class);

    /**
     * Parses the build test.
     *
     * @throws Exception the exception
     */
    @Test
    public void parseBuildTest() throws Exception {
        EbtsParser parser = new EbtsParser();
        File file = new File(ClassLoader.getSystemResource("EFT/S001-01-t10_01.eft").toURI());
        Ebts ebts = parser.parse(file);
        EbtsBuilder builder = new EbtsBuilder(1);
        Files.write(builder.build(ebts), new File("/tmp/test.eft"));
    }

    /**
     * Basic builder test.
     *
     * @throws Exception the exception
     */
    @Test
    public void basicBuilderTest() throws Exception {
        Ebts ebts = new Ebts();
        GenericRecord type1 = new GenericRecord(1);
        type1.setField(3, new Field("0400"));
        type1.setField(8, new Field("WVMEDS001"));

        GenericRecord type2 = new GenericRecord(2);
        type2.setField(2, new Field("04"));
        type2.setField(19, new Field("Smith,John"));
        type2.getField(19).getOccurrences().add(new Occurrence("Smith,Johnny"));
        type2.setField(18, new Field("Smith,Jo"));
        type2.setField(41, new Field("B"));
        type2.setField(40, new Field("A"));

        List<String> strings = new ArrayList<String>();
        strings.add("Test1");
        strings.add("Test2");
        strings.add("Test3");
        List<Occurrence> occs = EbtsUtils.convertStringList(strings);
        occs.add(new Occurrence("HI"));
        occs.remove(new Occurrence("HI"));

        type2.setField(50,new Field(occs));

        GenericRecord type10 = new GenericRecord(10);
        type10.setField(6, new Field("600"));
        Random rand = new Random();
        byte[] imageData = new byte[10];
        rand.nextBytes(imageData);
        type10.setImageData(imageData);
        ebts.addRecord(type1);
        ebts.addRecord(type10);
        ebts.addRecord(type2);



        EbtsBuilder builder = new EbtsBuilder(2);
        byte[] data = builder.build(ebts);

        log.info("EBTS Length: {}",data.length);

        ByteBuffer bb = ByteBuffer.wrap(data);
        assertTrue(ByteBufferUtils.find(bb, "A".getBytes()[0]) < ByteBufferUtils.find(bb, "B".getBytes()[0]));

        assertEquals(200,data.length);
        new EbtsParser().parse(data);

    }

    @Test
    public void idcCreationTest() throws Exception {
        Ebts ebts = new Ebts();
        GenericRecord type1 = new GenericRecord(1);
        type1.setField(3, new Field("0400"));
        type1.setField(8, new Field("WVMEDS001"));

        GenericRecord type2 = new GenericRecord(2);
        type2.setField(2, new Field("04"));
        type2.setField(19, new Field("Smith,John"));

        GenericRecord type10 = new GenericRecord(10);
        ebts.addRecord(type1);
        ebts.addRecord(type2);
        ebts.addRecord(type10);
        ebts.addRecord(type10);
        ebts.addRecord(type10);

        byte[] data = new EbtsBuilder().build(ebts);
        ebts = new EbtsParser().parse(data);


    }

}
