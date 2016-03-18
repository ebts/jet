/*
 * WSQ image format encoder based on NBIS.
 *
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
 * --
 * This code is based on FFPIS and / or NBIS by Craig Watson and Michael Garris of NIST.
 * Part of the public domain.
 *
 * $Id: $
 */

package org.jnbis;

import org.jnbis.WSQHelper.Ref;
import org.jnbis.WSQHelper.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WSQ encoder based on NBIS.
 *
 * @author Martijn Oostdijk (martijn.oostdijk@gmail.com)
 *
 * @version 0.0.2
 */
public class WSQEncoder implements WSQConstants, NISTConstants {

    private static final Logger log = LoggerFactory.getLogger(WSQEncoder.class);

    public static void encode(final OutputStream os, final Bitmap bitmap, final double bitRate, final String... comments) throws IOException {
        encode(os, bitmap, bitRate, null, comments);
    }

    public static void encode(final DataOutput dataOutput, final Bitmap bitmap, final double bitRate, final String... comments) throws
            IOException {
        encode(dataOutput, bitmap, bitRate, null, comments);
    }

    public static void encode(final OutputStream os, final Bitmap bitmap, final double bitRate, final Map<String, String> metadata,
                              final String... comments) throws
            IOException {
        encode((DataOutput) new DataOutputStream(os), bitmap, bitRate, metadata, comments);
    }

    public static void encode(final DataOutput dataOutput, final Bitmap _bitmap, final double bitRate, final Map<String, String> metadata,
                              final String... comments) throws
            IOException {
        final BitmapWithMetadata bitmap;
        if (_bitmap instanceof BitmapWithMetadata) {
            bitmap = (BitmapWithMetadata) _bitmap;
        } else {
            bitmap = new BitmapWithMetadata(_bitmap.getPixels(), _bitmap.getWidth(), _bitmap.getHeight(),
                                            _bitmap.getPpi(), _bitmap.getDepth(), _bitmap.getLossyflag());
        }
        if (metadata != null)
            bitmap.getMetadata().putAll(metadata);
        if (comments != null)
            for (final String s : comments)
                if (s != null)
                    bitmap.getComments().add(s);

        final float[] fdata;	/* floating point pixel image  */
        final int[] qdata;	/* quantized image pointer     */
        final AtomicInteger qsize = new AtomicInteger();  /* quantized block sizes */
        final AtomicInteger qsize1 = new AtomicInteger();
        final AtomicInteger qsize2 = new AtomicInteger();
        final AtomicInteger qsize3 = new AtomicInteger();
        WSQHelper.HuffCode[] hufftable;
        final Ref<int[]> huffbits = new Ref<int[]>(); /* huffman code parameters */
        final Ref<int[]> huffvalues = new Ref<int[]>();

        /* Convert image pixels to floating point. */
        final Ref<Float> m_shift = new Ref<Float>();
        final Ref<Float> r_scale = new Ref<Float>();
        fdata = convertImageToFloat(bitmap.getPixels(), bitmap.getWidth(), bitmap.getHeight(), m_shift, r_scale);

        log.debug("Input image pixels converted to floating point");
        log.debug("");

        final Token token = new Token();

        /* Build WSQ decomposition trees */
        WSQHelper.buildWSQTrees(token, bitmap.getWidth(), bitmap.getHeight());

        log.debug("Tables for wavelet decomposition finished");
        log.debug("");

        /* WSQ decompose the image */
        wsqDecompose(token, fdata, bitmap.getWidth(), bitmap.getHeight(), token.tableDTT.hifilt, MAX_HIFILT, token.tableDTT.lofilt, MAX_LOFILT);

        log.debug("WSQ decomposition of image finished");
        log.debug("");

        /* Set compression ratio and 'q' to zero. */
        token.quant_vals.cr = 0;
        token.quant_vals.q = 0.0f;

        /* Assign specified r-bitrate into quantization structure. */
        token.quant_vals.r = (float)bitRate;

        /* Compute subband variances. */
        variance(token, fdata, bitmap.getWidth(), bitmap.getHeight());

        log.debug("Subband variances computed");
        log.debug("");

        /* Quantize the floating point pixmap. */
        qdata = quantize(token, qsize, fdata, bitmap.getWidth(), bitmap.getHeight());

        log.debug("WSQ subband decomposition data quantized");
        log.debug("");

        /* Compute quantized WSQ subband block sizes */
        quant_block_sizes(token, qsize1, qsize2, qsize3);

        if (qsize.get() != qsize1.get() + qsize2.get() + qsize3.get()) {
            throw new IllegalStateException("ERROR : wsq_encode_1 : problem w/quantization block sizes");
        }

        /* Add a Start Of Image (SOI) marker to the WSQ buffer. */
        dataOutput.writeShort(SOI_WSQ);

        putc_nistcom_wsq(dataOutput, bitmap, (float)bitRate, metadata, comments);

        /* Store the Wavelet filter taps to the WSQ buffer. */
        putc_transform_table(dataOutput, token.tableDTT.lofilt, MAX_LOFILT, token.tableDTT.hifilt, MAX_HIFILT);

        /* Store the quantization parameters to the WSQ buffer. */
        putc_quantization_table(dataOutput, token);

        /* Store a frame header to the WSQ buffer. */
        putc_frame_header_wsq(dataOutput, bitmap.getWidth(), bitmap.getHeight(), m_shift.value, r_scale.value);

        log.debug("SOI, tables, and frame header written");
        log.debug("");

        /* ENCODE Block 1 */

        /* Compute Huffman table for Block 1. */
        hufftable = gen_hufftable_wsq(token, huffbits, huffvalues, qdata, 0, new int[] { qsize1.get() });

        /* Store Huffman table for Block 1 to WSQ buffer. */
        putc_huffman_table(dataOutput, DHT_WSQ, 0, huffbits.value, huffvalues.value);

        log.debug("Huffman code Table 1 generated and written");
        log.debug("");

        /* Store Block 1's header to WSQ buffer. */
        putc_block_header(dataOutput, 0);

        /* Compress Block 1 data. */
        compress_block(dataOutput, qdata, 0, qsize1.get(), MAX_HUFFCOEFF, MAX_HUFFZRUN, hufftable);

        log.debug("Block 1 compressed and written");
        log.debug("");

        /* ENCODE Block 2 */

        /* Compute  Huffman table for Blocks 2 & 3. */
        hufftable = gen_hufftable_wsq(token, huffbits, huffvalues, qdata, qsize1.get(), new int[] { qsize2.get(), qsize3.get() });

        /* Store Huffman table for Blocks 2 & 3 to WSQ buffer. */
        putc_huffman_table(dataOutput, DHT_WSQ, 1, huffbits.value, huffvalues.value);

        log.debug("Huffman code Table 2 generated and written");
        log.debug("");

        /* Store Block 2's header to WSQ buffer. */
        putc_block_header(dataOutput, 1);

        /* Compress Block 2 data. */
        compress_block(dataOutput, qdata, qsize1.get(), qsize2.get(), MAX_HUFFCOEFF, MAX_HUFFZRUN, hufftable);

        log.debug("Block 2 compressed and written");
        log.debug("");

        /* ENCODE Block 3 */

        /* Store Block 3's header to WSQ buffer. */
        putc_block_header(dataOutput, 1);

        /* Compress Block 3 data. */
        compress_block(dataOutput, qdata, qsize1.get() + qsize2.get(), qsize3.get(), MAX_HUFFCOEFF, MAX_HUFFZRUN, hufftable);

        log.debug("Block 3 compressed and written");
        log.debug("");

        /* Add a End Of Image (EOI) marker to the WSQ buffer. */
        dataOutput.writeShort(EOI_WSQ);
    }

    /**
     * Corresponds to conv_img_2_flt_ret in imgtools/src/lib/wsq/encoder.c.
     * Converts an image's unsigned character pixels to floating point values in the range +/- 128.0.
     *
     * @param data input image, values should be regarded as unsigned
     * @param width width of input image
     * @param height height of input image
     * @param m_shift output for shift, needs be non-null and length >= 1
     * @param r_scale output for scale, needs to be non-null and length >= 1
     */
    private static float[] convertImageToFloat(final byte[] data, final int width, final int height, final Ref<Float> m_shift, final Ref<Float> r_scale) {
        if (data == null) { throw new IllegalArgumentException("Image cannot be null"); }
        int cnt;                     /* pixel cnt */
        int sum;                     /* sum of pixel values */
        int low, high;               /* low/high pixel values */
        final float low_diff;   /* new low/high pixels values shifting */
        final float high_diff;

        final float[] fip = new float[data.length];

        sum = 0;
        low = 255;
        high = 0;
        for (cnt = 0; cnt < data.length; cnt++) {
            if ((data[cnt] & 0xFF) > high) {
                high = data[cnt] & 0xFF;
            }
            if ((data[cnt] & 0xFF) < low) {
                low = data[cnt] & 0xFF;
            }
            sum += (data[cnt] & 0xFF);
        }

        m_shift.value = (float) sum / (float) data.length;

        low_diff = m_shift.value - low;
        high_diff = high - m_shift.value;

        if (low_diff >= high_diff) {
            r_scale.value = low_diff;
        } else {
            r_scale.value = high_diff;
        }

        r_scale.value /= (float)128.0;

        for (cnt = 0; cnt < data.length; cnt++) {
            fip[cnt] = ((float)(data[cnt] & 0xFF) - m_shift.value) / r_scale.value;
        }
        return fip;
    }

