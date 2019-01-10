package org.n3r.acc.process.input;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;

import java.io.InputStream;
import java.util.Map;

@EcRocTag("Direct")
public class DirectInput implements DataInput, ParamsAppliable {
    private String directContent;

    public DirectInput() {

    }

    public DirectInput(String directContent) {
        this.directContent = directContent;
    }

    @Override
    public InputStream getInputStream() {
        return IOUtils.toInputStream(directContent);
    }

    @Override
    public void applyParams(String[] params) {
        directContent = ParamsUtils.getStr(params, 0, null);
        if (StringUtils.isEmpty(directContent))
            throw new RuntimeException("@Direct needs set direct content");
    }
}
