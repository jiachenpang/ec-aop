package org.n3r.mock.impl;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.esql.Esql;
import org.n3r.mock.Mockable;

import freemarker.template.Template;

public class RmdbMockImpl implements Mockable, ParamsAppliable {


    private String tableName;
    private String connectionName;


    @Override
    public String mock(String key, Object in) {
        List<Map<String, String>> mockresults = queryMockConfig(key);

        Template template = EcMockUtil.initTemplate(key, "");
        final int size = mockresults.size();
        if (0 == size) {
            return EcMockUtil.process(in, template);
        }

        StringBuilder sb = generateFreemarkStr(mockresults, size);
        template = EcMockUtil.initTemplate(key, sb.toString());
        return EcMockUtil.process(in, template);
    }

    private StringBuilder generateFreemarkStr(List<Map<String, String>> mockresults, final int size) {
        StringBuilder sb = new StringBuilder("<#if ");
        for (int i = 0; i < size; i++) {
            Map<String, String> mockresult = mockresults.get(i);
            appendStartTag(sb, size, i);
            appendCondition(mockresult, sb);
            appendRsp(sb, mockresult);
        }
        sb.append("</#if>");
        return sb;
    }

    private List<Map<String, String>> queryMockConfig(String key) {
        List<Map<String, String>> mockresults = new Esql(connectionName)
        .useSqlFile(RmdbMockImpl.class)
        .dynamics(tableName)
        .params(key).select("QueryMethodRsp")
        .execute();
        return mockresults;
    }

    private void appendRsp(StringBuilder sb, Map<String, String> mockresult) {
        String mockRsp = StringUtils.trim(MapUtils.getString(mockresult, "MOCK_RSP"));
        sb.append(mockRsp);
    }

    private void appendCondition(Map<String, String> mockresult, StringBuilder sb) {
            String condition = StringUtils.trim(MapUtils.getString(mockresult, "CONDITION"));
            condition = StringUtils.isEmpty(condition) ? " 1 == 1" : condition;
            sb.append(condition).append(">");
    }

    private void appendStartTag(StringBuilder sb, final int size, int i) {
        if (0 != i) {
            sb.append("<#elseif ");
        }
    }

    @Override
    public void applyParams(String[] params) {
        this.tableName = ParamsUtils.getStr(params, 0, "n3_mock");
        this.connectionName = ParamsUtils.getStr(params, 1, "aopMockConnection");
    }

}
