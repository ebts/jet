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

package org.mitre.createEbts;

import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.mitre.jet.ebts.Ebts;
import org.mitre.jet.ebts.EbtsBuilder;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.ebts.records.BinaryHeaderImageRecord;
import org.mitre.jet.ebts.records.GenericRecord;

import javax.imageio.ImageIO;

public class createEbts {

	public static void main(String[] args){
		Ebts ebts = new Ebts();
		try {
			//Create Type 1 Record
			String tot = "CAR";
			String dest = "DESTORI01";
			String ori = "TESTWV123";
			String tcn = "TESTWV123-20140930163518-JET1-0001-07171";
			ebts.addRecord(createType1Record(tot, dest, ori, tcn));

			//Create Type 2 Record
			String name = "JOHNSON, SALLY";
			String dob = "19921201";
			String sex = "F";
			String rfp = "TEST TRANSACTION";
			String height = "510";
			String weight = "150";
			ebts.addRecord(createType2Record(name, dob, sex, rfp, height, weight));

			//Create Type 4 Record
			InputStream is4 = createEbts.class.getClassLoader().getResourceAsStream("a001.wsq");
			byte[] imgData4 = IOUtils.toByteArray(is4);
			boolean markRolled = true;
			String position = "1";
			ebts.addRecord(createType4Record(markRolled, position, imgData4, ebts));

			//Create Type 10 Record
			String imgType = "FACE";
			String agency = "BATTT499Z0";
			InputStream is10 = createEbts.class.getClassLoader().getResourceAsStream("face.jpg");
			byte[] imgData10 = IOUtils.toByteArray(is10);
			ebts.addRecord(createType10Record(imgType, agency, imgData10, ebts));


			//Create Type 14 Record
			InputStream is14 = createEbts.class.getClassLoader().getResourceAsStream("a001.wsq");
			byte[] imgData14 = IOUtils.toByteArray(is14);
			ebts.addRecord(createType14Record(markRolled, position, imgData14, ebts));

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		saveEbts(ebts);
	}

	//type 1
	public static GenericRecord createType1Record(String tot, String dest, String ori, String tcn){
		GenericRecord type1Record = new GenericRecord(1);

		type1Record.setField(2, new Field("0201"));
		type1Record.setField(3, new Field());//file content
		type1Record.setField(4, new Field(tot));
		type1Record.setField(5, new Field(todaysDate()));
		type1Record.setField(7, new Field(dest));
		type1Record.setField(8, new Field(ori));
		type1Record.setField(9, new Field(tcn));
		type1Record.setField(11, new Field("19.69"));
		type1Record.setField(12, new Field("19.69"));

		return type1Record;
	}

	//type 2
	public static GenericRecord createType2Record(String name, String dob, String sex, String RFP, String height, String weight){
		GenericRecord type2Record = new GenericRecord(2);

		type2Record.setField(2, new Field("00"));
		type2Record.setField(18, new Field(name));
		type2Record.setField(20, new Field(dob));
		type2Record.setField(24, new Field(sex));
		type2Record.setField(37, new Field(RFP));
		type2Record.setField(27, new Field(height));
		type2Record.setField(29, new Field(weight));

		return type2Record;
	}

	//type 4
	public static BinaryHeaderImageRecord createType4Record(boolean markRolled, String fgp, byte[] imgData, Ebts ebts) throws IOException{
		BinaryHeaderImageRecord type4Record = new BinaryHeaderImageRecord(4);
		BufferedImage image = createImageFromBytes(imgData);
		int length = 18 + imgData.length;
		Integer idc = 01;
		
		type4Record.setField(1, new Field((String.valueOf(length))));
		type4Record.setField(2, new Field(idc.toString()));
		if (markRolled) {
			type4Record.setField(3, new Field("1"));
		} else {
			type4Record.setField(3, new Field("0"));
		}
		type4Record.setField(4, new Field(fgp));
		type4Record.setField(5, new Field("0"));
		type4Record.setField(6, new Field(String.valueOf(image.getWidth())));
		type4Record.setField(7, new Field(String.valueOf(image.getHeight())));
		type4Record.setField(8, new Field("1")); //Scale Units - Value of (1) Denotes that 1.011 and 1.012 designate scale.
		
		type4Record.setImageData(imgData);

		return type4Record;
	}

	//type 2
	public static GenericRecord createType10Record(String imgType, String agency, byte[] imgData, Ebts ebts) throws IOException{
		GenericRecord type10Record = new GenericRecord(10);
		BufferedImage image = createImageFromBytes(imgData);
		int length = 12 + imgData.length;
		Integer idc = 02;

		type10Record.setField(1, new Field((String.valueOf(length))));
		type10Record.setField(2, new Field(idc.toString()));
		type10Record.setField(3, new Field(imgType));
		type10Record.setField(4, new Field(agency));
		type10Record.setField(5, new Field(todaysDate()));
		type10Record.setField(6, new Field(String.valueOf(image.getWidth())));
		type10Record.setField(7, new Field(String.valueOf(image.getHeight())));
		type10Record.setField(8, new Field("1")); //Scale Units - Vale of (1) denotes type 1 information is correct 1.011/1.012
		type10Record.setField(9, new Field("1"));
		type10Record.setField(10, new Field("1"));
		type10Record.setField(11, new Field("JPEGB"));
		type10Record.setField(12, new Field("YCC"));

		type10Record.setImageData(imgData);

		return type10Record;
	}

	//type 14
	public static GenericRecord createType14Record(boolean markRolled, String pos, byte[] imgData, Ebts ebts) throws IOException{
		GenericRecord type14Record = new GenericRecord(14);
		BufferedImage image = createImageFromBytes(imgData);
		Integer idc = 03;

		type14Record.setField(2, new Field(idc.toString()));
		if (markRolled) {
			type14Record.setField(3, new Field("1"));
		} else {
			type14Record.setField(3, new Field("0"));
		}
		type14Record.setField(5, new Field(todaysDate()));
		type14Record.setField(6, new Field(String.valueOf(image.getWidth())));
		type14Record.setField(7, new Field(String.valueOf(image.getHeight())));
		type14Record.setField(12, new Field("8"));
		type14Record.setField(13, new Field(pos));
		
		type14Record.setImageData(imgData);

		return type14Record;
	}

	//save Ebts
	public static void saveEbts(Ebts ebts){
		try{   	
			EbtsBuilder ebtsBuilder = new EbtsBuilder();
			byte [] sample = ebtsBuilder.build(ebts);

			String fileName = "testEbts.eft";
            File outputFile = new File("JETExample/src/main/resources/"+fileName);
			FileOutputStream fos = new FileOutputStream(outputFile);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			bos.write(sample);

			System.out.println("Done");
			bos.close();
		}catch(Exception ex){
			ex.printStackTrace();
		}
	}

    public static BufferedImage createImageFromBytes(byte[] imageData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        ImageIO.setUseCache(false);
        try {
            return ImageIO.read(bais);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String todaysDate() {
        String todaysDate = null;
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        todaysDate = formatter.format(new java.sql.Timestamp(date.getTime()));
        return todaysDate;
    }

}
