package org.n3r.acc.compare.right;

import org.n3r.acc.compare.FieldReference;
import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.Startable;
import org.n3r.core.tag.FromSpecConfig;

import java.io.Closeable;

public interface RightData extends Closeable, Startable {
    Record readRecord(FieldReference right);
}
