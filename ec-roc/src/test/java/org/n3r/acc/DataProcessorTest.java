package org.n3r.acc;


import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import org.junit.Test;
import org.n3r.acc.process.DataProcessor;
import org.n3r.acc.process.field.DateField;
import org.n3r.acc.process.field.DateToLongField;
import org.n3r.acc.process.field.IntField;
import org.n3r.acc.process.filter.TextFileFilter;
import org.n3r.acc.process.input.DirectInput;
import org.n3r.acc.process.output.DirectOutput;
import org.n3r.bdb.Bdbs;
import org.n3r.bytes.Byter;
import org.n3r.core.lang.RByte;
import org.n3r.core.lang.RDate;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public class DataProcessorTest {
    @Test
    public void test1() {
        DirectOutput directOutput = new DirectOutput().outputFields("money", "time");
        new DataProcessor()
                .input(new DirectInput("1,2013-06-03 13:53:17,100"))
                .filter(
                        new TextFileFilter()
                                .dataFieldNames("id,time,money")
                                .dataField("id", new IntField())
                                .dataField("time"
                                        , new DateField("yyyy-MM-dd HH:mm:ss")
                                        , new DateToLongField())
                                .dataField("money", new IntField())
                )
                .output(directOutput)
                .process();


        assertEquals("100,1370238797000,", directOutput.toString());
    }

    @Test
    public void test2() {
//        DataProcessor dataProcessor = new DataProcessor().fromSpec("test2");
//        dataProcessor.process();

        walkBdb("C:\\bdb\\bdbdemo4", "test2", "array(int,long,int)");


        //assertEquals("1,1370238797000,100,", dataProcessor.getOutput().toString());


    }

    private void walkBdb(String pathname, String dbName, String meta) {
        Environment environment = Bdbs.openEnv(new File(pathname), 1024 * 1024);
        Database database = Bdbs.openDb(environment, dbName);

        Cursor cursor = Bdbs.openCursor(database);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        while (Bdbs.cursorNext(cursor, key, value)) {
            System.out.println(RByte.toStr(key.getData()));
            Object[] objects = (Object[]) Byter.decode(value.getData(), meta);
            System.out.println(Arrays.toString(objects));
        }
        Bdbs.closeCursor(cursor);
    }

    private void walkBdbJson(String pathname, String dbName) {
        Environment environment = Bdbs.openEnv(new File(pathname), 1024 * 1024);
        Database database = Bdbs.openDb(environment, dbName);

        Cursor cursor = Bdbs.openCursor(database);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        while (Bdbs.cursorNext(cursor, key, value)) {
            System.out.print(RByte.toStr(key.getData()));
            System.out.print(":");
            System.out.println(RByte.toStr(value.getData()));
        }
        Bdbs.closeCursor(cursor);
    }

    @Test
    public void testbroadband() {
       DataProcessor dataProcessor = new DataProcessor().fromSpec("broadbandAcc", null);
       dataProcessor.process();

       walkBdbJson("C:\\bdb\\bdbdemo3","broadband");
    }

    @Test
    public void testXL() {
            Date dateInstance = RDate.parse("20130619", "yyyyMMdd");
            System.out.println(dateInstance.toString());
    }


}
