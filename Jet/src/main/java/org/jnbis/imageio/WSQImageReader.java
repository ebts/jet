/*
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 * 
 * $Id: $
 */

package org.jnbis.imageio;

import com.google.common.base.Stopwatch;
import org.jnbis.BitmapWithMetadata;
import org.jnbis.WSQDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WSQImageReader extends ImageReader {

    private static final Logger log = LoggerFactory.getLogger(WSQImageReader.class);

    private WSQMetadata metadata;
    private BufferedImage image;

    public WSQImageReader(final ImageReaderSpi provider) {
        super(provider);
    }

    public int getNumImages(final boolean allowSearch) throws IIOException {
        processInput(0);
        return 1;
    }

    public BufferedImage read(final int imageIndex, final ImageReadParam param) throws IIOException {
        processInput(imageIndex);

        //TODO:Subsampling accordingly to ImageReadParam

        return image;
    }

    public int getWidth(final int imageIndex) throws IOException {
        processInput(imageIndex);
        return image.getWidth();
    }

    public int getHeight(final int imageIndex) throws IOException {
        processInput(imageIndex);
        return image.getHeight();
    }

    public IIOMetadata getImageMetadata(final int imageIndex) throws IOException {
        processInput(imageIndex);
        return metadata;
    }

    public Iterator<ImageTypeSpecifier> getImageTypes(final int imageIndex) throws IOException {
        processInput(imageIndex);
        return Collections.singletonList(ImageTypeSpecifier.createFromRenderedImage(image)).iterator();
    }

    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    private void processInput(final int imageIndex) {
        try {
            if (imageIndex != 0) { throw new IndexOutOfBoundsException("imageIndex " + imageIndex); }

            /* Already processed */
            if (image != null) { return; }

            final Object input = getInput();
            if (input == null) {
                this.image = null;
                return;
            }
            if (!(input instanceof ImageInputStream)) { throw new IllegalArgumentException("bad input: " + input.getClass().getCanonicalName()); }
            final Stopwatch stopwatch = new Stopwatch();
            stopwatch.start();
            log.debug("Input:{}",getInput());
            final BitmapWithMetadata bitmap = WSQDecoder.decode((ImageInputStream)getInput());
            stopwatch.stop();
            //log.debug("Decode took: {}",stopwatch.elapsed(TimeUnit.MILLISECONDS));

            metadata = new WSQMetadata(); 

            for (final Map.Entry<String, String> entry : bitmap.getMetadata().entrySet()) {
                //System.out.println(entry.getKey() + ": " + entry.getValue());
                metadata.setProperty(entry.getKey(), entry.getValue());
            }
            for (final String s:bitmap.getComments()) {
                //System.out.println("//"+s);
                metadata.addComment(s);
            }

            image = new BufferedImage(bitmap.getWidth(), bitmap.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            final byte[] imageData = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
            System.arraycopy(bitmap.getPixels(),0,imageData,0,bitmap.getLength());
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            this.image = null;
        }
    }
}
