package org.n3r.acc.process.field;


public interface DataField<From, To> {
    To filter(From field);

    Class getFieldClass();
}
