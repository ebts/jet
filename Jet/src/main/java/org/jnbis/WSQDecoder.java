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
 * --
 * This code is based on JNBIS 1.0.3 which was licensed under Apache License 2.0.
 *
 * $Id: $
 */

package org.jnbis;

import org.jnbis.WSQHelper.Token;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;

/**
 * WSQDecoder based on NBIS and/or JNBIS.
 *
 * @author <a href="mailto:m.h.shams@gmail.com">M. H. Shamsi</a>
 * @version 1.0.0
 * @date Oct 1, 2007
 */
public class WSQDecoder implements WSQConstants, NISTConstants {

    public static BitmapWithMetadata decode(final InputStream is) throws IOException {
        if (is instanceof DataInput)
            return decode((DataInput)is);
        else
            return decode((DataInput)new DataInputStream(is));
    }

    public static BitmapWithMetadata decode(final DataInput dataInput) throws IOException {
        final Token token = new Token();

        /* Read the SOI marker. */
        getCMarkerWSQ(dataInput, SOI_WSQ);

        /* Read in supporting tables up to the SOF marker. */
        int marker = getCMarkerWSQ(dataInput, TBLS_N_SOF);
        while (marker != SOF_WSQ) {
            getCTableWSQ(dataInput, token, marker);
            marker = getCMarkerWSQ(dataInput, TBLS_N_SOF);
        }

        /* Read in the Frame Header. */

        final WSQHelper.HeaderFrm frmHeaderWSQ = getCFrameHeaderWSQ(dataInput);
        final int width = frmHeaderWSQ.width;
        final int height = frmHeaderWSQ.height;

        /* Build WSQ decomposition trees. */
        WSQHelper.buildWSQTrees(token, width, height);

        /* Decode the Huffman encoded buffer blocks. */
        final int[] qdata = huffmanDecodeDataMem(dataInput, token, width * height);

        /* Decode the quantize wavelet subband buffer. */
        final float[] fdata = unquantize(token, qdata, width, height);

        wsqReconstruct(token, fdata, width, height);

        /* Convert floating point pixels to unsigned char pixels. */
        final byte[] cdata = convertImageToByte(fdata, width, height, frmHeaderWSQ.mShift, frmHeaderWSQ.rScale);


        final Map<String,String> nistcom = new LinkedHashMap<String,String>();
        final List<String> comments = new ArrayList<String>();
        for (final String comment : token.comments) {
            try {
                nistcom.putAll(stringToFet(comment));
            } catch (final Exception e) {
                comments.add(comment);
            }
        }
        nistcom.remove(NCM_HEADER);
        nistcom.put(NCM_PIX_WIDTH , Integer.toString(width));
        nistcom.put(NCM_PIX_HEIGHT, Integer.toString(height));
        nistcom.put(NCM_PIX_DEPTH, "8");
        nistcom.put(NCM_LOSSY, "1");
        nistcom.put(NCM_COLORSPACE , "GRAY");
        nistcom.put(NCM_COMPRESSION, "WSQ");
        boolean ppiOk=false;
        try {
            if (Integer.parseInt(nistcom.get(NCM_PPI)) > 0)
                ppiOk = true;
        } catch (final Throwable t){}
        if (!ppiOk)
            nistcom.put(NCM_PPI, "-1");
        return new BitmapWithMetadata(cdata, width, height, Integer.parseInt(nistcom.get(NCM_PPI)), 8, 1, nistcom,
                                      comments.toArray(new String[comments.size()]));
    }

    private static int getCMarkerWSQ(final DataInput dataInput, final int type) throws IOException {
        final int marker = dataInput.readUnsignedShort();

        switch (type) {
            case SOI_WSQ:
                if (marker != SOI_WSQ) {
                    throw new RuntimeException("ERROR : getCMarkerWSQ : No SOI marker : " + marker);
                }

                return marker;

            case TBLS_N_SOF:
                if (marker != DTT_WSQ
                        && marker != DQT_WSQ
                        && marker != DHT_WSQ
                        && marker != SOF_WSQ
                        && marker != COM_WSQ) {
                    throw new RuntimeException("ERROR : getc_marker_wsq : No SOF, Table, or comment markers : " + marker);
                        }

                return marker;

            case TBLS_N_SOB:
                if (marker != DTT_WSQ
                        && marker != DQT_WSQ
                        && marker != DHT_WSQ
                        && marker != SOB_WSQ
                        && marker != COM_WSQ) {
                    throw new RuntimeException("ERROR : getc_marker_wsq : No SOB, Table, or comment markers : " +
                            marker);
                        }
                return marker;
            case ANY_WSQ:
                if ((marker & 0xff00) != 0xff00) {
                    throw new RuntimeException("ERROR : getc_marker_wsq : no marker found : " + marker);
                }

                /* Added by MDG on 03-07-05 */
                if ((marker < SOI_WSQ) || (marker > COM_WSQ)) {
                    throw new RuntimeException("ERROR : getc_marker_wsq : not a valid marker : " + marker);
                }

                return marker;
            default:
                throw new RuntimeException("ERROR : getc_marker_wsq : Invalid marker : " + marker);
        }
    }

