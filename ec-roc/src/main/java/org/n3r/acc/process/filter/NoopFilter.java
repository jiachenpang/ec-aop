package org.n3r.acc.process.filter;

import org.n3r.acc.process.output.DataOutput;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;

import java.io.InputStream;

@EcRocTag("Noop")
public class NoopFilter implements DataFileFilter{
    @Override
    public void filter(InputStream is, DataOutput dataOutput) {
    }
}
