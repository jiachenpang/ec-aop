package com.ailk.ecaop.common.extractor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.CertTypeChangeUtils;

@EcRocTag("certTypeChange")
public class CertTypeChangeProcess extends BaseAopProcessor implements ParamsAppliable {

    String in, sysCode;

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        String[] str = in.split(".");
        for (int i = 0; i < str.length; i++) {
        }
    }

    @Override
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

    public static void main(String[] args) {
        String s = "msg.province";

    }
}
