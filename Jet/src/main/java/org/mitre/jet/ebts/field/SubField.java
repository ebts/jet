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
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SubField implements Serializable {

    private byte[] data = new byte[0];

    /** Creates a new instance of this class. */
    public SubField() {}

    /**
     * <p>
     *     Creates a new instance of this class using the provided {@literal data}.
     * </p>
     * @param data The data which will be associated with this instance.
     */
    public SubField(@NotNull final byte[] data) {

        this.data = data;
    }

    /**
     * <p>
     *     Creates a new instance of this class using the provided {@literal data}.
     * </p>
     * @param data The data which will be associated with this instance. If this value is {@literal null}, it is implicitly converted to an empty string.
     */
    public SubField(@NotNull final String data) {

        setData(data);
    }

    /** @return The data associated with this instance. */
    @NotNull
    public byte[] getData() {

        return data;
    }

    /** Sets the data associated with this instance. */
    public void setData(@NotNull final byte[] data) {

        this.data = data;
    }

    /** Sets the data associated with this instance. */
    public void setData(@NotNull final String data) {

        this.data = data.getBytes(StandardCharsets.UTF_8);
    }

    public void setData(@NotNull final String data, @NotNull final String encoding) throws UnsupportedEncodingException {

        this.data = data.getBytes(encoding);
    }

    public String toString(@NotNull final String encoding) throws UnsupportedEncodingException {

        return new String(data, encoding);
    }

    @Override
    public String toString() {

        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(@Nullable final Object o) {

        return this == o || o instanceof SubField && Arrays.equals(this.data, ((SubField) o).data);

    }

    @Override
    public int hashCode() {

        return data != null ? Arrays.hashCode(data) : 0;
    }
}
