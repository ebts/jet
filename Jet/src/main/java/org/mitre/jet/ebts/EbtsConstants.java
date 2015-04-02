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

/**
 * <p>
 *     Constants that are used in EBTS parsing.
 * </p>
 */
public class EbtsConstants {
    /** Separates records within a file. */
    public static final byte SEPARATOR_FILE = 0x1c;

    /** Separates fields within a record. */
    public static final byte SEPARATOR_GROUP = 0x1d;

    /** Separates subfields within a field. */
    public static final byte SEPARATOR_RECORD = 0x1e;

    /** Separates information items within a field or subfield. */
    public static final byte SEPARATOR_UNIT = 0x1f;
}