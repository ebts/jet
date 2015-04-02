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

package org.mitre.jet.ebts;

import com.google.common.io.Files;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.Assert.assertTrue;

/**
* User: cforter
*/
public class WsqDecoderTest {

    private static final Logger log = LoggerFactory.getLogger(WsqDecoderTest.class);

    public static File outputFolder = new File("JET/src/test/resources/test-output/");

    @BeforeClass
    public static void setup() {
        if (outputFolder.exists()) {
            for(File file : outputFolder.listFiles()) {
                file.delete();
            }
        } else {
            outputFolder.mkdirs();
        }
    }

    @Test
    public void decodeWsqImage(){
        File file = null;
        File outputFile = null;
        BufferedImage biWsq = null;
        BufferedImage biBmp = null;


        try {
            file = new File(ClassLoader.getSystemResource("a001.wsq").toURI());


            if(file != null) {
                //Decode WSQ Image to Java BufferedImage
                biWsq = ImageIO.read(file);
                log.info("Decoded WSQ File from {}", file.getAbsolutePath());

                //Write WSQ Image to BMP
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(biWsq,"bmp", baos);
                log.info("Converted Buffered Image to BMP");
                baos.flush();
                byte[] imageData = baos.toByteArray();
                outputFile = new File("JET/src/test/resources/test-output/a001.bmp");
                Files.write(imageData, outputFile);
                log.info("Wrote BMP image file to {}", outputFile.getAbsolutePath());
            }
            else{
                log.error("Test WSQ File is NULL");
            }

            biBmp = ImageIO.read(outputFile);

        } catch (IOException e) {
            e.printStackTrace();
        }catch (URISyntaxException e) {
            e.printStackTrace();
        }

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length()>0);

    }


}
