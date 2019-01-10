package com.ailk.ecaop.biz.sub.gdjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.processor.Xml2JsonMappingProcessor;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;

public class OpenApply4GProcessor {

    private static String MAIN_PRODUCT = "1";
    private static String MIXMAIN_PRODUCT = "0";
    private static String MIXADD_PRODUCT = "1";
    private static String ADD_PRODUCT = "2";
    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    private final Logger log = LoggerFactory.getLogger(OpenApply4GProcessor.class);

    public Map openApp4GProcess(Exchange exchange, ParametersMappingProcessor[] pmp) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map msgcontainer = new HashMap();

        Object apptx = body.get("apptx");

        msgcontainer.putAll(msg);

        Map<String, String> appendMap = new HashMap<String, String>();
        // 生成cb订单号
        Map tradeMap = new HashMap();
        MapUtils.arrayPut(tradeMap, msg, MagicNumber.COPYARRAY);
        String orderid = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "seq_trade_id", 1).get(0);
        msg.put("tradeId", orderid);
        Map phoneTemplate = (Map) msg.get("phoneTemplate");
        String serialNumber = (String) phoneTemplate.get("serialNumber");
        String mainNumberTag = (String) phoneTemplate.get("mainNumberTag");
        String METHODCODE = (String) msg.get("METHODCODE");
        Map niceinfo = (Map) phoneTemplate.get("niceNumberInfo");
        List<Map> activityInfo = (List<Map>) phoneTemplate.get("activityInfo");
        Map customerInfo = (Map) phoneTemplate.get("phoneCustInfo");
        Map mixTemplate = (Map) msg.get("mixTemplate");
        String mainCardTag = (String) phoneTemplate.get("mainCardTag");
        msg.put("addType", mixTemplate.get("addType"));
        if (StringUtils.isEmpty(mainCardTag)) {
            mainCardTag = "1";// 非加入主卡 0加入主卡
            msg.put("mainCardTag", mainCardTag);
        }
        else {
            msg.put("mainCardTag", "0");
        }
        String groupFlag = (String) phoneTemplate.get("groupFlag");
        msg.put("groupFlag", groupFlag);
        msg.put("phoneCBSSGroupId", phoneTemplate.get("cBSSGroupId"));
        // 收入归集集团处理
        if ("1".equals(groupFlag)) {// FIXME 收入集团归集
            Map jtcpMap = new HashMap();
            jtcpMap.put("province", "00" + msg.get("province"));
            jtcpMap.put("groupId", phoneTemplate.get("groupId"));
            jtcpMap.put("operatorId", msg.get("operatorId"));
            jtcpMap.put("city", ChangeCodeUtils.changeEparchy(msg));
            jtcpMap.put("district", msg.get("district"));
            jtcpMap.put("channelId", msg.get("channelId"));
            jtcpMap.put("channelType", msg.get("channelType"));
            msg = dealJtcp(exchange, jtcpMap, msg);
        }
        // 调用ESS终端查询接口
        List<Map> resourcesInfo = (List<Map>) phoneTemplate.get("resourcesInfo");

        String custType = (String) (customerInfo.get("custType"));
        String certNum = (String) (customerInfo.get("certNum"));
        String certType = (String) (customerInfo.get("certType"));
        String customerName = (String) (customerInfo.get("customerName"));
        String eModeCode = (String) (msg.get("eModeCode"));
        if (eModeCode == null || "".equals(eModeCode)) {
            eModeCode = "E";
        }

        String[] copyArray = { "checkType", "contactPerson", "contactPhone", "contactAddress", "certType", "certNum",
                "certExpireDate", "certAdress" };
        MapUtils.arrayPut(msg, customerInfo, copyArray);
        msg.put("operType", "1");
        msg.put("serialNumber", serialNumber);

        String acceptDate = GetDateUtils.getDate();

        // 发展人信息
        Map phoneRecomInfo = (Map) phoneTemplate.get("phoneRecomInfo");
        if (null != phoneRecomInfo && phoneRecomInfo.size() > 0) {
            msg.putAll(phoneRecomInfo);
        }

        List<Map> acctInfo = (List<Map>) (((Map) msg.get("mixTemplate")).get("acctInfo"));
        if (null != acctInfo && acctInfo.size() > 0) {
            // 账户付费方式
            String accountPayType = (String) (acctInfo.get(0).get("accountPayType"));
            msg.put("accountPayType", accountPayType);
        }

        // 判断用户是否为老用户
        Boolean isExistCust = false;
        // Boolean isExistAcct = false;

        msgcontainer.putAll(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // String newCustId = GetSeqUtil.getSeqFromCb(exchange, "SEQ_CUST_ID");
        // if (StringUtils.isEmpty(newCustId)) {
        // newCustId = "20150918180300";
        // }

        msg.put("certCode", certNum);
        msg.put("operateType", "1");
        msg.put("checkType", "0");
        msg.put("opeSysType", MagicNumber.SYS_TYPE_CBSS);
        msg.put("serType", "1");
        msg.put("certType", certType);
        msgcontainer.putAll(msg);

        LanUtils lan = new LanUtils();
        Object custId = customerInfo.get("custId");
        if ("1".equals(custType) && (null == custId || "".equals(custId))) {
            throw new EcAopServerBizException("0001", "客户验证异常:老客户ID为空");
        }

        msg.put("custType", custType);
        msg.put("custId", "1".equals(custType) ? custId : (String) msg.get("custId"));
        msg.put("custName", customerName);

        msgcontainer.putAll(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        String payRelId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "SEQ_PAYRELA_ID", 1).get(0);
        msgcontainer.putAll(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        // String acctId = GetSeqUtil.getSeqFromCb(exchange, "SEQ_ACCT_ID");
        msgcontainer.putAll(msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        String userId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "SEQ_USER_ID", 1).get(0);
        String purchaseItemId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, tradeMap),
                "SEQ_ITEM_ID", 1).get(0);

        msg.put("userId", userId);
        msg.put("purchaseItemId", purchaseItemId);
        // msg.put("acctId", acctId);
        msg.put("payRelId", payRelId);

        Map inMap = new HashMap();
        inMap.put("date", GetDateUtils.getDate());
        // 查询cycId

        List<Map> cycIdresult = dao.querycycId(inMap);
        String cycId = (String) (cycIdresult.get(0).get("CYCLE_ID"));
        msg.put("cycId", cycId);
        // 转换地市编码
        Map param = new HashMap();
        param.put("province", (msg.get("province")));
        param.put("city", (msg.get("city")));
        String provinceCode = "00" + (String) (msg.get("province"));
        msg.put("provinceCode", provinceCode);
        param.put("provinceCode", provinceCode);
        List<Map> eparchyCoderesult = dao.queryEparchyCode(param);
        String eparchyCode = (String) (eparchyCoderesult.get(0).get("PARAM_CODE"));
        msg.put("eparchyCode", eparchyCode);
        // 查询员工信息
        String tradeStaffId = (String) (msg.get("operatorId"));
        msg.put("tradeStaffId", tradeStaffId);
        msg.put("checkMode", "1");

        msgcontainer.putAll(msg);

        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[2], exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
        Xml2JsonMappingProcessor proc = new Xml2JsonMappingProcessor();
        proc.applyParams(new String[] { "ecaop.template.3g.staffcheck" });
        proc.process(exchange);
        Map retStaffinfo = exchange.getOut().getBody(Map.class);
        String departId = (String) (retStaffinfo.get("departId"));
        String cityCode = (String) (retStaffinfo.get("district"));
        msg.put("departId", departId);
        // msg.put("district", district);

        msgcontainer.putAll(msg);
        List<Map> productInfo = (List<Map>) ((Map) msgcontainer.get("phoneTemplate")).get("phoneProduct");

        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();

        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();

        String mProductId = "";
        String resourceFee = "";
        // 处理活动
        // 终端费用,现在规范中没有，需要加进来
        Object isOwnerPhone = "0";

        List<Map> userInfoList = new ArrayList<Map>();
        Map userInfo = new HashMap();
        userInfo.put("activityInfo", activityInfo);
        userInfo.put("product", productInfo);
        userInfo.put("bipType", "1");// 默认号卡类业务，如不传，N25productInfo里，userInfo.get()空指针
        userInfoList.add(userInfo);
        msgcontainer.put("userInfo", userInfoList);
        msgcontainer.put("resourcesInfo", resourcesInfo);
        // 取融合产品下的首月付费模式类型
        String firstMonBillMode = (String) (((List<Map>) ((Map) msgcontainer.get("mixTemplate")).get("productInfo"))
                .get(0).get("firstMonBillMode"));
        msgcontainer.put("firstMonBillMode", firstMonBillMode);

        msg.put("apptx", apptx);
        getProductInfo(msgcontainer);
        productTypeList = (List<Map>) msgcontainer.get("productTypeList");
        productList = (List<Map>) msgcontainer.get("productList");
        discntList = (List<Map>) msgcontainer.get("discntList");
        svcList = (List<Map>) msgcontainer.get("svcList");
        spList = (List<Map>) msgcontainer.get("spList");
        List<Map> subItemList = (List<Map>) msgcontainer.get("subItemList");
        if (IsEmptyUtils.isEmpty(subItemList)) {
            subItemList = new ArrayList<Map>();
        }
        resourceFee = (String) msgcontainer.get("resourceFee");
        mProductId = (String) msgcontainer.get("mProductId");
        isOwnerPhone = msgcontainer.get("isOwnerPhone");
        // 拼装base节点报文
        // 拼装tradeRes节点报文

        // 拼装trade_item和trade_sub_item节点报文
        appendMap.put("CITY_CODE", (String) msg.get("district"));
        appendMap.put("localNetCode", eparchyCode);// 暂无
        appendMap.put("USER_PASSWD", serialNumber.substring(serialNumber.length() - 6));
        appendMap.put("serialNumber", serialNumber);
        appendMap.put("INIT_PASSWD", serialNumber.substring(serialNumber.length() - 6));
        appendMap.put("USER_TYPE_CODE", "0");
        String nbrSrc = getNumberSrc(provinceCode, serialNumber);
        appendMap.put("nbrSrc", nbrSrc); // nbrSrc
        appendMap.put("tradeId", (String) phoneTemplate.get("ordersId")); // nbrSrc
        appendMap.put("NUMBER_TYPE", serialNumber.substring(0, 3)); // NUMBER_TYPE
        appendMap.put("MAIN_PRODUCT_ID", mProductId); // 主产品
        appendMap.put("PROVINCE_CODE", provinceCode);
        appendMap.put("SUBSYS_CODE", "MALL");
        appendMap.put("initpwd", "1");
        appendMap.put("groupFlag", "0");
        appendMap.put("custgroup", "0");
        appendMap.put("TRADE_TYPE_CODE", ("0010").substring(1));

        appendMap.put("REL_COMP_PROD_ID", (String) msg.get("mixVmproductId"));// 虚拟产品id
        appendMap.put("COMP_DEAL_STATE", "1");

        List<Map> itemList = new ArrayList<Map>();

        // FIXME by wangmc 20180630 北分沃易售修改,TRADE_ITEM成员订单的该表中需要有属性：COMP_DEAL_STATE 0：新装 1:纳入，2：拆分，3：拆机 , 4:变更
        if ("smno".equals(exchange.getMethodCode()) && "mabc|bjaop".contains(exchange.getAppCode())) {
            appendMap.put("COMP_DEAL_STATE", "0");
            // 北分要下这个值,但是appendMap里面没有配,单独处理
            subItemList.add(lan.createTradeSubItemE("USER_TYPE_CODE", "0", userId));
            // 下发是否新建账户标识 0-新建账户,1-继承老账户
            itemList.add(LanUtils.createTradeItem("EXISTS_ACCT", msg.get("isNewAcct")));
            // 下发是否新建客户标识 0-新建客户,1-老客户
            itemList.add(LanUtils.createTradeItem("EXISTS_CUST", custType));
            // 20180717新修改内容 处理发展人信息
            if (null != phoneRecomInfo && phoneRecomInfo.size() > 0) {
                subItemList
                        .add(lan.createTradeSubItemE("developerStaffId", phoneRecomInfo.get("recomPersonId"), userId));
                subItemList
                        .add(lan.createTradeSubItemE("developDepartId", phoneRecomInfo.get("recomDepartId"), userId));
                subItemList.add(lan.createTradeSubItemE("developerStaffName", phoneRecomInfo.get("recomPersonName"),
                        userId));
            }
        }

        // 是否是主号码
        // if("0".equals(mainNumberTag)){
        appendMap.put("ROLE_CODE_B", "1");
        // }
        // else
        // {
        // appendMap.put("ROLE_CODE_B", "2");
        // // custId=newCustId;
        // // msg.put("custId", newCustId);
        // }

        appendMap.put("STANDARD_KIND_CODE", "1");
        appendMap.put("OPER_CODE", "1");
        appendMap.put("PRODUCT_TYPE_CODE", (String) msg.get("mProductTypeCode"));// z主产品
        appendMap.put("NET_TYPE_CODE", "50");
        appendMap.put("IS_SAME_CUST", (String) msg.get("custId"));// custId
        appendMap.put("IS_CHANGE_NET", "1");
        appendMap.put("ALONE_TCS_COMP_INDEX", String.valueOf(msg.get("aloneTcsCompIndex")));
        if ("0".equals(msg.get("isNumCentre"))) {// 号卡中心标志
            appendMap.put("NUMERICAL_SELECTION", "2");
        }
        else {
            appendMap.put("NUMERICAL_SELECTION", "1");
        }

        if ("apsb".equals(body.get("appCode"))) {
            appendMap.put("E_IN_MODE", "A");
        }
        if ("mnsb".equals(body.get("appCode"))) {
            appendMap.put("E_IN_MODE", "M");
        }
        if (null != phoneRecomInfo && phoneRecomInfo.size() > 0) {
            appendMap.put("recomPerName", (String) phoneRecomInfo.get("recomPersonName"));
            appendMap.put("DEVELOP_STAFF_ID", (String) phoneRecomInfo.get("recomPersonId"));
            appendMap.put("DEVELOP_DEPART_ID", (String) phoneRecomInfo.get("recomDepartId"));
        }
        // 行销标示处理
        if ("1".equals(msg.get("markingTag"))) {
            appendMap.put("MARKING_APP", "1");
        }

        // 是否扣款
        if (StringUtils.isNotEmpty(eModeCode)) {
            appendMap.put("E_IN_MODE", eModeCode);
        }
        msg.put("eModeCode", eModeCode);
        msg.put("appendMap", appendMap);

        msg.put("discntList", discntList);
        msg.put("svcList", svcList);
        msg.put("spList", spList);
        msg.put("productTypeList", productTypeList);
        msg.put("productList", productList);
        List<Map> tradeFeeItemList = new ArrayList<Map>();
        if (StringUtils.isNotEmpty(resourceFee) && !"0".equals(resourceFee)) {
            // 传入单位为厘
            int fee = Integer.parseInt(resourceFee);
            Map tradeFeeItem = new HashMap();
            tradeFeeItem.put("feeMode", "0");
            tradeFeeItem.put("feeTypeCode", "4310");
            tradeFeeItem.put("oldFee", String.valueOf(fee));
            tradeFeeItem.put("fee", String.valueOf(fee));
            tradeFeeItem.put("chargeSourceCode", "1");
            tradeFeeItem.put("apprStaffId", tradeStaffId);
            tradeFeeItem.put("calculateId", orderid);
            tradeFeeItem.put("calculateDate", acceptDate);
            tradeFeeItem.put("staffId", tradeStaffId);
            tradeFeeItem.put("calculateTag", "0");
            tradeFeeItem.put("payTag", "0");
            tradeFeeItem.put("xDatatype", "NULL");
            tradeFeeItemList.add(tradeFeeItem);
        }

        // RHA2017102300039-关于支撑通过标记控制代理商办理业务时是否扣周转金的需求//移网新装周转金
        if ("0".equals(msg.get("deductionTag")) && "smno".equals(exchange.getMethodCode())) {
            itemList.add(LanUtils.createTradeItem("FEE_TYPE", "1"));
        }
        // RHA2018010500038-融合业务后激活需求
        // 压单标志为1且为写卡前置接口才生效
        if ("1".equals(msg.get("delayTag")) && "smno".equals(exchange.getMethodCode())) {
            itemList.add(LanUtils.createTradeItem("E_DELAY_TIME_CEL", "1"));
        }
        // 订单是否同步到电子渠道激活
        if ("1".equals(msg.get("isAfterActivation")) && "smno".equals(exchange.getMethodCode())) {
            itemList.add(LanUtils.createTradeItem("IS_AFTER_ACTIVATION", "1"));
        }
        String niceItemId = TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID");
        if ("1".equals(isOwnerPhone)) {
            Map item = new HashMap();
            item.put("attrTypeCode", "6");
            item.put("attrCode", "isOwnerPhone");
            item.put("itemId", msg.get("userId"));
            item.put("attrValue", "1");
            item.put("xDatatype", "NULL");
            item.put("startDate", acceptDate);
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            subItemList.add(item);
        }
        // 处理靓号信息
        if (niceinfo != null && niceinfo.size() > 0) {

            String staffId = tradeStaffId;
            String tradeId = orderid;
            String feeTypeCode = "101117"; // 普号预存话费
            String endDate = "2050-12-31 23:59:59";
            String months = (String) niceinfo.get("protocolTime");
            // 靓号有效期最多120个月
            /* if (null == months || "".equals(months) || "00000".equals(months) || "9999".equals(months)) {
             * months = "120";
             * } else if (months.compareTo("120") > 0) {
             * months = "120";
             * } */
            try {
                months = null == months || "".equals(months) || "null".equalsIgnoreCase(months)
                        || "00000".equals(months) || "9999".equals(months) ? "120" : months;
                months = Integer.parseInt(months) > 120 ? "120" : months;
            }
            catch (Exception e) {
                throw new EcAopServerBizException("9999", "协议时长protocolTime校验时转换为Int异常：" + e.getMessage());
            }
            endDate = GetDateUtils.getSpecifyDateTime(1, 2, GetDateUtils.getMonthLastDay(), Integer.valueOf(months));
            // 2017-6-1 RHQ2017051200006 - 移动靓号减免规则优化需求 by Zeng -start 2/2
            /**
             * 靓号规则条件修改：
             * a）靓号等级，即classId 为1-6时；
             * b）靓号协议期，即protocolTime为0时；
             * 以上两个条件同时满足时，给cb下发的逻辑做以下修改
             * （1）资费属性值，leaseLength 协议期（在网时长）落0；
             * （2）88888888资费的开始时间落：次月1日 0：00：00；失效时间落：次月1日 0：10：00；yyyyMMddHHmmss
             * （3）88888888资费属性值的开始时间落：次月1日 0：00：00；失效时间落：次月1日 0：10：00；yyyyMMddHHmmss
             */
            if ("1|2|3|4|5|6".contains((String) niceinfo.get("classId")) && "0".equals(niceinfo.get("protocolTime"))) {
                months = "0";
                endDate = GetDateUtils.getNextMonthFirstDayFormat().substring(0, 10) + "1000";

            }
            // 2017-6-1 RHQ2017051200006 - 移动靓号减免规则优化需求 by Zeng -end 2/2

            // 如果是靓号，费用项编码为123456
            if (StringUtils.isNotEmpty((String) niceinfo.get("classId")) && !"9".equals(niceinfo.get("classId"))) {
                feeTypeCode = "123457";// 20161119 靓号资费修改
            }
            // 号码台账费用
            String numFee = (String) niceinfo.get("advancePay");
            if (StringUtils.isNotEmpty(numFee) && !"0".equals(numFee)) {
                int fee = Integer.parseInt(numFee);// 传入单位为分
                Map tradeFeeItem1 = new HashMap();
                tradeFeeItem1.put("feeMode", "2");
                tradeFeeItem1.put("feeTypeCode", feeTypeCode);
                tradeFeeItem1.put("oldFee", String.valueOf(fee));
                tradeFeeItem1.put("fee", String.valueOf(fee));
                tradeFeeItem1.put("chargeSourceCode", "1");
                tradeFeeItem1.put("apprStaffId", staffId);
                tradeFeeItem1.put("calculateId", tradeId);
                tradeFeeItem1.put("calculateDate", GetDateUtils.getNextMonthFirstDayFormat());
                tradeFeeItem1.put("staffId", staffId);
                tradeFeeItem1.put("calculateTag", "0");
                tradeFeeItem1.put("payTag", "0");
                tradeFeeItem1.put("xDatatype", "NULL");
                tradeFeeItemList.add(tradeFeeItem1);
            }

            //
            if ("123457".equals(feeTypeCode)) { // 如果是靓号，需要传88888888 资费

                Map discntItem = new HashMap();
                discntItem.put("id", userId);
                discntItem.put("idType", "1");
                discntItem.put("productId", "-1");
                discntItem.put("packageId", "-1");
                discntItem.put("discntCode", "88888888");
                discntItem.put("specTag", "0");
                discntItem.put("relationTypeCode", "");
                discntItem.put("itemId", niceItemId);
                discntItem.put("modifyTag", "0");
                discntItem.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                discntItem.put("endDate", endDate);
                discntItem.put("userIdA", "-1");
                discntItem.put("xDatatype", "NULL");
                msg.put("numberDiscnt", discntItem);

                // if (StringUtils.isNotEmpty(numFee) && !"0".equals(numFee)) { // 如果靓号有预存
                // Map disItem = new HashMap();
                // disItem.put("id", userId);
                // disItem.put("idType", "1");
                // disItem.put("productId", "-1");
                // disItem.put("packageId", "-1");
                // disItem.put("discntCode", "88888881");
                // disItem.put("specTag", "0");
                // disItem.put("relationTypeCode", "");
                // disItem.put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                // disItem.put("modifyTag", "0");
                // disItem.put("startDate", acceptDate);
                // disItem.put("endDate", endDate);
                // disItem.put("userIdA", "-1");
                // disItem.put("xDatatype", "NULL");
                // msg.put("numberDis", disItem);
                // }
                // 低消
                if (StringUtils.isNotEmpty((String) niceinfo.get("lowFee")) && !"0".equals(niceinfo.get("lowFee"))) {
                    Map item = new HashMap();
                    item.put("attrTypeCode", "3");
                    item.put("attrCode", "lowCost");
                    item.put("itemId", niceItemId);
                    item.put("attrValue", Integer.parseInt((String) niceinfo.get("lowFee")));
                    item.put("xDatatype", "NULL");
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    item.put("endDate", endDate);
                    subItemList.add(item);
                }
                // 在网时长
                if (StringUtils.isNotEmpty(months) && !months.startsWith("00")) {
                    Map item = new HashMap();
                    item.put("attrTypeCode", "3");
                    item.put("attrCode", "leaseLength");
                    item.put("itemId", niceItemId);
                    item.put("attrValue", months);
                    item.put("xDatatype", "NULL");
                    item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                    item.put("endDate", endDate);
                    subItemList.add(item);
                }
                // 靓号号码标识,值为1（默认值） --标识是新靓号
                Map item = new HashMap();
                item.put("attrTypeCode", "3");
                item.put("attrCode", "NewNumTag");
                item.put("itemId", niceItemId);
                item.put("attrValue", "1");
                item.put("xDatatype", "NULL");
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("endDate", endDate);
                subItemList.add(item);
            }
        }
        // start==2017-2-14 支持成卡改造之2 共4
        Map phoneTemplateMap = (Map) msg.get("phoneTemplate");
        // 卡类型 0：白卡 1：成卡 默认0：白卡
        String card_type = (String) phoneTemplateMap.get("card_type");
        if ("1".equals(card_type)) {
            // List<Map> simCardNoList = (List<Map>) msg.get("simCardNo");
            // Map simCardNo = (Map) msg.get("simCardNo");
            // if (null != msg.get("simCardNo") && !simCardNoList.isEmpty())
            // {
            putCardInfoIntoMsg(msg, exchange, serialNumber, pmp);
            subItemList.add(new LanUtils().createTradeSubItem("simCardNo",
                    (String) ((Map) msg.get("cardInfo")).get("simCardNo"), (String) msg.get("userId")));
            Map cardInfo = (Map) msg.get("cardInfo");
            // 成卡
            appendMap.put("NOT_REMOTE", "1");
            /* if (EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains(
             * (String) msg.get("province")))
             * {
             * appendMap.put("NEW_CARD_DATA", (String) cardInfo.get("KI"));
             * } */
            /* }
             * else
             * {
             * throw new EcAopServerBizException("9999", "办理新装成卡业务,未下发卡信息节点");
             * } */
        }
        else {
            // end==2017-2-14 支持成卡改造2 共4
            // 20160228写卡前置时字段
            // 20160623配合江苏改simCardNo字段，cgd
            JSONArray simCardNo = (JSONArray) msg.get("simCardNo");
            String cardType = (String) msg.get("cardType");
            // List<Map> simCardNo = (List<Map>) msg.get("simCardNo");
            if (null != msg.get("simCardNo") && !simCardNo.isEmpty()) {
                // Map simCardNoMap = simCardNo.get(0);
                List simCardNolist = JSONArray.toJavaObject(simCardNo, List.class);
                Map simCardNoMap = (Map) simCardNolist.get(0);
                String simId = (String) simCardNoMap.get("simId");
                if (simId.length() == 20) {// 如果白卡是20位就截取19位
                    simId = simId.substring(0, 19);
                }
                itemList.add(LanUtils.createTradeItem("SIM_CARD", simId));
                itemList.add(LanUtils.createTradeItem("CARD_DATA", simCardNoMap.get("cardData")));
                itemList.add(LanUtils.createTradeItem("CARD_TYPE", simCardNoMap.get("cardType")));
                itemList.add(LanUtils.createTradeItem("PROCID", simCardNoMap.get("cardDataProcId")));
                itemList.add(new LanUtils().createTradeSubItem("bosscardprice", "0", (String) msg.get("userId")));
                itemList.add(new LanUtils().createTradeSubItem("simCardNo", (String) simCardNoMap.get("cardData"),
                        (String) msg.get("userId")));
                if ("0".equals(cardType)) {
                    itemList.add(LanUtils.createTradeItem("RemoteTag", "1"));
                }
            }
        }

        Map appParam = new HashMap();
        appParam.put("TRADE_TYPE_CODE", "10");
        appParam.put("NET_TYPE_CODE", "ZZ");// 默认ZZ
        appParam.put("BRAND_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PRODUCT_ID", "-1");// 默认-1
        appParam.put("EPARCHY_CODE", "ZZZZ");// 默认ZZZZ
        appParam.put("PROVINCE_CODE", "ZZZZ");// 默认ZZZZ
        // param.put("SUBSYS_CODE", baseObject.get("subSysCode"));//SQL语句中没这个条件

        List<Map> appendMapResult = dao.queryAppendParam(appParam);
        for (int n = 0; n < appendMapResult.size(); n++) {
            String attrTypeCode = String.valueOf(appendMapResult.get(n).get("ATTR_TYPE_CODE"));
            String attrValue = String.valueOf(appendMapResult.get(n).get("ATTR_VALUE"));
            String attrcode = String.valueOf(appendMapResult.get(n).get("ATTR_CODE"));
            for (String key : appendMap.keySet()) {
                if (attrValue.endsWith(key) && "I".equals(attrTypeCode)) {
                    Map item = new HashMap();
                    item.put("xDatatype", "NULL");
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    itemList.add(item);
                }
                if (attrValue.endsWith(key) && "0".equals(attrTypeCode)) {// attrTypeCode为0 使用userId

                    Map item = new HashMap();
                    item.put("attrTypeCode", attrTypeCode);
                    item.put("xDatatype", "NULL");
                    item.put("itemId", msg.get("userId"));
                    item.put("attrValue", appendMap.get(key));
                    item.put("attrCode", attrcode);
                    subItemList.add(item);
                }
            }
        }

        // if (null != msg.get("simCardNo")) {
        // boolean isList = msg.get("simCardNo") instanceof List;
        //
        //
        // else {
        // simCardNoMap = (Map) msg.get("simCardNo");
        // }
        //
        // }

        msg.put("itemList", itemList);
        msg.put("subItemList", subItemList);
        msg.put("tradeFeeItemList", tradeFeeItemList);
        msg.put("tradePost", preTradePostItem(msg));
        msgcontainer.putAll(msg);
        Map trade = preTradeData(msgcontainer);
        msg.put("ordersId", msg.get("subscribeId"));
        msg.put("operTypeCode", "0");
        msg.put("serviceClassCode", "00CP");
        msg.putAll(trade);
        msgcontainer.putAll(msg);
        return msgcontainer;

    }

    private Map dealJtcp(Exchange exchange, Map jtcpMap, Map msg) {
        Exchange jtcpExchange = ExchangeUtils.ofCopy(exchange, jtcpMap);
        LanUtils lan = new LanUtils();
        try {
            lan.preData("ecaop.trades.sell.mob.jtcp.ParametersMapping", jtcpExchange);
            CallEngine.wsCall(jtcpExchange, "ecaop.comm.conf.url.cbss.services.OrdForAopthSer");// 地址
            lan.xml2Json("ecaop.trades.sell.mob.jtcp.template", jtcpExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "集团信息查询失败" + e.getMessage());
        }
        Map retMap = jtcpExchange.getOut().getBody(Map.class);
        List<Map> userList = (List<Map>) retMap.get("useList");
        if (null == userList || userList.isEmpty()) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        String serialNumberA = (String) userList.get(0).get("serialNumber");
        String userIdA = (String) userList.get(0).get("userId");
        // 若外围传了cBSSGroupId 则取用groupId查询出来的结果中与cBSSGroupId一致的信息,否则默认取第一条 create by zhaok date 2018/5/14
        String cBSSGroupId = (String) msg.get("phoneCBSSGroupId");
        if (!IsEmptyUtils.isEmpty(cBSSGroupId)) {
            for (Map item : userList) {
                if (cBSSGroupId.equals(item.get("userId"))) {
                    userIdA = cBSSGroupId;
                    serialNumberA = (String) item.get("serialNumber");
                }
            }
        }
        if (StringUtils.isEmpty(serialNumberA) || StringUtils.isEmpty(userIdA)) {
            throw new EcAopServerBizException("9999", "未获取到CBSS集团客户下的用户信息");
        }
        msg.put("userIdA", userIdA);
        msg.put("serialNumberA", serialNumberA);
        return msg;
    }

    /**
     * 准备邮寄信息节点节点 TRADE_POST
     * @return
     */
    private List<Map> preTradePostItem(Map msg) {
        List<Map> tradePost = new ArrayList<Map>();
        List<Map> postInfo = (List<Map>) msg.get("postInfo");
        if (IsEmptyUtils.isEmpty(postInfo)) {
            return tradePost;
        }
        Map idMap = MapUtils.asMap("0", msg.get("custId"), "1", msg.get("userId"), "2", msg.get("acctId"));
        for (Map post : postInfo) {
            if (IsEmptyUtils.isEmpty(post.get("postIdType"))) {
                throw new EcAopServerBizException("9999", "邮寄信息节点未下发标识类型,请确认");
            }
            post.put("xDatatype", "NULL");
            post.put("idType", post.get("postIdType"));
            // 根据外围传入标识类型获取标识id 0-客户,1-用户,2-帐户
            post.put("id", idMap.get("idType"));
            if (IsEmptyUtils.isEmpty(post.get("startDate"))) {
                post.put("startDate", GetDateUtils.getDate());
            }
            if (IsEmptyUtils.isEmpty(post.get("endDate"))) {
                post.put("endDate", "20501231235959");
            }
            tradePost.add(MapUtils.asMap("item", post));
        }
        return tradePost;
    }

    private Map preTradeData(Map msg) throws Exception {
        Map trade = new HashMap();
        trade.put("base", preBaseData(msg));
        trade.put("ext", preExtDataforItem(msg));
        return trade;
    }

    // 拼装base节点
    private Map preBaseData(Map msg) {
        Map base = new HashMap();
        base.put("subscribeId", msg.get("subscribeId"));
        base.put("tradeId", msg.get("tradeId"));// 此处不一样
        base.put("acceptDate", GetDateUtils.getDate());
        base.put("startDate", GetDateUtils.getDate());
        base.put("endDate", "20501231122359");
        base.put("nextDealTag", "Z");
        base.put("olcomTag", "0");
        base.put("inModeCode", msg.get("eModeCode"));
        base.put("tradeTypeCode", "0010");
        base.put("productId", msg.get("mProductId"));
        base.put("brandCode", msg.get("mBrandCode"));
        base.put("userId", msg.get("userId"));
        base.put("custId", msg.get("custId"));
        base.put("usecustId", msg.get("custId"));
        base.put("acctId", msg.get("acctId"));
        base.put("userDiffCode", "00");
        base.put("netTypeCode", "50");
        base.put("serinalNamber", msg.get("serialNumber"));
        base.put("custName", msg.get("custName"));
        base.put("termIp", "0.0.0.0");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("cityCode", msg.get("district"));
        base.put("execTime", GetDateUtils.getDate());
        base.put("operFee", "0");
        base.put("foregift", "0");
        base.put("advancePay", "0");
        base.put("feeStaffId", "");
        base.put("cancelTag", "0");
        base.put("checktypeCode", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        if (null != msg.get("remark")) {
            base.put("remark", msg.get("remark"));
        }
        else {
            base.put("remark", "");
        }
        base.put("feeState", "0");
        base.put("chkTag", "0");
        base.put("contact", msg.get("contactPerson"));
        base.put("contactPhone", msg.get("contactPhone"));
        base.put("contactAddress", msg.get("contactAddress"));
        return base;
    }

    // 拼装ext节点
    /**
     * @param msg
     * @return
     */
    private Map preExtDataforItem(Map msg) throws Exception {
        Map ext = new HashMap();
        if ("0".equals(msg.get("isNewAcct"))) {
            ext.put("tradeAcct", preAcctData(msg));
        }
        if ("0".equals(msg.get("custType"))) {
            ext.put("tradeCustomer", preCustomerData(msg));
            ext.put("tradeCustPerson", preCustPerData(msg));
        }
        log.info("qqqqqqqqqqqqqqqqqqqqqqqq3" + msg.get("mBrandCode"));
        ext.put("tradeUser", preUserData(msg));
        ext.put("tradeProductType", preProductTpyeListData(msg));
        ext.put("tradeProduct", preProductData(msg));
        ext.put("tradeDiscnt", preDiscntData(msg));
        ext.put("tradeSvc", preTradeSvcData(msg));
        ext.put("tradeSp", preTradeSpData(msg));
        // start==2017-2-14 支持成卡改造3 共4 当成卡时调preTradeResDataForReadyCard()
        ext.put("tradeRes", "1".equals(msg.get("IS_REMOTE")) ? preTradeResDataForReadyCard(msg) : preTradeResData(msg));
        // end==2017-2-14 支持成卡改造3 共4
        ext.put("tradePayrelation", preTradePayRelData(msg));
        ext.put("tradeElement", preTradeElementData(msg));
        ext.put("tradePurchase", preTradePurchase(msg));
        ext.put("tradefeeSub", preTradeFeeSub(msg));
        ext.put("tradeItem", preTradeItem(msg));
        ext.put("tradeSubItem", preTradeSubItem(msg));
        // ext.put("tradeSubItem", preTradeSubItemData(msg));
        if ("1".equals(getPayModeCode(msg)) || "3".equals(getPayModeCode(msg))) {
            ext.put("tradeAcctConsign", preTradeAcctConsign(msg));
        }
        // 冰激凌融合,天王卡融合支持移网加入主卡 by wangmc 20180827
        if ("0".equals(msg.get("mainCardTag")) && "3|5|6".contains((String) msg.get("addType"))) {
            ext.put("tradeRelation", preDataForTradeRelation(msg));
        }
        if ("1".equals(msg.get("groupFlag"))) {
            ext.put("tradeRelation", preJtcpForTradeRelation(msg, ext));
        }
        return ext;
    }

    private Map preJtcpForTradeRelation(Map msg, Map ext) {
        List<Map> relaItem = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(ext.get("tradeRelation"))) {
            relaItem = (List<Map>) ((Map) ext.get("tradeRelation")).get("item");
        }
        Map item = new HashMap();
        Map tradeRelation = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("relationAttr", "");
        item.put("relationTypeCode", "2222");
        item.put("idA", msg.get("userIdA"));
        item.put("idB", msg.get("userId"));
        item.put("roleCodeA", "0");
        item.put("roleCodeB", "0");
        item.put("orderno", "");
        item.put("shortCode", "");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "2050-12-31 23:59:59");
        item.put("modifyTag", "0");
        item.put("remark", "");
        item.put("serialNumberA", msg.get("serialNumberA"));
        item.put("serialNumberB", msg.get("serialNumber"));
        item.put("itemId", TradeManagerUtils.getSequence((String) msg.get("eparchyCode"), "SEQ_ITEM_ID"));
        relaItem.add(item);
        tradeRelation.put("item", relaItem);
        return tradeRelation;
    }

    // 拼装TRADE_RELATION
    private Map preDataForTradeRelation(Map msg) {
        try {
            Map phoneTemplate = (Map) msg.get("phoneTemplate");
            String serialNumber = (String) phoneTemplate.get("serialNumber");
            Map tradeRelation = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            // 主卡开户流程准备参数
            item.put("idA", msg.get("userId"));
            item.put("serialNumberA", serialNumber);
            item.put("roleCodeB", "1");
            item.put("relationAttr", "0");
            item.put("xDatatype", "NULL");
            item.put("relationTypeCode", "ZF");
            item.put("serialNumberB", serialNumber);
            item.put("idB", msg.get("userId"));
            item.put("roleCodeA", "0");
            item.put("modifyTag", "0");
            item.put("startDate", GetDateUtils.getDate());
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            itemList.add(item);
            tradeRelation.put("item", itemList);
            return tradeRelation;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_RELATION节点报错" + e.getMessage());
        }

    }

    private Map preTradeSubItem(Map msg) {
        List<Map> subItemList = (List<Map>) msg.get("subItemList");
        if (IsEmptyUtils.isEmpty(subItemList)) {
            subItemList = new ArrayList<Map>();
        }
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            subItemList.add(MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", "MAIN_CARD_TAG",
                    "attrValue", "0", "itemId", msg.get("userId"), "startDate", GetDateUtils.getDate(), "endDate",
                    MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
        }
        // KI值下发2
        Map cardInfo = (Map) msg.get("cardInfo");
        if (null != cardInfo
                && !cardInfo.isEmpty()
                && EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains(
                        (String) msg.get("province"))) {
            subItemList.add(MapUtils.asMap("xDatatype", "NULL", "attrTypeCode", "0", "attrCode", "NEW_CARD_DATA",
                    "attrValue", cardInfo.get("KI"), "itemId", msg.get("userId"), "startDate", GetDateUtils.getDate(),
                    "endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME));
        }
        Map tradeSubItem = new HashMap();
        tradeSubItem.put("item", subItemList);
        return tradeSubItem;
    }

    private Map preTradeItem(Map msg) {
        List<Map> itemList = (List<Map>) msg.get("itemList");
        if (IsEmptyUtils.isEmpty(itemList)) {
            itemList = new ArrayList<Map>();
        }
        if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType"))) {
            itemList.add(MapUtils.asMap("xDatatype", "NULL", "attrCode", "MAIN_CARD_TAG", "attrValue", "0"));
        }
        Map tradeItem = new HashMap();
        tradeItem.put("item", itemList);
        return tradeItem;
    }

    private Map preAcctData(Map msg) {
        Map item = new HashMap();
        Map tradeAcct = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("acctId", msg.get("acctId"));
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", msg.get("district"));
        item.put("custId", msg.get("custId"));
        item.put("payName", msg.get("custName"));
        item.put("payModeCode", getPayModeCode(msg));
        item.put("scoreValue", "0");
        item.put("creditClassId", "0");
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("creditControlId", "0");
        item.put("removeTag", "0");
        item.put("debutyUserId", msg.get("userId"));
        item.put("debutyCode", msg.get("serialNumber"));
        item.put("removeDate", "20501231235959");
        item.put("acctPasswd", "123456");
        item.put("contractNo", "0");
        item.put("openDate", GetDateUtils.getDate());
        tradeAcct.put("item", item);
        return tradeAcct;
    }

    private Map preTradeAcctConsign(Map msg) {
        Map tradeAcctCon = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("acctId", msg.get("acctId"));
        item.put("payModeCode", getPayModeCode(msg));
        item.put("consignMode", "1");
        item.put("assistantTag", "0");
        item.put("bankCode", "");
        item.put("bankAcctNo", "");
        item.put("bankAcctName", "");
        item.put("startCycleId", msg.get("cycId"));
        item.put("endCycleId", "203712");
        tradeAcctCon.put("item", item);
        return tradeAcctCon;

    }

    private Map preCustomerData(Map msg) {
        Map tradeCustomer = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("sustId", msg.get("custId"));
        item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) msg.get("certType")));
        item.put("psptId", msg.get("certNum"));
        item.put("custName", msg.get("custName"));
        item.put("custType", "0");
        item.put("custState", "0");
        item.put("openLimit", "0");
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", msg.get("district"));
        item.put("developDepartId", msg.get("recomDepartId"));
        item.put("inDate", GetDateUtils.getDate());
        item.put("removeTag", "0");
        item.put("rsrvTag1", decodeCheckType4G(msg.get("checkType")));
        tradeCustomer.put("item", item);
        return tradeCustomer;
    }

    private Map preCustPerData(Map msg) {
        Map tradeCustPerson = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("custId", msg.get("custId"));
        item.put("psptTypeCode", CertTypeChangeUtils.certTypeMall2Cbss((String) msg.get("certType")));
        item.put("psptId", msg.get("certNum"));
        item.put("psptEndDate", msg.get("certExpireDate"));
        item.put("psptAddr", msg.get("certAdress"));
        item.put("custName", msg.get("custName"));
        item.put("sex", msg.get("sex"));
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", msg.get("district"));
        item.put("postAddress", msg.get("contactAddress"));
        item.put("phone", msg.get("contactPhone"));
        item.put("contact", msg.get("contactPerson"));
        item.put("contactPhone", msg.get("contactPhone"));
        item.put("removeTag", "0");
        tradeCustPerson.put("item", item);
        return tradeCustPerson;
    }

    private Map preUserData(Map msg) {
        Map tradeUser = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("custId", msg.get("custId"));
        item.put("usecustId", msg.get("custId"));
        item.put("brandCode", msg.get("mBrandCode"));
        item.put("productId", msg.get("mProductId"));
        item.put("eparchyCode", msg.get("eparchyCode"));
        item.put("cityCode", msg.get("district"));
        item.put("userPasswd",
                msg.get("serialNumber").toString().substring(msg.get("serialNumber").toString().length() - 6));
        item.put("userTypeCode", "0");
        item.put("serialNumber", msg.get("serialNumber"));
        item.put("netTypeCode", "50");
        item.put("scoreValue", "0");
        item.put("creditClass", "0");
        item.put("basicCreditValue", "0");
        item.put("creditValue", "0");
        item.put("acctTag", "0");
        item.put("prepayTag", "0");
        item.put("inDate", GetDateUtils.getDate());
        item.put("openDate", GetDateUtils.getDate());
        item.put("openMode", "0");
        item.put("openDepartId", msg.get("departId"));
        item.put("openStaffId", msg.get("tradeStaffId"));
        item.put("inDepartId", msg.get("departId"));
        item.put("inStaffId", msg.get("tradeStaffId"));
        item.put("removeTag", "0");
        item.put("userStateCodeset", "0");
        item.put("userDiffCode", "00");
        item.put("mputeMonthFee", "0");
        item.put("assureDate", GetDateUtils.getDate());
        item.put("developDate", GetDateUtils.getDate());
        item.put("developStaffId", msg.get("recomPersonId"));
        item.put("developDepartId", msg.get("recomDepartId"));
        item.put("productTypeCode", msg.get("tradeUserProductTypeCode"));
        item.put("inNetMode", "0");
        tradeUser.put("item", item);
        return tradeUser;

    }

    public Map preProductTpyeListData(Map msg) {
        Map tradeProductType = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> productTpye = (List<Map>) msg.get("productTypeList");
        // 针对产品变更业务
        if (!"".equals(msg.get("oldProductId")) && null != msg.get("oldProductId")) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", "00");
            item.put("productId", msg.get("oldProductId"));
            item.put("productTypeCode", msg.get("productTypeCode"));
            item.put("modifyTag", "1");
            item.put("startDate", msg.get("startDate"));
            if ("spec|spes|bymc|byms".contains(msg.get("method") + "")) {
                item.put("startDate", msg.get("oldProductStartDate"));
            }

            item.put("endDate", GetDateUtils.getMonthLastDayFormat());
            itemList.add(item);

        }
        for (int i = 0; i < productTpye.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", productTpye.get(i).get("productMode"));
            item.put("productId", productTpye.get(i).get("productId"));
            item.put("productTypeCode", productTpye.get(i).get("productTypeCode"));
            item.put("modifyTag", "0");
            if ("mofc|ofpc|smnp|spec|spes|bymc|byms".contains(msg.get("methodCode") + "")) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                // 如果存费送费有计算好的产品开始时间,则取计算好的开始时间 by wangmc 20171018
                if ("mofc".equals(msg.get("methodCode") + "") && !"00".equals(productTpye.get(i).get("productMode"))
                        && !IsEmptyUtils.isEmpty(productTpye.get(i).get("activityStarTime"))) {
                    item.put("startDate", productTpye.get(i).get("activityStarTime"));
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", productTpye.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("50".equals(productTpye.get(i).get("productMode"))) {
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate", productTpye.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", productTpye.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("01".equals(productTpye.get(i).get("productMode"))) {
                if (null != productTpye.get(i).get("activityStarTime")) {
                    item.put("startDate", productTpye.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != productTpye.get(i).get("activityTime")) {
                    item.put("endDate", productTpye.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
                item.put("endDate", "20501231235959");
            }
            itemList.add(item);
        }
        tradeProductType.put("item", itemList);
        return tradeProductType;

    }

    public Map preProductData(Map msg) {
        Map tradeProduct = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> product = (List<Map>) msg.get("productList");
        // 针对老用户存费送费业务
        if (!IsEmptyUtils.isEmpty(msg.get("oldProductId"))) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", "00");
            item.put("productId", msg.get("oldProductId"));
            item.put("brandCode", msg.get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            item.put("modifyTag", "1");
            item.put("startDate", msg.get("startDate"));
            if ("spec|spes|bymc|byms".contains(msg.get("method") + "")) {
                item.put("startDate", msg.get("oldProductStartDate"));
            }
            item.put("endDate", GetDateUtils.getMonthLastDayFormat());
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }
        for (int i = 0; i < product.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("productMode", product.get(i).get("productMode"));
            item.put("productId", product.get(i).get("productId"));
            item.put("brandCode", product.get(i).get("brandCode"));
            item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            // item.put("itemId", "");
            item.put("modifyTag", "0");
            // 存费送费新产品生效时间下月初
            if ("mofc|ofpc|smnp|spec|spes|bymc|byms".contains(msg.get("methodCode") + "")) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                // 如果存费送费有计算好的产品开始时间,则取计算好的开始时间 by wangmc 20171018
                if ("mofc".equals(msg.get("methodCode") + "") && !"00".equals(product.get(i).get("productMode"))
                        && !IsEmptyUtils.isEmpty(product.get(i).get("activityStarTime"))) {
                    item.put("startDate", product.get(i).get("activityStarTime"));
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", product.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("50".equals(product.get(i).get("productMode"))) {
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", product.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", product.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else if ("01".equals(product.get(i).get("productMode"))) {
                if (null != product.get(i).get("activityStarTime")) {
                    item.put("startDate", product.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != product.get(i).get("activityTime")) {
                    item.put("endDate", product.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
            }
            else {
                item.put("startDate", GetDateUtils.getDate());
                item.put("endDate", "20501231235959");
            }
            item.put("userIdA", MagicNumber.DEFAULT_NO_VALUE);
            itemList.add(item);
        }

        tradeProduct.put("item", itemList);
        return tradeProduct;
    }

    public Map preDiscntData(Map msg) {
        Map tradeDis = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> discnt = (List<Map>) msg.get("discntList");
        List<Map> oldDiscnt = (List<Map>) msg.get("oldProDiscnt");

        for (int i = 0; i < discnt.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("id", msg.get("userId"));
            item.put("idType", "1");
            item.put("userIdA", "-1");
            item.put("productId", discnt.get(i).get("productId"));
            item.put("packageId", discnt.get(i).get("packageId"));
            item.put("discntCode", discnt.get(i).get("discntCode"));
            item.put("specTag", "0");
            item.put("relationTypeCode", "");
            if ("mofc|ofpc".contains(msg.get("methodCode") + "")) {
                String itemid = TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID");
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("itemId", "");
                // 如果存费送费有计算好的产品开始时间,则取计算好的开始时间 by wangmc 20171018
                if ("mofc".equals(msg.get("methodCode") + "") && !"00".equals(discnt.get(i).get("productMode"))
                        && !IsEmptyUtils.isEmpty(discnt.get(i).get("activityStarTime"))) {
                    item.put("startDate", discnt.get(i).get("activityStarTime"));
                }
                if (null != discnt.get(i).get("activityTime")) {
                    item.put("endDate", discnt.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                if ("50".equals(discnt.get(i).get("productMode"))) {
                    if ("mofc".equals(msg.get("methodCode") + "")) {
                        msg.put("discntItemId", itemid);
                        item.put("itemId", itemid);
                    }
                    else {
                        item.put("itemId", msg.get("itemId"));
                    }
                }
                else {// 老用户存费送费产品不是50活动类型的时候，itemId没有下发
                    msg.put("discntItemId", itemid);
                    item.put("itemId", itemid);
                }

            }
            // 应赵伟斌要求 固网纳入的时候修改discnt和svc的itemid要不一样
            // else if ("smnp".contains(msg.get("methodCode") + "")) {
            // item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
            // item.put("itemId", msg.get("itemIdMixDiscnt"));
            // if (null != discnt.get(i).get("activityTime")) {
            // item.put("endDate", discnt.get(i).get("activityTime"));
            // }
            // else {
            // item.put("endDate", "20501231235959");
            // }
            // if ("50".equals(discnt.get(i).get("productMode"))) {
            // item.put("itemId", msg.get("itemId"));
            // }
            // }
            else if ("ipnr".contains(msg.get("iptvCode") + "")) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                if (null != discnt.get(i).get("activityTime")) {
                    item.put("endDate", discnt.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            }
            else {
                if (null != discnt.get(i).get("activityStarTime")) {
                    item.put("startDate", discnt.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != discnt.get(i).get("activityTime")) {
                    item.put("endDate", discnt.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            }
            // item.put("itemId", msg.get("subscribeId"));
            item.put("modifyTag", "0");

            itemList.add(item);
        }
        Map numberDiscnt = (Map) msg.get("numberDiscnt");
        if (null != numberDiscnt && !numberDiscnt.isEmpty()) {
            Map item = new HashMap();
            item.putAll(numberDiscnt);
            itemList.add(item);
        }
        Map numberDis = (Map) msg.get("numberDis");
        if (null != numberDis && !numberDis.isEmpty()) {
            Map item1 = new HashMap();
            item1.putAll(numberDis);
            itemList.add(item1);

        }
        tradeDis.put("item", itemList);
        return tradeDis;
    }

    public Map preTradeSvcData(Map msg) {
        Map svcList = new HashMap();
        List<Map> svc = new ArrayList<Map>();
        svc = (List<Map>) msg.get("svcList");
        List<Map> svList = new ArrayList();
        for (int i = 0; i < svc.size(); i++) {
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            item.put("userId", msg.get("userId"));
            item.put("serviceId", svc.get(i).get("serviceId"));
            item.put("modifyTag", "0");
            if ("mofc|ofpc|smnp|spec|spes|bymc|byms".contains(msg.get("methodCode") + "")) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                item.put("itemId", msg.get("itemId"));
                // 如果存费送费有计算好的产品开始时间,则取计算好的开始时间 by wangmc 20171018
                if ("mofc".equals(msg.get("methodCode") + "")
                        && !IsEmptyUtils.isEmpty(svc.get(i).get("activityStarTime"))) {
                    item.put("startDate", svc.get(i).get("activityStarTime"));
                }
                if (null != svc.get(i).get("activityTime")) {
                    item.put("endDate", svc.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                if ("50".equals(svc.get(i).get("productMode"))) {
                    item.put("itemId", msg.get("itemId"));
                }
            }
            else {
                if (null != svc.get(i).get("activityStarTime")) {
                    item.put("startDate", svc.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != svc.get(i).get("activityTime")) {
                    item.put("endDate", svc.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            }
            item.put("productId", svc.get(i).get("productId"));
            item.put("packageId", svc.get(i).get("packageId"));
            item.put("userIdA", "-1");
            svList.add(item);
        }
        svcList.put("item", svList);
        return svcList;
    }

    public Map preTradeSpData(Map msg) {
        Map tardeSp = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        List<Map> sp = (List<Map>) msg.get("spList");
        for (int i = 0; i < sp.size(); i++) {
            Map item = new HashMap();
            item.put("userId", msg.get("userId"));
            item.put("serialNumber", msg.get("serialNumber"));
            item.put("productId", sp.get(i).get("productId"));
            item.put("packageId", sp.get(i).get("packageId"));
            item.put("partyId", sp.get(i).get("partyId"));
            item.put("spId", sp.get(i).get("spId"));
            item.put("spProductId", sp.get(i).get("spProductId"));
            item.put("firstBuyTime", GetDateUtils.getDate());
            item.put("paySerialNumber", msg.get("serialNumber"));
            item.put("endDate", MagicNumber.CBSS_DEFAULT_EXPIRE_TIME);
            if ("mofc".contains(msg.get("methodCode") + "")) {
                item.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
                // 如果存费送费有计算好的产品开始时间,则取计算好的开始时间 by wangmc 20171018
                if (!IsEmptyUtils.isEmpty(sp.get(i).get("activityStarTime"))) {
                    item.put("startDate", sp.get(i).get("activityStarTime"));
                }
                item.put("itemId", msg.get("itemId"));
            }
            else {
                if (null != sp.get(i).get("activityStarTime")) {
                    item.put("startDate", sp.get(i).get("activityStarTime"));
                }
                else {
                    item.put("startDate", GetDateUtils.getDate());
                }
                if (null != sp.get(i).get("activityTime")) {
                    item.put("endDate", sp.get(i).get("activityTime"));
                }
                else {
                    item.put("endDate", "20501231235959");
                }
                item.put("itemId", TradeManagerUtils.getSequence((String) (msg.get("eparchyCode")), "SEQ_ITEM_ID"));
            }
            item.put("updateTime", GetDateUtils.getDate());
            item.put("remark", "");
            item.put("modifyTag", "0");
            item.put("payUserId", msg.get("userId"));
            item.put("spServiceId", sp.get(i).get("spServiceId"));
            item.put("userIdA", msg.get("userId"));
            item.put("xDatatype", "NULL");
            itemList.add(item);
        }
        tardeSp.put("item", itemList);
        return tardeSp;
    }

    private Map preTradeResData(Map msg) {
        Map tradeRes = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("reTypeCode", "0");
        item.put("resCode", msg.get("serialNumber"));
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "20501231235959");
        itemList.add(item);
        // 写卡前置判断，cgd
        // 辽宁不判断写卡前置，不下发改节点
        if (!"91".equals(msg.get("province"))) {
            if (!"smno".equals(msg.get("METHODCODE"))) {
                Map item1 = new HashMap();
                item1.put("xDatatype", "NULL");
                item1.put("reTypeCode", "1");
                item1.put("resCode", "89860");
                item1.put("modifyTag", "0");
                item1.put("startDate", GetDateUtils.getDate());
                item1.put("endDate", "20501231235959");
                itemList.add(item1);
            }
        }
        JSONArray simCardNoJson = (JSONArray) msg.get("simCardNo");
        if (!IsEmptyUtils.isEmpty(simCardNoJson)) {// 写卡前置时数据
            Map simCardNo = (Map) JSONArray.toJavaObject(simCardNoJson, List.class).get(0);
            Map item2 = new HashMap();
            item2.put("xDatatype", "NULL");
            item2.put("modifyTag", "0");
            item2.put("reTypeCode", "1");
            item2.put("resCode", simCardNo.get("simId"));
            item2.put("resInfo1", simCardNo.get("imsi"));
            item2.put("resInfo2", simCardNo.get("cardType"));
            // item2.put("resInfo3", simCardNo.get("cardData"));
            item2.put("resInfo4", simCardNo.get("capacityTypeCode"));
            item2.put("resInfo5", simCardNo.get("resKindCode"));
            item2.put("resInfo6", simCardNo.get("cardDataProcId"));
            item2.put("startDate", GetDateUtils.getDate());
            item2.put("endDate", "20501231235959");
            itemList.add(item2);
        }
        tradeRes.put("item", itemList);
        return tradeRes;

    }

    private Map preTradePayRelData(Map msg) {
        Map tradePayRel = new HashMap();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("userId", msg.get("userId"));
        item.put("acctId", msg.get("acctId"));
        item.put("payitemCode", "-1");
        item.put("acctPriority", "0");
        item.put("userPriority", "0");
        item.put("bindType", "1");
        item.put("startAcycId", msg.get("cycId"));
        item.put("endAcycId", "203712");
        item.put("defaultTag", "1");
        item.put("limitType", "0");
        item.put("limit", "0");
        item.put("complementTag", "0");
        item.put("addupMonths", "0");
        item.put("addupMethod", "0");
        item.put("payrelationId", msg.get("payRelId"));
        item.put("actTag", "1");
        tradePayRel.put("item", item);
        return tradePayRel;
    }

    private Map preTradeElementData(Map msg) {
        Map elementMap = (Map) msg.get("elementMap");
        Map tradeElement = new HashMap();
        if (elementMap != null && elementMap.size() > 0) {

            Map item = new HashMap();
            item.putAll(elementMap);
            tradeElement.put("item", item);
        }
        return tradeElement;
    }

    private Map preTradePurchase(Map msg) {
        Map tradePurchaseMap = (Map) msg.get("tradePurchase");
        Map tradePurchase = new HashMap();
        if (tradePurchaseMap != null && tradePurchaseMap.size() > 0) {
            Map item = new HashMap();
            item.putAll(tradePurchaseMap);
            tradePurchase.put("item", item);
        }
        return tradePurchase;
    }

    private Map preTradeFeeSub(Map msg) {
        List<Map> tradeFeeItemList = (List<Map>) msg.get("tradeFeeItemList");
        Map tradeFeeSubList = new HashMap();
        List tradeFeeList = new ArrayList();
        for (int i = 0; i < tradeFeeItemList.size(); i++) {
            if (tradeFeeItemList != null && tradeFeeItemList.size() > 0) {
                Map tradeFeeMap = tradeFeeItemList.get(i);
                Map item = new HashMap();
                item.putAll(tradeFeeMap);
                tradeFeeList.add(item);
            }

        }
        tradeFeeSubList.put("item", tradeFeeList);
        return tradeFeeSubList;

    }

    // 获取账户付费方式编码
    private String getPayModeCode(Map msg) {
        LanOpenApp4GDao dao = new LanOpenApp4GDao();
        Map param = new HashMap();
        param.put("EPARCHY_CODE", msg.get("eparchyCode"));
        param.put("PARAM_CODE", msg.get("accountPayType"));
        param.put("PROVINCE_CODE", msg.get("provinceCode"));
        List<Map> payModeCoderesult = dao.queryPayModeCode(param);
        if (payModeCoderesult.size() > 0) {
            return payModeCoderesult.get(0).get("PARA_CODE1").toString();
        }
        return "0";
    }

    // 转换cbcheckType
    public String decodeCheckType4G(Object checkType) {
        if ("01".equals(checkType) || "02".equals(checkType)) {
            return "3";
        }
        return "03".equals(checkType) ? "4" : "2";
    }

    // 遍历trans_item表取得每条属性信息
    public String getValueFromItem(String attrCode, List<Map> productItemInfoResult) {
        String attrValue = "";
        for (int i = 0; i < productItemInfoResult.size(); i++) {
            Map productItemInfo = productItemInfoResult.get(i);
            if ("U".equals(productItemInfo.get("PTYPE"))) {
                if (attrValue == "" && attrCode.equals(productItemInfo.get("ATTR_CODE"))) {
                    attrValue = productItemInfo.get("ATTR_VALUE").toString();
                }
            }
        }
        return attrValue;
    }

    /**
     * 获取蛋疼的NUMBER_SRC CBSS无此字段无法完工
     * nbrsrc 1.总部号码 2.南25省号码 3.北六号码
     * @param provinceCode
     * @param serialNumber
     * @return
     */
    private String getNumberSrc(String provinceCode, String serialNumber) {
        // 1 -> 176号段为 总部号码
        if (serialNumber.startsWith("175") || serialNumber.startsWith("176") || serialNumber.startsWith("185")) {
            return "1";
        }
        // 如果是北六省份号码 NUMBER_SRC 为3
        if ("0011|0076|0017|0018|0097|0091".contains(provinceCode)) {
            return "3";
        }
        return "2";
    }

    /**
     * 资源类型
     * 输出到北六编码 描述
     * 01 3G手机终端
     * 02 2G手机终端
     * 03 固网话终端（有串号）
     * 04 宽带终端（有串号）
     * 05 上网终端(卡)
     * 06 上网终端(本)
     * 07 其它
     * 08 固话终端（无串号）
     * 09 宽带终端（无串号）
     * 13 互联网增值终端(无串码)
     * 14 互联网增值终端
     * 开户申请传入终端类别编码：
     * Iphone：IP
     * 乐phone：LP
     * 智能终端：PP
     * 普通定制终端：01
     * 上网卡：04
     * 上网本：05
     * @param terminalType
     * @return
     */
    private String decodeTerminalType(String terminalType) {
        if ("04".equals(terminalType))
            return "05";
        if ("05".equals(terminalType))
            return "06";
        return "01";
    }

    /**
     * 成卡卡数据查询
     * 支持成卡改造2
     * @param msg
     * @param simCardNoList
     * @param exchange
     * @param serialNumber
     * @return
     */
    private Map qryProvinceCardInfo(Map msg, String simId, Exchange exchange, String serialNumber,
            ParametersMappingProcessor[] pmp) {
        LanUtils lan = new LanUtils();
        Map preDataMap = new HashMap();
        List<Map> cardNumberInfo = new ArrayList<Map>();
        Map cardNumber = new HashMap();
        cardNumber.put("simCardNo", simId);
        cardNumber.put("serialNumber", serialNumber);
        cardNumberInfo.add(cardNumber);
        preDataMap.put("cardNumberInfo", cardNumberInfo);
        preDataMap.put("province", msg.get("province"));
        preDataMap.put("city", msg.get("city"));
        preDataMap.put("channelType", msg.get("channelType"));
        preDataMap.put("channelId", msg.get("channelId"));
        preDataMap.put("operatorId", msg.get("operatorId"));
        preDataMap.put("tradeType", "1");
        Exchange qryCardExchange = ExchangeUtils.ofCopy(exchange, preDataMap);
        try {

            lan.preData(pmp[4], qryCardExchange);
            CallEngine.wsCall(qryCardExchange, "ecaop.comm.conf.url.osn.services.changedCardRequest");
            lan.xml2Json("ecaop.trades.smoca.changedcardReq.Template", qryCardExchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "成卡卡数据查询失败" + e.getMessage());
        }
        Map retMap = qryCardExchange.getOut().getBody(Map.class);
        List<Map> cardNumberInfoList = (List<Map>) retMap.get("cardNumberInfo");
        if (null == cardNumberInfoList || cardNumberInfoList.isEmpty()) {
            throw new EcAopServerBizException("9999", "省份未返回卡信息");
        }
        Map cardInfo = (Map) cardNumberInfoList.get(0).get("cardInfo");
        if (null == cardInfo || cardInfo.isEmpty()) {
            throw new EcAopServerBizException("9999", "省份未返回卡信息");
        }

        return cardInfo;
    }

    /**
     * 从号卡中心获取成卡信息
     * 支持成卡改造2
     * @param msg
     * @param simCardNoList
     * @param exchange
     * @return
     * @throws Exception
     */
    private Map qryCardInfo(Map msg, String simId, Exchange exchange) throws Exception {
        Map preDataMap = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        Map qryCardInfoReq = new HashMap();
        String sysCode = new NumCenterUtils().changeSysCode(exchange);
        qryCardInfoReq.put("STAFF_ID", msg.get("operatorId"));
        qryCardInfoReq.put("PROVINCE_CODE", msg.get("province"));
        qryCardInfoReq.put("CITY_CODE", msg.get("city"));
        qryCardInfoReq.put("DISTRICT_CODE", msg.get("district"));
        qryCardInfoReq.put("CHANNEL_ID", msg.get("channelId"));
        qryCardInfoReq.put("CHANNEL_TYPE", msg.get("channelType"));
        qryCardInfoReq.put("SYS_CODE", sysCode);// 操作系统编码
        qryCardInfoReq.put("CARD_STATUS", "01");// 空闲
        qryCardInfoReq.put("CARD_TYPE", "");
        qryCardInfoReq.put("ICCID_START", simId);
        qryCardInfoReq.put("ICCID_END", simId);
        preDataMap.put("UNI_BSS_BODY", MapUtils.asMap("QRY_CARD_INFO_REQ", qryCardInfoReq));
        Exchange qryExchange = ExchangeUtils.ofCopy(preDataMap, exchange);
        CallEngine.numCenterCall(qryExchange, "ecaop.comm.conf.url.numbercenter.qryCardInfo");
        Map retCardInfo = (Map) JSON.parse(qryExchange.getOut().getBody().toString());
        return retCardInfo;
    }

    /**
     * phoneTemplate下有simCardNo
     * 支持成卡改造2
     */
    private void putCardInfoIntoMsg(Map msg, Exchange exchange, String serialNumber, ParametersMappingProcessor[] pmp)
            throws Exception {
        List<Map> simCardNoList = (List<Map>) msg.get("simCardNo");
        String simId = "";
        if (null != simCardNoList && !simCardNoList.isEmpty()) {
            simId = (String) simCardNoList.get(0).get("simId");
            msg.put("simId", simId);
        }
        if (EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains((String) msg.get("province"))) {// 号卡中心
            Map cardCenterRet = qryCardInfo(msg, simId, exchange);
            if (null == cardCenterRet || cardCenterRet.isEmpty()) {
                throw new EcAopServerBizException("9999", "号卡中心未返回卡信息");
            }
            Map uniBssBody = (Map) cardCenterRet.get("UNI_BSS_BODY");
            Map qryCardInfoRsp = (Map) uniBssBody.get("QRY_CARD_INFO_RSP");
            if (!"0000".equals(qryCardInfoRsp.get("RESP_CODE"))) {
                throw new EcAopServerBizException("9999", qryCardInfoRsp.get("RESP_DESC").toString());
            }
            List<Map> cardInfoList = (List<Map>) qryCardInfoRsp.get("INFO");
            if (null == cardInfoList || cardInfoList.isEmpty()) {
                throw new EcAopServerBizException("9999", "号卡中心卡信息未返回");
            }
            Map cardInfo = cardInfoList.get(0);
            cardInfo.put("simCardNo", cardInfo.get("IMSI"));// ICCID还是IMSI
            cardInfo.put("KI", cardInfo.get("KI"));// 融合下发KI值下发1，zzc，2017-08-24
            msg.put("cardInfo", cardInfo);

        }
        else {// 调省份走原流程
            Map cardInfoRet = qryProvinceCardInfo(msg, simId, exchange, serialNumber, pmp);
            msg.put("cardInfo", cardInfoRet);
        }
        msg.put("IS_REMOTE", "1");// 成卡
    }

    private Map preTradeResDataForReadyCard(Map msg) {
        Map tradeRes = new HashMap();
        List<Map> itemList = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("xDatatype", "NULL");
        item.put("reTypeCode", "0");
        item.put("resCode", msg.get("serialNumber"));
        item.put("modifyTag", "0");
        item.put("startDate", GetDateUtils.getDate());
        item.put("endDate", "20501231235959");
        itemList.add(item);
        String simId = (String) msg.get("simId");
        if (StringUtils.isNotEmpty(simId)) {
            Map cardInfo = (Map) msg.get("cardInfo");
            Map item2 = new HashMap();
            if (EcAopConfigLoader.getStr("ecaop.global.param.card.aop.province").contains((String) msg.get("province"))) {// 走号卡中心

                item2.put("xDatatype", "NULL");
                item2.put("modifyTag", "0");
                item2.put("reTypeCode", "1");
                item2.put("resCode", simId);
                item2.put("resInfo1", cardInfo.get("IMSI"));
                item2.put("resInfo2", "notRemote");
                item2.put("resInfo4", "1000101");
                item2.put("resInfo5", ChangeCodeUtils.changeCardType(cardInfo.get("MATERIAL_CODE")));// 号卡中心卡类型转换
                item2.put("resInfo7", "");
                item2.put("resInfo8", "");// PIN2-PUK2
                item2.put("resInfo6", "1234-12345678");// CB说写死
                item2.put("startDate", GetDateUtils.getDate());
                item2.put("endDate", "20501231235959");
                itemList.add(item2);
            }
            else {// 走原流程
                item2.put("xDatatype", "NULL");
                item2.put("modifyTag", "0");
                item2.put("reTypeCode", "1");
                item2.put("resCode", simId);
                item2.put("resInfo1", cardInfo.get("imsi"));
                item2.put("resInfo2", "notRemote");
                item2.put("resInfo4", "1000101");
                item2.put("resInfo5", cardInfo.get("cardType"));
                item2.put("resInfo7", cardInfo.get("ki"));
                item2.put("resInfo8", cardInfo.get("pin2") + "-" + cardInfo.get("puk2"));
                item2.put("resInfo6", cardInfo.get("pin") + "-" + cardInfo.get("puk"));
                item2.put("startDate", GetDateUtils.getDate());
                item2.put("endDate", "20501231235959");
                itemList.add(item2);
            }
        }
        else {// 插一条RES_TYPE_CODE为1 的数据
            Map item3 = new HashMap();
            item3.put("xDatatype", "NULL");
            item3.put("reTypeCode", "1");
            item3.put("resCode", "89860");
            item3.put("modifyTag", "0");
            item3.put("startDate", GetDateUtils.getDate());
            item3.put("endDate", "20501231235959");
            itemList.add(item3);
        }
        tradeRes.put("item", itemList);
        return tradeRes;
    }

    /**
     * 处理融合的移网成员新装产品
     * @param msg
     */
    public void getProductInfo(Map msg) {
        List<Map> productInfo = new ArrayList<Map>();
        List<Map> activityInfo = new ArrayList<Map>();
        // List<Map> paraList = (List<Map>) msg.get("para");// 处理速率和remark--改为直接从msg中取
        List<Map> userInfo = (List<Map>) msg.get("userInfo");
        if (null != userInfo && !userInfo.isEmpty()) {
            productInfo = (List<Map>) userInfo.get(0).get("product");
            activityInfo = (List<Map>) userInfo.get(0).get("activityInfo");
        }
        List<Map> resourcesInfo = (List<Map>) msg.get("resourcesInfo");
        // 准备预提交EXT节点参数
        List<Map> productTypeList = new ArrayList<Map>();
        List<Map> productList = new ArrayList<Map>();
        List<Map> discntList = new ArrayList<Map>();
        List<Map> svcList = new ArrayList<Map>();
        List<Map> spList = new ArrayList<Map>();
        String provinceCode = "00" + msg.get("province");
        String eparchyCode = (String) msg.get("eparchyCode");
        String monthFirstDay = GetDateUtils.getDate();
        String monthLasttDay = "20501231235959";
        String isFinalCode = "";
        String mProductId = "";
        String activityStartTime = "";
        String activityEndTime = "";
        String mBrandCode = "";
        String mProductTypeCode = "";
        String tradeUserProductTypeCode = "";
        String purFeeTypeCode = "4310";
        String resourcesCode = "";
        String resourceFee = "";
        String isOwnerPhone = "0";
        // String remark = "";
        String isTest = "1";
        // 处理活动信息
        if (null != activityInfo && !activityInfo.isEmpty()) {

            for (int i = 0; i < activityInfo.size(); i++) {
                Map activityMap = activityInfo.get(i);
                List<Map> packageElement = (List<Map>) activityInfo.get(i).get("packageElement");
                if (activityMap.isEmpty()) {
                    continue;
                }
                String actPlanId = (String) activityMap.get("actPlanId");
                if (activityMap.containsKey("resourcesCode")) {
                    resourcesCode = (String) activityMap.get("resourcesCode");
                }
                if (activityMap.containsKey("resourcesFee")) {
                    resourceFee = (String) activityMap.get("resourcesFee");
                }
                if (activityMap.containsKey("isTest")) {
                    isTest = (String) activityMap.get("isTest");
                }
                Map actparam = new HashMap();
                actparam.put("PROVINCE_CODE", provinceCode);
                actparam.put("PRODUCT_MODE", "50");
                actparam.put("PRODUCT_ID", actPlanId);
                actparam.put("EPARCHY_CODE", eparchyCode);
                actparam.put("FIRST_MON_BILL_MODE", null);
                List<Map> actInfoResult = n25Dao.qryDefaultPackageElement(actparam);
                if (actInfoResult == null || actInfoResult.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + actPlanId + "】的产品信息");
                }
                String actProductId = String.valueOf(actInfoResult.get(0).get("PRODUCT_ID"));
                Map productDate = TradeManagerUtils.getEffectTime(actProductId, monthFirstDay, monthLasttDay);
                activityStartTime = (String) productDate.get("monthFirstDay");
                activityEndTime = (String) productDate.get("monthLasttDay");
                String resActivityper = (String) productDate.get("resActivityper");
                for (int j = 0; j < actInfoResult.size(); j++) {
                    // // 针对北分卖场，流量，短信，通话三选一，如果是北分卖场且传了附加产品则不处理该产品包的默认结果.by:cuij 2016-12-06
                    // if (packageElement != null && packageElement.size() > 0) {
                    // Object appCode = msg.get("appCode");
                    // String PackageIdFromJSON = (String) packageElement.get(0).get("packageId");
                    // String PackageIdFromDB = actInfoResult.get(j).get("PACKAGE_ID") + "";
                    // boolean isTure = "mabc".equals(appCode);
                    // boolean isSamePackage = PackageIdFromJSON.equals(PackageIdFromDB);
                    // if (isTure && isSamePackage) {
                    // continue;
                    // }
                    // }
                    if ("D".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map dis = new HashMap();
                        dis.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        dis.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        dis.put("discntCode", actInfoResult.get(j).get("ELEMENT_ID"));
                        // 算出活动的开始结束时间，预提交下发
                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(
                                String.valueOf(actInfoResult.get(j).get("ELEMENT_ID")), activityStartTime,
                                activityEndTime);
                        dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                        dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                        discntList.add(dis);
                    }
                    if ("S".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        Map svc = new HashMap();
                        svc.put("serviceId", actInfoResult.get(j).get("ELEMENT_ID"));
                        svc.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        svc.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        // 算出活动的开始结束时间，预提交下发
                        svc.put("activityStarTime", activityStartTime);
                        svc.put("activityTime", activityEndTime);
                        svcList.add(svc);
                    }
                    if ("X".equals(actInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                        String spId = "-1";
                        String partyId = "-1";
                        String spProductId = "-1";
                        Map spItemParam = new HashMap();
                        spItemParam.put("PTYPE", "X");
                        spItemParam.put("PRODUCT_ID", actPlanId);
                        spItemParam.put("PROVINCE_CODE", provinceCode);
                        spItemParam.put("SPSERVICEID", actInfoResult.get(j).get("ELEMENT_ID"));
                        List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                        if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                            throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                    + actInfoResult.get(j).get("ELEMENT_ID") + "】的元素属性信息");
                        }
                        for (int l = 0; l < spItemInfoResult.size(); l++) {
                            Map spItemInfo = spItemInfoResult.get(l);
                            spId = (String) spItemInfo.get("SP_ID");
                            partyId = (String) spItemInfo.get("PARTY_ID");
                            spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                        }
                        Map sp = new HashMap();
                        sp.put("productId", actInfoResult.get(j).get("PRODUCT_ID"));
                        sp.put("packageId", actInfoResult.get(j).get("PACKAGE_ID"));
                        sp.put("partyId", partyId);
                        sp.put("spId", spId);
                        sp.put("spProductId", spProductId);
                        sp.put("spServiceId", actInfoResult.get(j).get("ELEMENT_ID"));
                        sp.put("activityStarTime", activityStartTime);
                        sp.put("activityTime", activityEndTime);
                        spList.add(sp);
                    }

                }
                msg.put("PART_ACTIVE_PRODUCT", actProductId);

                Map actProParam = new HashMap();
                actProParam.put("PRODUCT_ID", actProductId);
                List<Map> actProductInfo = n25Dao.queryProductAndPtypeProduct(actProParam);// 查询产品属性
                if (actProductInfo == null || actProductInfo.size() == 0) {
                    throw new EcAopServerBizException("9999", "在产品表或者产品属性表中未查询到产品ID【" + actProductId + "】的产品信息");
                }
                String strProductMode = String.valueOf(actInfoResult.get(0).get("PRODUCT_MODE"));
                String strBrandCode = (String) actProductInfo.get(0).get("BRAND_CODE");
                String strProductTypeCode = (String) actProductInfo.get(0).get("PRODUCT_TYPE_CODE");

                Map productTpye = new HashMap();
                Map product = new HashMap();
                if (!"0".equals(actPlanId)) {

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", actProductId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    // 算出活动的开始结束时间，预提交下发
                    productTpye.put("activityStarTime", activityStartTime);
                    productTpye.put("activityTime", activityEndTime);

                    product.put("brandCode", strBrandCode);
                    product.put("productId", actProductId);
                    product.put("productMode", strProductMode);
                    // 算出活动的开始结束时间，预提交下发
                    product.put("activityStarTime", activityStartTime);
                    product.put("activityTime", activityEndTime);

                    // 如果有附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", actPlanId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (int c = 0; c < packageElementInfo.size(); c++) {

                                    if ("D".equals(packageElementInfo.get(c).get("ELEMENT_TYPE_CODE"))) {
                                        Map dis = new HashMap();
                                        dis.put("productId", packageElementInfo.get(c).get("PRODUCT_ID"));
                                        dis.put("packageId", packageElementInfo.get(c).get("PACKAGE_ID"));
                                        dis.put("discntCode", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(
                                                String.valueOf(packageElementInfo.get(c).get("ELEMENT_ID")),
                                                activityStartTime, activityEndTime);
                                        dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                        dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageElementInfo.get(c).get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        svc.put("productId", packageElementInfo.get(c).get("PRODUCT_ID"));
                                        svc.put("packageId", packageElementInfo.get(c).get("PACKAGE_ID"));
                                        // 算出活动的开始结束时间，预提交下发
                                        svc.put("activityStarTime", activityStartTime);
                                        svc.put("activityTime", activityEndTime);
                                        svcList.add(svc);
                                    }
                                    if ("X".equals(packageElementInfo.get(c).get("ELEMENT_TYPE_CODE"))) {
                                        String spId = "-1";
                                        String partyId = "-1";
                                        String spProductId = "-1";
                                        Map spItemParam = new HashMap();
                                        spItemParam.put("PTYPE", "X");
                                        spItemParam.put("PRODUCT_ID", actPlanId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("SPSERVICEID", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                        if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                                            throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                    + packageElementInfo.get(c).get("ELEMENT_ID") + "】的元素属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            spId = (String) spItemInfo.get("SP_ID");
                                            partyId = (String) spItemInfo.get("PARTY_ID");
                                            spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                        }
                                        Map sp = new HashMap();
                                        sp.put("productId", packageElementInfo.get(c).get("PRODUCT_ID"));
                                        sp.put("packageId", packageElementInfo.get(c).get("PACKAGE_ID"));
                                        sp.put("partyId", partyId);
                                        sp.put("spId", spId);
                                        sp.put("spProductId", spProductId);
                                        sp.put("spServiceId", packageElementInfo.get(c).get("ELEMENT_ID"));
                                        sp.put("activityStarTime", activityStartTime);
                                        sp.put("activityTime", activityEndTime);
                                        spList.add(sp);
                                    }

                                }
                            }

                        }

                    }

                    productTypeList.add(productTpye);
                    productList.add(product);

                }

                for (int j = 0; j < actInfoResult.size(); j++) {
                    Map subProductInfo = actInfoResult.get(j);
                    if ("A".equals(subProductInfo.get("ELEMENT_TYPE_CODE"))) {
                        String packageId = String.valueOf(subProductInfo.get("PACKAGE_ID"));
                        String elementDiscntId = String.valueOf(subProductInfo.get("ELEMENT_ID"));

                        // 对终端信息进行预占
                        if (!"0".equals(actPlanId)) {
                            if (resourcesInfo != null && resourcesInfo.size() > 0) {
                                // 取3GE返回的终端信息，如果为空，则取请求中的终端信息 lixl 2016-07-20
                                Map rescMap = new HashMap();
                                for (Map resourceMap : resourcesInfo) {
                                    if (resourceMap.get("resourcesCode").equals(resourcesCode)) {
                                        rescMap = resourceMap;
                                        break;
                                    }
                                }
                                if (rescMap.isEmpty()) {
                                    return;
                                }
                                String salePrice = ""; // 销售价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) rescMap.get("salePrice"))) {
                                    salePrice = (String) (rescMap.get("salePrice"));
                                    salePrice = String.valueOf(Integer.parseInt(salePrice) / 10);
                                }
                                String resCode = resourcesCode;// 资源唯一标识
                                String resBrandCode = (String) (rescMap.get("resourcesBrandCode")); // 品牌
                                String resBrandName = ""; // 终端品牌名称
                                if (StringUtils.isEmpty((String) (rescMap.get("resourcesBrandName")))) {
                                    resBrandName = "无说明";
                                }
                                else {
                                    resBrandName = (String) (rescMap.get("resourcesBrandName"));
                                }
                                String resModelCode = (String) (rescMap.get("resourcesModelCode")); // 型号
                                String resModeName = ""; // 终端型号名称
                                if (StringUtils.isEmpty((String) (rescMap.get("resourcesModelName")))) {
                                    resModeName = "无说明";
                                }
                                else {
                                    resModeName = (String) (rescMap.get("resourcesModelName"));
                                }
                                String machineTypeCode = "";// 终端机型编码
                                if (StringUtils.isEmpty((String) (rescMap.get("machineTypeCode")))) {
                                    machineTypeCode = "无说明";
                                }
                                else {
                                    machineTypeCode = (String) (rescMap.get("machineTypeCode"));
                                }
                                String orgdeviceBrandCode = "";
                                if (StringUtils.isNotEmpty((String) rescMap.get("orgDeviceBrandCode"))) {
                                    orgdeviceBrandCode = (String) (rescMap.get("orgDeviceBrandCode"));// 3GESS维护品牌，当iphone时品牌与上面的一致
                                }
                                String cost = ""; // 成本价格（单位：厘）
                                if (StringUtils.isNotEmpty((String) (rescMap.get("cost")))) {
                                    cost = (String) (rescMap.get("cost"));
                                    cost = String.valueOf(Integer.parseInt(cost) / 10);
                                }
                                String machineTypeName = ""; // 终端机型名称
                                if (StringUtils.isEmpty((String) (rescMap.get("machineTypeName")))) {
                                    machineTypeName = "无说明";
                                }
                                else {
                                    machineTypeName = (String) (rescMap.get("machineTypeName"));
                                }
                                String terminalSubtype = "";
                                if (StringUtils.isNotEmpty((String) (rescMap.get("terminalTSubType")))) {
                                    terminalSubtype = (String) (rescMap.get("terminalTSubType"));
                                }
                                String terminalType = (String) (rescMap.get("terminalType"));// 终端类别编码
                                if (!"0".equals(actPlanId)) {
                                    Map elemntItem1 = new HashMap();
                                    elemntItem1.put("userId", msg.get("userId"));
                                    elemntItem1.put("productId", actProductId);
                                    elemntItem1.put("packageId", packageId);
                                    elemntItem1.put("idType", "C");
                                    elemntItem1.put("id", elementDiscntId);
                                    elemntItem1.put("modifyTag", "0");
                                    elemntItem1.put("startDate", activityStartTime);
                                    elemntItem1.put("endDate", activityEndTime);
                                    elemntItem1.put("modifyTag", "0");
                                    elemntItem1
                                            .put("itemId", TradeManagerUtils.getSequence(eparchyCode, "SEQ_ITEM_ID"));
                                    elemntItem1.put("userIdA", "-1");
                                    elemntItem1.put("xDatatype", "NULL");
                                    msg.put("elementMap", elemntItem1);
                                }
                                // msgcontainer.putAll(msg);
                                // body.put("msg", msg);
                                // exchange.getIn().setBody(body);
                                // String purchaseItemId = (String) GetSeqUtil.getSeqFromCb(pmp[0],
                                // ExchangeUtils.ofCopy(exchange, tradeMap), "SEQ_ITEM_ID", 1).get(0);
                                // 在外面创建传进来
                                String purchaseItemId = (String) msg.get("purchaseItemId");
                                List<Map> tradeSubItem = new ArrayList<Map>();
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceType",
                                        decodeTerminalType(terminalType), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceno", machineTypeCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("devicebrand", orgdeviceBrandCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("deviceintag", resBrandName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobilecost",
                                        String.valueOf(Integer.parseInt(cost) / 100), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobileinfo", machineTypeName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("mobilesaleprice",
                                        String.valueOf(Integer.parseInt(salePrice) / 100), purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resActivityper", resActivityper,
                                        purchaseItemId));
                                // 因为活动处理提前，partActiveProduct为mProductId，但为空，现只从传参里取，先不查库了
                                String partActiveProduct = "";
                                if (productInfo != null && productInfo.size() > 0) {
                                    for (int k = 0; k < productInfo.size(); k++) {
                                        // String productMode = (String) productInfo.get(i).get("productMode");
                                        // 类型转换修改List-> Map create by zhaok 2017-08-21
                                        String productMode = (String) productInfo.get(k).get("productMode");
                                        if (MAIN_PRODUCT.equals(productMode)) {
                                            partActiveProduct = (String) productInfo.get(k).get("productId");
                                        }
                                    }
                                }
                                tradeSubItem.add(LanUtils.createTradeSubItemH("partActiveProduct", partActiveProduct,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesBrandCode", resBrandCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesBrandName", resBrandName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesModelCode", resModelCode,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesModelName", resModeName,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("SALE_PRODUCT_LIST", strProductTypeCode,
                                        purchaseItemId));

                                if (StringUtils.isNotEmpty(terminalSubtype)) // 有时为空
                                {
                                    tradeSubItem.add(LanUtils.createTradeSubItemH("terminalTSubType", terminalSubtype,
                                            purchaseItemId));
                                }
                                tradeSubItem.add(LanUtils.createTradeSubItemH("terminalType", terminalType,
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("isOwnerPhone", isOwnerPhone.toString(),
                                        purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("isPartActive", "0", purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("holdUnitType", "01", purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("resourcesType", "07", purchaseItemId));
                                tradeSubItem.add(LanUtils.createTradeSubItemH("packageType", "10", purchaseItemId));
                                tradeSubItem
                                        .add(LanUtils.createTradeSubItemH("itemid", purchaseItemId, purchaseItemId));

                                msg.put("subItemList", tradeSubItem);
                                // /拼装SUB_ITEM结束

                                // tf_b_trade_purchase表的台账
                                Map tradePurchase = new HashMap();
                                tradePurchase.put("userId", msg.get("userId"));
                                tradePurchase.put("bindsaleAttr", elementDiscntId);
                                tradePurchase.put("extraDevFee", "");
                                tradePurchase.put("mpfee", resourceFee);
                                tradePurchase.put("feeitemCode", purFeeTypeCode);
                                tradePurchase.put("foregift", "0");
                                tradePurchase.put("foregiftCode", "-1");
                                tradePurchase.put("foregiftBankmod", "");
                                tradePurchase.put("agreementMonths", resActivityper);
                                tradePurchase.put("endMode", "N");
                                tradePurchase.put("deviceType", machineTypeCode);
                                tradePurchase.put("moblieCost", cost);
                                tradePurchase.put("deviceName", resModeName);
                                tradePurchase.put("deviceBrand", orgdeviceBrandCode);
                                tradePurchase.put("imei", resCode);
                                tradePurchase.put("listBank", "");
                                tradePurchase.put("listFee", "");
                                tradePurchase.put("listCode", "");
                                tradePurchase.put("creditOrg", "");
                                tradePurchase.put("creditType", "");
                                tradePurchase.put("creditCardNum", "");
                                tradePurchase.put("agreement", "");
                                tradePurchase.put("productId", actProductId);
                                tradePurchase.put("packgaeId", packageId);
                                // tradePurchase.put("staffId", tradeStaffId);
                                // tradePurchase.put("departId", departId);
                                // 从msg中取
                                tradePurchase.put("staffId", msg.get("operatorId"));
                                tradePurchase.put("departId", msg.get("channelId"));
                                tradePurchase.put("startDate", activityStartTime);
                                tradePurchase.put("endDate", activityEndTime);
                                tradePurchase.put("remark", "");
                                tradePurchase.put("itemId", purchaseItemId);
                                tradePurchase.put("xDatatype", "NULL");
                                msg.put("tradePurchase", tradePurchase);
                            }
                        } // end
                    } // END IF A
                }

            }

        }
        // 处理产品信息开始
        if (productInfo != null && productInfo.size() > 0) {
            for (int i = 0; i < productInfo.size(); i++) {
                // String firstMonBillMode = null == userInfo.get(0).get("firstMonBillMode") ? "02" : (String) userInfo
                // .get(0).get("firstMonBillMode");
                // 首月付费模式默认取融合节点下的,若移网成员下有首月付费类型,则取移网成员下的
                String firstMonBillMode = (String) msg.get("firstMonBillMode");
                if (null != productInfo.get(i).get("firstMonBillMode")) {
                    firstMonBillMode = (String) productInfo.get(i).get("firstMonBillMode");
                }
                if (IsEmptyUtils.isEmpty(firstMonBillMode)) {
                    firstMonBillMode = "02";
                }
                String addProStartDay = "";
                String addProEndDay = "";
                String productId = (String) productInfo.get(i).get("productId");
                String productMode = (String) productInfo.get(i).get("productMode");
                List<Map> packageElement = (List<Map>) productInfo.get(i).get("packageElement");
                if (MAIN_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";

                    Map proparam = new HashMap();
                    proparam.put("PROVINCE_CODE", provinceCode);
                    proparam.put("PRODUCT_ID", productId);
                    proparam.put("EPARCHY_CODE", eparchyCode);
                    proparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                    proparam.put("PRODUCT_MODE", "00");
                    List<Map> productInfoResult = n25Dao.qryDefaultPackageElement(proparam);
                    if (productInfoResult == null || productInfoResult.size() == 0) {
                        // throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                        // 未查询到产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170321
                        List<Map> productInfoList = n25Dao.qryProductInfoByProductTable(proparam);
                        if (!IsEmptyUtils.isEmpty(productInfoList)) {
                            productId = String.valueOf(productInfoList.get(0).get("PRODUCT_ID"));
                            strProductMode = (String) productInfoList.get(0).get("PRODUCT_MODE");
                        }
                        else {
                            throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                        }
                    }
                    else {
                        productId = String.valueOf(productInfoResult.get(0).get("PRODUCT_ID"));
                        strProductMode = (String) productInfoResult.get(0).get("PRODUCT_MODE");
                    }
                    mProductId = productId;
                    Map itparam = new HashMap();
                    itparam.put("PROVINCE_CODE", provinceCode);
                    itparam.put("PRODUCT_ID", productId);
                    List<Map> productItemInfoResult = n25Dao.queryProductAndPtypeProduct(itparam);
                    if (productItemInfoResult == null || productItemInfoResult.size() == 0) {
                        throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品属性信息");
                    }

                    // 选速率,默认42M // 在mixOpenAppProcessor中已放入
                    String speed = (String) msg.get("speed");
                    // if (null != paraList && !paraList.isEmpty()) {
                    // for (Map para : paraList) {
                    // if ("SPEED".equalsIgnoreCase((String) para.get("paraId"))) {
                    // speed = (String) para.get("paraValue");
                    // }
                    // if ("REMARK".equalsIgnoreCase((String) para.get("paraId"))) {
                    // remark = (String) para.get("paraValue");
                    // }
                    // }
                    // }
                    // 选速率
                    // productInfoResult = chooseSpeed(productInfoResult, speed);
                    // 新的速率处理方法
                    productInfoResult = new NewProductUtils().chooseSpeed(productInfoResult, speed);
                    // msg.put("remark", remark);//在mixOpenAppProcessor中已放入

                    // 需要按偏移处理时间的资费
                    // String specialProductDis = EcAopConfigLoader.getStr("ecaop.global.param.product.special.discnt");
                    for (int j = 0; j < productInfoResult.size(); j++) {
                        if ("D".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map dis = new HashMap();
                            dis.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            dis.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            dis.put("discntCode", productInfoResult.get(j).get("ELEMENT_ID"));
                            // // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20170310
                            // if (specialProductDis
                            // .contains(productId + "," + productInfoResult.get(j).get("ELEMENT_ID"))) {
                            // if (dealMainProDiscntDate(dis, productInfoResult.get(j).get("ELEMENT_ID"))) {
                            // continue;
                            // }
                            // }
                            discntList.add(dis);
                        }
                        if ("S".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            Map svc = new HashMap();
                            svc.put("serviceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            svc.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            svc.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            svcList.add(svc);
                        }
                        if ("X".equals(productInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                            String spId = "-1";
                            String partyId = "-1";
                            String spProductId = "-1";
                            Map spItemParam = new HashMap();
                            spItemParam.put("PTYPE", "X");
                            spItemParam.put("COMMODITY_ID", productId);
                            spItemParam.put("PROVINCE_CODE", provinceCode);
                            spItemParam.put("SPSERVICEID", productInfoResult.get(j).get("ELEMENT_ID"));
                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                            if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                                throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                        + productInfoResult.get(j).get("ELEMENT_ID") + "】的元素属性信息");
                            }
                            for (int l = 0; l < spItemInfoResult.size(); l++) {
                                Map spItemInfo = spItemInfoResult.get(l);
                                spId = (String) spItemInfo.get("SP_ID");
                                partyId = (String) spItemInfo.get("PARTY_ID");
                                spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                            }
                            Map sp = new HashMap();
                            sp.put("productId", productInfoResult.get(j).get("PRODUCT_ID"));
                            sp.put("packageId", productInfoResult.get(j).get("PACKAGE_ID"));
                            sp.put("partyId", partyId);
                            sp.put("spId", spId);
                            sp.put("spProductId", spProductId);
                            sp.put("spServiceId", productInfoResult.get(j).get("ELEMENT_ID"));
                            spList.add(sp);
                        }
                    }

                    mProductTypeCode = strProductMode;
                    strBrandCode = (String) productItemInfoResult.get(0).get("BRAND_CODE");
                    mBrandCode = strBrandCode;
                    strProductTypeCode = (String) productItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                    tradeUserProductTypeCode = strProductTypeCode;

                    productTpye.put("productMode", strProductMode);
                    productTpye.put("productId", productId);
                    productTpye.put("productTypeCode", strProductTypeCode);
                    product.put("brandCode", strBrandCode);
                    product.put("productId", productId);
                    product.put("productMode", strProductMode);

                    productTypeList.add(productTpye);
                    productList.add(product);
                    msg.put("mBrandCode", mBrandCode);
                    msg.put("mProductId", mProductId);
                    msg.put("mProductTypeCode", mProductTypeCode);
                    msg.put("tradeUserProductTypeCode", tradeUserProductTypeCode);

                    // 附加包
                    if (packageElement != null && packageElement.size() > 0) {
                        for (int n = 0; n < packageElement.size(); n++) {
                            Map peparam = new HashMap();
                            peparam.put("PROVINCE_CODE", provinceCode);
                            peparam.put("PRODUCT_ID", productId);
                            peparam.put("EPARCHY_CODE", eparchyCode);
                            peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                            peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                            List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                            if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                for (int a = 0; a < packageElementInfo.size(); a++) {

                                    if ("D".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        Map dis = new HashMap();
                                        dis.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        dis.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        dis.put("discntCode", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        // // 根据配置判断是否处理主资费的开始时间和结束时间by wangmc 20170310
                                        // if (specialProductDis.contains(productId + ","
                                        // + packageElementInfo.get(a).get("ELEMENT_ID"))) {
                                        // if (dealMainProDiscntDate(dis, packageElementInfo.get(a).get("ELEMENT_ID")))
                                        // {
                                        // continue;
                                        // }
                                        // }
                                        discntList.add(dis);
                                    }
                                    if ("S".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        Map svc = new HashMap();
                                        svc.put("serviceId", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        svc.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        svc.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        svcList.add(svc);
                                    }
                                    if ("X".equals(packageElementInfo.get(a).get("ELEMENT_TYPE_CODE"))) {
                                        String spId = "-1";
                                        String partyId = "-1";
                                        String spProductId = "-1";
                                        Map spItemParam = new HashMap();
                                        spItemParam.put("PRODUCT_ID", productId);
                                        spItemParam.put("PROVINCE_CODE", provinceCode);
                                        spItemParam.put("SPSERVICEID", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                        if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                                            throw new EcAopServerBizException("9999", "在SP表中未查询到【"
                                                    + packageElementInfo.get(a).get("ELEMENT_ID") + "】的元素属性信息");
                                        }
                                        for (int j = 0; j < spItemInfoResult.size(); j++) {
                                            Map spItemInfo = spItemInfoResult.get(j);
                                            spId = (String) spItemInfo.get("SP_ID");
                                            partyId = (String) spItemInfo.get("PARTY_ID");
                                            spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                        }
                                        Map sp = new HashMap();
                                        sp.put("productId", packageElementInfo.get(a).get("PRODUCT_ID"));
                                        sp.put("packageId", packageElementInfo.get(a).get("PACKAGE_ID"));
                                        sp.put("partyId", partyId);
                                        sp.put("spId", spId);
                                        sp.put("spProductId", spProductId);
                                        sp.put("spServiceId", packageElementInfo.get(a).get("ELEMENT_ID"));
                                        spList.add(sp);
                                    }

                                }
                            }
                        }
                    }

                }
                else if (ADD_PRODUCT.equals(productMode)) {

                    Map productTpye = new HashMap();
                    Map product = new HashMap();

                    String addProductId = "";
                    String strBrandCode = "";
                    String strProductTypeCode = "";
                    String strProductMode = "";
                    // 针对融合新装主副卡，支撑语音翻倍赠送
                    if ("0".equals(msg.get("mainCardTag")) && "3".equals(msg.get("addType")) && "-1".equals(productId)) {
                        if (packageElement != null && packageElement.size() > 0) {
                            for (int n = 0; n < packageElement.size(); n++) {
                                if ("D".equals(packageElement.get(n).get("elementType"))
                                        && "-1".equals(packageElement.get(n).get("packageId"))) {
                                    Map dis = new HashMap();
                                    dis.put("productId", "-1");
                                    dis.put("packageId", "-1");
                                    dis.put("discntCode", String.valueOf(packageElement.get(n).get("elementId")));
                                    dis.put("activityStarTime", GetDateUtils.getDate());
                                    dis.put("activityTime", "2040-12-31 23:59:59");
                                    discntList.add(dis);
                                }
                            }
                        }
                    }
                    else {
                        Map addproparam = new HashMap();
                        addproparam.put("PROVINCE_CODE", provinceCode);
                        addproparam.put("PRODUCT_ID", productId);
                        addproparam.put("EPARCHY_CODE", eparchyCode);
                        addproparam.put("PRODUCT_MODE", "01");
                        addproparam.put("FIRST_MON_BILL_MODE", firstMonBillMode);
                        List<Map> addproductInfoResult = n25Dao.qryDefaultPackageElement(addproparam);

                        if (addproductInfoResult == null || addproductInfoResult.size() == 0) {
                            // 未查询到附加产品的默认资费或服务,不报错,去TD_B_PRODUCT表查询,产品不存在就抛错,存在继续执行 by wangmc 20170302
                            List<Map> productInfoList = n25Dao.qryProductInfoByProductTable(addproparam);
                            if (!IsEmptyUtils.isEmpty(productInfoList)) {
                                addProductId = String.valueOf(productInfoList.get(0).get("PRODUCT_ID"));
                                strProductMode = (String) productInfoList.get(0).get("PRODUCT_MODE");
                            }
                            else {
                                throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品信息");
                            }
                        }
                        else {
                            addProductId = String.valueOf(addproductInfoResult.get(0).get("PRODUCT_ID"));
                            strProductMode = (String) addproductInfoResult.get(0).get("PRODUCT_MODE");
                        }

                        // isFinalCode = TradeManagerUtils.getEndDate(addProductId);
                        isFinalCode = NewProductUtils.getEndDateType(addProductId);// cuij
                        if ("N".equals(isFinalCode) || "X".equals(isFinalCode)) {
                            Map productDate = TradeManagerUtils.getEffectTime(addProductId, monthFirstDay,
                                    monthLasttDay);
                            addProStartDay = (String) productDate.get("monthFirstDay");
                            addProEndDay = (String) productDate.get("monthLasttDay");
                        }
                        else if ("Y".equals(isFinalCode)) {
                            if (StringUtils.isEmpty(activityEndTime)) {
                                throw new EcAopServerBizException("9999", "所选附加产品" + productId + "生失效时间需要和合约保持一致，"
                                        + "请检查合约信息是否已传或更换附加产品信息");
                            }
                            addProStartDay = activityStartTime;
                            addProEndDay = activityEndTime;
                        }
                        for (int j = 0; j < addproductInfoResult.size(); j++) {

                            if ("D".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                                String elementId = String.valueOf(addproductInfoResult.get(j).get("ELEMENT_ID"));
                                Map dis = new HashMap();
                                dis.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                                dis.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                                dis.put("discntCode", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                if (!"Y".equals(isFinalCode)) {
                                    Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(elementId,
                                            addProStartDay, addProEndDay);
                                    dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                    dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                }
                                else {
                                    dis.put("activityStarTime", addProStartDay);
                                    dis.put("activityTime", addProEndDay);
                                }
                                discntList.add(dis);
                            }
                            else if ("S".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                                Map svc = new HashMap();
                                svc.put("serviceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                svc.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                                svc.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                                svc.put("activityStarTime", addProStartDay);
                                svc.put("activityTime", addProEndDay);
                                svcList.add(svc);
                            }
                            else if ("X".equals(addproductInfoResult.get(j).get("ELEMENT_TYPE_CODE"))) {
                                String spId = "-1";
                                String partyId = "-1";
                                String spProductId = "-1";
                                Map spItemParam = new HashMap();
                                spItemParam.put("PROVINCE_CODE", provinceCode);
                                spItemParam.put("SPSERVICEID", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                                    throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【"
                                            + addproductInfoResult.get(j).get("ELEMENT_ID") + "】的产品属性信息");
                                }
                                for (int l = 0; l < spItemInfoResult.size(); l++) {
                                    Map spItemInfo = spItemInfoResult.get(l);
                                    spId = (String) spItemInfo.get("SP_ID");
                                    partyId = (String) spItemInfo.get("PARTY_ID");
                                    spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                }
                                Map sp = new HashMap();
                                sp.put("productId", addproductInfoResult.get(j).get("PRODUCT_ID"));
                                sp.put("packageId", addproductInfoResult.get(j).get("PACKAGE_ID"));
                                sp.put("partyId", partyId);
                                sp.put("spId", spId);
                                sp.put("spProductId", spProductId);
                                sp.put("spServiceId", addproductInfoResult.get(j).get("ELEMENT_ID"));
                                sp.put("activityStarTime", addProStartDay);
                                sp.put("activityTime", addProEndDay);
                                spList.add(sp);
                            }
                        }

                        Map additparam = new HashMap();
                        additparam.put("PROVINCE_CODE", provinceCode);
                        additparam.put("PRODUCT_ID", productId);
                        List<Map> addProductItemInfoResult = n25Dao.queryProductAndPtypeProduct(additparam);
                        if (addProductItemInfoResult == null || addProductItemInfoResult.size() == 0) {
                            throw new EcAopServerBizException("9999", "在产品表中未查询到产品ID【" + productId + "】的产品属性信息");
                        }

                        strBrandCode = (String) addProductItemInfoResult.get(0).get("BRAND_CODE");
                        strProductTypeCode = (String) addProductItemInfoResult.get(0).get("PRODUCT_TYPE_CODE");
                        productTpye.put("productMode", strProductMode);
                        productTpye.put("productId", addProductId);
                        productTpye.put("productTypeCode", strProductTypeCode);
                        productTpye.put("activityStarTime", addProStartDay);
                        productTpye.put("activityTime", addProEndDay);
                        product.put("activityStarTime", addProStartDay);
                        product.put("activityTime", addProEndDay);
                        product.put("brandCode", strBrandCode);
                        product.put("productId", addProductId);
                        product.put("productMode", strProductMode);

                        productTypeList.add(productTpye);
                        productList.add(product);

                        // 附加包
                        if (packageElement != null && packageElement.size() > 0) {
                            for (int n = 0; n < packageElement.size(); n++) {
                                Map peparam = new HashMap();
                                peparam.put("PROVINCE_CODE", provinceCode);
                                peparam.put("PRODUCT_ID", productId);
                                peparam.put("EPARCHY_CODE", eparchyCode);
                                peparam.put("PACKAGE_ID", packageElement.get(n).get("packageId"));
                                peparam.put("ELEMENT_ID", packageElement.get(n).get("elementId"));
                                List<Map> packageElementInfo = n25Dao.queryPackageElementInfo(peparam);
                                if (packageElementInfo != null && packageElementInfo.size() > 0) {
                                    for (int b = 0; b < packageElementInfo.size(); b++) {

                                        if ("D".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                            String elementId = String.valueOf(packageElementInfo.get(b).get(
                                                    "ELEMENT_ID"));
                                            Map dis = new HashMap();
                                            dis.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                            dis.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
                                            dis.put("discntCode", elementId);
                                            if (ADD_PRODUCT.equals(productMode)) {
                                                if (!"Y".equals(isFinalCode)) {
                                                    Map discntDateMap = TradeManagerUtils.getDiscntEffectTime(
                                                            elementId, addProStartDay, addProEndDay);
                                                    dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                                                    dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                                                }
                                                else {
                                                    dis.put("activityStarTime", addProStartDay);
                                                    dis.put("activityTime", addProEndDay);
                                                }
                                            }
                                            discntList.add(dis);
                                        }
                                        if ("S".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                            Map svc = new HashMap();
                                            svc.put("serviceId", packageElementInfo.get(b).get("ELEMENT_ID"));
                                            svc.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                            svc.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
                                            svcList.add(svc);
                                        }
                                        if ("X".equals(packageElementInfo.get(b).get("ELEMENT_TYPE_CODE"))) {
                                            String spId = "-1";
                                            String partyId = "-1";
                                            String spProductId = "-1";
                                            Map spItemParam = new HashMap();
                                            spItemParam.put("PROVINCE_CODE", provinceCode);
                                            spItemParam.put("SPSERVICEID", packageElementInfo.get(b).get("ELEMENT_ID"));
                                            List<Map> spItemInfoResult = n25Dao.querySPServiceAttr(spItemParam);
                                            if (IsEmptyUtils.isEmpty(spItemInfoResult)) {
                                                throw new EcAopServerBizException("9999", "在SP产品表中未查询到【"
                                                        + packageElementInfo.get(b).get("ELEMENT_ID") + "】的元素属性信息");
                                            }

                                            for (int j = 0; j < spItemInfoResult.size(); j++) {
                                                Map spItemInfo = spItemInfoResult.get(j);
                                                spId = (String) spItemInfo.get("SP_ID");
                                                partyId = (String) spItemInfo.get("PARTY_ID");
                                                spProductId = (String) spItemInfo.get("SP_PRODUCT_ID");
                                            }

                                            Map sp = new HashMap();
                                            sp.put("productId", packageElementInfo.get(b).get("PRODUCT_ID"));
                                            sp.put("packageId", packageElementInfo.get(b).get("PACKAGE_ID"));
                                            sp.put("partyId", partyId);
                                            sp.put("spId", spId);
                                            sp.put("spProductId", spProductId);
                                            sp.put("spServiceId", packageElementInfo.get(b).get("ELEMENT_ID"));
                                            spList.add(sp);
                                        }

                                    }
                                }

                            }
                        }
                    }
                }
            }

        }
        // discntList = newDealRepeat(discntList, "discntList");
        // svcList = newDealRepeat(svcList, "svcList");
        // spList = newDealRepeat(spList, "spList");
        // productTypeList = newDealRepeat(productTypeList, "productTypeList");
        // productList = newDealRepeat(productList, "productList");
        msg.put("isTest", isTest);
        msg.put("resourceFee", resourceFee);
        msg.put("isOwnerPhone", isOwnerPhone);
        msg.put("discntList", NewProductUtils.newDealRepeat(discntList, "discntList"));
        msg.put("svcList", NewProductUtils.newDealRepeat(svcList, "svcList"));
        msg.put("spList", NewProductUtils.newDealRepeat(spList, "spList"));
        msg.put("productTypeList", NewProductUtils.newDealRepeat(productTypeList, "productTypeList"));
        msg.put("productList", NewProductUtils.newDealRepeat(productList, "productList"));
    }

}
