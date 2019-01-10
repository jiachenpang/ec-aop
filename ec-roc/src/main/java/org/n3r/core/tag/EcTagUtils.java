package org.n3r.core.tag;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.n3r.config.Config;
import org.n3r.core.joor.Reflect;
import org.n3r.core.lang.RClassPath;
import org.n3r.core.util.AfterPropertiesSet;
import org.n3r.core.util.ParamsAppliable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EcTagUtils {
    public static <T> T parseTag(Class<T> class1, Spec spec) {
        String packages = parsePackages();

        return parse(spec.getName(), packages, class1, spec);
    }

    private static String parsePackages() {
        String packageConfigKey = "ecroctag.basepackages";
        String packages = Config.getStr(packageConfigKey, "");
        if (!packages.contains("org.n3r"))
            packages = "org.n3r," + packages;

        return packages;
    }

    public static <T> List<T> parseTags(String tags, Class<T> clazz) {
        List<T> paramValidators = Lists.newArrayList();
        if (StringUtils.isEmpty(tags)) return paramValidators;

        for (Spec spec : SpecParser.parseSpecs(tags)) {
            T paramValidator = parseTag(clazz, spec);
            if (paramValidator != null) {
                paramValidators.add(paramValidator);
                if (paramValidator instanceof AfterPropertiesSet)
                    ((AfterPropertiesSet) paramValidator).afterPropertiesSet();
            }
        }

        return paramValidators;
    }

    public static <T> T parse(String targetTag, String basePackage, Class<T> baseClazz, Spec spec) {
        for (Class<?> clazz : RClassPath.getSubClasses(basePackage, baseClazz)) {
            EcRocTag tagAnn = clazz.getAnnotation(EcRocTag.class);
            if (tagAnn == null) continue;
            if (!StringUtils.equalsIgnoreCase(tagAnn.value(), targetTag)) continue;

            T object = Reflect.on(clazz).create().get();
            if (object instanceof SpecAppliable) ((SpecAppliable) object).applySpec(spec);
            if (object instanceof ParamsAppliable) ((ParamsAppliable) object).applyParams(spec.getParams());

            return object;
        }

        return null;
    }

    public static <T> List<T> getObjects(Class<? extends T> baseClazz) {
        String basePackage = parsePackages();
        List<T> objects = new ArrayList<T>();
        for (Class<?> clazz : RClassPath.getSubClasses(basePackage, baseClazz)) {
            EcRocTag tagAnn = clazz.getAnnotation(EcRocTag.class);
            if (tagAnn == null) continue;

            T object = Reflect.on(clazz).create().get();

            objects.add(object);
        }

        return objects;
    }
}
