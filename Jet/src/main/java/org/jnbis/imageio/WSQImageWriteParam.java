package org.jnbis.imageio;

import javax.imageio.ImageWriteParam;
import java.util.Locale;

public class WSQImageWriteParam extends ImageWriteParam {

    private double bitRate = Double.NaN;

    public WSQImageWriteParam(final Locale locale) {
        super(locale);
    }

    public double getBitRate() {
        return bitRate;
    }

    public void setBitrate(final double bitRate) {
        this.bitRate = bitRate;
    }
}
