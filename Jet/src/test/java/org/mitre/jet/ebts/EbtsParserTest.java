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
import org.mitre.jet.ebts.records.GenericRecord;
import org.mitre.jet.ebts.records.LogicalRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;


/**
 * The Class EbtsParserTest.
 */
public class EbtsParserTest {

    private static final Logger log = LoggerFactory.getLogger(EbtsParserTest.class);
    Ebts ebts = null;

    //TODO: Modify the tests to include occurrences with empty subfields (trailing especially)
    //and empty occurrences

    /**
     * Parser test before.
     *
     * @throws Exception the exception
     */
    public void parserTestBefore() throws Exception {
        EbtsParser parser = new EbtsParser();
        File file = new File(ClassLoader.getSystemResource("EFT/S001-01-t10_01.eft").toURI());
        ebts = parser.parse(file);
    }

    /**
     * Record test.
     *
     * @throws Exception the exception
     */
    @Test
    public void recordTest() throws Exception {
        parserTestBefore();
        assertNotNull(ebts);
        assertEquals(3,ebts.getAllRecords().size());
        assertEquals(10,ebts.getAllRecords().get(2).getRecordType());
    }

    /**
     * Type1 test.
     *
     * @throws Exception the exception
     */
    @Test
    public void type1Test() throws Exception {
        parserTestBefore();
        assertNotNull(ebts);
        GenericRecord type1 = (GenericRecord) ebts.getRecordsByType(1).get(0);
        assertFalse(type1.getFields().isEmpty());
        //Check Type 1 Content
        assertEquals("122",type1.getField(1).toString());
        assertEquals("122",type1.getField(1).getOccurrences().get(0).toString());
        assertEquals("2",type1.getField(3).getOccurrences().get(1).getSubFields().get(0).toString());
        assertEquals("00",type1.getField(3).getOccurrences().get(1).getSubFields().get(1).toString());
        assertEquals("WVMEDS001",type1.getField("ORI").getOccurrences().get(0).getSubFields().get(0).toString());
    }

    /**
     * Type2 test.
     *
     * @throws Exception the exception
     */
    @Test
    public void type2Test() throws Exception {
        parserTestBefore();
        assertNotNull(ebts);
        GenericRecord type2 = (GenericRecord) ebts.getRecordsByType(2).get(0);
        assertFalse(type2.getFields().isEmpty());
        assertEquals("20",type2.getField(79).toString());
    }

    /**
     * Type10 test.
     *
     * @throws Exception the exception
     */
    @Test
    public void type10Test() throws Exception {
        parserTestBefore();
        assertNotNull(ebts);
        GenericRecord type10 = (GenericRecord) ebts.getRecordsByType(10).get(0);
        assertFalse(type10.getFields().isEmpty());
        assertEquals("JPEGB",type10.getField(11).toString());
        assertTrue(type10.hasImageData());
        assertEquals(34086,type10.getImageData().length);
    }

    @Test
    public void testDescriptiveOnly() throws Exception {
        File file = new File(ClassLoader.getSystemResource("EFT/S001-01-t10_01.eft").toURI());

        EbtsParser ebtsParser = new EbtsParser();
        Ebts ebtsDescriptiveOnly = ebtsParser.parse(file,ParseType.DESCRIPTIVE_ONLY);

        Ebts ebts = ebtsParser.parse(file,ParseType.FULL);

        //Type 1 and 2 only parsed
        assertEquals(2,ebtsDescriptiveOnly.getAllRecords().size());

        //Same number of fields
        assertEquals(ebts.getRecordsByType(1).get(0).getLength(),ebtsDescriptiveOnly.getRecordsByType(1).get(0).getLength());
        assertEquals(ebts.getRecordsByType(2).get(0).getLength(),ebtsDescriptiveOnly.getRecordsByType(2).get(0).getLength());

    }

    @Test
    public void type10EmptyImageTest() throws Exception {
        File file = new File(ClassLoader.getSystemResource("EFT/empty_image.eft").toURI());

        EbtsParser ebtsParser = new EbtsParser();
        //previously threw exception
        Ebts ebts = ebtsParser.parse(file,ParseType.FULL);

        assertNotNull(ebts);
        GenericRecord type10 = (GenericRecord) ebts.getRecordsByType(10).get(0);
        assertFalse(type10.getFields().isEmpty());
        assertEquals("JPEGB",type10.getField(11).toString());
        assertFalse(type10.hasImageData());
    }

//    @Test
//    public void type7ImageBoundsTest() throws Exception {
//        File file = new File(ClassLoader.getSystemResource("EFT/type7_image_oob.eft").toURI());
//
//        EbtsParser ebtsParser = new EbtsParser();
//        //previously threw exception
//        Ebts ebts = ebtsParser.parse(file, ParseType.FULL);
//
//        ebts.getRecordsByType(7);
//    }

}
