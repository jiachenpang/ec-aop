package com.ailk.ecaop.biz.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.util.MapUtils;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("EcAopQuery")
public class EcAopQuery extends BaseAopProcessor {

    // 获取redis里的内容
    private static String GET_REDIS_CONTENT = "GET_REDIS_CONTENT";

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = new HashMap();
        Map retMap = new HashMap();
        List<Map> retList = new ArrayList<Map>();
        try {
            boolean isString = body.get("msg") instanceof String;
            if (isString) {
                msg = JSON.parseObject((String) body.get("msg"));
            }
            else {
                msg = (Map) body.get("msg");
            }
            List<Map> dealParams = (List<Map>) msg.get("dealParas");
            if (IsEmptyUtils.isEmpty(dealParams)) {
                return;
            }
            for (Map param : dealParams) {
                // 操作类型
                String dealType = String.valueOf(param.get("dealType"));
                if (GET_REDIS_CONTENT.equals(dealType)) {
                    retList.add(getRedisContent(String.valueOf(param.get("dealKey"))));
                }
            }
            retMap.put("code", "0000");
            retMap.put("detail", "success");
            retMap.put("data", retList);
        }
        catch (Exception e) {
            e.printStackTrace();
            retMap.put("code", "9999");
            retMap.put("detail", "error");
            retMap.put("data", retList);
        }

        Message out = new DefaultMessage();
        out.setBody(retMap);
        exchange.setOut(out);
    }

    /**
     * 获取redis里的内容
     * @param dealKey
     * @return
     */
    private Map getRedisContent(String dealKey) {
        return MapUtils.asMap(dealKey, EcAopConfigLoader.getStr(dealKey));
    }

    public static void main(String[] args) throws Exception {
        // List<Map> params = new ArrayList<Map>();
        // params.add(MapUtils.asMap("dealType", GET_REDIS_CONTENT, "dealKey", "ecaop.core.app.map.wmctest.sub"));
        // Exchange exchange = new DefaultExchange();
        // Message in = new DefaultMessage();
        // in.setBody(MapUtils.asMap("msg", MapUtils.asMap("dealParas", params)));
        // exchange.setIn(in);
        // new EcAopQuery().process(exchange);
        // System.out.println(exchange.getOut().getBody());
        //
        // EcAopMethod method = EcAopConfigLoader.getMethod("ecaop.trades.query.ecaop.everything.qry");
        // System.out.println(method);

        String[] methods = new String[] { "ecaop.trades.query.comm.cust.mcheck", "ecaop.serv.curt.custinfo.create",
                "ecaop.trades.query.comm.simpsnres.qry", "ecaop.trades.query.comm.simpsnres.chg",
                "ecaop.trades.query.resi.term.chg", "ecaop.trades.sell.mob.newu.open.app",
                "ecaop.trades.sell.mob.newu.open.sub", "ecaop.trades.sell.mob.comm.carddate.qry",
                "ecaop.trades.sell.mob.comm.cardres.notify", "ecaop.trades.sell.mob.newu.opencarddate.syn",
                "ecaop.trades.query.comm.snres.qry", "ecaop.trades.query.comm.snres.chg",
                "ecaop.trades.query.comm.pro.qry", "ecaop.trades.query.comm.acti.qry",
                "ecaop.trades.query.comm.devr.qry", "ecaop.trades.check.developer.check",
                "ecaop.trades.query.comm.staff.activity.qry", "ecaop.trades.query.comm.cert.check",
                "ecaop.trades.sell.mob.cbss.order.act", "ecaop.trades.serv.curt.cbssso.cancle.sub",
                "ecaop.trades.query.resi.snres.qry", "ecaop.trades.query.comm.cust.check",
                "ecaop.trades.sell.brd.sinp.address.check", "ecaop.trades.query.comm.brdband.check",
                "ecaop.trades.sell.brd.sinp.open.app", "ecaop.trades.sell.iptv.sinp.open.app",
                "ecaop.trades.sell.brd.sinp.open.sub", "ecaop.trades.sell.brdcomm.cannel.sub",
                "ecaop.trades.sell.brdcomm.cannelfee.sub", "ecaop.trades.sell.fix.sinp.open.app",
                "ecaop.trades.sell.fix.sinp.open.sub", "ecaop.trades.serv.curt.actfee.rev",
                "ecaop.trades.serv.payment.fee.sub", "ecaop.trades.sell.brd.yearly.pay.check",
                "ecaop.trades.sell.brd.yearly.pay.sub", "ecaop.trades.serv.grant.fee.sub",
                "ecaop.trades.serv.curt.pay.cannel", "ecaop.trades.serv.payment.fixfee.sub",
                "ecaop.trades.query.comm.simpcust.check", "ecaop.trades.serv.curt.custinfo.mod",
                "ecaop.trades.query.comm.face.check", "ecaop.trades.query.comm.face.decrypt",
                "ecaop.trades.query.comm.balance.qry", "ecaop.trades.query.comm.discnt.qry",
                "ecaop.trades.query.comm.user.spthreepart.check", "ecaop.trades.sell.mob.oldu.product.chg",
                "ecaop.trades.query.comm.realfee.qry" };
        String allowMethods = "";
        int methodNum = 0;
        int MAX_LINE_COUNT = 120;
        for (String method : methods) {
            methodNum++;
            allowMethods += EcAopConfigLoader.getStr("ecaop.core.method.map." + method) + ",";
        }
        int length = allowMethods.length();
        int index = 0;
        if (length > MAX_LINE_COUNT - 50) {
            index = allowMethods.substring(0, MAX_LINE_COUNT - 50).lastIndexOf(",");
            System.out.println(allowMethods.substring(0, index + 1) + "\\");
            allowMethods = allowMethods.substring(index + 1, length);
            length -= index + 1;
        }
        while (length > MAX_LINE_COUNT) {
            index = allowMethods.substring(0, MAX_LINE_COUNT).lastIndexOf(",");
            System.out.println(allowMethods.substring(0, index + 1) + "\\");
            allowMethods = allowMethods.substring(index + 1, length);
            length -= index + 1;
        }
        System.out.println(allowMethods);
        System.out.println("接口数量:" + methods.length + "code数量:" + methodNum);
    }
}
