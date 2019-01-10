package org.n3r.acc.process.input;

import org.apache.commons.lang3.StringUtils;
import org.n3r.config.Configable;
import org.n3r.core.lang.Substituters;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

/**
 * 读取本地文件形成输入.
 */
@EcRocTag("File")
public class FileInput implements DataInput, ParamsAppliable, FromSpecConfig<FileInput> {
    private String path;


    @Override
    public InputStream getInputStream() {
        File file = new File(path);
        if (!file.exists())
            throw new RuntimeException("file path does not exists: " + path);

        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void applyParams(String[] params) {
        path = ParamsUtils.getStr(params, 0, null);
        if (StringUtils.isEmpty(path))
            throw new RuntimeException("file path required!");


    }

    @Override
    public FileInput fromSpec(Configable config, Map<String, String> context) {
        path = Substituters.parse(path, context);

        return this;
    }
}
