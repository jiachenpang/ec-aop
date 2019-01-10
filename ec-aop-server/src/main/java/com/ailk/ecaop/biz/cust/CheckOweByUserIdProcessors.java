package com.ailk.ecaop.biz.cust;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.QryCommInfoThread;
import com.alibaba.fastjson.JSON;

/**
 * 此方法是通过USERID获取用户欠费标识，当前仅用于通过证件号码到CBSS进行客户资料校验的场景
 * CBSS侧代码已经进行改造，在正常返回客户资料的同时，也会在备用字段返回USERID和SERIALNUMBER信息
 * USERID和SERIALNUMBER一一对应，保证返回给上游系统的信息正确
 * 由于一个证件可以存在多个用户，此方法采用并发调用的方式进行处理，以减少交互时间
 * RSRV_NUM1--往月欠费
 * RSRV_NUM2--实时话费
 * RSRV_NUM3--实时结余
 * INTFFEE01--当期欠费
 * INTFFEE02--逾期欠费
 * 
 * @author Steven
 */
@EcRocTag("checkOweByUserId")
public class CheckOweByUserIdProcessors extends BaseAopProcessor {

    static ExecutorService pool = Executors.newFixedThreadPool(12);

    @Override
    public void process(Exchange exchange) throws Exception {
        if (200 != Integer.valueOf(exchange.getProperty(Exchange.HTTP_STATUSCODE).toString())) {
            return;
        }
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if (!"13".equals(msg.get("province"))) {
            return;
        }
        if (!"2".equals(msg.get("opeSysType"))) {
            return;
        }
        // 只有使用证件查询客户资料时,才会调用欠费校验接口
        if (null == msg.get("certNum") || "".equals(msg.get("certNum"))) {
            return;
        }
        Map out = JSON.parseObject(exchange.getOut().getBody(String.class));
        List<Map> outPara = (List<Map>) out.get("para");
        if (null == outPara || 0 == outPara.size()) {
            return;
        }
        String remark = getValueFromPara(outPara, "REMARK");
        if (null == remark || "".equals(remark)) {
            out.remove("para");
            exchange.getOut().setBody(out);
            return;
        }
        String[] userID = remark.split("\\|");
        String[] number = getValueFromPara(outPara, "number").split("\\|");
        String[] copyArray = { "operatorId", "province", "city", "district", "channelId", "channelType" };
        int callCount = userID.length;
        Exchange[] exchangeList = new Exchange[callCount];
        Future[] futures = new Future[callCount];
        Callable[] callables = new Callable[callCount];
        for (int i = 0; i < callCount; i++) {
            List<Map> para = new ArrayList<Map>();
            para.add(LanUtils.createPara("LCU_NAME", "QCS_QUERYOWEFEEFORDESTROYUSER"));
            para.add(LanUtils.createPara("ID", userID[i]));
            para.add(LanUtils.createPara("ID_TYPE", "1"));
            Map oweMap = MapUtils.asMap("serviceProvideDomain", "0", "qrySysType", "0", "para", para);
            MapUtils.arrayPut(oweMap, msg, copyArray);
            exchangeList[i] = ExchangeUtils.ofCopy(exchange, oweMap);
        }
        for (int i = 0; i < callCount; i++) {
            callables[i] = new QryCommInfoThread(exchangeList[i]);
            futures[i] = pool.submit(callables[i]);
        }
        // 统一进行取值操作,防止get()的阻塞操作延迟返回
        ArrayList<Map> result = new ArrayList<Map>();
        for (int i = 0; i < callCount; i++) {
            result.add((Map) futures[i].get());
        }
        List<Map> arrearageMess = new ArrayList<Map>();
        for (int i = 0; i < callCount; i++) {
            Map para = (Map) result.get(i).get("para");
            if (null == para || para.isEmpty()) {
                continue;
            }
            List<Map> itemList = (ArrayList<Map>) para.get("item");
            if (null == itemList || 0 == itemList.size()) {
                continue;
            }
            for (Map item : itemList) {
                if (!"INTFFEE02".equals(item.get("paraId"))) {
                    continue;
                }
                if ("0".equals(item.get("paraValue")) || item.get("paraValue").toString().startsWith("-")) {
                    continue;
                }
                arrearageMess.add(MapUtils.asMap("serialNumber", number[i], "arrearageFee", item.get("paraValue")));
            }
        }
        Map outMap = JSON.parseObject(exchange.getOut().getBody(String.class));
        if (0 != arrearageMess.size()) {
            outMap.put("arrearageFlag", "1");
            outMap.put("arrearageMess", arrearageMess);
            outMap.remove("para");
        }
        exchange.getOut().setBody(outMap);
    }

    private String getValueFromPara(List<Map> paraList, String key) {
        for (Map para : paraList) {
            if (key.equals(para.get("paraId"))) {
                return (String) para.get("paraValue");
            }
        }
        return "";
    }
}
