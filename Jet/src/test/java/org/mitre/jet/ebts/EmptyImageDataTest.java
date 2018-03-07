package org.mitre.jet.ebts;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.jet.ebts.field.Field;
import org.mitre.jet.ebts.records.GenericRecord;
import org.mitre.jet.ebts.records.LogicalRecord;

public class EmptyImageDataTest {

    private static void checkRecord(LogicalRecord record) {
        Assert.assertTrue("Record should report having field 999.", record.hasField(999));
        Assert.assertNotNull("Record should return non-null 999 field.", record.getField(999));
        Assert.assertNotNull("Field should return non-null data.", record.getField(999).getData());
        Assert.assertTrue("Record should report the 999 field's length as 0.", record.getField(999).getData().length == 0);
    }

    @Test
    public void testType2() throws Exception {
        Ebts ebts = new Ebts();
        ebts.addRecord(new GenericRecord(1));

        // Create a Type-2 with a 999 field that is present but empty.
        GenericRecord type2 = new GenericRecord(2);
        type2.setField(999, new Field(new byte[0], ParseContents.FALSE));
        ebts.addRecord(type2);

        // Inspect the record before building the file to rule out defects associated
        // with setField.
        checkRecord(type2);

        // Build then parse the ebts.
        byte[] ebtsData = new EbtsBuilder().build(ebts);
        Ebts parsed = EbtsParser.parse(ebtsData);

        // Retrieve the Type 2 and inspect it.
        LogicalRecord parsedType2 = parsed.getRecordsByType(2).get(0);
        checkRecord(parsedType2);

        // Verify that getImageData() is consistent.
        Assert.assertEquals("Record should report image data field -1.", -1, parsedType2.getImageField());
        Assert.assertNotNull("Record should not report null image data.", parsedType2.getImageData());
        Assert.assertEquals("Record should have 0-length image data.", 0, parsedType2.getImageData().length);
    }

    @Test
    public void testType10() throws Exception {
        Ebts ebts = new Ebts();
        ebts.addRecord(new GenericRecord(1));
        ebts.addRecord(new GenericRecord(2));

        // Create a Type-10 with a 999 field that is present but empty.
        GenericRecord type10 = new GenericRecord(10);
        type10.setField(999, new Field(new byte[0], ParseContents.FALSE));
        ebts.addRecord(type10);

        // Inspect the record before building the file to rule out defects associated
        // with setField.
        checkRecord(type10);

        // Build the ebts.
        byte[] ebtsData = new EbtsBuilder().build(ebts);

        // Parse the ebts.
        Ebts parsed = EbtsParser.parse(ebtsData);

        // Retrieve the Type 10 and inspect it.
        LogicalRecord parsedType10 = parsed.getRecordsByType(10).get(0);
        checkRecord(parsedType10);

        // Verify that getImageData() is consistent.
        Assert.assertEquals("Record should report image data field 999.", 999, parsedType10.getImageField());
        Assert.assertNotNull("Record should not report null image data.", parsedType10.getImageData());
        Assert.assertEquals("Record should have 0-length image data.", 0, parsedType10.getImageData().length);
    }
}
