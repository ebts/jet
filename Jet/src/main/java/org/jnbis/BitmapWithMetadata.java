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

package org.jnbis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BitmapWithMetadata extends Bitmap {

    private static final long serialVersionUID = -4243273616650162026L;

    private final Map<String, String> metadata = new LinkedHashMap<String, String>();
    private final List<String>        comments = new ArrayList<String>();

    public BitmapWithMetadata(final byte[] pixels, final int width, final int height, final int ppi, final int depth, final int lossyflag) {
        this(pixels, width, height, ppi, depth, lossyflag, null);
    }

    public BitmapWithMetadata(final byte[] pixels, final int width, final int height, final int ppi, final int depth, final int lossyflag, final Map<String,String> metadata, final String... comments) {
        super(pixels, width, height, ppi, depth, lossyflag);
        if (metadata != null)
            this.metadata.putAll(metadata);
        if (comments != null)
            for (final String s:comments)
                if (s != null)
                    this.comments.add(s);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public List<String> getComments() {
        return comments;
    }

}
