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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.ebts.field.Occurrence;
import org.mitre.jet.ebts.field.SubField;
import org.mitre.jet.ebts.records.LogicalRecord;

import java.util.*;

/**
 * The Class Ebts.
 *
 * @author ADAY
 */
public class Ebts {

    private final ListMultimap<Integer, LogicalRecord> records = ArrayListMultimap.create();

    /**
     * Create an Ebts instance to store the records for a particular file.
     */
    public Ebts() {}

    /**
     * Add a record to the Ebts instance.
     * 
     * @param record Record to add.
     */
    public void addRecord(final LogicalRecord record) {
        this.records.put(record.getRecordType(), record);
    }
    /**
     * Remove a record from the Ebts instance.
     * 
     * @param record Record to remove.
     */
    public boolean removeRecord(final LogicalRecord record) {
        return this.records.remove(record.getRecordType(), record);
    }

    /**
     * <p>
     *     Gets a mapping of Record Types to the number of records of that type
     *     which appear in this EBTS file. This information is parsed from the
     *     1.003 CNT field, so it is not affected by the {@link ParseType}.
     * </p>
     * @return A mapping of record types to number of records of that type appearing
     * in the EBTS file. If this information cannot be collected for any reason,
     * an empty map is returned.
     */
    public final Map<Integer, Integer> getLogicalRecordCounts() {
        final Map<Integer, Integer> logicalRecordCounts = new HashMap<Integer, Integer>();
        final Field cntField = this.getRecordsByType(1).get(0).getField(3);

        if (cntField == null) {
            throw new IllegalStateException("Ebts does not have CNT field");
        }

        for (final Occurrence occurrence : cntField.getOccurrences()) {
            final List<SubField> subFields = occurrence.getSubFields();
            final int recordType = Integer.valueOf(subFields.get(0).toString());

            if (!logicalRecordCounts.containsKey(recordType)) {
                logicalRecordCounts.put(recordType, 1);
            } else {
                logicalRecordCounts.put(recordType, logicalRecordCounts.get(recordType) + 1);
            }
        }

        return logicalRecordCounts;
    }

    /**
     * Remove a record from the Ebts instance by record type.
     * 
     * @param recordType record type to be removed.
     */
    public void removeRecordsByType(final int recordType) {
        this.records.removeAll(recordType);
    }
    /**
     * Remove all data from the Ebts instance.
     */
    public void clear() {
        this.records.clear();
    }    
    /**
     * Return a list of all records for a given type.
     * @param recordType Record type to return.
     * @return List of logical records.
     */

    public List<LogicalRecord> getRecordsByType(final int recordType) {
        return this.records.get(recordType);
    }

    /**
     * Return all records in the Ebts instance.
     *
     * @return List of all logical records.
     */
    public List<LogicalRecord> getAllRecords() {

        final List<LogicalRecord> recordList = new ArrayList<LogicalRecord>();
        final List<Integer> keys = new ArrayList<Integer>();
        keys.addAll(this.records.keySet());
        Collections.sort(keys);
        for (final Integer key : keys) {
            recordList.addAll(getRecordsByType(key));
        }
        return recordList;

    }

    public ListMultimap<Integer, LogicalRecord> getRecords() {
        return this.records;
    }

    public boolean containsRecord(final int recordType) {
        return this.records.containsKey(recordType) && !this.records.get(recordType).isEmpty();
    }
    //TODO: Add methods that relate to the idc


    @Override
    public String toString() {
        return "Ebts{" +
                "records=" + records +
                '}';
    }
}
