package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("ChangeCertType")
public class ChangeCertTypeProcessor extends BaseAopProcessor implements
        ParamsAppliable {

    private String methodName;

    static String[] cert_Mall = { "01", "02", "03", "04", "05", "06", "07",
            "08", "09", "10", "11", "12", "13", "14",
            "15", "16", "17", "18", "33", "99", "34", "35" };

    static String[] cert_Fbs = { "12", "01", "13", "05", "13", "13", "06",
            "02", "08", "10", "11", "09", "14", "15",
            "16", "17", "18", "19", "33", "13", "34", "35" };
    static String[] cert_Cbss = { "0", "1", "8", "3", "8", "8", "4", "2", "5",
            "6", "7", "9", "S", "R", "N", "T", "L",
            "8", "D", "8", "F", "G"  };

    @Override
    public void process(Exchange exchange) {
        Map msg = new HashMap();
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        if (null != msg.get("usePsptType")) {
            msg.put("usePsptType", CertTypeChangeUtils.certTypeMall2Cbss(msg
                    .get("usePsptType").toString()));
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);

    }

    public static String certTypeMall2Fbs(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = MagicNumber.CERT_TYPE_CODE_OTHER;
        for (int i = 0; i < cert_Mall.length; i++) {
            if (certType.equals(cert_Mall[i])) {
                index = i;
                break;
            }
        }
        return cert_Fbs[index];
    }

    public static String certTypeMall2Cbss(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = 18;
        for (int i = 0; i < cert_Mall.length; i++) {
            if (certType.equals(cert_Mall[i])) {
                index = i;
                break;
            }
        }
        if ("99".contains(certType)) {
            index = MagicNumber.CERT_TYPE_CODE_OTHER;
        }
        return cert_Cbss[index];
    }

    public static String certTypeCbss2Mall(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = 18;
        for (int i = 0; i < cert_Cbss.length; i++) {
            if (certType.equals(cert_Cbss[i])) {
                index = i;
                break;
            }
        }
        if ("8".contains(certType)) {
            index = MagicNumber.CERT_TYPE_CODE_OTHER;
        }
        return cert_Mall[index];
    }

    public static String certTypeFbs2Mall(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = 18;
        for (int i = 0; i < cert_Fbs.length; i++) {
            if (certType.equals(cert_Fbs[i])) {
                index = i;
                break;
            }
        }
        if ("13".contains(certType)) {
            index = MagicNumber.CERT_TYPE_CODE_OTHER;
        }
        return cert_Mall[index];
    }

    @Override
    public void applyParams(String[] params) {
        methodName = params[0];
    }

}
