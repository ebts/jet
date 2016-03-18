package org.jnbis.imageio;

import org.jnbis.Bitmap;
import org.jnbis.WSQEncoder;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WSQImageWriter extends ImageWriter {

    private static final Logger log = LoggerFactory.getLogger(WSQImageWriter.class);

    public static final double DEFAULT_PPI = -1; //Unknown PPI
    public static final double DEFAULT_BITRATE = 1.5; // MO - shouldn't this also be -1 if unknown?
    public WSQImageWriter(final ImageWriterSpi provider) {
        super(provider);
    }
    /**
     * Progressive, tiling, etcetera disabled.
     *
     * @see javax.imageio.ImageWriter#getDefaultWriteParam()
     */
    public ImageWriteParam getDefaultWriteParam() {
        return new WSQImageWriteParam(getLocale());
    }
    public IIOMetadata convertImageMetadata(final IIOMetadata inData, final ImageTypeSpecifier imageType, final ImageWriteParam param) {
        return null;
    }
    public IIOMetadata convertStreamMetadata(final IIOMetadata inData, final ImageWriteParam param) {
        if (inData instanceof WSQMetadata) {
            return inData;
        }
        return null;
    }
    public IIOMetadata getDefaultImageMetadata(final ImageTypeSpecifier imageType, final ImageWriteParam param) {
        return new WSQMetadata();
    }
    public IIOMetadata getDefaultStreamMetadata(final ImageWriteParam param) {
        return null;
    }
    public void write(final IIOMetadata streamMetaData, final IIOImage image, final ImageWriteParam param) throws IIOException {
        try {
            double bitRate = DEFAULT_BITRATE;
            double ppi = DEFAULT_PPI;
            //Use default metadata if not available
            WSQMetadata metadata = (WSQMetadata)image.getMetadata();
            if (metadata == null)
                metadata = new WSQMetadata();
            //Extract PPI from metadata
            if (!Double.isNaN(metadata.getPPI()))
                ppi=metadata.getPPI();
            //Extract Bitrate from metadata or WriteParam
            if (!Double.isNaN(metadata.getBitrate()))
                bitRate=metadata.getBitrate();
            if (param instanceof WSQImageWriteParam) {
                final WSQImageWriteParam wsqParam = (WSQImageWriteParam)param;
                if (!Double.isNaN(wsqParam.getBitRate()))
                    bitRate = wsqParam.getBitRate();
            }
            final BufferedImage bufferedImage = convertRenderedImage(image.getRenderedImage());
            //TODO: Subsampling accordingly to ImageWriteParam
            final Object output = getOutput();
            if (output == null || !(output instanceof ImageOutputStream)) { throw new IllegalStateException("bad output"); }

            log.info("PPI:{}, BITRATE:{}",ppi,bitRate);
            final Bitmap bitmap = new Bitmap(
                    (byte[])bufferedImage.getRaster().getDataElements(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null),
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    (int)Math.round(ppi),
                    8, 1);
            WSQEncoder.encode((ImageOutputStream) getOutput(), bitmap, bitRate, metadata.nistcom, "");
        } catch (final Throwable t) {
            throw new IIOException(t.getMessage(), t);
        }
    }
    /**
     * Converts the given image into a BufferedImage of type {@link BufferedImage#TYPE_BYTE_GRAY}.
     */
    private static BufferedImage convertRenderedImage(final RenderedImage renderedImage) {
        if (renderedImage instanceof BufferedImage) {
            final BufferedImage bufferedImage = (BufferedImage)renderedImage;
            if (bufferedImage.getType() == BufferedImage.TYPE_BYTE_GRAY) {
                log.info("Image is already grayscale");
                return bufferedImage;
            }
        }
        log.info("Image is not grayscale. Converting");

        BufferedImage result = new BufferedImage(renderedImage.getWidth(), renderedImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        //renderedImage.copyData(result.getRaster());

        Graphics g = result.getGraphics();
        g.drawImage((BufferedImage)renderedImage, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        //result = Scalr.apply((BufferedImage)renderedImage,Scalr.OP_GRAYSCALE);

        return result;
    }
} 
