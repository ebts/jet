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

package org.mitre.printEbts;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mitre.jet.ebts.Ebts;
import org.mitre.jet.ebts.EbtsParser;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.ebts.field.Occurrence;
import org.mitre.jet.ebts.field.SubField;
import org.mitre.jet.ebts.records.LogicalRecord;
import org.mitre.jet.exceptions.EbtsParsingException;


public class printEbts {

	public static void main(String[]args) throws IOException, EbtsParsingException{
		System.out.println(printEbts.class.getClassLoader());
		InputStream inputStream = printEbts.class.getClassLoader().getResourceAsStream("testEbts.eft");
		byte[] ebtsData = org.apache.tika.io.IOUtils.toByteArray(inputStream);
		EbtsParser ebtsParser = new EbtsParser();
		Ebts ebts = ebtsParser.parse(ebtsData);	

		List <Integer> recordTypes = new ArrayList<Integer>();
		recordTypes.add(1);
		recordTypes.add(2);
		recordTypes.add(4);
		recordTypes.add(7);
		recordTypes.add(9);
		recordTypes.add(10);
		recordTypes.add(13);
		recordTypes.add(14);
		recordTypes.add(15);

		System.out.println("<Ebts>");

		for (int a=0; a<recordTypes.size(); a++){		
			if (ebts.containsRecord(recordTypes.get(a))){
				List <LogicalRecord> records = ebts.getRecordsByType(recordTypes.get(a));
				//Records
				for (int h = 0; h<records.size(); h++){
					System.out.println("\t <Record>");
					System.out.println("\t\t <RecordType>" + recordTypes.get(a)+" </RecordType>");
					Map<Integer, Field> fields = records.get(h).getFields();

					//Fields
					for (Map.Entry<Integer, Field> entry : fields.entrySet()){
						String fieldNumber = Integer.toString(entry.getKey());
						if (!fieldNumber.equals("999") && !(recordTypes.get(a)==4 && fieldNumber.equals("9"))){
							System.out.println("\t\t\t <FieldValue> " +fieldNumber+ " </FieldValue>");
							List<Occurrence> occurrences = entry.getValue().getOccurrences();

							for(Occurrence occurrence : occurrences){
								List<SubField> subfields = occurrence.getSubFields();
								for(SubField subfield : subfields){
									System.out.println("\t\t\t\t <FieldContents> " +subfield+ " </FieldContents>");
								}
							}
						}
					}
					System.out.println("\t </Record>");
				}	
			}
		}
		System.out.println("</Ebts>");
	}
}