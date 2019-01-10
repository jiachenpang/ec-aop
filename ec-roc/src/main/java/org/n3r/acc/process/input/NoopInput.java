package org.n3r.acc.process.input;

import org.n3r.core.tag.EcRocTag;

import java.io.InputStream;

@EcRocTag("Noop")
public class NoopInput implements DataInput {
    @Override
    public InputStream getInputStream() {
        return null;
    }
}
