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

package org.jnbis.test;

import org.jnbis.Bitmap;
import org.jnbis.WSQDecoder;
import org.jnbis.WSQEncoder;

import javax.swing.*;
import java.awt.*;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class WSQTest {

    private static final String WSQ_FILE_IN  = "/home/jsterrett/shared/rectangle.wsq";
    private static final String WSQ_FILE_OUT = "/home/jsterrett/shared/rectangle-copy.wsq";

    public WSQTest() {
        testDecode();
        testEncode();
    }

    public void testDecode() {
        try {
            final File file = new File(WSQ_FILE_IN);
            final Bitmap bitmap = WSQDecoder.decode(new FileInputStream(file));
            System.out.println("bitmap = " + bitmap);
            final BufferedImage image = convert(bitmap);
            showImage(image);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public void testEncode() {
        try {
            File file = new File(WSQ_FILE_IN);
            System.out.println("DEBUG: WSQ_FILE_IN length = " + file.length());
            Bitmap bitmap = WSQDecoder.decode(new FileInputStream(file));
            final OutputStream outputStream = new FileOutputStream(WSQ_FILE_OUT);
            final float bitrate = 0.75f;
            //			int depth = 24; /* set it in bitmap */
            //			int ppi = 500; /* set it in bitmap */
            final String commentText = "";
            WSQEncoder.encode(outputStream, bitmap, bitrate, commentText);
            outputStream.close();

            file = new File(WSQ_FILE_OUT);
            System.out.println("DEBUG: WSQ_FILE_OUT length = " + file.length());
            bitmap = WSQDecoder.decode(new FileInputStream(file));
            System.out.println("bitmap = " + bitmap);
            final BufferedImage image = convert(bitmap);
            showImage(image);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static void showImage(final BufferedImage image) {
        final JFrame frame = new JFrame("Image " + image.getWidth() + " x " + image.getHeight());
        final Container contentPane = frame.getContentPane();
        final JPanel imgPanel = new JPanel(new BorderLayout());
        imgPanel.add(new JLabel(new ImageIcon(image)), BorderLayout.CENTER);
        contentPane.add(imgPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private static BufferedImage convert(final Bitmap bitmap) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final byte[] data = bitmap.getPixels();
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        final WritableRaster raster = image.getRaster();
        raster.setDataElements(0, 0, width, height, data);
        return image;
    }

    public static void main(final String[] arg) {
        new WSQTest();
    }
}
