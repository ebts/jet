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

package org.mitre.jet.ebts.records;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import org.jetbrains.annotations.NotNull;
import org.mitre.jet.ebts.ParseContents;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.exceptions.EbtsBuildingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * The Class BinaryHeaderImageRecord.
 *
 * @author ADAY
 */
public class BinaryHeaderImageRecord extends LogicalRecord implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(BinaryHeaderImageRecord.class);

    private final int[] headerFormat;

    /**
     * Creates a new binary header image record.
     *
     * @param recordType Record Type
     * @param headerFormat Integer array containing the format of each block of the header.
     *  For instance, a type-4 record header is formatted as follows
     *  [4 bytes][1 byte][1 byte][6 bytes][1 byte][2 bytes][2 bytes][1 byte]
     *  and would be represented as new int[]{4,1,1,6,1,2,2,1}
     */
    public BinaryHeaderImageRecord(final int recordType, @NotNull final int[] headerFormat) {

        super(recordType);
        //Validate type or throw exception
        this.headerFormat = Arrays.copyOf(headerFormat, headerFormat.length);
    }

    /**
     * Creates a new binary header image record.
     * Currently defaults to type 3-6.
     *
     * @param recordType The type of the record
     */
    public BinaryHeaderImageRecord(final int recordType) {

        super(recordType);

        if (recordType == 3 || recordType == 4 || recordType == 5 || recordType == 6) {
            this.headerFormat = new int[]{4, 1, 1, 6, 1, 2, 2, 1};
        } else {
            throw new UnsupportedOperationException("Unable to predict header from this record, use alternate constructor");
        }
    }

    @NotNull
    public int[] getHeaderFormat() {

        return headerFormat;
    }

    /**
     * Get the binary image data.
     * @return byte array containing the fingerprint image data
     */
    @NotNull
    @Override
    public byte[] getImageData() {

        final Field dataField = this.getField(headerFormat.length + 1);

        if (dataField == null) {
            return new byte[0];
        } else {
            return dataField.getData();
        }
    }

    /**
     * Set the binary image data. (FieldOccurrence number 9)
     * The length will automatically be updated based on the length of the data.
     *
     * @param data the new image data
     */
    public void setImageData(@NotNull final byte[] data) {

        this.setField(headerFormat.length + 1, new Field(data, ParseContents.FALSE));
        updateLength();
    }
    /**
     * Get the current length of the record.
     * Length = header length + image data size.
     * 
     * @return Record length
     */
    public int getLength() {

        final Field lengthField = this.getField(1);

        if (lengthField == null) {
            return -1;
        } else {
            try {
                return Integer.parseInt(lengthField.toString());
            } catch (final NumberFormatException e) {

                log.error(e.getMessage());

                return -1;
            }
        }
    }

    /**
     * Return the header in a packed byte buffer.
     *
     * @return byte array containing the header
     */
    @NotNull
    public byte[] getHeader() throws EbtsBuildingException {

        final ByteBuffer bb = ByteBuffer.allocate(getHeaderLength());

        for (int i = 0; i < headerFormat.length; i++) {
            if(fields.get(i + 1) != null) {
                final String data = fields.get(i + 1).getOccurrences().get(0).toString();
                if (headerFormat[i] == 1) {
                    bb.put(Byte.parseByte(data));
                } else if (headerFormat[i] == 2) {
                    bb.putShort(Short.parseShort(data));
                } else if (headerFormat[i] == 4) {
                    bb.putInt(Integer.parseInt(data));
                } else if (headerFormat[i] == 6) {
                    bb.put(Byte.parseByte(data));
                    for (int pos = 1; pos < 6; pos++) {
                        bb.put((byte) -1);
                    }
                }
            }
            else {
                int headerPosition = i+1;
                throw new EbtsBuildingException("Invalid header found at header position:"+headerPosition);
            }
        }

        return bb.array();
    }

    /**
     * Returns the value for a field as a string.
     * 
     * @param field FieldOccurrence Number
     * @return String value for the field
     */
    @NotNull
    public String getFieldValue(final int field) {

        if (field >= headerFormat.length + 1) {
            throw new IllegalArgumentException("Field not found in record");
        }

        if (field == headerFormat.length + 1) {
            return "<BinaryImageData>";
        } else {
            final byte[] value = fields.get(field).getData();
            if (value.length == 1) {
                return Byte.toString(value[0]);
            } else if (value.length == 2) {
                return Short.toString(Shorts.fromByteArray(value));
            } else if (value.length == 4) {
                return Integer.toString(Ints.fromByteArray(value));
            } else if (value.length == 6) {
                final StringBuilder stringOut = new StringBuilder();
                for (int i =0; i < 6; i++) {
                    stringOut.append((int)value[i]);
                    stringOut.append(",");
                }
                return stringOut.toString();
            } else {
                return "";
            }
        }
    }

    /* (non-Javadoc)
     * @see org.mitre.jet.ebts.records.LogicalRecord#hasImageData()
     */
    @Override
    public boolean hasImageData() {
        //Currently assuming image data is at position headersize + 1
        return hasField(headerFormat.length + 1) && (fields.get(headerFormat.length + 1) != null);
    }

    /**
     * Gets the header length.
     *
     * @return the header length
     */
    public int getHeaderLength() {

        int length = 0;
        for (final int headerSize : headerFormat) {
            length += headerSize;
        }

        return length;
    }

    /**
     * Update length.
     */
    private void updateLength() {

        final int length = this.getHeaderLength() + this.getImageData().length;

        setField(1, new Field(String.valueOf(length)));
    }


    @Override
    public int getRecordType() {

        return this.recordType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final LogicalRecord that = (LogicalRecord) o;

        return recordType == that.recordType && fields.equals(that.fields);

    }

    @Override
    public int hashCode() {

        int result = fields.hashCode();
        result = 31 * result + recordType;
        return result;
    }
}
