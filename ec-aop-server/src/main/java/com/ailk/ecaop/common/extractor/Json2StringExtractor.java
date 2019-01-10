package com.ailk.ecaop.common.extractor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.core.util.ParamsUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.ParameterValueExtractor;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.conf.domain.EcAopMethod;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

/**
 * 此方法默认发起方为UESS
 * osnDuns:0400
 * origDomain:UESS
 * msgReceiver:1000
 * msgSender:0400
 * orderType:01
 * accessType:00
 * testFlag:0
 * @author Steven
 */
@EcRocTag("json2StringNew")
public class Json2StringExtractor implements ParameterValueExtractor, ParamsAppliable {

    private String jsonString;
    private Map<String, String> inMap;
    private final String[] arrayPut = { "operatorId", "city", "district", "channelId", "channelType" };
    private String offset = "0";// 默认不偏移时间

    @Override
    public Object extract(Exchange exchange) {
        Map<String, Object> root = exchange.getIn().getBody(Map.class);
        preCommInfo(root);
        root.putAll(inMap);
        if (!IsEmptyUtils.isEmpty(root.get("operateName"))) {
            exchange.setProperty("bipCode", root.get("serviceName"));
            exchange.setProperty("activityCode", root.get("operateName"));
        }
        else {
            exchange.setProperty("bipCode", root.get("bipCode"));
            exchange.setProperty("activityCode", root.get("activityCode"));
        }
        root.put("transIDO", transIDO(exchange));
        return JSON.toJSONString(root.get(jsonString));
    }

    @Override
    public void applyParams(String[] params) {
        params = params[0].split(",");
        jsonString = ParamsUtils.getStr(params, 0, null);
        int length = params.length;
        if (length > 1) {
            inMap = (Map) JSON.parse(Config.getStr(params[1]));
        }
        if (length > 2) {
            offset = "@50";
        }
    }

    private void preCommInfo(Map root) {
        Map msg = (Map) root.get("msg");
        if ("2".equals(msg.get("opeSysType"))) {
            offset = "@50";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String processTime = sdf.format(new Date());
        root.put("processTime", processTime);
        MapUtils.arrayPutFix("", root, arrayPut);
        root.put("district", "00000000");// 此处先塞8个0,如果msg中下发了,后面会覆盖
        root.put("channelType", "4300000");// 此处先塞4300000,如果msg中下发了,后面会覆盖
        MapUtils.arrayPut(root, msg, arrayPut);
        root.put("cutOffDay", processTime.substring(0, 8));
        // 默认UESS发起,如有特殊情况,可以进行配置
        if (StringUtils.isEmpty((String) root.get("osnDuns"))) {
            root.put("osnDuns", "0400");
        }
        if (StringUtils.isEmpty((String) root.get("province"))) {
            root.put("province", msg.get("province"));
            // 有特殊情况
        }
        if (StringUtils.isEmpty((String) root.get("origDomain"))) {
            root.put("origDomain", "UESS");
        }
        if (StringUtils.isEmpty((String) root.get("msgReceiver"))) {
            root.put("msgReceiver", "1000");
        }
        if (StringUtils.isEmpty((String) root.get("msgSender"))) {
            root.put("msgSender", "0400");
        }
        if (StringUtils.isEmpty((String) root.get("orderType"))) {
            root.put("orderType", "01");
        }
        if (StringUtils.isEmpty((String) root.get("accessType"))) {
            root.put("accessType", "00");
        }
        root.put("testFlag", "0");
        // 白卡割接号卡中心开关,配置在ec-aop.props中 wangmc 20171031
        String simCardSwitch = EcAopConfigLoader.getStr("ecaop.global.param.simcard.province");
        if (!StringUtils.isEmpty((String) root.get("simCardSwitch"))
                && simCardSwitch.contains(String.valueOf(msg.get("province")))) {
            root.put("route", root.get("simCardSwitch"));
            root.remove("simCardSwitch");
        }
        // 如果是大于6位的区县字段,则截取前六位 wangmc 20171110
        String district = (String) root.get("district");
        if (!IsEmptyUtils.isEmpty(district) && district.length() > 6) {
            root.put("district", district.substring(0, 6));
        }
    }

    public static void main(String[] args) {
        String appAllow = "smct,cmck,crck,qcop";
        String[] methods = new String[] { "ecaop.trades.query.comm.cust.check", "ecaop.trades.query.comm.face.check",
                "ecaop.trades.sell.mob.newu.open.app", "ecaop.trades.sell.mob.newu.open.sub",
                "ecaop.trades.sell.mob.comm.carddate.qry", "ecaop.trades.sell.mob.comm.cardres.notify",
                "ecaop.trades.sell.mob.newu.opencarddate.syn" };
        for (String method : methods) {
            String methodCode = EcAopConfigLoader.getStr("ecaop.core.method.map." + method);
            EcAopMethod aopMethod = EcAopConfigLoader.getMethod(method);
            if (!appAllow.contains(methodCode)) {
                System.out.println(method);
            }
        }
    }

    private Object transIDO(Exchange exchange) {
        TransIdFromRedisValueExtractor tfrve = new TransIdFromRedisValueExtractor();
        tfrve.applyParams(new String[] { offset });
        return tfrve.extract(exchange);
    }
}
