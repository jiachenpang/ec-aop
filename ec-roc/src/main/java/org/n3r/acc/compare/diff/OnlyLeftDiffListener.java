package org.n3r.acc.compare.diff;


import org.n3r.acc.compare.Record;

public interface OnlyLeftDiffListener {
    Record onLeftRight(Record record);
}
