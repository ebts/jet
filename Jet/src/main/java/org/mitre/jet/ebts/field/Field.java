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

import org.jetbrains.annotations.NotNull;
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
import java.util.Iterator;
import java.util.List;

/**
 * <p>
 *     Field objects are containers for data. Fields may contain one or more {@link Occurrence} or {@link SubField} instances.
 * </p>
 */
public class Field implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(Field.class);
    private List<Occurrence> occurrences = new ArrayList<Occurrence>();

    public Field() {}

    /**
     * <p>
     *     Instantiates a new field. The new field will contain a single {@link Occurrence} and {@link SubField} containing the provided {@literal data} element.
     * </p>
     * <p>
     *     Note: If {@literal data} is {@literal null}, the data element is implicitly converted to an empty string.
     * </p>
     * @param data The data contained by this field. If this value is {@literal null}, it is implicitly converted to an empty string.
     */
    public Field(String data) {
        if (data == null) {
            data = "";
        }
        occurrences.add(new Occurrence(data));
    }


    /**
     * Instantiates a new field.
     * If parseContents is true, the data will be parsed into occurrences/subfields. If not, the data
     * will be added to the first occurrence/subfield.
     *
     * @param data the data
     * @param parseContents whether the binary data should be parsed (contains RS/US characters)
     */
    public Field(final byte[] data, final ParseContents parseContents) {
        if (parseContents.equals(ParseContents.TRUE)) {
            this.occurrences = parseData(data);
        } else {
            setData(data);
        }
    }

    /**
     * Instantiates a new field.
     *
     * @param occurrences the occurrences
     */
    public Field(final List<Occurrence> occurrences) {
        this.setOccurrences(occurrences);
    }

    /**
     *
     * @param occurrence The occurrence
     */
    public Field(final Occurrence occurrence) {
        this.getOccurrences().add(occurrence);
    }

    public void setData(final byte[] data) {
        this.occurrences.clear();
        this.occurrences.add(new Occurrence(data,ParseContents.FALSE));
    }

    /**
     * Gets the occurrences.
     *
     * @return the occurrences
     */
    @NotNull
    public List<Occurrence> getOccurrences() {
        return occurrences;
    }

    /**
     * Sets the field occurrences.
     *
     * @param occurrences the new field occurrences
     */
    public void setOccurrences(final List<Occurrence> occurrences) {
        this.occurrences = occurrences;
    }

    /**
     * Retrieves all of the field data for this occurrence, with the provided
     * separators used to delimit the occurrences/subfields.
     *
     * @param occurrenceSeparator the occurrence separator
     * @param subFieldSeparator the subfield separator
     * @return the string
     */
    public String toString(final String occurrenceSeparator, final String subFieldSeparator){
        final StringBuilder sb = new StringBuilder();
        final Iterator<Occurrence> itr = this.occurrences.listIterator();
        while (itr.hasNext()) {
            sb.append(itr.next().toString(subFieldSeparator));
            if (itr.hasNext()) {
                //once all the subfields are joined with .toString above, put an occurrence separator in
                sb.append(occurrenceSeparator);
            }
        }
        return sb.toString();
    }

    /**
     * Retrieves all of the field data for this field, with no
     * separators used to delimit the occurrences/subfields.
     *
     * @return the string
     */
    @Override
    public String toString(){
        return toString("","");
    }


    /**
     * Gets the data in its binary form (including occurrence/subfield seperators)
     *
     * @return the data
     */
    public byte[] getData() {
        final ByteArrayOutputStream bbos = new ByteArrayOutputStream();
        try {
            for (int occurrenceCount = 0; occurrenceCount < this.occurrences.size(); occurrenceCount++) {
                bbos.write(this.occurrences.get(occurrenceCount).getData());
                if (occurrenceCount != this.occurrences.size()-1) {
                    bbos.write((int) EbtsConstants.SEPARATOR_RECORD);
                }
            }
        } catch (final IOException e) {
            log.error("Error extracting binary data from byte array: {}",e.getMessage());
        }
        return bbos.toByteArray();
    }

    /**
     * Parses the data.
     *
     * @param data the data
     * @return the list
     */
    private static List<Occurrence> parseData(final byte[] data) {
        final List<Occurrence> occurrences = new ArrayList<Occurrence>();
        final ByteBuffer bb = ByteBuffer.wrap(data);

        while (bb.hasRemaining()) {

            final int occSep = ByteBufferUtils.find(bb.slice(), EbtsConstants.SEPARATOR_RECORD);

            //No occurrences remaining.
            if (occSep == -1) {
                final byte[] value = new byte[bb.remaining()];
                bb.get(value);
                occurrences.add(new Occurrence(value,ParseContents.TRUE));
            } else if (occSep > -1) {
                final byte[] value = new byte[occSep-1];
                bb.get(value);
                occurrences.add(new Occurrence(value,ParseContents.TRUE));
                bb.position(bb.position()+1);
                if (!bb.hasRemaining()) {
                    occurrences.add(new Occurrence());
                }
            }
        }

        return occurrences;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Field field = (Field) o;

        return !(occurrences != null ? !occurrences.equals(field.occurrences) : field.occurrences != null);
    }

    @Override
    public int hashCode() {
        return occurrences != null ? occurrences.hashCode() : 0;
    }
}