    /**
     * Corresponds to wsq_decompose() in imgtools/src/lib/wsq/util.c
     *
     * WSQ decompose the image.  NOTE: this routine modifies and returns
     * the results in "fdata"
     */
    private static void wsqDecompose(final Token token, final float[] fdata, final int width, final int height, final float[] hifilt, final int hisz, final float[] lofilt, final int losz) {

        final int num_pix = width * height;
        /* Allocate temporary floating point pixmap. */
        final float[] fdata1 = new float[num_pix];

        /* Compute the Wavelet image decomposition. */
        for (int node = 0; node < token.wtree.length; node++) {
            final int fdataBse = (token.wtree[node].y * width) + token.wtree[node].x;

            getLets(fdata1, fdata, 0, fdataBse, token.wtree[node].leny, token.wtree[node].lenx,
                    width, 1, hifilt, hisz, lofilt, losz, token.wtree[node].invrw);
            getLets(fdata, fdata1, fdataBse, 0, token.wtree[node].lenx, token.wtree[node].leny,
                    1, width, hifilt, hisz, lofilt, losz, token.wtree[node].invcl);
        }
    }

    private static void getLets(final float[] newdata,     /* image pointers for creating subband splits */
            final float[] olddata,
            final int newIndex,
            final int oldIndex,
            final int len1,       /* temporary length parameters */
            final int len2,
            final int pitch,      /* pitch gives next row_col to filter */
            final int  stride,    /*           stride gives next pixel to filter */
            final float[] hi,
            final int hsz,   /* NEW */
            final float[] lo,      /* filter coefficients */
            final int lsz,   /* NEW */
            final int inv)        /* spectral inversion? */ {
        if (newdata == null) { throw new IllegalArgumentException("newdata == null"); }
        if (olddata == null) { throw new IllegalArgumentException("olddata == null"); }
        if (lo == null) { throw new IllegalArgumentException("lo == null"); }

        int lopass, hipass;	/* pointers of where to put lopass
                                   and hipass filter outputs */
        int p0, p1;		/* pointers to image pixels used */
        int pix, rw_cl;		/* pixel counter and row/column counter */
        int i;		/* even or odd row/column of pixels */
        final int da_ev;
        final int fi_ev;
        int loc;
        int hoc;
        final int nstr;
        final int pstr;
        final int llen;
        final int hlen;
        int lpxstr, lspxstr;
        int lpx, lspx;
        int hpxstr, hspxstr;
        int hpx, hspx;
        int olle, ohle;
        final int olre;
        final int ohre;
        int lle, lle2;
        int lre, lre2;
        int hle, hle2;
        int hre, hre2;

        da_ev = len2 % 2;
        fi_ev = lsz % 2;

        if (fi_ev != 0) {
            loc = (lsz-1)/2;
            hoc = (hsz-1)/2 - 1;
            olle = 0;
            ohle = 0;
            olre = 0;
            ohre = 0;
        } else {
            loc = lsz/2 - 2;
            hoc = hsz/2 - 2;
            olle = 1;
            ohle = 1;
            olre = 1;
            ohre = 1;

            if (loc == -1) {
                loc = 0;
                olle = 0;
            }
            if (hoc == -1) {
                hoc = 0;
                ohle = 0;
            }

            for (i = 0; i < hsz; i++) {
                hi[i] *= -1.0;
            }
        }

        pstr = stride;
        nstr = -pstr;

        if (da_ev != 0) {
            llen = (len2+1)/2;
            hlen = llen - 1;
        } else {
            llen = len2/2;
            hlen = llen;
        }

        for (rw_cl = 0; rw_cl < len1; rw_cl++) {
            if (inv != 0) {
                hipass = newIndex + rw_cl * pitch;
                lopass = hipass + hlen * stride;
            } else {
                lopass = newIndex + rw_cl * pitch;
                hipass = lopass + llen * stride;
            }

            p0 = oldIndex + rw_cl * pitch;
            p1 = p0 + (len2-1) * stride;

            lspx = p0 + (loc * stride);
            lspxstr = nstr;
            lle2 = olle;
            lre2 = olre;
            hspx = p0 + (hoc * stride);
            hspxstr = nstr;
            hle2 = ohle;
            hre2 = ohre;
            for (pix = 0; pix < hlen; pix++) {
                lpxstr = lspxstr;
                lpx = lspx;
                lle = lle2;
                lre = lre2;
                newdata[lopass] = olddata[lpx] * lo[0];
                for (i = 1; i < lsz; i++) {
                    if (lpx == p0) {
                        if (lle != 0) {
                            lpxstr = 0;
                            lle = 0;
                        } else
                            lpxstr = pstr;
                    }
                    if (lpx == p1) {
                        if (lre != 0) {
                            lpxstr = 0;
                            lre = 0;
                        } else {
                            lpxstr = nstr;
                        }
                    }
                    lpx += lpxstr;
                    newdata[lopass] += olddata[lpx] * lo[i];
                }
                lopass += stride;

                hpxstr = hspxstr;
                hpx = hspx;
                hle = hle2;
                hre = hre2;
                newdata[hipass] = olddata[hpx] * hi[0];
                for (i = 1; i < hsz; i++) {
                    if (hpx == p0) {
                        if (hle != 0) {
                            hpxstr = 0;
                            hle = 0;
                        } else {
                            hpxstr = pstr;
                        }
                    }
                    if (hpx == p1) {
                        if (hre != 0) {
                            hpxstr = 0;
                            hre = 0;
                        } else {
                            hpxstr = nstr;
                        }
                    }
                    hpx += hpxstr;
                    newdata[hipass] += olddata[hpx] * hi[i];
                }
                hipass += stride;

                for (i = 0; i < 2; i++) {
                    if (lspx == p0) {
                        if (lle2 != 0) {
                            lspxstr = 0;
                            lle2 = 0;
                        } else {
                            lspxstr = pstr;
                        }
                    }
                    lspx += lspxstr;
                    if (hspx == p0) {
                        if (hle2 != 0) {
                            hspxstr = 0;
                            hle2 = 0;
                        } else {
                            hspxstr = pstr;
                        }
                    }
                    hspx += hspxstr;
                }
            }
            if (da_ev != 0) {
                lpxstr = lspxstr;
                lpx = lspx;
                lle = lle2;
                lre = lre2;
                newdata[lopass] = olddata[lpx] * lo[0];
                for (i = 1; i < lsz; i++) {
                    if (lpx == p0) {
                        if (lle != 0) {
                            lpxstr = 0;
                            lle = 0;
                        } else {
                            lpxstr = pstr;
                        }
                    }
                    if (lpx == p1) {
                        if (lre != 0) {
                            lpxstr = 0;
                            lre = 0;
                        } else {
                            lpxstr = nstr;
                        }
                    }
                    lpx += lpxstr;
                    newdata[lopass] += olddata[lpx] * lo[i];
                }
                lopass += stride;
            }
        }
        if (fi_ev == 0) {
            for (i = 0; i < hsz; i++) {
                hi[i] *= -1.0;
            }
        }
    }

