package com.ailk.ecaop.common.extractor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;
import org.n3r.ecaop.core.extractor.PropertyGetValueExtractor;

import com.ailk.ecaop.common.utils.CertTypeChangeUtils;

@EcRocTag("certTypeChange")
public class CertTypeChangeExtractor implements ParameterValueExtractor {

    String in, sysCode;

    @Override
    public Object extract(Exchange exchange) {
        PropertyGetValueExtractor get = new PropertyGetValueExtractor();
        get.applyParams(new String[] { in });
        in = (String) get.extract(exchange);
        return certChange(sysCode);
    }

    public void applyParams(String[] params) {
        String[] param = params[0].split(",");
        in = param[0];
        sysCode = param[1];
    }

    private Object certChange(String sysCode) {
        try {
            Class clazz = CertTypeChangeUtils.class;
            Method method = clazz.getMethod("certTypeMall2" + sysCode, String.class);
            return method.invoke(null, in);
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return CertTypeChangeUtils.certTypeMall2Cbss(in);
    }
}
