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
import org.mitre.jet.ebts.ParseContents;
import org.mitre.jet.ebts.field.Field;

import java.io.Serializable;


/**
 * The Class GenericRecord.
 *
 * @author ADAY
 */
public class GenericRecord extends LogicalRecord implements Serializable {

    /**
     * Creates a generic record for Type 1,2,10,13,14,16,17,etc records.
     *
     * @param recordType Record type
     */
    public GenericRecord(final int recordType) {

        super(recordType);
    }


    /* (non-Javadoc)
     * @see org.mitre.jet.ebts.records.LogicalRecord#getLength()
     */
    public int getLength() {

        final Field field1 = this.getField(1);

        if (field1 == null) {
            return -1;
        } else {
            try {
                return Integer.parseInt(field1.toString());
            } catch (final NumberFormatException e) {
                return -1;
            }
        }
    }

    /**
     * Set the image data for the record.
     *
     * @param data Image data
     */
    public void setImageData(@NotNull final byte[] data) {

        if (isValidImageRecordType(this.getRecordType())) {
            this.fields.put(999, new Field(data, ParseContents.FALSE));
        } else {
            throw new UnsupportedOperationException("Record Type cannot contain image data");
        }
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

    @Override
    public String toString() {
        return "GenericRecord{}";
    }
}
