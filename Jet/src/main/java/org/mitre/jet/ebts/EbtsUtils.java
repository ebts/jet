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

import com.google.common.base.Function;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.mitre.jet.ebts.field.Occurrence;
import org.mitre.jet.ebts.field.SubField;
import org.mitre.jet.exceptions.EbtsHandlingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * The Class EbtsUtils.
 *
 * @author ADAY
 */
public final class EbtsUtils {


    private static final Logger log = LoggerFactory.getLogger(EbtsUtils.class);
    private static final Map<Integer, HashBiMap<Integer, String>> tagMap = loadPropertiesFile();

    private static final Set<Integer> binaryHeaderTypes = Sets.newHashSet(3,4,5,6,7,8);
    private static final Set<Integer> genericRecordTypes = Sets.newHashSet(1,2,9,10,13,14,15,16,17);

    private EbtsUtils() {}

    //Ensures that the Bimap for a particular record exists before trying to 
    //add a map to it

    public static Set<Integer> getBinaryHeaderTypes() {
        return binaryHeaderTypes;
    }

    public static Set<Integer> getGenericRecordTypes() {
        return genericRecordTypes;
    }

    /**
     * Ensure existance.
     *
     * @param map the map

     * @param recordType the record type
     */
    private static void ensureExistence(final Map<Integer, HashBiMap<Integer, String>> map, final int recordType) {
        if (!map.containsKey(recordType)) {
            final HashBiMap<Integer,String> biMap = HashBiMap.create();
            map.put(recordType, biMap);
        }
    }

