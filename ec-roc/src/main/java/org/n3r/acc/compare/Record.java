package org.n3r.acc.compare;

public interface Record {
    String getKey();

    Object getValue(FieldReference compareField);
}
