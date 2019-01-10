package org.n3r.core.tag;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.joor.Reflect;
import org.n3r.core.lang.RClassPath;
import org.n3r.core.util.ParamsAppliable;


public class EcRocTagParser {

    public <T> T parse(String targetTag, String basePackage, Class<T> baseClazz, ParamDef paramDef) {
        for (Class<?> clazz : RClassPath.getSubClasses(basePackage, baseClazz)) {
            EcRocTag tagAnn = clazz.getAnnotation(EcRocTag.class);
            if (tagAnn == null) continue;
            if (!StringUtils.equalsIgnoreCase(tagAnn.value(), targetTag)) continue;

            T object = Reflect.on(clazz).create().get();
            if (object instanceof ParamDefAppliable) {
                ((ParamDefAppliable)object).applyParamDef(paramDef);
            }
            if (object instanceof ParamsAppliable) {
                ((ParamsAppliable)object).applyParams(paramDef.getParams());
            }

            return object;
        }

        // throw new EcAopException(targetTag + " is not defined in package " + basePackage + " for " + baseClazz + "!");
        return null;
    }

    public <T> T parse(String targetTag, String basePackage, Class<T> baseClazz, String... params) {
        for (Class<?> clazz : RClassPath.getSubClasses(basePackage, baseClazz)) {
            EcRocTag tagAnn = clazz.getAnnotation(EcRocTag.class);
            if (tagAnn == null) continue;
            if (!StringUtils.equalsIgnoreCase(tagAnn.value(), targetTag)) continue;

            T object = Reflect.on(clazz).create().get();
            if (object instanceof ParamsAppliable) {
                ((ParamsAppliable)object).applyParams(params);
            }

            return object;
        }

        // throw new EcAopException(targetTag + " is not defined in package " + basePackage + " for " + baseClazz + "!");
        return null;
    }

}