    private static void getCTableWSQ(final DataInput DataInput, final Token token, final int marker) throws IOException {
        switch (marker) {
            case DTT_WSQ:
                getCTransformTable(DataInput, token);
                return;
            case DQT_WSQ:
                getCQuantizationTable(DataInput, token);
                return;
            case DHT_WSQ:
                getCHuffmanTableWSQ(DataInput, token);
                return;
            case COM_WSQ:
                token.comments.add(getCComment(DataInput, token));
                return;
            default:
                throw new RuntimeException("ERROR: getCTableWSQ : Invalid table defined : " + Integer.toHexString(marker));
        }
    }

    private static Map<String, String> stringToFet(final String comment) {
        try {
            if (!comment.startsWith(NCM_HEADER))
                throw new IllegalArgumentException("Not a NISTCOM header");

            final Scanner in = new Scanner(comment);
            final Map<String, String> result = new LinkedHashMap<String, String>();
            while (in.hasNextLine()) {
                final String line = in.nextLine();
                final int split = line.indexOf(" ");
                if (split < 0) {
                    System.err.println("Illegal NISTCOM header: Missing separator on line '" + line + "'");
                    continue;
                }
                final String key   = URLDecoder.decode(line.substring(0, split), "UTF-8");
                final String value = URLDecoder.decode(line.substring(split+1 ), "UTF-8");
                result.put(key, value);

            }
            return result;
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getCComment(final DataInput dataInput, final Token token) throws IOException {
        final int size = dataInput.readUnsignedShort() - 2;
        final byte[] bytes = new byte[size];
        dataInput.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private static void getCTransformTable(final DataInput dataInput, final Token token) throws IOException {
        // read header Size;
        dataInput.readUnsignedShort();

        token.tableDTT.hisz = dataInput.readUnsignedByte();
        token.tableDTT.losz = dataInput.readUnsignedByte();

        token.tableDTT.hifilt = new float[token.tableDTT.hisz];
        token.tableDTT.lofilt = new float[token.tableDTT.losz];

        int aSize;
        if (token.tableDTT.hisz % 2 != 0) {
            aSize = (token.tableDTT.hisz + 1) / 2;
        } else {
            aSize = token.tableDTT.hisz / 2;
        }

        final float[] aLofilt = new float[aSize];

        aSize--;
        for (int cnt = 0; cnt <= aSize; cnt++) {
            final int sign = dataInput.readUnsignedByte();
            int scale = dataInput.readUnsignedByte();
            final long shrtDat = dataInput.readInt() & 0xFFFFFFFFL;

            aLofilt[cnt] = (float) shrtDat;

            while (scale > 0) {
                aLofilt[cnt] /= 10.0;
                scale--;
            }

            if (sign != 0) {
                aLofilt[cnt] *= -1.0;
            }

            if (token.tableDTT.hisz % 2 != 0) {
                token.tableDTT.hifilt[cnt + aSize] = intSign(cnt) * aLofilt[cnt];
                if (cnt > 0) {
                    token.tableDTT.hifilt[aSize - cnt] = token.tableDTT.hifilt[cnt + aSize];
                }
            } else {
                token.tableDTT.hifilt[cnt + aSize + 1] = intSign(cnt) * aLofilt[cnt];
                token.tableDTT.hifilt[aSize - cnt] = -1 * token.tableDTT.hifilt[cnt + aSize + 1];
            }
        }

        if (token.tableDTT.losz % 2 != 0) {
            aSize = (token.tableDTT.losz + 1) / 2;
        } else {
            aSize = token.tableDTT.losz / 2;
        }

        final float[] aHifilt = new float[aSize];

        aSize--;
        for (int cnt = 0; cnt <= aSize; cnt++) {
            final int sign = dataInput.readUnsignedByte();
            int scale = dataInput.readUnsignedByte();
            final long shrtDat = dataInput.readInt() & 0xFFFFFFFFL;

            aHifilt[cnt] = (float) shrtDat;

            while (scale > 0) {
                aHifilt[cnt] /= 10.0;
                scale--;
            }

            if (sign != 0) {
                aHifilt[cnt] *= -1.0;
            }

            if (token.tableDTT.losz % 2 != 0) {
                token.tableDTT.lofilt[cnt + aSize] = intSign(cnt) * aHifilt[cnt];
                if (cnt > 0) {
                    token.tableDTT.lofilt[aSize - cnt] = token.tableDTT.lofilt[cnt + aSize];
                }
            } else {
                token.tableDTT.lofilt[cnt + aSize + 1] = intSign(cnt + 1) * aHifilt[cnt];
                token.tableDTT.lofilt[aSize - cnt] = token.tableDTT.lofilt[cnt + aSize + 1];
            }
        }

        token.tableDTT.lodef = 1;
        token.tableDTT.hidef = 1;
    }

    public static void getCQuantizationTable(final DataInput dataInput, final Token token) throws IOException {
        dataInput.readUnsignedShort(); /* header size */
        int scale = dataInput.readUnsignedByte(); /* scaling parameter */
        int shrtDat = dataInput.readUnsignedShort(); /* counter and temp short buffer */

        token.tableDQT.binCenter = (float) shrtDat;
        while (scale > 0) {
            token.tableDQT.binCenter /= 10.0;
            scale--;
        }

        for (int cnt = 0; cnt < WSQHelper.Table_DQT.MAX_SUBBANDS; cnt++) {
            scale = dataInput.readUnsignedByte();
            shrtDat = dataInput.readUnsignedShort();
            token.tableDQT.qBin[cnt] = (float) shrtDat;
            while (scale > 0) {
                token.tableDQT.qBin[cnt] /= 10.0;
                scale--;
            }

            scale = dataInput.readUnsignedByte();
            shrtDat = dataInput.readUnsignedShort();
            token.tableDQT.zBin[cnt] = (float) shrtDat;
            while (scale > 0) {
                token.tableDQT.zBin[cnt] /= 10.0;
                scale--;
            }
        }

        token.tableDQT.dqtDef = 1;
    }

    public static void getCHuffmanTableWSQ(final DataInput DataInput, final Token token) throws IOException {
        /* First time, read table len. */
        final WSQHelper.HuffmanTable firstHuffmanTable = getCHuffmanTable(DataInput, token, MAX_HUFFCOUNTS_WSQ, 0, true);

        /* Store table into global structure list. */
        int tableId = firstHuffmanTable.tableId;
        token.tableDHT[tableId].huffbits = firstHuffmanTable.huffbits.clone();
        token.tableDHT[tableId].huffvalues = firstHuffmanTable.huffvalues.clone();
        token.tableDHT[tableId].tabdef = 1;

        int bytesLeft = firstHuffmanTable.bytesLeft;
        while (bytesLeft != 0) {
            /* Read next table without rading table len. */
            final WSQHelper.HuffmanTable huffmantable = getCHuffmanTable(DataInput, token, MAX_HUFFCOUNTS_WSQ, bytesLeft, false);

            /* If table is already defined ... */
            tableId = huffmantable.tableId;
            if (token.tableDHT[tableId].tabdef != 0) {
                throw new RuntimeException("ERROR : getCHuffmanTableWSQ : huffman table already defined.");
            }

            /* Store table into global structure list. */
            token.tableDHT[tableId].huffbits = huffmantable.huffbits.clone();
            token.tableDHT[tableId].huffvalues = huffmantable.huffvalues.clone();
            token.tableDHT[tableId].tabdef = 1;
            bytesLeft = huffmantable.bytesLeft;
        }
    }

    private static WSQHelper.HuffmanTable getCHuffmanTable(final DataInput dataInput, final Token token, final int maxHuffcounts, int bytesLeft, final boolean readTableLen) throws IOException {
        final WSQHelper.HuffmanTable huffmanTable = new WSQHelper.HuffmanTable();

        /* table_len */
        if (readTableLen) {
            huffmanTable.tableLen = dataInput.readUnsignedShort();
            huffmanTable.bytesLeft = huffmanTable.tableLen - 2;
            bytesLeft = huffmanTable.bytesLeft;
        } else {
            huffmanTable.bytesLeft = bytesLeft;
        }

        /* If no bytes left ... */
        if (bytesLeft <= 0) {
            throw new RuntimeException("ERROR : getCHuffmanTable : no huffman table bytes remaining");
        }

        /* Table ID */
        huffmanTable.tableId = dataInput.readUnsignedByte();
        huffmanTable.bytesLeft--;


        huffmanTable.huffbits = new int[MAX_HUFFBITS];
        int numHufvals = 0;
        /* L1 ... L16 */
        for (int i = 0; i < MAX_HUFFBITS; i++) {
            huffmanTable.huffbits[i] = dataInput.readUnsignedByte();
            numHufvals += huffmanTable.huffbits[i];
        }
        huffmanTable.bytesLeft -= MAX_HUFFBITS;

        if (numHufvals > maxHuffcounts + 1) {
            throw new RuntimeException("ERROR : getCHuffmanTable : numHufvals is larger than MAX_HUFFCOUNTS");
        }

        /* Could allocate only the amount needed ... then we wouldn't */
        /* need to pass MAX_HUFFCOUNTS. */
        huffmanTable.huffvalues = new int[maxHuffcounts + 1];

        /* V1,1 ... V16,16 */
        for (int i = 0; i < numHufvals; i++) {
            huffmanTable.huffvalues[i] = dataInput.readUnsignedByte();
        }
        huffmanTable.bytesLeft -= numHufvals;

        return huffmanTable;
    }

    private static WSQHelper.HeaderFrm getCFrameHeaderWSQ(final DataInput dataInput) throws IOException {
        final WSQHelper.HeaderFrm headerFrm = new WSQHelper.HeaderFrm();

        /* int hdrSize = */ dataInput.readUnsignedShort(); /* header size */

        headerFrm.black = dataInput.readUnsignedByte();
        headerFrm.white = dataInput.readUnsignedByte();
        headerFrm.height = dataInput.readUnsignedShort();
        headerFrm.width = dataInput.readUnsignedShort();
        int scale = dataInput.readUnsignedByte(); /* exponent scaling parameter */
        int shrtDat = dataInput.readUnsignedShort(); /* buffer pointer */
        headerFrm.mShift = (float) shrtDat;
        while (scale > 0) {
            headerFrm.mShift /= 10.0;
            scale--;
        }

        scale = dataInput.readUnsignedByte();
        shrtDat = dataInput.readUnsignedShort();
        headerFrm.rScale = (float) shrtDat;
        while (scale > 0) {
            headerFrm.rScale /= 10.0;
            scale--;
        }

        headerFrm.wsqEncoder = dataInput.readUnsignedByte();
        headerFrm.software = dataInput.readUnsignedShort();

        return headerFrm;
    }

    private static int[] huffmanDecodeDataMem(final DataInput DataInput, final Token token, final int size) throws IOException {
        final int[] qdata = new int[size];
        final int[] maxcode = new int[MAX_HUFFBITS_PLUS_ONE];
        final int[] mincode = new int[MAX_HUFFBITS_PLUS_ONE];
        final int[] valptr = new int[MAX_HUFFBITS_PLUS_ONE];

        final WSQHelper.Ref<Integer> marker = new WSQHelper.Ref<Integer>(getCMarkerWSQ(DataInput, TBLS_N_SOB));

        final WSQHelper.Ref<Integer> bitCount = new WSQHelper.Ref<Integer>(0); /* bit count for getc_nextbits_wsq routine */
        final WSQHelper.Ref<Integer> nextByte = new WSQHelper.Ref<Integer>(0); /*next byte of buffer*/
        int hufftableId = 0; /* huffman table number */
        int ip = 0;

        boolean isPrematureEOF = false;
        while (!isPrematureEOF && (marker.value != EOI_WSQ)) {

            if (marker.value != 0) {
                while (marker.value != SOB_WSQ) {
                    getCTableWSQ(DataInput, token, marker.value);
                    marker.value = getCMarkerWSQ(DataInput, TBLS_N_SOB);
                }
                hufftableId = getCBlockHeader(DataInput); /* huffman table number */

                if (token.tableDHT[hufftableId].tabdef != 1) {
                    throw new RuntimeException("ERROR : huffmanDecodeDataMem : huffman table undefined.");
                }

                /* the next two routines reconstruct the huffman tables */
                final WSQHelper.HuffCode[] hufftable = buildHuffsizes(token.tableDHT[hufftableId].huffbits, MAX_HUFFCOUNTS_WSQ);
                buildHuffcodes(hufftable);

                /* this routine builds a set of three tables used in decoding */
                /* the compressed buffer*/
                genDecodeTable(hufftable, maxcode, mincode, valptr, token.tableDHT[hufftableId].huffbits);

                bitCount.value = 0;
                marker.value = 0;
            }

            try {
                /* get next huffman category code from compressed input buffer stream */
                final int nodeptr = decodeDataMem(DataInput, mincode, maxcode, valptr, token.tableDHT[hufftableId].huffvalues, bitCount, marker, nextByte);
                /* nodeptr  pointers for decoding */

                if (nodeptr == -1) {
                    continue;
                }

                if (nodeptr > 0 && nodeptr <= 100) {
                    for (int n = 0; n < nodeptr; n++) {
                        qdata[ip++] = 0; /* z run */
                    }
                } else if (nodeptr > 106 && nodeptr < 0xff) {
                    qdata[ip++] = nodeptr - 180;
                } else if (nodeptr == 101) {
                    qdata[ip++] = getCNextbitsWSQ(DataInput,  marker, bitCount, 8, nextByte);
                } else if (nodeptr == 102) {
                    qdata[ip++] = -getCNextbitsWSQ(DataInput, marker, bitCount, 8, nextByte);
                } else if (nodeptr == 103) {
                    qdata[ip++] = getCNextbitsWSQ(DataInput, marker, bitCount, 16, nextByte);
                } else if (nodeptr == 104) {
                    qdata[ip++] = -getCNextbitsWSQ(DataInput, marker, bitCount, 16, nextByte);
                } else if (nodeptr == 105) {
                    int n = getCNextbitsWSQ(DataInput, marker, bitCount, 8, nextByte);
                    while (n-- > 0) {
                        qdata[ip++] = 0;
                    }
                } else if (nodeptr == 106) {
                    int n = getCNextbitsWSQ(DataInput, marker, bitCount, 16, nextByte);
                    while (n-- > 0) {
                        qdata[ip++] = 0;
                    }
                } else {
                    throw new RuntimeException("ERROR: huffman_decode_data_mem : Invalid code (" + nodeptr + ")");
                }
            } catch (final EOFException eof) {
                System.out.println("DEBUG: MO - ignoring EOF in WSQDecoder");
                isPrematureEOF = true;
            }
        }

        return qdata;
    }

    private static int getCBlockHeader(final DataInput dataInput) throws IOException {
        dataInput.readUnsignedShort(); /* block header size */
        return dataInput.readUnsignedByte();
    }

    private static WSQHelper.HuffCode[] buildHuffsizes(final int[] huffbits, final int maxHuffcounts) {
        final WSQHelper.HuffCode[] huffcodeTable;    /*table of huffman codes and sizes*/
        int numberOfCodes = 1;     /*the number codes for a given code size*/

        huffcodeTable = new WSQHelper.HuffCode[maxHuffcounts + 1];

        int tempSize = 0;
        for (int codeSize = 1; codeSize <= MAX_HUFFBITS; codeSize++) {
            while (numberOfCodes <= huffbits[codeSize - 1]) {
                huffcodeTable[tempSize] = new WSQHelper.HuffCode();
                huffcodeTable[tempSize].size = codeSize;
                tempSize++;
                numberOfCodes++;
            }
            numberOfCodes = 1;
        }

        huffcodeTable[tempSize] = new WSQHelper.HuffCode();
        huffcodeTable[tempSize].size = 0;

        return huffcodeTable;
    }

    private static void buildHuffcodes(final WSQHelper.HuffCode[] huffcodeTable) {
        short tempCode = 0;  /*used to construct code word*/
        int pointer = 0;     /*pointer to code word information*/

        int tempSize = huffcodeTable[0].size;
        if (huffcodeTable[pointer].size == 0) {
            return;
        }

        do {
            do {
                huffcodeTable[pointer].code = tempCode;
                tempCode++;
                pointer++;
            } while (huffcodeTable[pointer].size == tempSize);

            if (huffcodeTable[pointer].size == 0)
                return;

            do {
                tempCode <<= 1;
                tempSize++;
            } while (huffcodeTable[pointer].size != tempSize);
        } while (huffcodeTable[pointer].size == tempSize);
    }

    private static void genDecodeTable(final WSQHelper.HuffCode[] huffcodeTable, final int[] maxcode, final int[] mincode, final int[] valptr, final int[] huffbits) {
        for (int i = 0; i <= MAX_HUFFBITS; i++) {
            maxcode[i] = 0;
            mincode[i] = 0;
            valptr[i] = 0;
        }

        int i2 = 0;
        for (int i = 1; i <= MAX_HUFFBITS; i++) {
            if (huffbits[i - 1] == 0) {
                maxcode[i] = -1;
                continue;
            }
            valptr[i] = i2;
            mincode[i] = huffcodeTable[i2].code;
            i2 = i2 + huffbits[i - 1] - 1;
            maxcode[i] = huffcodeTable[i2].code;
            i2++;
        }
    }

    private static int decodeDataMem(final DataInput DataInput, final int[] mincode, final int[] maxcode, final int[] valptr, final int[] huffvalues, final WSQHelper.Ref<Integer> bitCount, final WSQHelper.Ref<Integer> marker, final WSQHelper.Ref<Integer> nextByte) throws IOException {

        short code = (short) getCNextbitsWSQ(DataInput, marker, bitCount, 1, nextByte);   /* becomes a huffman code word  (one bit at a time) */
        if (marker.value != 0) {
            return -1;
        }

        int inx;
        for (inx = 1; code > maxcode[inx]; inx++) {
            final int tbits = getCNextbitsWSQ(DataInput, marker, bitCount, 1, nextByte);  /* becomes a huffman code word  (one bit at a time)*/
            code = (short) ((code << 1) + tbits);

            if (marker.value != 0) {
                return -1;
            }
        }

        final int inx2 = valptr[inx] + code - mincode[inx];  /*increment variables*/
        return huffvalues[inx2];
    }

    private static int getCNextbitsWSQ(final DataInput dataInput, final WSQHelper.Ref<Integer> marker, final WSQHelper.Ref<Integer> bitCount, final int bitsReq, final WSQHelper.Ref<Integer> nextByte) throws IOException {
        if (bitCount.value == 0) {
            nextByte.value = dataInput.readUnsignedByte();

            bitCount.value = 8;
            if (nextByte.value == 0xFF) {
                final int code2 = dataInput.readUnsignedByte();  /*stuffed byte of buffer*/

                if (code2 != 0x00 && bitsReq == 1) {
                    marker.value = (nextByte.value << 8) | code2;
                    return 1;
                }
                if (code2 != 0x00) {
                    throw new RuntimeException("ERROR: getCNextbitsWSQ : No stuffed zeros.");
                }
            }
        }

        int bits;  /*bits of current buffer byte requested*/
        final int tbits;
        final int bitsNeeded; /*additional bits required to finish request*/

        if (bitsReq <= bitCount.value) {
            bits = (nextByte.value >> (bitCount.value - bitsReq)) & (BITMASK[bitsReq]);
            bitCount.value -= bitsReq;
            nextByte.value &= BITMASK[bitCount.value];
        } else {
            bitsNeeded = bitsReq - bitCount.value; /*additional bits required to finish request*/
            bits = nextByte.value << bitsNeeded;
            bitCount.value = 0;
            tbits = getCNextbitsWSQ(dataInput, marker, bitCount, bitsNeeded, nextByte);
            bits |= tbits;
        }

        return bits;
    }

    private static float[] unquantize(final Token token, final int[] sip, final int width, final int height) {
        final float[] fip = new float[width * height];  /* floating point image */

        if (token.tableDQT.dqtDef != 1) {
            throw new RuntimeException("ERROR: unquantize : quantization table parameters not defined!");
        }

        final float binCenter = token.tableDQT.binCenter; /* quantizer bin center */

        int sptr = 0;
        for (int cnt = 0; cnt < NUM_SUBBANDS; cnt++) {
            if (token.tableDQT.qBin[cnt] == 0.0) {
                continue;
            }

            int fptr = (token.qtree[cnt].y * width) + token.qtree[cnt].x;

            for (int row = 0; row < token.qtree[cnt].leny; row++, fptr += width - token.qtree[cnt].lenx) {
                for (int col = 0; col < token.qtree[cnt].lenx; col++) {
                    if (sip[sptr] == 0) {
                        fip[fptr] = 0.0f;
                    } else if (sip[sptr] > 0) {
                        fip[fptr] = (token.tableDQT.qBin[cnt] * (sip[sptr] - binCenter)) + (token.tableDQT.zBin[cnt] / 2.0f);
                    } else if (sip[sptr] < 0) {
                        fip[fptr] = (token.tableDQT.qBin[cnt] * (sip[sptr] + binCenter)) - (token.tableDQT.zBin[cnt] / 2.0f);
                    } else {
                        throw new RuntimeException("ERROR : unquantize : invalid quantization pixel value");
                    }
                    fptr++;
                    sptr++;
                }
            }
        }

        return fip;
    }

    private static void wsqReconstruct(final Token token, final float[] fdata, final int width, final int height) {
        if (token.tableDTT.lodef != 1) {
            throw new RuntimeException("ERROR: wsq_reconstruct : Lopass filter coefficients not defined");
        }

        if (token.tableDTT.hidef != 1) {
            throw new RuntimeException("ERROR: wsq_reconstruct : Hipass filter coefficients not defined");
        }

        final int numPix = width * height;
        /* Allocate temporary floating point pixmap. */
        final float[] fdataTemp = new float[numPix];

        /* Reconstruct floating point pixmap from wavelet subband buffer. */
        for (int node = W_TREELEN - 1; node >= 0; node--) {
            final int fdataBse = (token.wtree[node].y * width) + token.wtree[node].x;
            //log.debug("{} {} {} {}",new Object[] {token.wtree[node].lenx, token.wtree[node].leny,token.wtree[node].invcl,token.wtree[node].invrw});
            joinLets(fdataTemp, fdata, 0, fdataBse, token.wtree[node].lenx, token.wtree[node].leny,
                    1, width,
                    token.tableDTT.hifilt, token.tableDTT.hisz,
                    token.tableDTT.lofilt, token.tableDTT.losz,
                    token.wtree[node].invcl);
            joinLets(fdata, fdataTemp, fdataBse, 0, token.wtree[node].leny, token.wtree[node].lenx,
                    width, 1,
                    token.tableDTT.hifilt, token.tableDTT.hisz,
                    token.tableDTT.lofilt, token.tableDTT.losz,
                    token.wtree[node].invrw);
        }
    }

    private static  void joinLets(
            final float[] newdata,
            final float[] olddata,
            final int newIndex,
            final int oldIndex,
            final int len1,       /* temporary length parameters */
            final int len2,
            final int pitch,      /* pitch gives next row_col to filter */
            final int stride,    /*           stride gives next pixel to filter */
            final float[] hi,
            final int hsz,   /* NEW */
            final float[] lo,      /* filter coefficients */
            final int lsz,   /* NEW */
            final int inv)        /* spectral inversion? */ {

        int cl_rw;      /* column/row counter */
        int i;         /* if "scanline" is even or odd and */
        final int da_ev;
        int loc, hoc;
        final int hlen;
        final int llen;
        final int nstr;
        final int pstr;
        final int fi_ev;
        int olle;
        int ohle;
        final int olre;
        final int ohre;
        final int lotap;
        final int hotap;
        final int asym;
        int fhre = 0;
        final int ofhre;
        final float ssfac;


        da_ev = len2 % 2;
        fi_ev = lsz % 2;
        pstr = stride;
        nstr = -pstr;
        if (da_ev != 0) {
            llen = (len2 + 1) / 2;
            hlen = llen - 1;
        } else {
            llen = len2 / 2;
            hlen = llen;
        }

        if (fi_ev == 0) {

            asym = 1;
            ssfac = -1.0f;
            ofhre = 2;
            loc = lsz / 4 - 1;
            hoc = hsz / 4 - 1;
            lotap = (lsz / 2) % 2;
            hotap = (hsz / 2) % 2;
            if (da_ev != 0) {
                olre = 0;
            } else {
                olre = 1;

            }
            olle = 1;
            ohle = 1;
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
        } else {
            asym = 0;
            ssfac = 1.0f;
            ofhre = 0;
            loc = (lsz - 1) / 4;
            hoc = (hsz + 1) / 4 - 1;
            lotap = ((lsz - 1) / 2) % 2;
            hotap = ((hsz + 1) / 2) % 2;
            if (da_ev != 0) {
                olre = 0;
                ohre = 1;
            } else {
                olre = 1;
                ohre = 0;
            }
            olle = 0;
            ohle = 1;
        }

        for (cl_rw = 0; cl_rw < len1; cl_rw++) {
            fhre = loop1(newdata, olddata, newIndex, oldIndex, pitch, stride, hi, hsz, lo, lsz, inv, cl_rw, da_ev, loc, hoc, hlen, llen, nstr, pstr, olle, ohle, olre, ohre, lotap, hotap, asym, fhre, ofhre, ssfac);
        }

        if (fi_ev == 0) {
            for (i = 0; i < hsz; i++) {
                hi[i] *= -1.0;
            }
        }
    }

    private static int loop1(
            final float[] newdata, final float[] olddata, final int newIndex, final int oldIndex, final int pitch, final int stride, final float[] hi, final int hsz, final float[] lo, final int lsz, final int inv, final int cl_rw, final int da_ev, final int loc, final int hoc, final int hlen, final int llen, final int nstr, final int pstr, final int olle, final int ohle, final int olre, final int ohre, final int lotap, final int hotap, final int asym, int fhre, final int ofhre, final float ssfac) {

        final int lp0;
        final int lp1;
        final int hp0;
        final int hp1;
        final int lopass;   /* lo/hi pass image pointers */
        final int hipass;
        int limg, himg;
        int pix;         /* pixel counter */

        int lspx;
        int lspxstr;
        int lstap;
        int lle2;
        int lre2;
        int hspx;
        int hspxstr;
        int hstap;
        int hle2;
        int hre2;
        float osfac;
        int hle;
        int hre;
        int hpx;
        int hpxstr;
        float sfac;
        limg = newIndex + cl_rw * pitch;
        himg = limg;
        newdata[himg] = 0.0f;
        newdata[himg + stride] = 0.0f;
        if (inv != 0) {
            hipass = oldIndex + cl_rw * pitch;
            lopass = hipass + stride * hlen;
        } else {
            lopass = oldIndex + cl_rw * pitch;
            hipass = lopass + stride * llen;
        }

        lp0 = lopass;
        lp1 = lp0 + (llen - 1) * stride;
        lspx = lp0 + (loc * stride);
        lspxstr = nstr;
        lstap = lotap;
        lle2 = olle;
        lre2 = olre;

        hp0 = hipass;
        hp1 = hp0 + (hlen - 1) * stride;
        hspx = hp0 + (hoc * stride);
        hspxstr = nstr;
        hstap = hotap;
        hle2 = ohle;
        hre2 = ohre;
        osfac = ssfac;

        final int lstap_def;
        if (da_ev == 0 && lotap != 0) {
            lstap_def = 2;
        } else if (da_ev != 0) {
            if (lotap != 0) {
                lstap_def = 1;
            } else {
                lstap_def = 0;
            }
        } else {
            lstap_def = 1;
        }

        final int hstap_def;
        if (da_ev != 0) {

            if (hotap != 0) {
                hstap_def = 1;
            } else {
                hstap_def = 0;
            }

        } else if (hotap != 0) {
            hstap_def = 2;
        } else {
            hstap_def = 1;
        }

        for (pix = 0; pix < hlen; pix++) {

            for (int tap = lstap; tap >= 0; tap--) {
                limg = loop2(newdata, olddata, stride, lo, lsz, nstr, pstr, lp0, lp1, limg, lspx, lspxstr, lle2, lre2, tap);
            }


            if (lspx == lp0) {
                if (lle2 != 0) {
                    lspxstr = 0;
                    lle2 = 0;
                } else {
                    lspxstr = pstr;
                    lspx += lspxstr;
                }
            } else {
                lspx += lspxstr;
            }

            lstap = 1;

            for (int tap = hstap; tap >= 0; tap--) {
                hle = hle2;
                hre = hre2;
                hpx = hspx;
                hpxstr = hspxstr;
                fhre = ofhre;
                sfac = osfac;

                for (int i = tap; i < hsz; i += 2) {
                    if (hpx == hp0) {
                        if (hle != 0) {
                            hpxstr = 0;
                            hle = 0;
                        } else {
                            hpxstr = pstr;
                            sfac = 1.0f;
                        }
                    } else if (hpx == hp1) {
                        if (hre != 0) {
                            hpxstr = 0;
                            hre = 0;
                            if (asym != 0 && da_ev != 0) {
                                hre = 1;
                                fhre--;
                                sfac = (float) fhre;
                                if (fhre == 0) {
                                    hre = 0;
                                }
                            }
                        } else {
                            hpxstr = nstr;
                            if (asym != 0)
                                sfac = -1.0f;
                        }
                    }
                    newdata[himg] += olddata[hpx] * hi[i] * sfac;
                    hpx += hpxstr;
                }
                himg += stride;
            }

            if (hspx == hp0) {
                if (hle2 != 0) {
                    hspxstr = 0;
                    hle2 = 0;
                } else {
                    hspxstr = pstr;
                    osfac = 1.0f;
                }
            }
            hspx += hspxstr;
            hstap = 1;
        }

        lstap = lstap_def;

        //..... what on earth is this?! Why are we returning non-conditionally in the middle of a for loop?
//        for (int tap = 1; tap >= lstap; tap--) {
//            newdata[limg] = olddata[lspx] * lo[tap];
//            for (int i = tap + 2; i < lsz; i += 2) {
//                if (lspx != lp0 && lspx != lp1) {
//                    lspx += lspxstr;
//                } else if (lspx == lp0) {
//                    if (lle2 != 0) {
//                        lspxstr = 0;
//                        lle2 = 0;
//                    } else {
//                        lspxstr = pstr;
//                        lspx += pstr;
//                    }
//                } else {
//                    //(lpx == lp1)
//                    if (lre2 != 0) {
//                        lspxstr = 0;
//                        lre2 = 0;
//                    } else {
//                        lspxstr = nstr;
//                        lspx += nstr;
//                    }
//                }
//                newdata[limg] += olddata[lspx] * lo[i];
//            }
//            return limg += stride;
//        }

        // This is what the above actually does. If it's not supposed to, I'll come back and fix it.
        if (lstap <= 1) {
            newdata[limg] = olddata[lspx] * lo[1];
            for (int i = 3; i < lsz; i += 2) {
                if (lspx != lp0 && lspx != lp1) {
                    lspx += lspxstr;
                } else if (lspx == lp0) {
                    if (lle2 != 0) {
                        lspxstr = 0;
                        lle2 = 0;
                    } else {
                        lspxstr = pstr;
                        lspx += pstr;
                    }
                } else {
                    if (lre2 != 0) {
                        lspxstr = 0;
                        lre2 = 0;
                    } else {
                        lspxstr = nstr;
                        lspx += nstr;
                    }
                }
                newdata[limg] += olddata[lspx] * lo[i];
            }

            return limg + stride;
        }

        if (da_ev != 0 && hsz == 2) {
            hspx -= hspxstr;
            fhre = 1;
        }
        hstap = hstap_def;

        for (int tap = 1; tap >= hstap; tap--) {

            if (hsz != 2) {
                fhre = ofhre;
            }

            for (int i = tap; i < hsz; i += 2) {
                if (hspx == hp1) {
                    if (hre2 != 0) {
                        hspxstr = 0;
                        hre2 = 0;
                        if (asym != 0 && da_ev != 0) {
                            hre2 = 1;
                            fhre--;
                            if (fhre == 0) {
                                hre2 = 0;
                            }
                        }
                    } else {
                        hspxstr = nstr;
                    }
                } else if (hspx != hp0) {
                    newdata[himg] += olddata[hspx] * hi[i] * osfac;
                } else {
                    if (hle2 != 0) {
                        hspxstr = 0;
                        hle2 = 0;
                    } else {
                        hspxstr = pstr;
                        osfac = 1.0f;
                    }
                }

                hspx += hspxstr;
            }
            himg += stride;
        }

        return fhre;
    }

    private static int loop2(final float[] newdata, final float[] olddata, final int stride, final float[] lo, final int lsz, final int nstr, final int pstr, final int lp0, final int lp1, final int limg, int lpx, int lpxstr, int lle, int lre, final int tap) {

        newdata[limg] = olddata[lpx] * lo[tap];
        for (int i = tap + 2; i < lsz; i += 2) {
            if (lpx != lp0 && lpx != lp1) {
                lpx += lpxstr;
            } else if (lpx == lp0) {
                if (lle != 0) {
                    lpxstr = 0;
                    lle = 0;
                } else {
                    lpxstr = pstr;
                    lpx += pstr;
                }
            } else {
                if (lre != 0) {
                    lpxstr = 0;
                    lre = 0;
                } else {
                    lpxstr = nstr;
                    lpx += nstr;
                }
            }
            newdata[limg] += olddata[lpx] * lo[i];
        }

        return limg + stride;
    }

    private static int intSign(final int power) { /* "sign" power */
        /*int cnt;        *//* counter *//*
        int num = -1;   *//* sign return value *//*

        if (power == 0) {
            return 1;
        }

        for (cnt = 1; cnt < power; cnt++) {
            num *= -1;
        }

        return num;*/

        return power % 2 == 0 ? 1 : -1;
    }

    private static byte[] convertImageToByte(final float[] img, final int width, final int height, float mShift, final float rScale) {

        final byte[] data = new byte[width * height];
        mShift += 0.5f;
        int idx = 0;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                final float pixel = (img[idx] * rScale) + mShift;

                if (pixel >= 0.0 && pixel <= 255) {
                    data[idx] = (byte) pixel;
                } else if (pixel < 0.0) {
                    data[idx] = 0; /* neg pix poss after quantization */
                } else {
                    data[idx] = (byte) 0xff;
                }
                idx++;
            }
        }

        return data;
    }
}
