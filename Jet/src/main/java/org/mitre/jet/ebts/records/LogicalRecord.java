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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mitre.jet.ebts.EbtsUtils;
import org.mitre.jet.ebts.ParseContents;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.exceptions.EbtsHandlingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Class LogicalRecord.
 *
 * @author ADAY
 */
public abstract class LogicalRecord implements Serializable, Comparable<LogicalRecord> {

    private static final Logger log = LoggerFactory.getLogger(LogicalRecord.class);

    protected final Map<Integer,Field> fields = new TreeMap<Integer,Field>();
    protected final int recordType;

    public LogicalRecord(final int recordType){

        this.recordType = recordType;
    }

    /**
     * Retrieve the record type for a record.
     * 
     * @return the integer value for the record type
     */
    public int getRecordType() {

        return recordType;
    }

    /**
     * Retrieve the image designation character for the record.
     *
     * Note: that this is not the literal byte that is stored as that is a
     * string representation of the value.
     * 
     * @return image designation character
     */
    public int getIdc() {

        //The bytes that are stored are the string representation.
        //Need to do conversion
        if (this.recordType != 1 && fields.containsKey(2) && fields.get(2).getOccurrences().size() == 1) {
            return Integer.parseInt(fields.get(2).getOccurrences().get(0).toString()); // TODO: NumberFormatException
        }
        //The Type1 record does not have an idc.
        else {
            return -1;
        }
    }


    /**
     * Set the image designation character for the record.
     * 
     * @param idc consisting of a byte >= 0
     */
    public void setIdc(final int idc) {

        if (this.recordType == 1) {
            throw new IllegalStateException("Cannot set the IDC of a Type 1 Record");
        }

        this.setField(2, new Field(String.format("%02d", idc)));
    }

    /**
     * Retrieve the length of the record.
     * 
     * @return Record length
     */
    public abstract int getLength();

    /**
     * Return all of the fieldOccurrences in the record.
     *
     * @return TreeMap containing all of the Fields.
     */
    @NotNull
    public Map<Integer,Field> getFields() {

        return this.fields;
    }

    /**
     * Returns the value of a field given the field number.
     *
     * @param fieldNumber The field number of the field.
     * e.g. For field 2.003, '3' would be represent field number 3.
     * @return The contents of the field. The contents of the field will be
     * concatenated if it contains multiple subfields/occurrences.
     */
    @Nullable
    public Field getField(final int fieldNumber) {

        return this.fields.get(fieldNumber);
    }

    /**
     * Return the Field for a field id.
     * @param fieldMnemonic (e.g. NAM)
     * @return FieldOccurrence
     */
    @Nullable
    public Field getField(@NotNull final String fieldMnemonic) {

        try {
            return getField(EbtsUtils.fieldMnemonicToNumber(getRecordType(), fieldMnemonic));
        } catch (final EbtsHandlingException e) {
            log.error(e.getMessage());
            return null;
        }
    }


    /**
     * Sets the field.
     *
     * @param fieldNumber the field number
     * @param field the field
     */
    public void setField(final int fieldNumber, @NotNull final Field field) {

        this.fields.put(fieldNumber, field);
    }


    /**
     * Returns whether a field exists in the record.
     *
     * @param fieldMnemonic String value indicating the field Id (e.g NAM)
     * @return boolean indicating if the field exists in the record.
     */
    public boolean hasField(@NotNull final String fieldMnemonic) {

        try {
            return hasField(EbtsUtils.fieldMnemonicToNumber(getRecordType(), fieldMnemonic));
        } catch (final EbtsHandlingException e) {
            return false;
        }
    }

    /**
     * Returns whether a field exists in the record.
     *
     * @param fieldNumber Integer value indicating the field number
     * @return boolean indicating if the field exists in the record.
     */
    /* (non-Javadoc)
     * @see org.mitre.jet.ebts.records.LogicalRecord#fieldExists(int)
     */
    public boolean hasField(final int fieldNumber) {

        return this.fields.containsKey(fieldNumber);
    }

    /**
     * Get the image data for the record.
     *
     * @return byte array contains the image data, or a zero-length byte if no data exists.
     */
    @NotNull
    public byte[] getImageData()  {

        if (isValidImageRecordType(this.recordType)) {
            final int dataField = getImageField();
            if (dataField != -1) {
                final Field imageField = this.fields.get(dataField);

                if (imageField != null) {
                    return imageField.getData();
                } else {
                    return new byte[0];
                }
            }
        }

        return new byte[0];
    }

    /**
     * Returns whether a field has image data.
     *
     *
     * @return boolean indicating whether the record contains image data.
     * Note: Returns false if image field exists with no data
     */
    public boolean hasImageData() {

        final int dataField = getImageField();

        return dataField > 0 && hasField(dataField) && getField(dataField).getData().length > 0;
    }

    public boolean isImageRecord() {

        return getImageField() > 0;
    }

    public int getImageField() {

        if (isValidImageRecordType(this.recordType)) {
            try {
                return EbtsUtils.fieldMnemonicToNumber(this.recordType, "DATA");
            } catch (final EbtsHandlingException e) {
                log.error(e.getMessage(), e);
            }
        }

        return -1;
    }

    public void setImageData(@NotNull final byte[] data) {

        if (isValidImageRecordType(this.recordType)) {
            final int fieldNumber = getImageField();
            log.debug("Setting image data for field #:" + fieldNumber);
            this.fields.put(fieldNumber, new Field(data, ParseContents.FALSE));
        } else {
            throw new UnsupportedOperationException("Unable to store image data in a type-"+this.getRecordType()+" record");
        }
    }

    protected static boolean isValidImageRecordType(final int recordType) {

        return ((recordType != 1) && (recordType != 2));
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

    // TODO: Can two distinct records have the same Record Type /and/ the same IDC?
    /**
     * <p>
     *     Compares two LogicalRecord instances using the following algorithm. This should not be considered a stable sorting algorithm.
     * </p>
     * <ol>
     *     <li>If the other instance is null, the current instance is considered to be greater.</li>
     *     <li>Given two instances with different record types, they are ordered according to the record type. That is Type-1 < Type-2 < Type-3 ...</li>
     *     <li>Given two instances with the same record types, they are ordered according to the IDC value.</li>
     *     <li>If both instances have the same record type and IDC value, they are considered equivalent.</li>
     * </ol>
     * @param other The other record
     * @return The comparison value
     */
    @Override
    public int compareTo(@Nullable final LogicalRecord other) {

        if (other == null) {
            return this.getRecordType();
        }

        if (other.getRecordType() != this.getRecordType()) {
            return (this.getRecordType() - other.getRecordType());
        } else {
            return this.getIdc() - other.getIdc();
        }
    }
}
