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

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import org.mitre.jet.common.ByteBufferUtils;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.ebts.field.Occurrence;
import org.mitre.jet.ebts.records.BinaryHeaderImageRecord;
import org.mitre.jet.ebts.records.GenericRecord;
import org.mitre.jet.ebts.records.LogicalRecord;
import org.mitre.jet.exceptions.EbtsParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Class EbtsParser
 *
 * @author ADAY
 */
public class EbtsParser {

    private static final Logger log = LoggerFactory.getLogger(EbtsParser.class);
    private static final Set<Integer> BINARY_HEADER_RECORD_TYPES = EbtsUtils.getBinaryHeaderTypes();
    private static final Set<Integer> GENERIC_RECORD_TYPES = EbtsUtils.getGenericRecordTypes();

    private static final byte COLON = 0x3a;

    private static final Set<String> IMAGE_MIME_EXTENSIONS = Sets.newHashSet(".jpg", ".jp2", ".png", ".tiff", ".gif");

    /**
     * Create an Ebts Parser to parse all of the record/field data in an Ebts
     * file.
     */
    public EbtsParser() {}

    public static Ebts parse(final byte[] bytes, final ParseType parseType) throws EbtsParsingException {
        return EbtsParser.parse(bytes,parseType,Type7Handling.TREAT_AS_TYPE4);
    }

    public static Ebts parse(final byte[] bytes, Type7Handling type7Handling) throws EbtsParsingException {
        return EbtsParser.parse(bytes,ParseType.FULL,type7Handling);
    }

        /**
         * Parses an Ebts file.
         *
         * @param bytes Byte array containing the Ebts file to be parsed.
         * @return Ebts instance
         * @throws EbtsParsingException the ebts parsing exception
         */
    public static Ebts parse(final byte[] bytes, final ParseType parseType, Type7Handling type7Handling) throws EbtsParsingException {
        final Ebts ebts = new Ebts();
        try {
            final ByteBuffer bb = ByteBuffer.wrap(bytes);
            log.debug("Parsing Record Type: 1");
            // Go after the first record (Type1) in the Ebts data
            final LogicalRecord type1Record = parseGenericRecord(1, bb.slice());
            ebts.addRecord(type1Record);

            //Update the position of the byte buffer to be past the parsed data
            if (type1Record.getLength() > 0) {
                bb.position(bb.position() + type1Record.getLength());
            } else {
                throw new EbtsParsingException("Error Parsing Type 1. No data was parsed.", 1, -1, -1);
            }

            final Field cntField = type1Record.getField(3);

            if (cntField == null) {
                throw new EbtsParsingException("Field 1/CNT not found");
            }

            //Get all of the records from the IDC list in 1.03
            final List<Occurrence> idcs = cntField.getOccurrences();
            if (idcs != null && !idcs.isEmpty()) {
                for (final Occurrence idcOccurrence : idcs) {
                    final int recordType = Integer.parseInt(idcOccurrence.getSubFields().get(0).toString());

                    //Skip Type 1
                    if (recordType != 1) {
                        final LogicalRecord record;
                        //Determine the record type and use correct parser based on the type
                        log.debug("Parsing type: {}", recordType);
                        if (GENERIC_RECORD_TYPES.contains(recordType)) {
                            record = parseGenericRecord(recordType, bb.slice());
                        } else if (BINARY_HEADER_RECORD_TYPES.contains(recordType) && recordType != 7 && recordType != 8) {
                            record = parseType3456(recordType, bb.slice());
                        } else if (recordType == 7) {
                            record = parseType7(recordType, bb.slice(), type7Handling);
                        } else if (recordType == 8) {
                            record = parseType8(recordType, bb.slice());
                        } else {
                            throw new EbtsParsingException("File contains unsupported record type", recordType, -1, -1);
                        }
                        if (record != null) {
                            ebts.addRecord(record);
                            //Update the position of the byte buffer to be past the parsed data
                            if (record.getLength() > 0 && bb.position() >= 0) {
                                bb.position(bb.position() + record.getLength());
                            } else {
                                throw new EbtsParsingException("Error parsing record. Empty record?", recordType, -1, -1);
                            }
                        } else {
                            throw new EbtsParsingException("Error parsing record", recordType, -1, -1);
                        }

                        if (parseType.equals(ParseType.DESCRIPTIVE_ONLY) && recordType == 2) {
                            break;
                        }
                    }
                }
            } else {
                throw new EbtsParsingException("Unable to parse IDC List in Type 1 Field 3.", 1, 3, -1);
            }
        }
        catch(final RuntimeException e) {
            throw new EbtsParsingException("Unhandled Parsing Exception",e);
        }
        return ebts;
    }


