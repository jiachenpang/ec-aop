package org.n3r.acc.compare.left;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.FieldsRecord;
import org.n3r.acc.compare.impl.JsonRecord;
import org.n3r.acc.utils.BdbUtils;
import org.n3r.bdb.Bdbs;
import org.n3r.bytes.Byter;
import org.n3r.config.Configable;
import org.n3r.core.lang.RByte;
import org.n3r.core.lang.Substituters;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.core.util.ParamsAppliable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EcRocTag("Bdb")
public class BdbLeftData implements LeftData, ParamsAppliable, FromSpecConfig<BdbLeftData> {
    private Database database;
    private String[] params;

    @Override
    public Record popup(String key) {
        byte[] keyBytes = RByte.toBytes(key);
        byte[] value = Bdbs.get(database, keyBytes);
        if (value == null) return null;

        Bdbs.delete(database, keyBytes);

        String json = RByte.toStr(value);

        return new JsonRecord(RByte.toStr(keyBytes), json);

    }

    @Override
    public List<Record> popupAll() {
        List<Record> records = new ArrayList<Record>();
        Cursor cursor = Bdbs.openCursor(database);
        DatabaseEntry foundKey = new DatabaseEntry();
        DatabaseEntry foundValue = new DatabaseEntry();
        while(Bdbs.cursorNext(cursor, foundKey, foundValue)) {
            String key = RByte.toStr(foundKey.getData());
            String json = RByte.toStr(foundValue.getData());

            records.add(new JsonRecord(key, json));
        }
        Bdbs.closeCursor(cursor);

        return records;
    }


    @Override
    public void applyParams(String[] params) {
        this.params = params;
    }

    @Override
    public void close() {
        BdbUtils.closeDbAndEvn(database);
    }

    @Override
    public void start() {

    }

    @Override
    public BdbLeftData fromSpec(Configable config, Map<String, String> context) {
        params[1] =  Substituters.parse(params[1], context);
        database = BdbUtils.applyParams(params, false);
        return this;
    }
}
