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