    /**
     * Load properties file.
     *
     * @return the hash map
     */
    private static Map<Integer,HashBiMap<Integer,String>> loadPropertiesFile(){
        //System.out.println("Loading FieldOccurrence ID -> FieldOccurrence Number Map");
        final Map<Integer,HashBiMap<Integer,String>> map = new HashMap<Integer,HashBiMap<Integer,String>>();
        final String PROP_FILE_CUSTOM = "/TagMapCustom.properties";
        final String PROP_FILE_DEFAULT = "/TagMap.properties";
        InputStream inputStream = null;
        try{
            inputStream = EbtsUtils.class.getResourceAsStream(PROP_FILE_CUSTOM);
            if (inputStream == null) {
                inputStream = EbtsUtils.class.getResourceAsStream(PROP_FILE_DEFAULT);
            }
            final Properties prop = new Properties();
            prop.load(inputStream);

            //Convert to map so that we can iterate
            final Map<String, String> propMap = new HashMap<String, String>((Map) prop);

            for (final Map.Entry<String, String> entry : propMap.entrySet()) {
                //tagArr[0] = Record #
                //tagArr[1] = FieldOccurrence  #
                final int[] tagArr = splitTag(entry.getKey());
                ensureExistence(map, tagArr[0]);
                map.get(tagArr[0]).put(tagArr[1], entry.getValue());

                //System.out.println(tagArr[0] + " " +tagArr[1] + "/" + entry.getValue());
            }
            //System.out.println("Finished loading map");

        }
        catch(final Exception ex){
            log.error(ex.getMessage(),ex);
        }
        finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                }
                catch (final IOException e) {
                    log.error(e.getMessage(),e);
                }
            }
        }
        return map;
    }  

    /**
     * Extracts the field number from a given tag.
     *
     * @param tag (e.g. 2.015)
     * @return integer value corresponding to the field number. (e.g. 15)
     */
    public static int tagToFieldNumber(final String tag) {
        final int[] tagArr = splitTag(tag);
        return tagArr[1];
    }

    /**
     * Extracts the record number from a given tag.
     *
     * @param tag (e.g. 2.015)
     * @return integer value corresponding to the record number. (e.g. 2)
     */
    public static int tagToRecordNumber(final String tag) {
        final int[] tagArr = splitTag(tag);
        return tagArr[0];
    }

    /**
     * Splits a tag into the record number and field number subcomponents.
     * 
     * @param tag (e.g. 2.015)
     * 
     * @return integer array with:
     * position 0 = Record Number
     * position 1 = FieldOccurrence Number
     * 
     */
    public static int[] splitTag(final String tag) {
        final int[] splitField = new int[2];
        //Remove anything before a . (1.2 => 2)
        if (tag.contains(".")) {
            final String[] splitString = tag.split("\\.",2);
            //TODO: Catch / Pass up NumberFormatException
            splitField[0] = Integer.parseInt(splitString[0]);
            splitField[1] = Integer.parseInt(splitString[1]);

        } else {
            log.warn("No field number found in tag: {}",tag);
            splitField[1] = Integer.parseInt(tag);
        }
        return splitField;
    }


    /**
     * Converts a fieldNumber to its corresponding ID.
     * (e.g. recordType 10, fieldNumber 3 => IMT)
     * 
     * @param recordType Record number of the desired field.
     * @param fieldNumber FieldOccurrence number of the desired field.
     * @return String FieldOccurrence ID associated with the field number.
     * 
     */
    public static String fieldNumberToMnemonic(final int recordType, final int fieldNumber) {
        if (tagMap.containsKey(recordType)) {
            return (String)((HashBiMap)tagMap.get(recordType)).get(Integer.valueOf(fieldNumber));
        } else {
            return "";
        }

    }

    /**
     * Converts a fieldNumber to its corresponding ID.
     * (e.g. 10.003 => IMT)
     * 
     * @param tag FieldOccurrence tag (e.g. 10.003)
     * @return String FieldOccurrence ID associated with the field number.
     * 
     */
    public static String fieldTagToMnemonic(final String tag)
        throws EbtsHandlingException {
        final int[] tagArr = splitTag(tag);
        final int recordType = tagArr[0];
        final int fieldNumber = tagArr[1];
        if (tagMap.containsKey(recordType)) {
            return (String)((HashBiMap)tagMap.get(recordType)).get(Integer.valueOf(fieldNumber));
        }

        throw new EbtsHandlingException("No record for record type:" + recordType);
    }

    /**
     * Converts a field tag to its corresponding field number
     * (e.g. Record type = 2, 
     *       FieldOccurrence identifier = IDC 
     *       => 2.002)
     * 
     * @param recordType Record number of the desired field.
     * @param fieldIdentifier ID associated with a field (e.g. IDC,IMT,etc)
     * @return FieldOccurrence number
     */
    public static int fieldMnemonicToNumber(final int recordType, final String fieldIdentifier) throws EbtsHandlingException {
        if ((tagMap.containsKey(recordType)) && (((HashBiMap)tagMap.get(recordType)).inverse().containsKey(fieldIdentifier.toUpperCase()))) {
            return (Integer) ((HashBiMap) tagMap.get(recordType)).inverse().get(fieldIdentifier.toUpperCase());
        } else {
            throw new EbtsHandlingException("Field identifier " + fieldIdentifier + " does not exist for record type:" + recordType);
        }
    }

    /**
     * Convert field list.
     *
     * @param occurrences the occurrences
     * @return the list
     */
    public static List<String> convertOccurrenceList(final List<Occurrence> occurrences) {
        return Lists.newArrayList(Lists.transform(occurrences, new Function<Occurrence, String>() {
            @Override
            public String apply(final Occurrence occurrence) {
                return occurrence.toString();
            }
        }));
    }

    /**
     * Convert string list.
     *
     * @param strings the strings
     * @return the list
     */
    public static List<Occurrence> convertStringList(final List<String> strings) {
        return Lists.newArrayList(Lists.transform(strings, new Function<String, Occurrence>() {
            @Override
            public Occurrence apply(final String s) {
                return new Occurrence(s);
            }
        }));
    }

    /**
     * Convert string list with a size cap.
     *
     * @param strings the strings.
     * @param limit the list cap
     * @return the list
     */
    public static List<Occurrence> convertStringList(final List<String> strings, final int limit) {
        final List<Occurrence> occurrences = new ArrayList<Occurrence>();
        for (final String string : strings) {
            if (occurrences.size() > limit) {   		
                break;
            }
            occurrences.add(new Occurrence(string));
        }
        return occurrences;
    }

    /**
     * Convert string list.
     *
     * @param strings the strings
     * @return the list
     */
    public static List<SubField> convertStringListToSubFields(final List<String> strings) {
        return Lists.newArrayList(Lists.transform(strings, new Function<String, SubField>() {
            @Override
            public SubField apply(final String s) {
                return new SubField(s);
            }
        }));
    }

    public static int byteArrayToInt(final byte[] bytes) {

        if (bytes.length == 4) {
            return Ints.fromByteArray(bytes);
        } else if (bytes.length == 2) {
            return Shorts.fromByteArray(bytes);
        } else if (bytes.length == 1) {
            return bytes[0] & 0xff;
        } else {
            throw new InputMismatchException("invalid data length of "+bytes.length);
        }
    }

    public static String getMimeExtension(final byte[] data) {

        final TikaConfig config = TikaConfig.getDefaultConfig();
        final Detector detector = config.getDetector();

        final TikaInputStream stream = TikaInputStream.get(data);

        final Metadata metadata = new Metadata();

        MediaType mediaType = null;
        try {
            mediaType = detector.detect(stream, metadata);
        } catch (final IOException e) {
            //TODO: Handle
            e.printStackTrace();
        }

        if (mediaType == null) {
            return "";
        }

        MimeType mimeType = null;

        try {
            mimeType = config.getMimeRepository().forName(mediaType.toString());
        } catch (final MimeTypeException e) {
            //TODO: Handle
            e.printStackTrace();
        }

        String extension = "";

        if (mimeType != null) {
            extension = mimeType.getExtension();
        }

        return extension;
    }
}