    /**
     * Corresponds to variance() in imgtools/src/lib/wsq/util.c
     *
     * This routine calculates the variances of the subbands.
     *
     * @param token contains quant_vals quantization parameters and quantization "tree" and treelen. NOTE: This routine will write to <code>var</code> field inside <code>quant_vals</code>.
     * @param fip image pointer
     * @param width image width
     * @param height image height
     */
    private static void variance(final Token token, final float[] fip, final int width, final int height) {
        int fp;                 /* temp image pointer */
        int lenx, leny; /* dimensions of area to calculate variance */
        int skipx, skipy;       /* pixels to skip to get to area for variance calculation */
        int row, col;           /* dimension counters */
        float ssq;              /* sum of squares */
        float sum2;             /* variance calculation parameter */
        float sum_pix;          /* sum of pixels */
        float vsum;             /* variance sum for subbands 0-3 */

        vsum = 0;
        for (int cvr = 0; cvr < 4; cvr++) {
            fp = ((token.qtree[cvr].y) * width) + token.qtree[cvr].x;
            ssq = 0.0f;
            sum_pix = 0.0f;

            skipx = token.qtree[cvr].lenx / 8;
            skipy = (9 * token.qtree[cvr].leny) / 32;

            lenx = (3 * token.qtree[cvr].lenx) / 4;
            leny = (7 * token.qtree[cvr].leny) / 16;

            fp += (skipy * width) + skipx;
            for (row = 0; row < leny; row++, fp += (width - lenx)) {
                for (col = 0; col < lenx; col++) {
                    sum_pix += fip[fp];
                    ssq += fip[fp] * fip[fp];
                    fp++;
                }
            }
            sum2 = (sum_pix * sum_pix)/(lenx * leny);
            token.quant_vals.var[cvr] = (ssq - sum2)/((lenx * leny)-1.0f);
            vsum += token.quant_vals.var[cvr];
        }

        //This part is needed to comply with WSQ 3.1
        if (vsum < 20000.0) {
            for (int cvr = 0; cvr < NUM_SUBBANDS; cvr++) {
                fp = (token.qtree[cvr].y * width) + token.qtree[cvr].x;
                ssq = 0;
                sum_pix = 0;

                lenx = token.qtree[cvr].lenx;
                leny = token.qtree[cvr].leny;

                for (row = 0; row < leny; row++, fp += (width - lenx)) {
                    for (col = 0; col < lenx; col++) {
                        sum_pix += fip[fp];
                        ssq += fip[fp] * fip[fp];
                        fp++;
                    }
                }
                sum2 = (sum_pix * sum_pix)/(lenx * leny);
                token.quant_vals.var[cvr] = (float)((ssq - sum2)/((lenx * leny)-1.0));
            }
        } else {
            for (int cvr = 4; cvr < NUM_SUBBANDS; cvr++) {
                fp = (token.qtree[cvr].y * width) + token.qtree[cvr].x;
                ssq = 0;
                sum_pix = 0;

                skipx = token.qtree[cvr].lenx / 8;
                skipy = (9 * token.qtree[cvr].leny)/32;

                lenx = (3 * token.qtree[cvr].lenx)/4;
                leny = (7 * token.qtree[cvr].leny)/16;

                fp += (skipy * width) + skipx;
                for (row = 0; row < leny; row++, fp += (width - lenx)) {
                    for (col = 0; col < lenx; col++) {
                        sum_pix += fip[fp];
                        ssq += fip[fp] * fip[fp];
                        fp++;
                    }
                }
                sum2 = (sum_pix * sum_pix)/(lenx * leny);
                token.quant_vals.var[cvr] = (float)((ssq - sum2)/((lenx * leny)-1.0));
            }
        }
    }

    /**
     * Corresponds to quantize() in imgtools/src/lib/wsq/util.c
     *
     * This routine quantizes the wavelet subbands.
     *
     * @param token contains quantization parameters, quantization tree, size of quantization tree
     * @param qsize output size
     * @param fip floating point image pointer
     * @param width image width
     * @param height image height
     *
     * @return quantized image
     */
    private static int[] quantize(final Token token, final AtomicInteger qsize, final float[] fip, final int width, final int height) {
        int row, col;          /* temp image characteristic parameters */
        float zbin;            /* zero bin size */
        final float[] A = new float[NUM_SUBBANDS]; /* subband "weights" for quantization */
        final float[] m = new float[NUM_SUBBANDS]; /* subband size to image size ratios */
        /* (reciprocal of FBI spec for 'm')  */
        final float m1;      /* reciprocal constants for 'm' */
        final float m2;
        final float m3;
        final float[] sigma = new float[NUM_SUBBANDS]; /* square root of subband variances */
        final int[] K0 = new int[NUM_SUBBANDS];  /* initial list of subbands w/variance >= thresh */
        final int[] K1 = new int[NUM_SUBBANDS];  /* working list of subbands */
        int K, nK;           /* pointers to sets of subbands */
        boolean[] NP;  /* current subbounds with nonpositive bit rates. */
        int K0len;             /* number of subbands in K0 */
        int Klen, nKlen;       /* number of subbands in other subband lists */
        int NPlen;             /* number of subbands flagged in NP */
        float S;               /* current frac of subbands w/positive bit rate */
        float q;               /* current proportionality constant */
        float P;               /* product of 'q/Q' ratios */

        /* Set up 'A' table. */
        for (int cnt = 0; cnt < STRT_SUBBAND_3; cnt++) {
            A[cnt] = 1.0f;
        }
        A[STRT_SUBBAND_3 /*52*/] = 1.32f;
        A[STRT_SUBBAND_3 + 1 /*53*/] = 1.08f;
        A[STRT_SUBBAND_3 + 2 /*54*/] = 1.42f;
        A[STRT_SUBBAND_3 + 3 /*55*/] = 1.08f;
        A[STRT_SUBBAND_3 + 4 /*56*/] = 1.32f;
        A[STRT_SUBBAND_3 + 5 /*57*/] = 1.42f;
        A[STRT_SUBBAND_3 + 6 /*58*/] = 1.08f;
        A[STRT_SUBBAND_3 + 7 /*59*/] = 1.08f;

        for (int cnt = 0; cnt < MAX_SUBBANDS; cnt++) {
            token.quant_vals.qbss[cnt] = 0.0f;
            token.quant_vals.qzbs[cnt] = 0.0f;
        }

        /* Set up 'Q1' (prime) table. */
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            if (token.quant_vals.var[cnt] < VARIANCE_THRESH) {
                token.quant_vals.qbss[cnt] = 0.0f;
            } else {
                /* NOTE: q has been taken out of the denominator in the next */
                /*       2 formulas from the original code. */
                if (cnt < STRT_SIZE_REGION_2 /*4*/) {
                    token.quant_vals.qbss[cnt] = 1.0f;
                } else {
                    token.quant_vals.qbss[cnt] = 10.0f / (A[cnt] * (float)Math.log(token.quant_vals.var[cnt]));
                }
            }
        }

        /* Set up output buffer. */
        final int[] sip = new int[width * height];
        int sptr = 0;

        /* Set up 'm' table (these values are the reciprocal of 'm' in the FBI spec). */
        m1 = 1.0f/1024.0f;
        m2 = 1.0f/256.0f;
        m3 = 1.0f/16.0f;
        for (int cnt = 0; cnt < STRT_SIZE_REGION_2; cnt++) {
            m[cnt] = m1;
        }
        for (int cnt = STRT_SIZE_REGION_2; cnt < STRT_SIZE_REGION_3; cnt++) {
            m[cnt] = m2;
        }
        for (int cnt = STRT_SIZE_REGION_3; cnt < NUM_SUBBANDS; cnt++) {
            m[cnt] = m3;
        }

