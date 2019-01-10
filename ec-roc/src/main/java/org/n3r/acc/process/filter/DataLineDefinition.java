package org.n3r.acc.process.filter;

import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.process.field.DataField;
import org.n3r.acc.process.field.ListDataField;
import org.n3r.config.Configable;
import org.n3r.core.lang.RStr;
import org.n3r.core.tag.EcTagUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DataLineDefinition {
    private BiMap<String, Integer> dataFieldNameIndex = HashBiMap.create();
    private Map<Integer, DataField> dataFieldFilters = Maps.newHashMap();
    private String[] dataFieldsNamesStrs = new String[0];
    private String dataLineSeperator = ",";

    public DataLineDefinition parseDataLineSeperator(Configable config, String seperatorConfigKey) {
        String seperator = config.getStr(seperatorConfigKey);
        if (StringUtils.isEmpty(seperator)) return this;

        dataLineSeperator(seperator);
        return this;
    }

    public Object[] parseLine(String line, int lineNo) {
        Iterable<String> fieldsIterable = Splitter.on(dataLineSeperator).trimResults().split(line);
        String[] fields = Iterables.toArray(fieldsIterable, String.class);
        Object[] filterValues = new Object[fields.length];

        for (int i = 0, ii = fields.length; i < ii; ++i) filterValues[i] = fields[i];

        for (Map.Entry<Integer, DataField> entry : dataFieldFilters.entrySet()) {
            Integer index = entry.getKey();
            if (index >= fields.length) throw new RuntimeException(lineNo + " fields num is invalid");
            DataField dataFieldFilter = entry.getValue();
            filterValues[index] = dataFieldFilter.filter(fields[index]);
        }
        return filterValues;
    }

    public DataLineDefinition parseDataFieldNames(Configable config,
                                                  String dataFieldNamesConfigKey, String fieldFilterConfigKey) {
        String dataFieldNames = config.getStr(dataFieldNamesConfigKey);
        if (StringUtils.isEmpty(dataFieldNames)) return this;

        dataFieldNames(dataFieldNames);

        // 检查以名称定义的字段过滤器
        for (String dataFiledName : dataFieldsNamesStrs) {
            String fieldFilterDef = config.getStr(fieldFilterConfigKey + "." + dataFiledName);
            if (StringUtils.isEmpty(fieldFilterDef)) continue;

            List<DataField> fieldFilters = EcTagUtils.parseTags(fieldFilterDef, DataField.class);
            dataField(dataFiledName, fieldFilters.toArray(new DataField[0]));
        }

        // 检查以序号定义的字段过滤器
        Configable dataFieldConfig = config.subset(fieldFilterConfigKey);
        Properties dataFields = dataFieldConfig.getProperties();
        for (Object key : dataFields.keySet()) {
            String strKey = (String) key;
            if (!RStr.isInteger(strKey)) continue;

            String fieldFilterDef = dataFields.getProperty(strKey);
            if (StringUtils.isEmpty(fieldFilterDef)) continue;
            List<DataField> fieldFilters = EcTagUtils.parseTags(fieldFilterDef, DataField.class);
            dataField(Integer.parseInt(strKey), fieldFilters.toArray(new DataField[0]));
        }

        return this;
    }

    public int findFieldIndex(String fieldName) {
        for (int i = 0, ii = dataFieldsNamesStrs.length; i < ii; ++i)
            if (StringUtils.equalsIgnoreCase(dataFieldsNamesStrs[i], fieldName)) return i;

        return -1;
    }

    public DataLineDefinition dataField(String fieldName, DataField... fieldFilters) {
        int fieldIndex = findFieldIndex(fieldName);
        if (fieldIndex >= 0) {
            dataFieldNameIndex.put(dataFieldsNamesStrs[fieldIndex], fieldIndex);
            dataField(fieldIndex + 1, fieldFilters);
            return this;
        }

        throw new RuntimeException(fieldName + " is not predefined by dataFieldNames");
    }

    public DataLineDefinition dataField(int fieldIndex, DataField... fieldFilters) {
        dataFieldFilters.put(fieldIndex - 1, new ListDataField(fieldFilters));
        return this;
    }

    public DataLineDefinition dataLineSeperator(String dataLineSeperator) {
        this.dataLineSeperator = dataLineSeperator;
        return this;
    }

    public DataLineDefinition dataFieldNames(String dataFieldNames) {
        Iterable<String> dataFieldNamesIterable = Splitter.on(',').trimResults().split(dataFieldNames);
        this.dataFieldsNamesStrs = Iterables.toArray(dataFieldNamesIterable, String.class);
        validateDataFieldsNames(dataFieldsNamesStrs);
        for (int i = 0, ii = this.dataFieldsNamesStrs.length; i < ii; ++i) {
            dataFieldNameIndex.put(this.dataFieldsNamesStrs[i], i);
        }

        return this;
    }

    private void validateDataFieldsNames(String[] fieldsNamesStrs) {
        for (String dataFiledName : fieldsNamesStrs)
            if (!isJavaIdentifier(dataFiledName))
                throw new RuntimeException(dataFiledName + " is invalid!");
    }

    public boolean isJavaIdentifier(String str) {
        boolean start = true;
        for (char ch : str.toCharArray()) {
            if (start) {
                if (!Character.isJavaIdentifierStart(ch)) return false;
                start = false;
            } else if (!Character.isJavaIdentifierPart(ch)) return false;
        }

        return true;
    }

    public BiMap<String, Integer> getDataFieldNameIndex() {
        return dataFieldNameIndex;
    }

}