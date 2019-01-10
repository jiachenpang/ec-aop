package org.n3r.core.tag;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.n3r.core.lang.RStr;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class ParamDefParser {
    private static final Pattern paramParams = Pattern
            .compile("\\w+([.$]\\w+)*\\s*(\\[\\s*\\])?\\s*(\\(\\s*[.\\w]+\\s*(,\\s*[.\\w]+\\s*)*\\))?");

    public ParamDef parseParam(String paramsConfig) {
        if (isEmpty(paramsConfig)) return null;

        int posBrace = paramsConfig.indexOf('(');
        String type = posBrace < 0 ? paramsConfig : paramsConfig.substring(0, posBrace);
        ParamDef param = new ParamDef();
        param.setDef(paramsConfig);

        type = type.trim();
        int indexBracket = type.indexOf('[');
        if (indexBracket < 0) {
            param.setName(type);
            param.setArray(false);
        }
        else {
            param.setName(type.substring(0, indexBracket).trim());
            param.setArray(true);
        }

        if (param.getName().startsWith("@")) {
            param.setName(param.getName().substring(1));
        }

        Splitter splitter = Splitter.on(',').trimResults();
        param.setParams(posBrace <= 0 ? new String[] {}
                : Iterables.toArray(splitter.split(RStr.substrInQuotes(paramsConfig, '(', posBrace)), String.class));

        return param;
    }

    public List<ParamDef> parseParams(String paramsConfig) {
        List<ParamDef> lst = new ArrayList<ParamDef>();
        if (isEmpty(paramsConfig)) return lst;

        Matcher matcher = paramParams.matcher(paramsConfig);

        while (matcher.find())
            lst.add(parseParam(matcher.group()));

        return lst;
    }
}