        /* Initialize 'K0' and 'K1' lists. */
        K0len = 0;
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            if (token.quant_vals.var[cnt] >= VARIANCE_THRESH){
                K0[K0len] = cnt;
                K1[K0len++] = cnt;
                /* Compute square root of subband variance. */
                sigma[cnt] = (float)Math.sqrt(token.quant_vals.var[cnt]);
            }
        }
        K = 0;
        Klen = K0len;

        while (true) {
            /* Compute new 'S' */
            S = 0.0f;
            for (int i = 0; i < Klen; i++){
                /* Remember 'm' is the reciprocal of spec. */
                S += m[K1[K + i]];
            }

            /* Compute product 'P' */
            P = 1.0f;
            for (int i = 0; i < Klen; i++){
                /* Remember 'm' is the reciprocal of spec. */
                P *= Math.pow((sigma[K1[K + i]] / token.quant_vals.qbss[K1[K + i]]), m[K1[K + i]]);
            }

            /* Compute new 'q' */
            q = ((float)Math.pow(2,((token.quant_vals.r/S)-1.0f))/2.5f) / (float)Math.pow(P, (1.0f/S));

            /* Flag subbands with non-positive bitrate. */
            NP = new boolean[NUM_SUBBANDS];
            NPlen = 0;
            for (int i = 0; i < Klen; i++){
                if ((token.quant_vals.qbss[K1[K + i]] / q) >= (5.0*sigma[K1[K + i]])) {
                    NP[K1[K + i]] = true;
                    NPlen++;
                }
            }

            /* If list of subbands with non-positive bitrate is empty ... */
            if (NPlen == 0){
                /* Then we are done, so break from while loop. */
                break;
            }

            /* Assign new subband set to previous set K minus subbands in set NP. */
            nK = 0;
            nKlen = 0;
            for (int i = 0; i < Klen; i++){
                if (!NP[K1[K + i]]) {
                    K1[nK + nKlen++] = K1[K + i];
                }
            }

            /* Assign new set as K. */
            K = nK;
            Klen = nKlen;
        }

        /* Flag subbands that are in set 'K0' (the very first set). */
        nK = 0;
        Arrays.fill(K1, nK, NUM_SUBBANDS, 0); // was: memset(nK, 0, NUM_SUBBANDS * sizeof(int));
        for (int i = 0; i < K0len; i++){
            K1[nK + K0[i]] = 1; /* MO: was = TRUE */
        }
        /* Set 'Q' values. */
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            if (K1[nK + cnt] != 0) {
                token.quant_vals.qbss[cnt] /= q;
            } else {
                token.quant_vals.qbss[cnt] = 0.0f;
            }
            token.quant_vals.qzbs[cnt] = 1.2f * token.quant_vals.qbss[cnt];
        }

        /* Now ready to compute and store bin widths for subbands. */
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            int fptr = (token.qtree[cnt].y * width) + token.qtree[cnt].x;

            if (token.quant_vals.qbss[cnt] != 0.0f) {

                zbin = token.quant_vals.qzbs[cnt] / 2.0f;

                for (row = 0; row < token.qtree[cnt].leny; row++, fptr += width - token.qtree[cnt].lenx){
                    for (col = 0; col < token.qtree[cnt].lenx; col++) {
                        if (-zbin <= fip[fptr] && fip[fptr] <= zbin) {
                            sip[sptr] = 0;
                        } else if (fip[fptr] > 0.0f) {
                            sip[sptr] = (int)(((fip[fptr] - zbin)/token.quant_vals.qbss[cnt]) + 1.0f);
                        } else {
                            sip[sptr] = (int)(((fip[fptr] + zbin)/token.quant_vals.qbss[cnt]) - 1.0f);
                        }
                        sptr++;
                        fptr++;
                    }
                }
            } else {
                log.debug(String.format("%d -> %.6f", cnt, token.quant_vals.qbss[cnt]));
            }
        }

        qsize.set(sptr);

        return sip;
    }

    /************************************************************************/
    /* Compute quantized WSQ subband block sizes.                           */
    /************************************************************************/
    /*
       Corresponds to quant_block_sizes() in imgtools/src/lib/wsq/util.c
       */
    private static void quant_block_sizes(final Token token, final AtomicInteger oqsize1, final AtomicInteger oqsize2, final AtomicInteger oqsize3) {
        int qsize1, qsize2, qsize3;
        int node;

        /* Compute temporary sizes of 3 WSQ subband blocks. */
        qsize1 = token.wtree[14].lenx * token.wtree[14].leny;
        qsize2 = (token.wtree[5].leny * token.wtree[1].lenx) +
            (token.wtree[4].lenx * token.wtree[4].leny);
        qsize3 = (token.wtree[2].lenx * token.wtree[2].leny) +
            (token.wtree[3].lenx * token.wtree[3].leny);

        /* Adjust size of quantized WSQ subband blocks. */
        for (node = 0; node < STRT_SUBBAND_2; node++) {
            if (token.quant_vals.qbss[node] == 0.0f) {
                qsize1 -= (token.qtree[node].lenx * token.qtree[node].leny);
            }
        }

        for (node = STRT_SUBBAND_2; node < STRT_SUBBAND_3; node++) {
            if (token.quant_vals.qbss[node] == 0.0f) {
                qsize2 -= (token.qtree[node].lenx * token.qtree[node].leny);
            }
        }

        for (node = STRT_SUBBAND_3; node < STRT_SUBBAND_DEL; node++) {
            if (token.quant_vals.qbss[node] == 0.0f) {
                qsize3 -= (token.qtree[node].lenx * token.qtree[node].leny);
            }
        }

        oqsize1.set(qsize1);
        oqsize2.set(qsize2);
        oqsize3.set(qsize3);
    }

    private static void putc_huffman_table(final DataOutput dataOutput, final int marker, final int tableId, final int[] huffbits, final int[] huffvalues) throws IOException {

        log.debug("Start writing huffman table.");

        /* DHT */
        dataOutput.writeShort(marker);

        /* "value(2) + table id(1) + bits(16)" */
        int table_len = 3 + MAX_HUFFBITS;
        final int values_offset = table_len;
        for (int i = 0; i < MAX_HUFFBITS; i++) {
            table_len += huffbits[i];   /* values size */
        }

        /* Table Len */
        dataOutput.writeShort(table_len & 0xFFFF);

        /* Table ID */
        dataOutput.writeByte(tableId & 0xFF);

        /* Huffbits (MAX_HUFFBITS) */
        for (int i = 0; i < MAX_HUFFBITS; i++){
            dataOutput.writeByte(huffbits[i] & 0xFF);
        }

        /* Huffvalues (MAX_HUFFCOUNTS) */
        for (int i = 0; i < table_len-values_offset; i++){
            dataOutput.writeByte(huffvalues[i] & 0xFF);
        }
        log.debug("Finished writing huffman table.");
        log.debug("");

    }

    /**
     * Corresponds to putc_frame_header_wsq() in imgtools/src/lib/wsq/tableio.c
     *
     * @param dataOutput
     * @param width
     * @param height
     * @param m_shift
     * @param r_scale
     * @throws IOException
     */
    private static void putc_frame_header_wsq(final DataOutput dataOutput, final int width, final int height, final float m_shift, final float r_scale) throws IOException {
        float flt_tmp;         /* temp variable */
        int scale_ex;         /* exponent scaling parameter */

        int shrt_dat;       /* temp variable */
        dataOutput.writeShort(SOF_WSQ); /* +2 = 2 */

        log.debug("Writing frame header.");

        /* size of frame header */
        dataOutput.writeShort(17);

        /* black pixel */
        dataOutput.writeByte(0); /* +1 = 3 */

        /* white pixel */
        dataOutput.writeByte(255); /* +1 = 4 */

        dataOutput.writeShort(height); /* +2 = 5 */
        dataOutput.writeShort(width); /* +2 = 7 */


        log.debug("m_shift = {}", m_shift);
        log.debug("\tr_scale = {}", r_scale);

        flt_tmp = m_shift;
        scale_ex = 0;
        if (flt_tmp != 0.0) {
            while (flt_tmp < 65535) {
                scale_ex += 1;
                flt_tmp *= 10;
            }
            scale_ex -= 1;
            shrt_dat = Math.round(flt_tmp / 10.0f);
        } else {
            shrt_dat = 0;
        }
        dataOutput.writeByte(scale_ex & 0xFF); /* +1 = 9 */
        dataOutput.writeShort(shrt_dat); /* +2 = 11 */

        flt_tmp = r_scale;
        scale_ex = 0;
        if (flt_tmp != 0.0) {
            while (flt_tmp < 65535) {
                scale_ex += 1;
                flt_tmp *= 10;
            }
            scale_ex -= 1;
            shrt_dat = Math.round(flt_tmp / 10.0f);
        } else {
            shrt_dat = 0;
        }
        dataOutput.writeByte(scale_ex); /* +1 = 12 */
        dataOutput.writeShort(shrt_dat); /* +2 = 13 */

        dataOutput.writeByte(0); /* +1 = 15 */
        dataOutput.writeShort(0); /* +2 = 17 */

        log.debug("Finished writing frame header.");
        log.debug("");
    }

    /*
       Corresponds to putc_transform_table() in imgtools/src/lib/wsq/tableio.c
       */
    private static void putc_transform_table(final DataOutput dataOutput, final float[] lofilt, final int losz, final float[] hifilt, final int hisz) throws IOException {
        long coef;				/* filter coefficient indicator */
        long int_dat;			/* temp variable */
        double dbl_tmp;			/* temp variable */
        int scale_ex, sign;	/* exponent scaling and sign parameters */

        log.debug("Writing transform table.");

        /* FIXME how big can hisz,losz get */
        if (losz < 0 || losz > Integer.MAX_VALUE / 2) {
            throw new IllegalStateException("Writing transform table: losz out of range");
        }
        if (hisz < 0 || hisz > Integer.MAX_VALUE / 2) {
            throw new IllegalStateException("Writing transform table: hisz out of range");
        }
        dataOutput.writeShort(DTT_WSQ);

        /* table size */
        dataOutput.writeShort(58);

        /* number analysis lowpass coefficients */
        dataOutput.writeByte(losz);

        /* number analysis highpass coefficients */
        dataOutput.writeByte(hisz);

        for (coef = (losz>>1); (coef & 0xFFFFFFFFL) < (long)losz; coef++) {
            dbl_tmp = lofilt[(int)(coef & 0xFFFFFFFFL)];
            if (dbl_tmp >= 0.0) {
                sign = 0;
            } else {
                sign = 1;
                dbl_tmp *= -1.0;
            }
            scale_ex = 0;
            if (dbl_tmp == 0.0) {
                int_dat = 0;
            } else if (dbl_tmp < 4294967295.0) {
                while (dbl_tmp < 4294967295.0) {
                    scale_ex += 1;
                    dbl_tmp *= 10.0;
                }
                scale_ex -= 1;
                int_dat = (int)Math.round(dbl_tmp / 10.0);
            } else {
                dbl_tmp = lofilt[(int)(coef & 0xFFFFFFFFL)];
                throw new IllegalStateException(String.format("ERROR: putc_transform_table : lofilt[%d] to high at %f" , coef, dbl_tmp));
            }

            if (log.isDebugEnabled()) {
                log.debug("lo[{}] = {}", coef, int_dat & 0xFFFFFFFFL);
                log.debug("lof[{}] = {}", coef, String.format("%.15f", lofilt[(int) coef]));
            }

            dataOutput.writeByte(sign & 0xFF);
            dataOutput.writeByte(scale_ex & 0xFF);
            dataOutput.writeInt((int)(int_dat & 0xFFFFFFFFL));
        }

        for (coef = (hisz>>1); (int)(coef & 0xFFFFFFFFL) < (long)hisz; coef++) {
            dbl_tmp = hifilt[(int)(coef & 0xFFFFFFFFL)];
            if (dbl_tmp >= 0.0) {
                sign = 0;
            } else {
                sign = 1;
                dbl_tmp *= -1.0;
            }
            scale_ex = 0;
            if (dbl_tmp == 0.0) {
                int_dat = 0;
            } else if (dbl_tmp < 4294967295.0) {
                while (dbl_tmp < 4294967295.0) {
                    scale_ex += 1;
                    dbl_tmp *= 10.0;
                }
                scale_ex -= 1;
                int_dat = (int)Math.round(dbl_tmp / 10.0);
            } else {
                dbl_tmp = hifilt[(int)(coef & 0xFFFFFFFFL)];
                throw new IllegalStateException("ERROR: putc_transform_table : hifilt[" + coef + "] to high at " + dbl_tmp + "");
            }

            if (log.isDebugEnabled()) {
                log.debug("hi[{}] = {}", coef, int_dat & 0xFFFFFFFFL);
                log.debug("hif[{}] = {}", coef, String.format("%.15f", hifilt[(int) coef]));
            }

            dataOutput.writeByte(sign & 0xFF);
            dataOutput.writeByte(scale_ex & 0xFF);
            dataOutput.writeInt((int)(int_dat & 0xFFFFFFFFL));
        }

        log.debug("Finished writing transform table.");
        log.debug("");
    }

    /**
     * Corresponds to putc_quantization_table() in imgtools/src/lib/wsq/tableio.c
     *
     * Stores quantization table in the output buffer.
     */
    private static void putc_quantization_table(final DataOutput dataOutput, final Token token)   /* quantization parameters  */
        throws IOException {
        int scale_ex, scale_ex2; /* exponent scaling parameters */
        int shrt_dat, shrt_dat2;  /* temp variables */
        float flt_tmp;            /* temp variable */

        log.debug("Writing quantization table.");

        dataOutput.writeShort(DQT_WSQ);

        /* table size */
        dataOutput.writeShort(389);

        /* exponent scaling value */
        dataOutput.writeByte(2);

        /* quantizer bin center parameter */
        dataOutput.writeShort(44);

        for (int sub = 0; sub < 64; sub ++) {
            if (sub >= 0 && sub < 60) {
                if (token.quant_vals.qbss[sub] != 0.0f) {
                    flt_tmp = token.quant_vals.qbss[sub];
                    scale_ex = 0;
                    if (flt_tmp < 65535) {
                        while (flt_tmp < 65535) {
                            scale_ex += 1;
                            flt_tmp *= 10;
                        }
                        scale_ex -= 1;
                        shrt_dat = (int)Math.round(flt_tmp / 10.0);
                    } else {
                        flt_tmp = token.quant_vals.qbss[sub];
                        throw new IllegalStateException(String.format("ERROR : putc_quantization_table : Q[%d] too high at %f", sub, flt_tmp));
                    }

                    flt_tmp = token.quant_vals.qzbs[sub];
                    scale_ex2 = 0;
                    if (flt_tmp < 65535) {
                        while (flt_tmp < 65535) {
                            scale_ex2 += 1;
                            flt_tmp *= 10;
                        }
                        scale_ex2 -= 1;
                        shrt_dat2 = (int)Math.round(flt_tmp / 10.0);
                    } else {
                        flt_tmp = token.quant_vals.qzbs[sub];
                        throw new IllegalArgumentException(String.format("ERROR : putc_quantization_table : Z[%d] too high at %f", sub, flt_tmp));
                    }
                } else {
                    scale_ex = 0;
                    scale_ex2 = 0;
                    shrt_dat = 0;
                    shrt_dat2 = 0;
                }
            } else {
                scale_ex = 0;
                scale_ex2 = 0;
                shrt_dat = 0;
                shrt_dat2 = 0;
            }

            log.debug(String.format("qi[%d] = %d", sub, shrt_dat));
            log.debug(String.format("\tzi[%d] = %d", sub, shrt_dat2));

            log.debug(String.format("q[%d] = %.7f", sub, token.quant_vals.qbss[sub]));
            log.debug(String.format("\tz[%d] = %.7f", sub, token.quant_vals.qzbs[sub]));

            dataOutput.writeByte(scale_ex & 0xFF);
            dataOutput.writeShort(shrt_dat & 0xFFFF);
            dataOutput.writeByte(scale_ex2 & 0xFF);
            dataOutput.writeShort(shrt_dat2 & 0xFFFF);
        }

        log.debug("Finished writing quantization table.");
        log.debug("");
    }

    /**
     * Corresponds to putc_block_header() in imgtools/src/lib/wsq/tableio.c
     *
     * Stores block header to the output buffer.
     *
     * @param dataOutput  token
     * @param table huffman table indicator
     */
    private static void putc_block_header(final DataOutput dataOutput, final int table) throws IOException {

        log.debug("Writing block header.");

        dataOutput.writeShort(SOB_WSQ);

        /* block header size */
        dataOutput.writeShort(3);

        dataOutput.writeByte(table & 0xFF);

        log.debug("Finished writing block header.");
        log.debug("");
    }

    /*
       Corresponds to putc_nistcom_wsq() in imgtools/src/lib/wsq/tableio.c
       */
    private static void putc_nistcom_wsq(final DataOutput dataOutput, final Bitmap bitmap, final float r_bitrate, final Map<String, String> metadata, final String[] comments) throws IOException {
        final Map<String, String> nistcom = new LinkedHashMap<String, String>();
        //These attributes will be filled later
        nistcom.put(NCM_HEADER     , "---");
        nistcom.put(NCM_PIX_WIDTH  , "---");
        nistcom.put(NCM_PIX_HEIGHT , "---");
        nistcom.put(NCM_PIX_DEPTH  , "---");
        nistcom.put(NCM_PPI        , "---");
        nistcom.put(NCM_LOSSY      , "---");
        nistcom.put(NCM_COLORSPACE , "---");
        nistcom.put(NCM_COMPRESSION, "---");
        nistcom.put(NCM_WSQ_RATE   , "---");

        if (metadata != null)
            nistcom.putAll(metadata);

        nistcom.put(NCM_HEADER     , Integer.toString(nistcom.size()));
        nistcom.put(NCM_PIX_WIDTH  , Integer.toString(bitmap.getWidth()));
        nistcom.put(NCM_PIX_HEIGHT , Integer.toString(bitmap.getHeight()));
        nistcom.put(NCM_PPI        , Integer.toString(bitmap.getPpi()));
        nistcom.put(NCM_PIX_DEPTH  , "8"); //WSQ has always 8 bpp
        nistcom.put(NCM_LOSSY      , "1"); //WSQ is always lossy
        nistcom.put(NCM_COLORSPACE , "GRAY");
        nistcom.put(NCM_COMPRESSION, "WSQ");
        nistcom.put(NCM_WSQ_RATE   , Float.toString(r_bitrate));

        putc_comment(dataOutput, COM_WSQ, fetToString(nistcom));
        if (comments != null)
            for (final String s : comments)
                if (s != null)
                    putc_comment(dataOutput, COM_WSQ, s);
    }

    /**
     * Puts comment field in output buffer.
     * MO, from tableio.c in ffpis_img
     *
     * @param dataOutput
     * @param marker
     * @param comment
     */
    private static void putc_comment(final DataOutput dataOutput, final int marker, final String comment) throws IOException {

        log.debug("Writing Comment Field to Buffer.");

        dataOutput.writeShort(marker);

        /* comment size */
        final int hdr_size = 2 + comment.length();
        dataOutput.writeShort(hdr_size & 0xFFFF);

        dataOutput.write(comment.getBytes()); // FIXME: should be UTF-8. Check FBI spec.

        log.debug("Finished Writing Comment Field to Buffer.");
    }

    /**
     * Corresponds to gen_hufftable_wsq() in imgtools/src/lib/wsq/encoder.c
     *
     * Generate a Huffman code table for a quantized data block.
     *
     * @param token
     * @param ohuffbits output, should contain one byte[] reference
     * @param ohuffvalues output, should contain one byte[] reference
     * @param sip
     * @param offset
     * @param block_sizes
     */
    private static WSQHelper.HuffCode[] gen_hufftable_wsq(final Token token, final Ref<int[]> ohuffbits, final Ref<int[]> ohuffvalues, final int[] sip, final int offset, final int[] block_sizes) {
        final int[] codesize;       /* code sizes to use */
        final AtomicInteger last_size = new AtomicInteger();       /* last huffvalue */
        final int[] huffbits;     /* huffbits values */
        final int[] huffvalues;   /* huffvalues */
        final int[] huffcounts;     /* counts for each huffman category */
        int[] huffcounts2;    /* counts for each huffman category */
        final WSQHelper.HuffCode[] hufftable1;  /* hufftables */
        final WSQHelper.HuffCode[] hufftable2;

        log.debug("block_sizes[0] = {}", block_sizes[0]);

        huffcounts = count_block(MAX_HUFFCOUNTS_WSQ, sip, offset, block_sizes[0], MAX_HUFFCOEFF, MAX_HUFFZRUN);

        log.debug("");
        log.debug("Iteration 0");

        for (int i = 0; i < huffcounts.length; ++i) {
            log.debug("huffcounts[{}] = {}", i, huffcounts[i]);
        }

        for (int i = 1; i < block_sizes.length; i++) {
            huffcounts2 = count_block(MAX_HUFFCOUNTS_WSQ, sip, offset + block_sizes[i - 1], block_sizes[i], MAX_HUFFCOEFF, MAX_HUFFZRUN);

            for (int j = 0; j < MAX_HUFFCOUNTS_WSQ; j++) {
                huffcounts[j] += huffcounts2[j];
            }

            log.debug("");
            log.debug("Iteration {}", i);

            for (int j = 0; j < huffcounts.length; ++j) {
                log.debug("huffcounts[{}] = {}", j, huffcounts[j]);
            }
        }

        // OUTPUT PAST THIS POINT IS NONCONFORMANT

        codesize = find_huff_sizes(huffcounts, MAX_HUFFCOUNTS_WSQ);

        /* tells if codesize is greater than MAX_HUFFBITS */
        final Ref<Boolean> adjust = new Ref<Boolean>();

        huffbits = find_num_huff_sizes(adjust, codesize, MAX_HUFFCOUNTS_WSQ);

        if (adjust.value) {
            sort_huffbits(huffbits);
        }

        huffvalues = sort_code_sizes(codesize, MAX_HUFFCOUNTS_WSQ);

        hufftable1 = build_huffsizes(last_size, huffbits, MAX_HUFFCOUNTS_WSQ);

        build_huffcodes(hufftable1);
        check_huffcodes_wsq(hufftable1, last_size.get());

        hufftable2 = build_huffcode_table(hufftable1, last_size.get(), huffvalues, MAX_HUFFCOUNTS_WSQ);

        ohuffbits.value = huffbits;
        ohuffvalues.value = huffvalues;
        return hufftable2;
    }

    /*****************************************************************/
    /* This routine counts the number of occurences of each category */
    /* in the huffman coding tables.                                 */
    /*****************************************************************/
    private static int[] count_block(
            // int **ocounts,     /* output count for each huffman catetory */
            final int max_huffcounts, /* maximum number of counts */
            final int[] sip,          /* quantized data */
            final int sip_offset, /* offset into sip */
            final int sip_siz,   /* size of block being compressed */
            final int MaxCoeff,  /* maximum values for coefficients */
            final int MaxZRun)   /* maximum zero runs */ {
        final int[] counts;         /* count for each huffman category */
        final int LoMaxCoeff;        /* lower (negative) MaxCoeff limit */
        int pix;             /* temp pixel pointer */
        int rcnt = 0, state;  /* zero run count and if current pixel
                                 is in a zero run or just a coefficient */
        int cnt;               /* pixel counter */

        if (MaxCoeff < 0 || MaxCoeff > 0xffff) {
            throw new IllegalStateException("ERROR : compress_block : MaxCoeff out of range.");
        }
        if (MaxZRun <0 || MaxZRun > 0xffff) {
            throw new IllegalStateException("ERROR : compress_block : MaxZRun out of range.");
        }
        /* Ininitalize vector of counts to 0. */
        counts = new int[max_huffcounts + 1];
        /* Set last count to 1. */
        counts[max_huffcounts] = 1;

        LoMaxCoeff = 1 - MaxCoeff;
        state = COEFF_CODE;
        for (cnt = sip_offset; cnt < sip_siz + sip_offset; cnt++) {
            pix = sip[cnt];
            switch(state) {

                case COEFF_CODE:   /* for runs of zeros */
                    if (pix == 0) {
                        state = RUN_CODE;
                        rcnt = 1;
                        break;
                    }
                    if (pix > MaxCoeff) {
                        if (pix > 255)
                            counts[103]++; /* 16bit pos esc */
                        else
                            counts[101]++; /* 8bit pos esc */
                    } else if (pix < LoMaxCoeff) {
                        if (pix < -255)
                            counts[104]++; /* 16bit neg esc */
                        else
                            counts[102]++; /* 8bit neg esc */
                    }
                    else
                        counts[pix+180]++; /* within table */
                    break;

                case RUN_CODE:  /* get length of zero run */
                    if (pix == 0  &&  rcnt < 0xFFFF) {
                        ++rcnt;
                        break;
                    }
                    /* limit rcnt to avoid EOF problem in bitio.c */
                    if (rcnt <= MaxZRun) {
                        counts[rcnt]++;  /** log zero run length **/
                    } else if (rcnt <= 0xFF) {
                        counts[105]++;
                    } else if (rcnt <= 0xFFFF) {
                        counts[106]++; /* 16bit zrun esc */
                    } else {
                        throw new IllegalStateException("ERROR: count_block : Zrun to long in count block.");
                    }

                    if (pix != 0) {
                        if (pix > MaxCoeff) { /** log current pix **/
                            if (pix > 255) {
                                counts[103]++; /* 16bit pos esc */
                            } else {
                                counts[101]++; /* 8bit pos esc */
                            }
                        } else if (pix < LoMaxCoeff) {
                            if (pix < -255) {
                                counts[104]++; /* 16bit neg esc */
                            } else {
                                counts[102]++; /* 8bit neg esc */
                            }
                        } else {
                            counts[pix+180]++; /* within table */
                        }
                        state = COEFF_CODE;
                    } else {
                        rcnt = 1;
                        state = RUN_CODE;
                    }
                    break;
            }
        }
        if (state == RUN_CODE) { /** log zero run length **/
            if (rcnt <= MaxZRun) {
                counts[rcnt]++;
            } else if (rcnt <= 0xFF) {
                counts[105]++;
            } else if (rcnt <= 0xFFFF) {
                counts[106]++; /* 16bit zrun esc */
            } else {
                throw new IllegalStateException("ERROR: count_block : Zrun to long in count block.");
            }
        }

        return counts;
            }

    /**
     * Routine to find number of codes of each size.
     * (From <code>huff.c</code>.)
     *
     * @param adjust should be boolean array of size 1
     *
     * @return
     */
    private static int[] find_num_huff_sizes(final Ref<Boolean> adjust, final int[] codesize, final int max_huffcounts) {
        adjust.value = false;

        /* Allocate 2X desired number of bits due to possible codesize. */
        final int[] bits = new int[2 * MAX_HUFFBITS];

        for (int i = 0; i < max_huffcounts; i++) {
            if (codesize[i] != 0) {
                bits[codesize[i] - 1]++;
            }
            if (codesize[i] > MAX_HUFFBITS) {
                adjust.value = true;
            }
        }

        for (int i = 0; i < MAX_HUFFBITS << 1; i++) {
            log.debug("bits[{}] = {}", i, bits[i]);
        }

        log.debug("ADJUST = {}", adjust.value ? 1 : 0);

        return bits;
    }

    /**
     * routine to sort the huffman code sizes
     */
    private static int[] sort_code_sizes(final int[] codesize, final int max_huffcounts) {

        /* defines order of huffman codelengths in relation to the code sizes */
        final int[] values = new int[max_huffcounts + 1];
        int i2 = 0;
        for (int i = 1; i <= (MAX_HUFFBITS << 1); i++) {
            for (int i3 = 0; i3 < max_huffcounts; i3++) {
                if (codesize[i3] == i) {
                    values[i2] = i3;
                    i2++;
                }
            }
        }

        for (int i = 0; i <= max_huffcounts; i++) {
            log.debug("values[{}] = {}", i, values[i]);
        }
        return values;
    }

    /**
     * This routine defines the huffman code sizes for each difference category
     */
    private static WSQHelper.HuffCode[] build_huffsizes(final AtomicInteger temp_size, final int[] huffbits, final int max_huffcounts) {
        /** the number codes for a given code size */
        int number_of_codes = 1;

        /** table of huffman codes and sizes */
        final WSQHelper.HuffCode[] huffcode_table = new WSQHelper.HuffCode[max_huffcounts + 1];
        for (int i = 0; i < huffcode_table.length; i++) { huffcode_table[i] = new WSQHelper.HuffCode(); }

        temp_size.set(0);

        for (int code_size = 1; code_size <= MAX_HUFFBITS; code_size++) {
            while (number_of_codes <= huffbits[code_size - 1]) {
                huffcode_table[temp_size.get()].size = code_size;
                temp_size.incrementAndGet();
                number_of_codes++;
            }
            number_of_codes = 1;
        }
        huffcode_table[temp_size.get()].size = 0;

        log.debug("In build_huffsizes:");
        for (int ii = 0; ii < max_huffcounts+1; ii++) {
            log.debug("hf_sz[{}] = {}", ii, huffcode_table[ii].size);
        }

        return huffcode_table;
    }

    /**
     * Routine to optimize code sizes by frequency of difference values.
     *
     * @param freq should be array of length 1
     * @param max_huffcounts TODO
     */
    private static int[] find_huff_sizes(final int[] freq, final int max_huffcounts) {
        int value1;
        /* smallest and next smallest frequency */
        int value2;          /* of difference occurrence in the largest difference category */

        /* codesizes for each category */
        final int [] codesize = new int[max_huffcounts+1];

        /* pointer used to generate codesizes */
        final int[] others = new int[max_huffcounts+1];

        for (int i = 0; i < freq.length; ++i) {
            log.debug("freq[{}] = {}", i, freq[i]);
        }

        for (int i = 0; i <= max_huffcounts; i++) {
            others[i] = -1;
        }

        while (true) {

            final int[] values = find_least_freq(freq, max_huffcounts);
            value1 = values[0];
            value2 = values[1];

            if (value2 == -1) {
                for (int i = 0; i <= max_huffcounts; i++) {
                    log.debug("codesize[{}] = {}", i, codesize[i]);
                }
                break;
            }

            freq[value1] += freq[value2];
            freq[value2] = 0;

            codesize[value1]++;
            while (others[value1] != -1) {
                value1 = others[value1];
                codesize[value1]++;
            }
            others[value1] = value2;
            codesize[value2]++;

            while (others[value2] != -1) {
                value2 = others[value2];
                codesize[value2]++;
            }
        }

        return codesize;
    }

    /**
     * Routine to find the largest difference with the least frequency value
     *
     * @return array with value1, value 2
     */
    /*
     * FIXME
     * what happens if first two freqs found are smallest but in wrong order?
     * FIXME
     */
    private static int[] find_least_freq(final int[] freq, final int max_huffcounts) {
        int code_temp;       /*store code*/
        int value_temp;      /*store size*/
        int code2 = Integer.MAX_VALUE;   /*next smallest frequency in largest diff category*/
        int code1 = Integer.MAX_VALUE;   /*smallest frequency in largest difference category*/
        int set = 1;         /*flag first two non-zero frequency values*/

        int value1 = -1;
        int value2 = -1;

        for (int i = 0; i <= max_huffcounts; i++) {
            if (freq[i] == 0) {
                continue;
            }
            if (set == 1) {
                code1 = freq[i];
                value1 = i;
                set++;
                continue;
            }
            if (set == 2) {
                code2 = freq[i];
                value2 = i;
                set++;
            }
            code_temp = freq[i];
            value_temp = i;
            if (code1 < code_temp && code2 < code_temp) {
                continue;
            }
            if ((code_temp < code1) || (code_temp == code1 && value_temp > value1)) {
                code2 = code1;
                value2 = value1;
                code1 = code_temp;
                value1 = value_temp;
                continue;
            }
            if ((code_temp < code2) || (code_temp == code2 && value_temp > value2)) {
                code2 = code_temp;
                value2 = value_temp;
            }
        }
        return new int[] { value1, value2 };
    }


    /******************************************************************/
    /* routine to insure that no huffman code size is greater than 16 */
    /******************************************************************/
    private static void sort_huffbits(final int[] bits) {
        int i, j;
        final int l1;
        final int l2;
        final int l3;

        l3 = MAX_HUFFBITS << 1;       /* 32 */
        l1 = l3 - 1;                /* 31 */
        l2 = MAX_HUFFBITS - 1;      /* 15 */

        final int[] tbits = new int[l3];

        for (i = 0; i < MAX_HUFFBITS<<1; i++) {
            tbits[i] = bits[i];
        }

        for (i = l1; i > l2; i--) {
            while (tbits[i] > 0) {
                j = i - 2;
                while (tbits[j] == 0) {
                    j--;
                }
                tbits[i] -= 2;
                tbits[i - 1] += 1;
                tbits[j + 1] += 2;
                tbits[j] -= 1;
            }
            tbits[i] = 0;
        }

        while (tbits[i] == 0) {
            i--;
        }

        tbits[i] -= 1;

        for (i = 0; i < MAX_HUFFBITS<<1; i++) {
            bits[i] = (byte)tbits[i];
        }

        for (i = MAX_HUFFBITS; i < l3; i++){
            if (bits[i] > 0){
                throw new IllegalStateException("ERROR : sort_huffbits : Code length of %d is greater than 16.");
            }
        }
    }

    /******************************************************************************/
    /* This routine defines the huffman codes needed for each difference category */
    /******************************************************************************/
    private static void build_huffcodes(final WSQHelper.HuffCode[] huffcode_table) {
        int pointer = 0;                     /*pointer to code word information*/
        int temp_code = 0;        /*used to construct code word*/

        int temp_size = huffcode_table[0].size;                    /*used to construct code size*/

        do {
            do {
                huffcode_table[pointer].code = temp_code;
                temp_code++;
                pointer++;
            } while (huffcode_table[pointer].size == temp_size);

            if (huffcode_table[pointer].size == 0) {
                return;
            }

            do {
                temp_code <<= 1;
                temp_size++;
            } while (huffcode_table[pointer].size != temp_size);
        } while (huffcode_table[pointer].size == temp_size);
    }

    private static void check_huffcodes_wsq(final WSQHelper.HuffCode[] hufftable, final int last_size) {
        boolean all_ones;

        for (int i = 0; i < last_size; i++){
            all_ones = true;
            for (int k = 0; (k < hufftable[i].size) && all_ones; k++) {
                all_ones = (all_ones && (((hufftable[i].code >> k) & 0x0001) != 0));
            }
            if (all_ones) {
                throw new IllegalStateException("WARNING: A code in the hufftable contains an "
                        + "all 1's code. This image may still be "
                        + "decodable. It is not compliant with "
                        + "the WSQ specification.");
            }
        }
    }

    /* routine to sort huffman codes and sizes */
    private static WSQHelper.HuffCode[] build_huffcode_table(final WSQHelper.HuffCode[] in_huffcode_table,
            final int last_size, final int[] values, final int max_huffcounts) {
        final WSQHelper.HuffCode[] new_huffcode_table = new WSQHelper.HuffCode[max_huffcounts + 1];
        for (int i = 0; i < new_huffcode_table.length; i++) { new_huffcode_table[i] = new WSQHelper.HuffCode(); }

        for (int size = 0; size < last_size; size++) {
            new_huffcode_table[values[size]].code = in_huffcode_table[size].code;
            new_huffcode_table[values[size]].size = in_huffcode_table[size].size;
        }

        for (int size = 0; size < new_huffcode_table.length; size++) {
            log.debug("huff_size[{}] = {}", size, new_huffcode_table[size].size);
            log.debug("huff_code[{}] = {}", size, new_huffcode_table[size].code);
        }

        return new_huffcode_table;
    }

    /* Routine "codes" the quantized image using the huffman tables. */
    private static void compress_block(final DataOutput dataOutput,
            final int[] sip,          /* quantized image */
            final int offset,
            final int length,
            final int MaxCoeff,  /* Maximum values for coefficients  */
            final int MaxZRun,   /* Maximum zero runs */
            final WSQHelper.HuffCode[] codes)     /* huffman code table  */
        throws IOException {
        final int LoMaxCoeff;        /* lower (negative) MaxCoeff limit */
        int pix;             /* temp pixel pointer */
        int rcnt = 0, state;  /* zero run count and if current pixel
                                 is in a zero run or just a coefficient */
        int cnt;               /* pixel counter */

        if (MaxCoeff <0 || MaxCoeff> 0xffff) {
            throw new IllegalStateException("ERROR : compress_block : MaxCoeff out of range.");
        }
        if (MaxZRun <0 || MaxZRun> 0xffff) {
            throw new IllegalStateException("ERROR : compress_block : MaxZRun out of range.");
        }
        LoMaxCoeff = 1 - MaxCoeff;

        final AtomicInteger outbit = new AtomicInteger(7);
        final AtomicInteger bytes = new AtomicInteger(0);
        final AtomicInteger bits = new AtomicInteger(0);

        state = COEFF_CODE;
        for (cnt = offset; cnt < length + offset; cnt++) {
            pix = sip[cnt];

            switch (state) {

                case COEFF_CODE:
                    if (pix == 0) {
                        state = RUN_CODE;
                        rcnt = 1;
                        break;
                    }
                    if (pix > MaxCoeff) {
                        if (pix > 255) {
                            /* 16bit pos esc */
                            write_bits(dataOutput, codes[103].size, codes[103].code , outbit, bits, bytes);
                            write_bits(dataOutput, 16, pix , outbit, bits, bytes);
                        } else {
                            /* 8bit pos esc */
                            write_bits(dataOutput, codes[101].size, codes[101].code , outbit, bits, bytes);
                            write_bits(dataOutput, 8, pix , outbit, bits, bytes);
                        }
                    } else if (pix < LoMaxCoeff) {
                        if (pix < -255) {
                            /* 16bit neg esc */
                            write_bits(dataOutput, codes[104].size, codes[104].code , outbit, bits, bytes);
                            write_bits(dataOutput, 16, -(pix ), outbit, bits, bytes);
                        } else {
                            /* 8bit neg esc */
                            write_bits(dataOutput, codes[102].size, codes[102].code , outbit, bits, bytes);
                            write_bits(dataOutput, 8, -(pix ), outbit, bits, bytes);
                        }
                    } else {
                        /* within table */
                        write_bits(dataOutput, codes[pix + 180].size, codes[pix + 180].code , outbit, bits, bytes);
                    }
                    break;

                case RUN_CODE:
                    if (pix == 0  &&  rcnt < 0xFFFF) {
                        ++rcnt;
                        break;
                    }
                    if (rcnt <= MaxZRun) {
                        /* log zero run length */
                        write_bits(dataOutput, codes[rcnt].size, codes[rcnt].code , outbit, bits, bytes);
                    } else if (rcnt <= 0xFF) {
                        /* 8bit zrun esc */
                        write_bits(dataOutput, codes[105].size, codes[105].code , outbit, bits, bytes);
                        write_bits(dataOutput, 8, rcnt , outbit, bits, bytes);
                    } else if (rcnt <= 0xFFFF) {
                        /* 16bit zrun esc */
                        write_bits(dataOutput, codes[106].size, codes[106].code , outbit, bits, bytes);
                        write_bits(dataOutput, 16, rcnt , outbit, bits, bytes);
                    } else {
                        throw new IllegalStateException("ERROR : compress_block : zrun too large.");
                    }

                    if (pix != 0) {
                        if (pix > MaxCoeff) {
                            /** log current pix **/
                            if (pix > 255) {
                                /* 16bit pos esc */
                                write_bits(dataOutput, codes[103].size, codes[103].code , outbit, bits, bytes);
                                write_bits(dataOutput, 16, pix, outbit, bits, bytes);
                            } else {
                                /* 8bit pos esc */
                                write_bits(dataOutput, codes[101].size, codes[101].code , outbit, bits, bytes);
                                write_bits(dataOutput, 8, pix , outbit, bits, bytes);
                            }
                        } else if (pix < LoMaxCoeff) {
                            if (pix < -255) {
                                /* 16bit neg esc */
                                write_bits(dataOutput, codes[104].size, codes[104].code , outbit, bits, bytes);
                                write_bits(dataOutput, 16, -pix, outbit, bits, bytes);
                            } else {
                                /* 8bit neg esc */
                                write_bits(dataOutput, codes[102].size, codes[102].code, outbit, bits, bytes);
                                write_bits(dataOutput, 8, -pix, outbit, bits, bytes);
                            }
                        } else {
                            /* within table */
                            write_bits(dataOutput, codes[pix + 180].size, codes[pix + 180].code , outbit, bits, bytes);
                        }
                        state = COEFF_CODE;
                    } else {
                        rcnt = 1;
                        state = RUN_CODE;
                    }
                    break;
            }
        }
        if (state == RUN_CODE) {
            if (rcnt <= MaxZRun) {
                write_bits(dataOutput, codes[rcnt].size, codes[rcnt].code , outbit, bits, bytes);
            } else if (rcnt <= 0xFF) {
                write_bits(dataOutput, codes[105].size, codes[105].code , outbit, bits, bytes);
                write_bits(dataOutput, 8, rcnt , outbit, bits, bytes);
            } else if (rcnt <= 0xFFFF) {
                write_bits(dataOutput, codes[106].size, codes[106].code , outbit, bits, bytes);
                write_bits(dataOutput, 16, rcnt , outbit, bits, bytes);
            } else {
                throw new IllegalStateException("ERROR : compress_block : zrun2 too large.");
            }
        }

        flush_bits(dataOutput, outbit, bits, bytes);
            }

    private static String fetToString(final Map<String, String> fet) {
        try {
            final StringBuilder result = new StringBuilder();
            final Set<Map.Entry<String, String>> entries = fet.entrySet();
            for (final Map.Entry<String, String> entry: entries) {
                if (entry.getKey()==null) continue;
                if (entry.getValue()==null) continue;
                final String key   = URLEncoder.encode(entry.getKey()  , "UTF-8");
                final String value = URLEncoder.encode(entry.getValue(), "UTF-8");
                result.append(key);
                result.append(" ");
                result.append(value);
                result.append("\n");
            }
            return result.toString();
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /* Routine to write "compressed" bits to output buffer. */
    private static void write_bits(
            final DataOutput outbuf,
            final int size,          /* numbers bits of code to write into buffer   */
            final int code, /* info to write into buffer                   */
            final AtomicInteger outbit,               /* current bit location in out buffer byte     */
            final AtomicInteger bits,       /* byte to write to output buffer              */
            final AtomicInteger bytes)                /* count of number bytes written to the buffer */
        throws IOException {
        int num;
        num = size;

        for (--num; num >= 0; num--) {
            int temp_value = bits.get();
            temp_value <<= 1;
            temp_value |= (((code >> num) & 0x0001)) & 0xFF;
            bits.set(temp_value);

            if ((outbit.get()-1) < 0) {
                outbit.decrementAndGet();
                outbuf.write(bits.get());
                if ((bits.get() & 0xFF) == 0xFF) {
                    outbuf.write(0);
                    bytes.incrementAndGet();
                }
                bytes.incrementAndGet();
                outbit.set(7);
                bits.set(0);
            }
            else {
                outbit.decrementAndGet();
            }

        }
    }

    /* Routine to "flush" left over bits in last */
    /* byte after compressing a block.           */
    private static void flush_bits(
            final DataOutput outbuf, /* output data buffer */
            final AtomicInteger outbit,            /* current bit location in out buffer byte */
            final AtomicInteger bits,    /* byte to write to output buffer */
            final AtomicInteger bytes)             /* count of number bytes written to the buffer */
        throws IOException {
        int cnt;              /* temp counter */

        if (outbit.get() != 7) {
            for (cnt = outbit.get(); cnt >= 0; cnt--) {
                int temp_value = bits.get();
                temp_value <<= 1;
                temp_value |= 0x01;
                bits.set(temp_value);
            }

            outbuf.write(bits.get());
            if (bits.get() == 0xFF) {
                bits.set(0);
                outbuf.write(0);
                bytes.incrementAndGet();
            }
            bytes.incrementAndGet();
            outbit.set(7);
            bits.set(0);
        }
    }
}
