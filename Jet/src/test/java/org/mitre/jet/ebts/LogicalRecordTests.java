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

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;

import org.junit.Test;
import org.mitre.jet.ebts.records.BinaryHeaderImageRecord;
import org.mitre.jet.ebts.records.GenericRecord;
import org.mitre.jet.ebts.records.LogicalRecord;

/**
 * The Class LogicalRecordTests.
 */
public class LogicalRecordTests {

    @Test
    public void getImageDataGenericRecordEmptyTest() throws Exception {

        List<Integer> allRecordTypes = Lists.newArrayList(1, 2, 9, 10, 13, 14, 15, 16, 17);

        for(Integer recordNumber : allRecordTypes) {
            LogicalRecord record = new GenericRecord(recordNumber);
            assertEquals(0,record.getImageData().length);
        }
    }

    @Test
    public void getImageDataBinaryRecordEmptyTest() throws Exception {

        List<Integer> allRecordTypes = Lists.newArrayList(3, 4, 5, 6);

        for(Integer recordNumber : allRecordTypes) {
            BinaryHeaderImageRecord record = new BinaryHeaderImageRecord(recordNumber);
            assertEquals(0,record.getImageData().length);
        }
    }
}
