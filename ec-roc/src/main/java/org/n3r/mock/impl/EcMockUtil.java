package org.n3r.mock.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.n3r.core.lang.RClassPath;
import org.n3r.freemarker.FreemarkerFunctionsTag;

import freemarker.cache.StringTemplateLoader;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModelException;

public  class EcMockUtil {

    public static String process(Object in, Template template) {
        StringWriter writer = new StringWriter();
        try {
            template.process(in, writer);
        } catch (TemplateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            IOUtils.closeQuietly(writer);
        }

        return writer.toString();
    }

    public static Template initTemplate(String key, String templateSource) {
        Configuration config = new Configuration();
        StringTemplateLoader stringLoader = new StringTemplateLoader();

        loadStringTemplateLoader4Config(config, stringLoader);

        stringLoader.putTemplate(key, templateSource);
        Template template = null;
        try {
            template = config.getTemplate(key);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return template;
    }

    private static void loadStringTemplateLoader4Config(Configuration config, StringTemplateLoader stringLoader) {
        BeansWrapper wrapper = BeansWrapper.getDefaultInstance();
        TemplateHashModel staticModels = wrapper.getStaticModels();

        List<Class<?>> annotatedClasses = RClassPath.getAnnotatedClasses("org.n3r.freemarker", FreemarkerFunctionsTag.class);
        for (Class<?> clazz : annotatedClasses) {
            FreemarkerFunctionsTag freemarkerFunctionsTag = clazz.getAnnotation(FreemarkerFunctionsTag.class);
            String namespace = freemarkerFunctionsTag.value();
            try {
                TemplateHashModel model = (TemplateHashModel) staticModels.get(clazz.getName());
                config.setSharedVariable(namespace, model);
            } catch (TemplateModelException e) {
                throw new RuntimeException(e);
            }
        }

        config.setTemplateLoader(stringLoader);
    }

}
