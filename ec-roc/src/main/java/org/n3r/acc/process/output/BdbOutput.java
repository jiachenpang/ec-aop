package org.n3r.acc.process.output;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.Iterables;
import com.sleepycat.je.Database;
import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.utils.BdbUtils;
import org.n3r.bdb.Bdbs;
import org.n3r.config.Configable;
import org.n3r.core.lang.RByte;
import org.n3r.core.lang.Substituters;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.fastjson.EjsonEncoder;

import java.util.Map;

@EcRocTag("Bdb")
public class BdbOutput extends DirectOutput implements ParamsAppliable {
    private Database database;
    private String[] params;
    private int bdbKeyNo;
    private String bdbKeyName;


    public void setDataFieldNameIndex(BiMap<String, Integer> dataFieldNameIndex) {
        super.setDataFieldNameIndex(dataFieldNameIndex);
        bdbKeyNo = dataFieldNameIndex.get(bdbKeyName);
    }

    @Override
    public void outputLine(Object[] fields) {
        JSONObject jsonObject = new JSONObject();

        Object[] outputFields = fields;
        if (outputIndices.length > 0) {
            outputFields = new Object[outputIndices.length];
            int outputIndex = 0;
            for (int index : outputIndices) {
                jsonObject.put(super.outputFields[index], fields[index]);
                outputFields[outputIndex++] = fields[index];
            }
        }


        String json = new EjsonEncoder().encode(jsonObject);
        Bdbs.put(database, RByte.toBytes("" + outputFields[bdbKeyNo]), RByte.toBytes(json));
    }

    @Override
    public void applyParams(String[] params) {
        this.params = params;
    }

    @Override
    public void finishOutput() {
        BdbUtils.closeDbAndEvn(database);
    }

    @Override
    public BdbOutput fromSpec(Configable config, Map<String, String> context) {
        parseOutputFields(config);
        parseDatabaseName(context);

        bdbKeyName = config.getStr("output.key");

        return this;
    }

    private void parseDatabaseName(Map<String, String> context) {
        // 对数据库名称提供变量替换支持
        params[1] =  Substituters.parse(params[1], context);
        database = BdbUtils.applyParams(params, false);
    }

    private void parseOutputFields(Configable config) {
        String outputFields = config.getStr("output.fields");
        if (StringUtils.isEmpty(outputFields)) return;

        Iterable<String> split = Splitter.on(',').trimResults().split(outputFields);
        outputFields(Iterables.toArray(split, String.class));
    }
}
