package com.ailk.ecaop.biz.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.util.MapUtils;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("quryCbssOrders")
@SuppressWarnings({ "unchecked", "rawtypes" })
public class QueryCbssOrdersProcessor extends BaseAopProcessor {

    // 查询CBSS订单
    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.cbss.oolq.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.OrdForNorthChSer");
        lan.xml2Json("ecaop.cbss.oolq.template", exchange);
        Map outMap = exchange.getOut().getBody(Map.class);
        List<Map> ordiInfo = (List<Map>) outMap.get("ordiInfo");
        if (null == ordiInfo || ordiInfo.size() == 0) {
            return;
        }
        for (Map ord : ordiInfo) {
            List<Map> product = (ArrayList<Map>) ord.get("product");
            if (null != product && 0 != product.size()) {
                for (Map prod : product) {
                    if ("00".equals(prod.get("productMode"))) {
                        prod.put("productMode", "1");
                    }
                    else {
                        prod.put("productMode", "2");
                    }
                }
            }
            List userInfo = new ArrayList();
            Map userMap = new HashMap();
            userMap.put("product", product);
            userMap.put("activityInfo", ord.get("activityInfo"));
            userInfo.add(userMap);
            ord.put("userInfo", userInfo);
            ord.remove("product");
            ord.remove("activityInfo");
            if (null == ord.get("totalFee") || "".equals(ord.get("totalFee"))) {
                ord.put("totalFee", "0");
            }
            //start by Zeng 2017-4-28 RHQ2017022400065-电商手工开户订单查询优化
            List<Map> para = (List<Map>) ord.get("para");
            String value = null;
            String imsiNumber = null;
            if (null != para && para.size() > 0) {
                /*   当cbss返回PARA_ID='tradeState'字段时，AOP需将PARA_VALUE按照以下转换规范返回给商城。
                           0 ：0H状态                                转为               5：0H状态
                           1 ：0Z状态                                                      6：0Z状态
                           2 ：0Y状态                                                      7：0Y状态
                           3 ：0H已撤单状态                                           8：0H已撤单状态
                           4 ：AZ、AR订单作废状态                                9：AZ、AR订单作废状态
                           5 ：其它                                                          4：其它*/

                Map exchangeMap = MapUtils.asMap("0", "5", "1", "6", "2", "7", "3", "8", "4", "9", "5", "4");
                for (Map p : para)
                {
                    if ("IMSI_NUMBER".equals(p.get("paraId"))) {
                        imsiNumber = (String) p.get("paraValue");
                    }
                    if ("tradeState".equals((String) p.get("paraId")))//tradeState
                    {
                        value = (String) exchangeMap.get((String) p.get("paraValue"));
                        if (null == value)
                        {
                            throw new EcAopServerBizException("9999",
                                    "qryTradeToEcs接口返回参数paraId：tradeState的paraValue值【" + p.get("paraValue")
                                            + "】不在范围0-5之内！");
                        }
                        para.remove(p);
                        break;
                    }
                }
                if (null == para || para.size() == 0)
                    ord.remove(para);
                if (null != value)
                    ord.put("orderCode", value);
                //end by Zeng 2017-4-28 RHQ2017022400065-电商手工开户订单查询优化
            }
            ord.put("imsiNumber", imsiNumber);
        }
        outMap.put("ordiInfo", ordiInfo);
        exchange.getOut().setBody(outMap);
    }

}
