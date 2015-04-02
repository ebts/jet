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

package org.mitre.jet.common;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * The Class ByteBufferUtils.
 *
 * @author ADAY
 */
public class ByteBufferUtils {

    /**
     * Instantiates a new byte buffer utils.
     */
    private ByteBufferUtils() {}

    /**
     * Returns the first index of the provided byte within the byte buffer and moves to that position
     * @param bb ByteBuffer
     * @param find The byte to locate
     * @return index (-1 if not found)
     */
    public static int find(@NotNull final ByteBuffer bb, final byte find) {

        while (bb.hasRemaining()) {
            if (bb.get() == find) {
                return bb.position();
            }
        }

        return -1;
    }

    public static int findIndex(@NotNull final ByteBuffer bb, final byte find) {

        final ByteBuffer bbLocal = bb.duplicate();
        return find(bbLocal,find);
    }

    public static int findIndex(@NotNull final ByteBuffer bb, final byte[] find) {

        int index = -1;
        final ByteBuffer bbLocal = bb.duplicate();
        int findPosition = 0;

        while (bbLocal.hasRemaining()) {
            if (bbLocal.get() == find[findPosition]) {

                findPosition++;

                if (findPosition == find.length) {

                    index = bbLocal.position() - find.length;
                    break;
                }
            } else {
                findPosition = 0;
            }
        }

        return index;
    }

    /**
     * Returns the first index of the provided byte within the byte buffer and moves to that position
     * @param bb ByteBuffer
     * @param find The byte to locate
     * @param start The position to start searching within the byte buffer.
     * @return index (-1 if not found)
     */
    public static int find(@NotNull final ByteBuffer bb, final byte find, final int start) {

        bb.position(start);
        while (bb.hasRemaining()) {
            if (bb.get() == find) {
                return bb.position();
            }
        }

        return -1;
    }      
}
