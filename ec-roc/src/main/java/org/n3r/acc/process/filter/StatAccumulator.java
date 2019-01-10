package org.n3r.acc.process.filter;

public interface StatAccumulator {
    void init(Object filterValue);

    void validate();

    void accumulate(Object filterValue);
}
