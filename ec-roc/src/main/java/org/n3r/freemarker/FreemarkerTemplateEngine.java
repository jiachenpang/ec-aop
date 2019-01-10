package org.n3r.freemarker;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import freemarker.cache.StringTemplateLoader;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.*;
import org.apache.commons.io.IOUtils;
import org.n3r.core.collection.RMap;
import org.n3r.core.lang.RClassPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class FreemarkerTemplateEngine {
    private static final Logger logger = LoggerFactory.getLogger(FreemarkerTemplateEngine.class);
    private static Configuration config = new Configuration();
    private static StringTemplateLoader stringLoader = new StringTemplateLoader();

    static {
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
                logger.error("load freemarker functions " + namespace + " error!", e);
                throw new RuntimeException(e);
            }
        }

        config.setTemplateLoader(stringLoader);
    }

    private static final LoadingCache<TemplateBean, Optional<Template>> templates = CacheBuilder.newBuilder().build(
            new CacheLoader<TemplateBean,  Optional<Template>>() {
                @Override
                public  Optional<Template> load(TemplateBean key) {
                    stringLoader.putTemplate(key.getName(), key.getTemplateSource());
                    try {
                        return Optional.of(config.getTemplate(key.getName()));
                    } catch (IOException e) {
                        logger.error("parse template " + key.getTemplateSource() + " failed " , e);
                    }
                    return Optional.absent();
                }
            });

    public static Template putTemplate(String templateName, String templateSource) {
        TemplateBean templateBean = new TemplateBean(templateName, templateSource);
        Optional<Template> templateOptional = templates.getUnchecked(templateBean);
        if (templateOptional.isPresent() ) return templateOptional.get();

        throw new RuntimeException(templateSource + " can not be parsed!");
    }

    public static String process(String templateName) throws Exception {
        return process(RMap.newHashMap(), templateName);
    }

    public static String process(Template template) throws Exception {
        return process(RMap.newHashMap(), template);
    }

    public static String process(Object root, String templateName) {
        Optional<Template> templateOptional = templates.getUnchecked(new TemplateBean(templateName));
        if (templateOptional.isPresent() ) return process(root, templateOptional.get());

        throw new RuntimeException(templateName + " is not found!");
    }

    public static String process(Object root, Template template) {
        StringWriter writer = new StringWriter();
        try {
            template.process(root, writer);
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

}
