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

package org.mitre.jet.exceptions;
/**
 * The Class EbtsParsingException.
 */
public class EbtsParsingException extends Exception {

    private int record = -1;
    private int field = -1;
    private int idc = -1;

    /**
     * Instantiates a new ebts parsing exception.
     */
    public EbtsParsingException() {super();}

    /**
     * Instantiates a new ebts parsing exception.
     *
     * @param message the message
     */
    public EbtsParsingException(final String message) {super(message);}

    /**
     * Instantiates a new ebts parsing exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public EbtsParsingException(final String message, final Throwable cause) {super(message, cause);}

    public EbtsParsingException(final String message, final Throwable cause, final int record, final int field, final int idc) {
        super(message, cause);
        this.setRecord(record);
        this.setField(field);
        this.setIdc(idc);
    }

    public EbtsParsingException(final String message, final int record, final int field, final int idc) {
        super(message);
        this.setRecord(record);
        this.setField(field);
        this.setIdc(idc);
    }

    public EbtsParsingException(final int record, final int field, final int idc) {
        super(buildMessageFromEBTSDefinitions(record,field,idc));
        this.setRecord(record);
        this.setField(field);
        this.setIdc(idc);
    }

    private static String buildMessageFromEBTSDefinitions(final int record, final int field, final int idc){
        return "Error Parsing EBTS File - Details: \n"
            + " RecordType: " + record
            + " FieldNumber: " + field
            + " EBTS IDC: " + idc;
    }

    /**
     * Instantiates a new ebts parsing exception.
     *
     * @param cause the cause
     */
    public EbtsParsingException(final Throwable cause) {super(cause);}

    public int getRecord() {
        return record;
    }

    public void setRecord(final int record) {
        this.record = record;
    }

    public int getField() {
        return field;
    }

    public void setField(final int field) {
        this.field = field;
    }

    public int getIdc() {
        return idc;
    }

    public void setIdc(final int idc) {
        this.idc = idc;
    }
}