    public static Ebts parse(final byte[] bytes) throws EbtsParsingException {
        return parse(bytes,ParseType.FULL);
    }

    public static Ebts parse(final File file, final ParseType parseType, final Type7Handling type7Handling) throws EbtsParsingException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            if (parseType.equals(ParseType.FULL)) {
                return parse(Files.toByteArray(file), parseType, type7Handling);
            } else {
                final int readSize = 1024;
                final byte[] tempData = new byte[readSize];

                int type1Fs = -1, type2Fs = -1;

                while ((fileInputStream.read(tempData, 0, readSize)) != -1) {

                    final ByteBuffer bb = ByteBuffer.wrap(tempData);

                    //Search for the type1 FS if we haven't found it
                    if (type1Fs == -1) {
                        type1Fs = ByteBufferUtils.find(bb, EbtsConstants.SEPARATOR_FILE);
                    } else {
                        //If we found the type1 FS, search for the type2 FS
                        type2Fs = ByteBufferUtils.find(bb, EbtsConstants.SEPARATOR_FILE);
                    }

                    //If we haven't found the type2 yet, write the entire array
                    if (type2Fs == -1) {
                        byteArrayOutputStream.write(tempData);
                    } else {
                        //If we found the type2 FS, write only up until the type2FS location
                        byteArrayOutputStream.write(tempData,0,bb.position());
                    }
                }

                final byte[] outputArray = byteArrayOutputStream.toByteArray();

                return parse(outputArray, parseType, type7Handling);
            }
        } catch (final IOException e) {
            throw new EbtsParsingException(e);
        }
        finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (final IOException e) {
                throw new EbtsParsingException(e);
            }
        }
    }

    /**
     * Parses an Ebts file.
     *
     * @param file File containing the Ebts file to be parsed.
     * @return Ebts instance
     * @throws EbtsParsingException the exception
         */
    public static Ebts parse(final File file, final ParseType parseType) throws EbtsParsingException {
        return parse(file, parseType, Type7Handling.TREAT_AS_TYPE4);
    }

    public static Ebts parse(final File file) throws EbtsParsingException{
        return parse(file,ParseType.FULL);
    }

    /**
     * Parses an Ebts file.
     *
     * @param filePath File path containing the Ebts file to be parsed.
     * @return Ebts instance
     * @throws EbtsParsingException the exception
     */
    @Deprecated
    public static Ebts parse(final String filePath, final ParseType parseType) throws EbtsParsingException {
        final File file = new File(filePath);
        return parse(file, parseType);
    }
    /*
     * Parser for record types that contain text fields and optionally image data
     * e.g. Type 1,2,10,14,etc
     * 
     */
    /**
     * Type generic parser.
     *
     * @param type the type
     * @param bb the bb
     * @return the logical record
     * @throws EbtsParsingException the ebts parsing exception
     */
    private static LogicalRecord parseGenericRecord(final int type, final ByteBuffer bb) throws EbtsParsingException {
        final GenericRecord record = new GenericRecord(type);

        if (bb.capacity() == 0) { //byte buffer doesn't contain data
            //record.setField(0,new FieldOccurrence("0"));
            return record;
        }

        int valueSep;
        int fieldSep;

        boolean endOfRecord = false;
        while (bb.hasRemaining() && !endOfRecord) {

            valueSep = ByteBufferUtils.find(bb.slice(), COLON);
            //Todo: Need to improve this ad hoc check
            if (valueSep-1 > 10 || valueSep-1 < 3) {
                int idc = -1;

                final Field idcField = record.getField("idc");

                if (idcField != null) {
                    idc = Integer.parseInt(idcField.toString());
                }

                throw new EbtsParsingException("Error parsing record. Invalid field tag.",record.getRecordType(),-1,idc);
            }
            final byte[] fieldTagBytes = new byte[valueSep-1];
            bb.get(fieldTagBytes);
            bb.position(bb.position()+1);
            final String fieldTag;
            //TODO: Add NON-ASCII Handling
            try {
                fieldTag = new String(fieldTagBytes,"ASCII");
            } catch (final UnsupportedEncodingException e) {
                int idc = -1;
                if (record.hasField("IDC")) {
                    idc = Integer.parseInt(record.getField("idc").toString());
                }
                throw new EbtsParsingException("Error parsing record",record.getRecordType(),-1,idc);
            }

            fieldSep = ByteBufferUtils.find(bb.slice(), EbtsConstants.SEPARATOR_GROUP);

            //Verify that the next GS doesn't exceed record length
            //If it does, we've jumped into the next record
            if (record.hasField(1)) {
                if (fieldSep == -1 || bb.position()+fieldSep > record.getLength()) {
                    //System.out.println("Record length exceeded:"+record.getRecordLength());
                    fieldSep = ByteBufferUtils.find(bb.slice(), EbtsConstants.SEPARATOR_FILE);
                    endOfRecord = true;
                }
            } else {
                if (fieldSep > ByteBufferUtils.find(bb.slice(), EbtsConstants.SEPARATOR_FILE)) {
                    throw new EbtsParsingException("Error parsing record",record.getRecordType(),-1,-1);
                }
            }

            //Perform this check if its a .999 (image data) and not type 1/2
            //Must check for type 1,2 as 2.999 is a user defined field
            if ((!fieldTag.contains("999") || type == 1 || type == 2) && fieldSep != -1) {

                final byte[] value = new byte[fieldSep-1];

                bb.get(value);

                record.setField(EbtsUtils.tagToFieldNumber(fieldTag), new Field(value,ParseContents.TRUE));

                log.debug("Parsed Field: {} Data:{}",fieldTag,record.getField(EbtsUtils.tagToFieldNumber(fieldTag)).toString(";",","));

                bb.position(bb.position()+1);
            } else {
                //The remaining data is image data
                //Verify that the recordLength exists and that the remaining data is > 0
                final int readLength = record.getLength()-bb.position()-1;
                if (record.getLength() != -1 && readLength >= 0 && bb.remaining() >= readLength) {

                    final byte[] value = new byte[readLength];
                    bb.get(value);
                    record.setImageData(value);
                    endOfRecord = true;
                } else {
                    int idc = -1;
                    if (record.hasField("IDC")) {
                        idc = Integer.parseInt(record.getField("idc").toString());
                    }
                    throw new EbtsParsingException("Error parsing end of record. Record:"+record.getRecordType()+". IDC:"+idc+". Record length incorrect?",record.getRecordType(),idc,-1);
                }
            }
        }                
        return record;
    }

    /**
     * Type7 parser.
     *
     * @param recordType the record type
     * @param bb the bb
     * @return the logical record
     */
    private static LogicalRecord parseType7(final int recordType, final ByteBuffer bb, final Type7Handling type7Handling) {

        BinaryHeaderImageRecord record = null;
        if(type7Handling.equals(Type7Handling.NIST)) {
            final int[] header = new int[]{4,1};
            int headerLength = 5;
            record = new BinaryHeaderImageRecord(recordType, header);

            //No data remains
            if (bb.capacity() == 0) {
                return record;
            }

            final byte[] len = new byte[4];
            bb.get(len);
            record.setField(1,new Field(convertBinaryFieldData(len),ParseContents.FALSE));

            //Get the idc
            final byte[] idc = {bb.get()};
            record.setField(2,new Field(convertBinaryFieldData(idc),ParseContents.FALSE));

            //Examine the mimetype of the remaining data
            int expectedRemaining = Ints.fromByteArray(len) - headerLength;
            if(expectedRemaining != bb.remaining()) {
                log.warn("Unexpected remaining length found in type7 record. Expected: {} Actual: {}", expectedRemaining, bb.remaining());
            }

            int remaining = Math.min(expectedRemaining, bb.remaining());

            byte[] imageData = new byte[remaining];
            bb.get(imageData);
            record.setField(9,new Field(imageData,ParseContents.FALSE));
        }
        else if(type7Handling.equals(Type7Handling.TREAT_AS_TYPE4)) {
            final int[] header = new int[]{4,1,1,6,1,2,2,1};
            int headerLength = 18;
            record = new BinaryHeaderImageRecord(recordType, header);

            //No data remains
            if (bb.capacity() == 0) {
                return record;
            }
            final byte[] len = new byte[4];
            bb.get(len);
            record.setField(1,new Field(convertBinaryFieldData(len),ParseContents.FALSE));

            //Get the idc
            final byte[] idc = {bb.get()};
            record.setField(2,new Field(convertBinaryFieldData(idc),ParseContents.FALSE));

            //Get the impression type
            final byte[] imp = {bb.get()};
            record.setField(3,new Field(convertBinaryFieldData(imp),ParseContents.FALSE));

            //Get the fingerprint position
            final byte[] fgp = new byte[6];
            bb.get(fgp);
            record.setField(4,new Field(convertBinaryFieldData(fgp),ParseContents.FALSE));

            final byte[] isr = {bb.get()};
            record.setField(5,new Field(convertBinaryFieldData(isr),ParseContents.FALSE));

            final byte[] hll = new byte[2];
            bb.get(hll);
            record.setField(6,new Field(convertBinaryFieldData(hll),ParseContents.FALSE));

            final byte[] vll = new byte[2];
            bb.get(vll);
            record.setField(7,new Field(convertBinaryFieldData(vll),ParseContents.FALSE));

            final byte[] alg = {bb.get()};
            record.setField(8,new Field(convertBinaryFieldData(alg),ParseContents.FALSE));

            final int remainingDataPosition = bb.position();

            //Examine the mimetype of the remaining data
            int expectedRemaining = Ints.fromByteArray(len) - headerLength;
            if(expectedRemaining != bb.remaining()) {
                log.warn("Unexpected remaining length found in type7 record. Expected: {} Actual: {}", expectedRemaining, bb.remaining());
            }

            int remaining = Math.min(expectedRemaining, bb.remaining());
            byte[] imageData = new byte[remaining];
            bb.get(imageData);
            final String ext = EbtsUtils.getMimeExtension(imageData);

            if (IMAGE_MIME_EXTENSIONS.contains(ext)) {
                log.debug("Found mime-type ext of remaining data to be: {}",ext);
                record.setField(9,new Field(imageData,ParseContents.FALSE));
            } else {
                log.debug("Ignoring mime-type ext of {} and searching for mimetype based on CGA",ext);
                bb.position(remainingDataPosition);

                //TODO: Add Length Checks
                //If CGA is provided, we hunt for the header as it may not be at the beginning of the remaining data (Thanks CBEFF)
                if (alg[0] != Byte.valueOf("0")) {
                    int imageLocation = -1;
                    if (alg[0] == Byte.valueOf("1")) {
                        imageLocation = ImageUtils.getWsqImagePosition(bb);
                        log.debug("Found WSQ at byte:{}",imageLocation);

                    } else if (alg[0] == Byte.valueOf("2")) {
                        imageLocation = ImageUtils.getJpgImagePosition(bb);
                        log.debug("Found WSQ at byte:{}",imageLocation);

                    } else if (alg[0] == Byte.valueOf("4") || alg[0] == Byte.valueOf("5")) {
                        imageLocation = ImageUtils.getJp2ImagePosition(bb);
                        log.debug("Found JP2 at byte:{}",imageLocation);
                    }
                    //PNG
                    else if (alg[0] == Byte.valueOf("6")) {
                        imageLocation = ImageUtils.getPngImagePosition(bb);
                        log.debug("Found PNG at byte:{}",imageLocation);
                    }

                    if (imageLocation != -1) {
                        bb.position(imageLocation);
                        imageData = new byte[Ints.fromByteArray(len)-imageLocation];
                        bb.get(imageData);
                        record.setField(9,new Field(imageData,ParseContents.FALSE));
                    }
                } else {
                    //Assume raw image data
                    //TODO: Validate lengths
                    final int hllInt = Shorts.fromByteArray(hll);
                    final int vllInt = Shorts.fromByteArray(vll);
                    final int imageLength = hllInt*vllInt;
                    log.debug("image length = {}",hllInt,vllInt);
                    imageData = new byte[imageLength];
                    bb.position(Ints.fromByteArray(len)-imageLength);
                    bb.get(imageData);
                    record.setField(9,new Field(imageData,ParseContents.FALSE));
                }
            }
        }

        if (log.isDebugEnabled()) {
            if(record != null) {
                for (final Map.Entry<Integer, Field> entry : record.getFields().entrySet()) {
                    if (entry.getKey() != 9) {
                        log.debug("Parsed Field: {} Data:{}", entry.getKey(), entry.getValue().toString());
                    }
                }
            }
        }

        return record;

    }

    /*
     * Parser for records containing binary header data
     */
    /**
     * Type3456 parser.
     *
     * @param recordType the record type
     * @param bb the bb
     * @return the logical record
     */
    private static LogicalRecord parseType3456(final int recordType, final ByteBuffer bb) throws EbtsParsingException {

        return parseBinaryHeaderRecord(recordType, null, bb);
    }

    private static LogicalRecord parseBinaryHeaderRecord(final int recordType, final int[] headerFormat, final ByteBuffer bb)
            throws EbtsParsingException {

        final BinaryHeaderImageRecord record;
        if (headerFormat != null && headerFormat.length > 0) {
            record = new BinaryHeaderImageRecord(recordType,headerFormat);
        } else {
            record = new BinaryHeaderImageRecord(recordType);
        }

        //No data remains
        if (bb.capacity() == 0) {
            return record;
        }

        final int[] format = record.getHeaderFormat();

        //iterate through the header format array, and grab bytes in segments as specified by the lengths stored in the
        //header format array
        int headerPosition;
        for (headerPosition = 1; headerPosition <= format.length; headerPosition++) {
            final int headerItemLength = format[headerPosition-1];
            final byte[] headerItem = new byte[headerItemLength];
            bb.get(headerItem);
            record.setField(headerPosition,new Field(convertBinaryFieldData(headerItem),ParseContents.FALSE));
        }

        final Field lenField = record.getField(1);

        if (lenField == null) {
            throw new EbtsParsingException(String.format("Field %d/LEN not found", record.getRecordType()));
        }

        final byte[] imageData = new byte[Integer.valueOf(lenField.toString())-record.getHeaderLength()];
        bb.get(imageData);
        record.setField(headerPosition,new Field(imageData,ParseContents.FALSE));

        if (log.isDebugEnabled()) {
            for (final Map.Entry<Integer,Field> entry : record.getFields().entrySet()) {
                if (entry.getKey() != headerPosition) {
                    log.debug("Parsed Field: {} Data:{}",entry.getKey(),entry.getValue().toString());
                } else {
                    log.debug("Parsed Field: {} Data:{}",entry.getKey(),"<Image Data>");
                }
            }
        }

        return record;
    }

    /*
     * Parser for records containing binary header data
     */
    /**
     * Type8 parser.
     *
     * @param recordType the record type
     * @param bb the bb
     * @return the logical record
     */
    private static LogicalRecord parseType8(final int recordType, final ByteBuffer bb) throws EbtsParsingException {

        final int[] headerFormat = new int[]{4,1,1,1,1,2,2};
        return parseBinaryHeaderRecord(recordType, headerFormat, bb);
    }

    /**
     * Convert binary field data.
     *
     * @param data the data
     * @return the byte[]
     */
    private static byte[] convertBinaryFieldData(final byte[] data) {

        if (data.length == 1 || data.length == 2 || data.length == 4) {
            return String.valueOf(EbtsUtils.byteArrayToInt(data)).getBytes();
        } else if (data.length == 6) {
            return String.valueOf((int) data[0]).getBytes();
        } else {
            return data;
        }
    }

}
