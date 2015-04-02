
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

import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.ebts.field.Occurrence;
import org.mitre.jet.ebts.field.SubField;
import org.mitre.jet.ebts.records.BinaryHeaderImageRecord;
import org.mitre.jet.ebts.records.GenericRecord;
import org.mitre.jet.ebts.records.LogicalRecord;
import org.mitre.jet.exceptions.EbtsBuildingException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

// TODO: Auto-generated Javadoc
/**
 * The Class EbtsBuilder.
 *
 * @author ADAY
 */
public class EbtsBuilder {

    private int minLength = 2;
    private static final Set<Integer> binaryHeaderTypes = EbtsUtils.getBinaryHeaderTypes();
    private static final Set<Integer> genericTypes = EbtsUtils.getGenericRecordTypes();

    private Ebts ebts;

    /**
     * Create an instance of an Ebts builder, which is used to convert the data
     * from an Ebts instance into a serialized legacy Ebts file.
     *
     */
    public EbtsBuilder() {}

    /**
     * Create an instance of an Ebts builder, which is used to convert the data
     * from an Ebts instance into a serialized legacy Ebts file.
     *
     * @param precedingZeros The number of preceding zeros to use on fields
     * (e.g. 0 => 1.1, 1=> 1.01, 2=> 1.001, etc)
     */
    public EbtsBuilder(final int precedingZeros) {

        setPrecedingZeros(precedingZeros);
    }

    /**
     * Fix cnt.
     *
     * @param record the record
     */
    private void fixCountField(final GenericRecord record) {

        final List<Occurrence> occurrences = new ArrayList<Occurrence>();

        for (final LogicalRecord logicalRecord : ebts.getAllRecords()) {
            final Occurrence occurrence = new Occurrence();
            //First occurrence contains 1 - # of records
            if (logicalRecord.getRecordType() == 1) {
                occurrence.getSubFields().add(new SubField("1"));
                final String length = String.format("%02d", ebts.getAllRecords().size()-1);
                occurrence.getSubFields().add(new SubField(length));
            } 
            //Subsequent occurrences contain FieldOccurrence # - IDC
            else {
                occurrence.getSubFields().add(new SubField(Integer.toString(logicalRecord.getRecordType())));
                int nextIdc = logicalRecord.getIdc();
                if (logicalRecord.getIdc() == -1) {
                    nextIdc = getNextAvailableIDC();
                }
                final String idc = String.format("%02d", nextIdc);
                logicalRecord.setIdc((byte) nextIdc);
                occurrence.getSubFields().add(new SubField(idc));
            }
            occurrences.add(occurrence);
        }
        record.setField(3, new Field(occurrences));
    }

    /**
     * Gets the next available idc.
     *
     * @return the next available idc
     */
    private int getNextAvailableIDC() {

        int highestIdc = -1;
        for (final LogicalRecord rec : ebts.getAllRecords()) {
            if (rec.getRecordType() != 1) {

                final Field idcField = rec.getField(2);

                if (idcField == null) {
                    continue;
                }

                final String idcString = idcField.toString();

                if (idcString.isEmpty()) {
                    continue;
                }

                final int currentIdc = Integer.parseInt(idcString);

                if (currentIdc > highestIdc) {
                    highestIdc = currentIdc;
                }
            }
        }

        return highestIdc + 1;
    }

    /**
     * Generic builder.
     *
     * @param record the record
     * @param baos the baos
     * @throws EbtsBuildingException the ebts building exception
     */
    private void genericBuilder(final GenericRecord record, final ByteArrayOutputStream baos) throws EbtsBuildingException {
        final ByteArrayOutputStream recordOutputStream = new ByteArrayOutputStream();
        final int recordType = record.getRecordType();

        //Correct the idc count field if it is a Type 1 record.
        if (recordType == 1) {
            fixCountField(record);
        }

        try {
            int fieldCount = 0;

            for (final Map.Entry<Integer, Field> entry : record.getFields().entrySet()) {
                ++fieldCount;
                // We skip the first field as the record length needs to be recalculated.
                if (entry.getKey() == 1) {
                    continue;
                }

                final String tag = recordType + "." + String.format("%0" + minLength + "d", entry.getKey()) + ":";

                recordOutputStream.write(tag.getBytes());
                recordOutputStream.write(entry.getValue().getData());

                if (fieldCount != record.getFields().keySet().size()) {
                    recordOutputStream.write((int) EbtsConstants.SEPARATOR_GROUP);
                }
            }

            recordOutputStream.write((int) EbtsConstants.SEPARATOR_FILE);

            //Writing out the new length for the record type to the .001 field on the baos, then adding the baosRec (.002-.XXXX)
            final String tag = recordType + "." + String.format("%0" + minLength + "d", 1) + ":";

            //Write the 1.01 tag to the primary output stream
            baos.write(tag.getBytes());

            //Calculate the length of the current record (not including
            //the length of the length)
            int recordLength = tag.length()+recordOutputStream.size()+1; //+1 for GS to be used on the 1.001
            //Get the length of the length
            final int prevLengthLength = String.valueOf(recordLength).length();
            //Add the length of the length to the length
            recordLength += prevLengthLength;
            //Get the new length of the length (to make sure it didnt change)
            final int postLengthLength = String.valueOf(recordLength).length();
            //Add the difference (should be 0 most of the time)
            recordLength += postLengthLength - prevLengthLength;
            //Write the length to the primary output stream
            baos.write(Integer.toString(recordLength).getBytes());
            baos.write((int) EbtsConstants.SEPARATOR_GROUP);
            //Write the rest of the record to the primary output stream
            baos.write(recordOutputStream.toByteArray());

        } catch (final IOException ex) {
            throw new EbtsBuildingException("Error building generic ebts record");
        }
    }

    /**
     * Binary header builder.
     *
     * @param record the record
     * @param baos the baos
     * @throws EbtsBuildingException the ebts building exception
     */
    private static void binaryHeaderBuilder(final BinaryHeaderImageRecord record, final ByteArrayOutputStream baos) throws EbtsBuildingException {
        //Don't need to worry about length as its generated in the record
        try {
            baos.write(record.getHeader());
            baos.write(record.getImageData());

            //throw new UnsupportedOperationException("Not yet implemented");
        } catch (final IOException ex) {
            throw new EbtsBuildingException("Error building binary header ebts record");
        }
    }

    /**
     * Set the number of preceding zeros for the fields.
     *
     * @param precedingZeros The number of preceding zeros to use on fields
     * (e.g. 0 => 1.1, 1=> 1.01, 2=> 1.001, etc)
     */
    public void setPrecedingZeros(final int precedingZeros) {
        this.minLength = precedingZeros+1;
    }

    /**
     * Build the Legacy Ebts file and return the byte array containing the
     * data for the file.
     *
     * @param ebts the ebts
     * @return byte array containing all data for the legacy eEBTS file.
     * @throws EbtsBuildingException the ebts building exception
     */
    public byte[] build(final Ebts ebts) throws EbtsBuildingException {
        this.ebts = ebts;
        //Create the auto-expanding output stream
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //Get list of all records
        //Overwrite CNT field(1.03)
        final List<LogicalRecord> records = ebts.getAllRecords();
        for (final LogicalRecord record : records) {

            if (genericTypes.contains(record.getRecordType())) {
                genericBuilder((GenericRecord)record,baos);
            } else if (binaryHeaderTypes.contains(record.getRecordType())) {
                binaryHeaderBuilder((BinaryHeaderImageRecord)record,baos);
            }
        }
        return baos.toByteArray();
    }
}
