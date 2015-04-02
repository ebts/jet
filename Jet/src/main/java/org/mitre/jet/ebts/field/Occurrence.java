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

package org.mitre.jet.ebts.field;

import org.mitre.jet.common.ByteBufferUtils;
import org.mitre.jet.ebts.EbtsConstants;
import org.mitre.jet.ebts.ParseContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The Class Occurrence.
 *
 * @author ADAY
 */
public class Occurrence implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(Occurrence.class);
    private List<SubField> subFields = new ArrayList<SubField>();

    public Occurrence() {}

    /**
     * Instantiates a new field occurrence. The @param data provided will be placed into the first subfield.
     *
     * @param subField a single subfield
     */
    public Occurrence(final SubField subField) {
        this.subFields.add(subField);
    }

    /**
     * Instantiates a new field occurrence. The @param data provided will be placed into the first subfield.
     *
     * @param data the data
     */
    public Occurrence(final String data) {
        this.subFields.add(new SubField(data));
    }

    /**
     * Instantiates a new field occurrence. The @param data provided will be placed into the first subfield.
     *
     * @param subFields list of subfields
     */
    public Occurrence(final List<SubField> subFields) {
        this.subFields = subFields;
    }

    /**
     * Instantiates a new occurrence.
     * If parseContents is true, the data will be parsed into subfields. If not, the data
     * will be added to the first subfield.
     *
     * @param data the data
     * @param parseContents whether the binary data should be parsed.
     * i.e. the data contains subfield separator characters
     */
    public Occurrence(final byte[] data, final ParseContents parseContents) {
        if (parseContents.equals(ParseContents.TRUE)) {
            this.subFields = parseData(data);
        } else {
            this.subFields.add(new SubField(data));
        }
    }

    /**
     * Concatenates all of the field data for this occurrence.
     *
     * @return String value for the field
     */
    @Override
    public String toString() {
        return this.toString("");
    }

    /**
     * Retrieves all of the field data for this occurrence, with the provided
     * separators used to delimit the subfields.
     *
     * @param subFieldSeparator the subfield sep
     * @return String value for the field
     */
    public String toString(final String subFieldSeparator) {
        final StringBuilder str = new StringBuilder();
        for (int subFieldPosition = 0; subFieldPosition < subFields.size(); subFieldPosition++) {
            str.append(this.subFields.get(subFieldPosition).toString());

            if (subFieldPosition != subFields.size()-1) {
                str.append(subFieldSeparator);
            }
        }
        return str.toString();
    }

    /**
     * Retrieves all of the field data for the occurrence, separated with the
     * subfield separators.
     *
     * @return byte subfields containing all of the field data.
     */
    public byte[] getData() {

        final ByteArrayOutputStream bbos = new ByteArrayOutputStream();
        try {
            for (int position = 0; position < subFields.size(); position++) {
                bbos.write(this.subFields.get(position).getData());
                if (position != subFields.size()-1) {
                    bbos.write(new byte[] {EbtsConstants.SEPARATOR_UNIT});
                }
            }
        }catch (final IOException ex) {
            log.error(ex.getMessage());
        }


        return bbos.toByteArray();
    }


    /**
     * Clear the occurrence of all data.
     */
    public void clear() {
        subFields.clear();
    }

    /**
     * Parses the data.
     *
     * @param data the data
     * @return the array list
     */
    private static List<SubField> parseData(final byte[] data) {

        final ArrayList<SubField> subFields = new ArrayList<SubField>();

        final ByteBuffer bb = ByteBuffer.wrap(data);

        while (bb.hasRemaining()) {

            final int subFieldSeparator = ByteBufferUtils.find(bb.slice(), EbtsConstants.SEPARATOR_UNIT);

            //If subfieldSep not found, then at end of data
            if (subFieldSeparator == -1) {
                final byte[] value = new byte[bb.remaining()];
                bb.get(value);
                subFields.add(new SubField(value));
            } else if (subFieldSeparator > -1) {
                final byte[] value = new byte[subFieldSeparator-1];
                bb.get(value); 
                subFields.add(new SubField(value));
                bb.position(bb.position()+1);
                if (!bb.hasRemaining()) {
                    subFields.add(new SubField());
                }
            }
        }

        return subFields;
    }


    public List<SubField> getSubFields() {
        return subFields;
    }

    public void setSubfields(final List<SubField> subFields) {
        this.subFields = subFields;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Occurrence that = (Occurrence) o;

        return subFields.equals(that.subFields);

    }

    @Override
    public int hashCode() {
        return subFields.hashCode();
    }
}
