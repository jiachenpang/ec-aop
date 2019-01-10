package org.n3r.acc.process.filter;


import org.n3r.acc.process.output.DataOutput;
import org.n3r.config.Configable;

import java.io.InputStream;

public interface DataFileFilter {
    void filter(InputStream is, DataOutput dataOutput);
}
