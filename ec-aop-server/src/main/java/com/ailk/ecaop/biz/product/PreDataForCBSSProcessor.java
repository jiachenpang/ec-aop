package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

@EcRocTag("preDataForCBSSProcessor")
public class PreDataForCBSSProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = {"ecaop.trades.sccc.cancelPre.paramtersmapping"}; // 预提交

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        ELKAopLogger.logStr("进入合约处理方法，msg的值为： " + msg);
        msg.put("appCode", exchange.getAppCode());
        String provinceCode = (String) msg.get("provinceCode");
        List<Map> activityInfoList = (List<Map>) msg.get("activityInfo");
        if (IsEmptyUtils.isEmpty(activityInfoList)) {
            // 没有传活动 ，直接调预提交
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.mmvc.sUniTrade.template", exchange);
        }
        else {
            Map ext = new HashMap();

            Map tempMsg = new HashMap();
            tempMsg.put("activityInfo", activityInfoList);
            tempMsg.put("eparchyCode", msg.get("eparchyCode"));
            tempMsg.put("methodCode", exchange.getMethodCode());
            tempMsg.put("userId", msg.get("userId"));
            // 处理活动，将活动的节点信息和产品的信息拼装，再调用预提交
            Map actInfoExt = TradeManagerUtils.newPreProductInfo(new ArrayList<Map>(), provinceCode, tempMsg);
            // 将处理变更中的参数放入msg中。
            msg.putAll(tempMsg);
            // 拼装合约产品信息中资费属性信息
            preTradeItemAndTradeSubItem(actInfoExt, msg);

            ELKAopLogger.logStr("test001活动处理后的参数为：" + actInfoExt);
            // 将活动的处理节点合并到产品变更的节点中。
            ext = dealProductInfo(msg, actInfoExt);
            ELKAopLogger.logStr("test002套餐变更和活动拼装后的信息：" + ext);

            msg.put("ext", ext);
            body.putAll(msg);
            exchange.getIn().setBody(body);

            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
            lan.xml2Json("ecaop.trades.mmvc.sUniTrade.template", exchange);

        }
        // 处理返回
        dealReturn(exchange, msg);
    }

    /**
     * 
     *处理返回
     * @param exchange
     * @param msg
     */
    private void dealReturn(Exchange exchange, Map msg) {
        Map out = exchange.getOut().getBody(Map.class);
        List<Map> rspInfo = (List) out.get("rspInfo");
        if (null == rspInfo || rspInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "cBSS统一预提交未返回应答信息");
        }

        Map realOut = new HashMap();
        realOut.put("bssOrderId", rspInfo.get(0).get("bssOrderId"));
        realOut.put("provOrderId", rspInfo.get(0).get("provOrderId"));
        Integer totalFee = 0;
        List feeList = new ArrayList();
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                // 费用计算
                for (Map provinceOrder : provinceOrderInfo) {
                    totalFee = totalFee + Integer.valueOf(provinceOrder.get("totalFee").toString());
                    List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                        continue;
                    }
                    feeList.addAll(dealFee(preFeeInfoRsp));
                }

            }
        }
        if (!IsEmptyUtils.isEmpty(feeList)) {
            realOut.put("feeInfo", feeList);
            // 记录trade表 正式提交的时候需要IS_REMOTE为3修改payTag 为1
            msg.put("ordersId", rspInfo.get(0).get("provOrderId"));
            insert2CBSSTradeChangePro(msg);
        }
        realOut.put("totalFee", totalFee + "");
        String startTime = (String) msg.get("magsStartDate");
        String endTime = (String) msg.get("magsEndDate");
        if (!IsEmptyUtils.isEmpty(startTime)) {
            realOut.put("startTime", startTime);
        }
        if (!IsEmptyUtils.isEmpty(endTime)) {
            realOut.put("endTime", endTime);
        }
        exchange.getOut().setBody(realOut);
    }

    /**
     * msg中包含province,SUBSCRIBE_ID
     * @param msg
     */
    public void insert2CBSSTradeChangePro(Map msg) {
        try {
            LanOpenApp4GDao dao = new LanOpenApp4GDao();
            Map tradeParam = new HashMap();
            tradeParam.put("SUBSCRIBE_ID", msg.get("ordersId"));
            tradeParam.put("SUBSCRIBE_STATE", "0");
            tradeParam.put("NEXT_DEAL_TAG", "9");
            tradeParam.put("TRADE_TYPE_CODE", "120");
            tradeParam.put("OUT_ORDER_ID", msg.get("ordersId"));
            tradeParam.put("PROVINCE_CODE", "00" + msg.get("province"));
            tradeParam.put("SUBSYS_CODE", "MALL");// 先随便给个值吧
            tradeParam.put("IS_REMOTE", "3");// 1:成卡、2：白卡、3：北六正式提交接口时会根据该字段判断是否将payTag字段设定为1
            tradeParam.put("NET_TYPE_CODE", "50");
            tradeParam.put("CANCEL_TAG", "0");
            tradeParam.put("BUSI_SPECIAL_FLAG", "0");
            if (null == msg.get("eModeCode") || "".equals(msg.get("eModeCode"))) {
                tradeParam.put("IN_MODE_CODE", "E");
            }
            else {
                tradeParam.put("IN_MODE_CODE", msg.get("eModeCode"));
            }
            tradeParam.put("SOP_STATE", "0");
            tradeParam.put("PRODUCT_ID", msg.get("mProductId"));
            tradeParam.put("BRAND_CODE", msg.get("mBrandCode"));
            tradeParam.put("USER_ID", msg.get("userId"));
            tradeParam.put("ACCT_ID", msg.get("acctId"));
            tradeParam.put("CUST_ID", msg.get("custId"));
            tradeParam.put("ACCEPT_DATE", GetDateUtils.getDate());
            tradeParam.put("TRADE_STAFF_ID", msg.get("operatorId"));
            tradeParam.put("TRADE_DEPART_ID", msg.get("channelId"));
            // 数据库中该字段为非空,23转4接口规范中区县为非必传,不传时插表会报错,增加默认值处理 by wangmc see 20170510版本技术文档
            tradeParam.put("TRADE_CITY_CODE",
                    IsEmptyUtils.isEmpty(msg.get("district")) ? "000000" : msg.get("district"));
            tradeParam.put("CITY_CODE", msg.get("district"));
            tradeParam.put("TRADE_EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("EPARCHY_CODE", msg.get("eparchyCode"));
            tradeParam.put("SERIAL_NUMBER", msg.get("serialNumber"));
            tradeParam.put("OPER_FEE", "0");
            tradeParam.put("FOREGIFT", "0");
            tradeParam.put("ADVANCE_PAY", "0");
            tradeParam.put("OLCOM_TAG", "0");
            tradeParam.put("REMARK", msg.get("remark"));
            tradeParam.put("RESOURCE_CODE", msg.get("resourcesCode"));
            tradeParam.put("ACTIVITY_TYPE", msg.get("ACTIVITY_TYPE"));
            dao.insertTrade(tradeParam);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "预提交完成后记录订单信息失败" + e.getMessage());
        }
    }

    private List<Map> dealFee(List<Map> feeList) {
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode") + "");// feeTypeCode
            retFee.put("feeCategory", fee.get("feeMode") + "");// feeMode
            retFee.put("feeDes", fee.get("feeTypeName") + "");// feeTypeName
            if (null != fee.get("maxDerateFee"))
            {
                retFee.put("maxRelief", fee.get("maxDerateFee"));// 非必返maxDerateFee
            }
            retFee.put("origFee", fee.get("fee"));// fee
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    /**
     * 
     *将活动的处理节点合并到产品变更的节点中。
     * @param msg
     * @param actInfoExt
     * @return
     */
    private Map dealProductInfo(Map msg, Map actInfoExt) {

        ELKAopLogger.logStr("test003套餐变更和活动拼装时的信息：" + "msg:" + msg + " ,actInfoExt: " + actInfoExt);
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> tradeItemList = new ArrayList<Map>();
        List<Map> tradeSubItemList = new ArrayList<Map>();

        Map prodExt = (Map) msg.get("ext");
        productTypeList.addAll(preGetListUtil(actInfoExt, prodExt, "tradeProductType"));
        productList.addAll(preGetListUtil(actInfoExt, prodExt, "tradeProduct"));
        discntList.addAll(preGetListUtil(actInfoExt, prodExt, "tradeDiscnt"));
        svcList.addAll(preGetListUtil(actInfoExt, prodExt, "tradeSvc"));
        tradeItemList.addAll(preGetListUtil(actInfoExt, prodExt, "tradeItem"));
        tradeSubItemList.addAll(preGetListUtil(actInfoExt, prodExt, "tradeSubItem"));

        prodExt.put("tradeProductType", preDataUtil(productTypeList));
        prodExt.put("tradeProduct", preDataUtil(productList));
        prodExt.put("tradeDiscnt", preDataUtil(discntList));
        prodExt.put("tradeSvc", preDataUtil(svcList));
        prodExt.put("tradeItem", preDataUtil(tradeItemList));
        prodExt.put("tradeSubItem", preDataUtil(tradeSubItemList));

        return prodExt;
    }

    /**
     * 
     *拼装合约产品信息中资费属性信息
     * @param actInfoExt
     * @param msg
     */
    private void preTradeItemAndTradeSubItem(Map actInfoExt, Map msg) {
        List<Map> tradeSubItemList = new ArrayList<Map>();
        List<Map> tradeItemList = new ArrayList<Map>();

        String tradeId = (String) msg.get("ordersId");
        String magsStartDate = (String) msg.get("magsStartDate");
        String magsEndDate = (String) msg.get("magsEndDate");
        // 只有续约时才下发这个属性
        if ("isRenew".equals(msg.get("isRenew"))) {
            tradeItemList.add(lan.createTradeItemTime1("B_SYNC_PAYTAG", "Y", magsStartDate, magsEndDate));
        }

        tradeSubItemList.add(lan.createTradeSubItem3(tradeId, "", tradeId));
        if ("ecsb".equals(msg.get("appCode"))) {
            tradeItemList.add(lan.createAttrInfoNoTime("E_IN_MODE", "B"));
        }
        actInfoExt.put("tradeItem", MapUtils.asMap("item", tradeItemList));
        actInfoExt.put("tradeSubItem", MapUtils.asMap("item", tradeSubItemList));
    }

    private List preGetListUtil(Map actInfoExt, Map prodExt, String keyList) {
        List<Map> dataList = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(actInfoExt) && actInfoExt.containsKey(keyList)) {
            dataList = (List<Map>) ((Map) actInfoExt.get(keyList)).get("item");
        }
        if (!IsEmptyUtils.isEmpty(prodExt) && prodExt.containsKey(keyList)) {
            dataList.addAll((List<Map>) ((Map) prodExt.get(keyList)).get("item"));
        }
        return dataList;
    }

    private Map preDataUtil(List<Map> dataList) {
        Map dataMap = new HashMap();
        dataMap.put("item", dataList);
        return dataMap;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }
}
