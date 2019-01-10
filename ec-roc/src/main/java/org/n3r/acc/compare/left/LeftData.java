package org.n3r.acc.compare.left;

import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.Startable;
import org.n3r.core.tag.FromSpecConfig;

import java.io.Closeable;
import java.util.List;

public interface LeftData extends Closeable, Startable {
    Record popup(String key);

    List<Record> popupAll();
}
