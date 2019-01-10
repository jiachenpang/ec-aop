package com.ailk.ecaop.common.extractor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.ParameterValueExtractor;

/**
 * 参数是parent 是入参层级结构父节点, 支持多级节点
 * 
 * @author Lionel
 * 
 */
public abstract class ParamIntoValueExtractor implements ParameterValueExtractor, ParamsAppliable {
    String[] keys;
    String parent;
    String outkey;
    String in;
    
    String defaultValue;

    protected Map getParent(Map<String, Object> root) {
        Map obj = root;

        if (keys.length == 2) {
            return getchild(obj, parent);
        }

        for (int i = 0; i < keys.length - 1; i++) {
            obj = getchild(obj, keys[i].trim());
        }
        return obj;
    }

    protected Map getchild(Map obj, String key) {
        Map child = (Map) obj.get(key);
        if (child == null) {
            obj.put(key, new HashMap());
        }
        return (Map) obj.get(key);
    }

    @Override
    public void applyParams(String[] params) {
        String[] param = params[0].split(",");
        
        if(param.length ==3){
            defaultValue = param[2].trim();
        }
        keys = param[0].trim().split("\\.");
        parent = keys[0].trim();
        outkey = keys[keys.length - 1].trim();
        in = param[1];
    }
}
