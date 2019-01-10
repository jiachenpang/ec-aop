package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 产品变更的23转4流程,先调用省份三户获取三户信息,
 * 再调用cbss客户资料校验接口,校验证件在cb是否有客户信息,若有则继承,否则新建,
 * 最后组织调用aop23转4接口的请求参数
 * @author by wangmc 2017-08-14
 */
@EcRocTag("preDataFor23To4Processor")
public class PreDataFor23To4Processor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    // 辽宁三户,北六三户,南25三户,cBSS客户资料校验
    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping.91",
            "ecaop.trade.n6.checkUserParametersMapping", "ecaop.masb.chku.checkUserParametersMapping",
            "ecaop.cust.cbss.check.ParametersMapping" };
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object apptx = body.get("apptx");
        System.out.println(apptx + ",产品变更流程---> 23转4流程--------");

        // 获取参数
        List<Map> userInfos = (List<Map>) msg.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfos)) {
            throw new EcAopRequestBizException("未下发用户信息,无法办理业务");
        }
        Map userInfo = userInfos.get(0);

        // 1,调用省份三户接口,获取三户信息
        Map threePartRet = checkUserInfoByNumber(exchange, msg);
        System.out.println(apptx + ",调完三户之后的msg:" + msg);
        // 2,调用cb客户资料校验接口,获取号码的证件在cb是否有客户信息
        Map checkCustRet = checkCustToCbssByCertNum(exchange, msg, threePartRet);
        System.out.println(apptx + ",调完客户资料校验之后的msg:" + msg);
        // 3,为调用aop23转4的接口准备参数
        Map transMsg = transPreSubmit(exchange, msg, threePartRet, checkCustRet, userInfo);
        System.out.println(apptx + ",23转4之前的msg:" + msg);

        body.put("msg", transMsg);
        exchange.getIn().setBody(body);
    }

    /**
     * 1.直接调用省份的三户接口获取信息
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map checkUserInfoByNumber(Exchange exchange, Map msg) throws Exception {
        msg.put("tradeTypeCode", "9999");
        // serviceClassCode字段上游必传 create by zhaok 7/26
        Object serviceClassCode = msg.get("serviceClassCode");
        msg.put("getMode", "101001101010001001000000000000");
        // 调用省份的三户
        String provinceCode = String.valueOf(msg.get("province"));
        Exchange threeExchange = ExchangeUtils.ofCopy(exchange, msg);
        if ("17|18|11|76|91|97".contains(provinceCode)) {
            String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType",
                    "serviceClassCode", "serialNumber" };
            // 为辽宁单独拉分支，支持返回acctInfo。2017-1-16 by cuij
            Map threePartMap = new HashMap();
            if ("91".equals(provinceCode)) {
                threePartMap = MapUtils.asMap("getMode", "1111111100000001", "tradeTypeCode", msg.get("tradeTypeCode")
                        + "");
            }
            else {
                threePartMap = MapUtils.asMap("getMode", "1111100000000001", "tradeTypeCode", msg.get("tradeTypeCode")
                        + "");// 山东北六E要单调三户接口获取活动信息，所以修改getMode跟tradeTypeCode
            }
            MapUtils.arrayPut(threePartMap, msg, copyArray);
            threeExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
            // 辽宁省份三户请求单独模板，带上参数serviceClassCode
            long start = System.currentTimeMillis();
            if ("91".equals(provinceCode)) {
                System.out.println("-----------------------辽宁三户校验单独模板------------------");
                lan.preData(pmp[0], threeExchange);
            }
            else {
                lan.preData(pmp[1], threeExchange);
            }
            CallEngine.wsCall(threeExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + provinceCode);
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threeExchange);
            System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "time,调用省份三户用时:"
                    + (System.currentTimeMillis() - start));
        }
        else {
            long start = System.currentTimeMillis();
            // 南25省需要在三户接口直接默认下发serviceClassCode为0000 by wangmc 20180427
            ((Map) threeExchange.getIn().getBody(Map.class).get("msg")).put("serviceClassCode", "0000");
            lan.preData(pmp[2], threeExchange);
            CallEngine.wsCall(threeExchange, "ecaop.comm.conf.url.osn.services.usrser");
            lan.xml2Json("ecaop.masb.chku.checkUserTemplate", threeExchange);
            System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "time,调用省份三户用时:"
                    + (System.currentTimeMillis() - start));
            // 防止被覆盖
            msg.put("serviceClassCode", serviceClassCode);
        }

        Map retMap = (Map) threeExchange.getOut().getBody();
        System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "调用三户的返回:" + retMap);
        // 校验三户接口是否没有返回细信息
        if (IsEmptyUtils.isEmpty(retMap)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "根据用户号码:[" + msg.get("serialNumber")
                    + "]未获取到三户信息");
        }
        // 账户信息不用
        String[] checkKeys = new String[] { "userInfo", "custInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息", "custInfo", "客户信息", "acctInfo", "账户信息");
        System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + " : " + retMap);
        for (String key : checkKeys) {
            if (IsEmptyUtils.isEmpty(retMap.get(key))) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "三户接口未返回" + errorDetail.get(key));
            }
            // 北六返回的账户信息是List 南二十五返回的账户信息是Map create by zhaok
            if ("17|18|11|76|91|97".contains(provinceCode)) {
                System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + " : " + key + retMap.get(key));
                retMap.put(key, ((List<Map>) retMap.get(key)).get(0));

            }

        }
        return retMap;
    }

    /**
     * 2.调用cbss的客户资料校验接口
     * @param exchange
     * @param msg
     * @param threePartMsg
     * @return
     * @throws Exception
     */
    private Map checkCustToCbssByCertNum(Exchange exchange, Map msg, Map threePartRet) throws Exception {
        try {
            Map custInfo = (Map) threePartRet.get("custInfo");
            if (IsEmptyUtils.isEmpty(custInfo.get("certCode")) || IsEmptyUtils.isEmpty(custInfo.get("certTypeCode"))) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "三户接口未返回证件信息");
            }
            // 转换证件类型,南25省份
            String certTypeCode = CertTypeChangeUtils.certTypeFbs2Mall(custInfo.get("certTypeCode") + "");
            if ("17|18|11|76|91|97".contains((String) msg.get("province"))) {
                // 转换证件类型,在三户接口中,证件类型已经从cb转换为aop规范 create by zhaok 08/03
                certTypeCode = CertTypeChangeUtils.certTypeCbss2Mall(custInfo.get("certTypeCode") + "");
            }

            // 调用cBSS客户资料校验接口
            Map custCheckMsg = new HashMap();
            MapUtils.arrayPut(custCheckMsg, msg, MagicNumber.COPYARRAY);
            // 当证件类型为集团时,operateType传13,与客户资料校验保持一致
            custCheckMsg.put("operateType", "1");
            if ("07|13|14|15|17|18|21|33".contains(certTypeCode)) {
                custCheckMsg.put("operateType", "13");
            }
            custCheckMsg.put("certTypeCode", CertTypeChangeUtils.certTypeMall2Cbss(certTypeCode));// 证件类型
            custCheckMsg.put("certCode", custInfo.get("certCode"));// 证件号码
            Exchange custCheckExchange = ExchangeUtils.ofCopy(exchange, custCheckMsg);
            lan.preData(pmp[3], custCheckExchange);
            CallEngine.wsCall(custCheckExchange, "ecaop.comm.conf.url.cbss.services.CustSer");
            lan.xml2JsonNoError("ecaop.cust.cbss.check.template", custCheckExchange);

            Map retCustInfo = custCheckExchange.getOut().getBody(Map.class);
            // 集团客户--客户资料校验报错，抛出异常。 个人客户如果返回报错则创建新客户。
            if (!"0000".equals(retCustInfo.get("code")) && "13".equals(custInfo.get("operateType"))) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "集团客户--客户资料校验返回报错："
                        + retCustInfo.get("detail"));
            }
            Map retMap = new HashMap();
            // 获取认证等级最高的客户信息,如果没有客户信息则不处理
            chooseCustInfoByCertTag(retCustInfo, retMap);
            return retMap;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用CBSS客户资料校验报错:" + e.getMessage());
        }
    }

    /**
     * 返回客户资料时，需要返回有实名标示的，并且取优先级最高的，如果所有的客户资料都没有实名标示，则取第一个。
     * 实名制标识：5:公安+二代 4：实名-二代 3：实名-公安 2：实名-系统 1：非实名
     * 优先级：二代+公安>>二代或者公安>>系统>>未实名或者无实名标 
     * @param retCustInfo
     * @return
     */
    private void chooseCustInfoByCertTag(Map retCustInfo, Map retMap) {
        if (!"0000".equals(retCustInfo.get("code")) || IsEmptyUtils.isEmpty(retCustInfo)) {
            return;
        }
        List<Map> custInfoList = (List<Map>) retCustInfo.get("custInfo");
        if (IsEmptyUtils.isEmpty(custInfoList)) {
            return;
        }
        // 获取认证等级最高的证件类型信息
        int maxLevel = 0;
        int maxLevelIndex = 0;// 默认返回第一个
        for (int i = 0; i < custInfoList.size(); i++) {
            String certTag = (String) custInfoList.get(i).get("rsrvTag1");
            if (StringUtils.isNotEmpty(certTag)) {// 实名标识不为空并且数字在这其中时,才处理
                int certLevel = Integer.parseInt(certTag);
                if (certLevel > maxLevel) {
                    maxLevel = certLevel;
                    maxLevelIndex = i;
                }
            }
        }
        retMap.putAll(custInfoList.get(maxLevelIndex));
    }

    /**
     * 从三户返回和客户资料校验返回中获取信息,拼装参数调用AOP的23转4接口
     * @param exchange
     * @param msg
     * @param threePartRet
     * @param checkCustRet
     * @param userInfo - 外围请求参数
     * @return
     * @throws Exception
     */
    private Map transPreSubmit(Exchange exchange, Map msg, Map threePartRet, Map checkCustRet, Map userInfo)
            throws Exception {
        // Object apptx = exchange.getIn().getBody(Map.class).get("apptx");
        try {
            // 组织调用请求参数
            Map transMsg = MapUtils.asMap("ordersId", msg.get("ordersId"), "opeSysType", "2", "eModeCode", "E",
                    "serviceClassCode", msg.get("serviceClassCode"));
            MapUtils.arrayPut(transMsg, msg, MagicNumber.COPYARRAY);

            // 号码信息
            List<Map> numIdList = new ArrayList<Map>();
            Map numIdMap = new HashMap();
            numIdMap.put("serialNumber", msg.get("serialNumber"));
            numIdList.add(numIdMap);
            transMsg.put("numId", numIdList);

            // 客户信息
            List<Map> customer = new ArrayList<Map>();
            Map custInfo = (Map) threePartRet.get("custInfo");
            Map threePartUserInfo = (Map) threePartRet.get("userInfo");

            Map customerInfo = new HashMap();
            customerInfo.put("authTag", "0");// 鉴权标识 0：未鉴权,1：已鉴权 FIXME 从哪里取
            customerInfo.put("realNameType", "1");// 客户实名标识 0：实名,1：匿名 FIXME
            if (IsEmptyUtils.isEmpty(checkCustRet)) {// 没有客户资料
                if (!IsEmptyUtils.isEmpty(threePartUserInfo.get("realNameTag"))) {// 付费类型
                    customerInfo.put("realNameType", threePartUserInfo.get("realNameTag"));
                }
                customerInfo.put("custType", "0"); // 新老客户标识 0：新增客户,1：老客户
                Map newCustomerInfo = new HashMap();
                newCustomerInfo.put("certType", custInfo.get("certTypeCode"));// 证件类型
                newCustomerInfo.put("certNum", custInfo.get("certCode"));// 证件号码
                newCustomerInfo.put("certAdress", custInfo.get("certAddr"));// 证件地址
                newCustomerInfo.put("customerName", custInfo.get("custName"));// 客户姓名默认为证件名称
                newCustomerInfo.put("certExpireDate", custInfo.get("certEndDate"));// 证件结束日期
                newCustomerInfo.put("contactPerson", custInfo.get("custName"));
                newCustomerInfo.put("contactPhone", msg.get("serialNumber"));// 暂时放该号码
                newCustomerInfo.put("contactAddress", custInfo.get("certAddr"));// 通讯地址
                newCustomerInfo.put("custType", custInfo.get("custType"));// 证件类型
                // newCustomerInfo.put("customerRemark", "");// 客户备注信息,先不放

                List<Map> newCustomer = new ArrayList<Map>();
                newCustomer.add(newCustomerInfo);

                customerInfo.put("newCustomerInfo", newCustomer);
                customer.add(customerInfo);
            }
            else {
                // Map customerInfo = new HashMap();
                //
                // customerInfo.put("authTag", "0");// 鉴权标识 0：未鉴权,1：已鉴权 FIXME 从哪里取
                // customerInfo.put("realNameType", "1");// 客户实名标识 0：实名,1：匿名 FIXME
                if ("3,4,5".contains((String) checkCustRet.get("rsrvTag1"))) {
                    customerInfo.put("realNameType", "0");// 客户实名标识 0：实名,1：匿名 FIXME
                }
                customerInfo.put("custType", "1"); // 新老客户标识 0：新增客户,1：老客户
                Map newCustomerInfo = new HashMap();
                newCustomerInfo.put("certType",
                        CertTypeChangeUtils.certTypeCbss2Mall((String) checkCustRet.get("certTypeCode")));// cb规范的证件类型
                newCustomerInfo.put("certNum", checkCustRet.get("certCode"));// 证件号码
                newCustomerInfo.put("certAdress", checkCustRet.get("certAddr"));// 证件地址
                if (IsEmptyUtils.isEmpty(newCustomerInfo.get("certAdress"))) {
                    newCustomerInfo.put("certAdress", custInfo.get("certAddr"));// 证件地址
                }
                newCustomerInfo.put("customerName", checkCustRet.get("custName"));// 客户名称
                newCustomerInfo.put("certExpireDate", checkCustRet.get("certEndDate"));// 证件结束日期
                if (IsEmptyUtils.isEmpty(newCustomerInfo.get("certExpireDate"))) {
                    newCustomerInfo.put("certExpireDate", custInfo.get("certEndDate"));// 证件结束日期
                }
                newCustomerInfo.put("contactPerson", newCustomerInfo.get("customerName"));
                newCustomerInfo.put("contactPhone", checkCustRet.get("phone"));// 暂时放该号码
                newCustomerInfo.put("contactAddress", newCustomerInfo.get("certAdress"));
                newCustomerInfo.put("custType", newCustomerInfo.get("certType"));// 证件类型

                List<Map> newCustomer = new ArrayList<Map>();
                newCustomer.add(newCustomerInfo);

                customerInfo.put("newCustomerInfo", newCustomer);
                customer.add(customerInfo);
            }
            transMsg.put("customerInfo", customer);
            // 用户信息节点
            List<Map> userInfoList = new ArrayList<Map>();
            Map user = new HashMap();
            user.put("userType", "1");// 用户类型,1-新用户,2-老用户 FIXME
            user.put("bipType", "2"); // 业务类型 1：合约类业务
            user.put("is3G", "2"); // 4G
            user.put("serType", "1");// 付费类型,默认后付费
            if (!IsEmptyUtils.isEmpty(threePartUserInfo.get("subscType"))) {// 付费类型
                String subscType = (String) threePartUserInfo.get("subscType");
                if ("1".equals(subscType)) { // 预付费
                    user.put("serType", "2");
                }
            }
            //
            if (!IsEmptyUtils.isEmpty(userInfo.get("transresourcesType"))) {
                if ("02".equals(userInfo.get("transresourcesType"))) {// 预付费转后付费
                    user.put("serType", "1");
                }
                else if ("03".equals(userInfo.get("transresourcesType"))) {// 后付费转预付费
                    user.put("serType", "2");
                }
            }
            user.put("packageTag", "0");// 非套包销售
            // 用户密码
            String serialNumber = (String) msg.get("serialNumber");
            user.put("userPwd", serialNumber.substring(serialNumber.length() - 6));

            // 拼装产品信息
            List<Map> prodcutInfoList = new ArrayList<Map>();
            Map product = new HashMap();
            // 外围传入的产品信息
            Map productInfo = (Map) userInfo.get("productInfo");
            if (IsEmptyUtils.isEmpty(productInfo)) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "跨系统办理用户23G转4G须下发产品信息!");
            }
            product.put("productId", productInfo.get("tarproductId"));// 取外围传入的目标套餐
            product.put("productMode", "1");// 默认为主产品
            List<Map> packageInfo = (List<Map>) productInfo.get("packageInfo");
            if (!IsEmptyUtils.isEmpty(packageInfo)) {
                List<Map> packageElementList = new ArrayList<Map>();
                for (Map pack : packageInfo) {
                    List<Map> elementInfo = (List<Map>) pack.get("elementInfo");
                    if (!IsEmptyUtils.isEmpty(elementInfo)) {
                        for (Map element : elementInfo) {
                            Map packageElement = new HashMap();
                            packageElement.put("packageId", pack.get("packageId"));
                            packageElement.put("elementId", element.get("elementCode"));
                            // packageElement.put("elementType", "");
                            packageElementList.add(packageElement);
                        }
                    }
                }
                if (!IsEmptyUtils.isEmpty(packageElementList)) {
                    product.put("packageElement", packageElementList);
                }
            }
            prodcutInfoList.add(product);
            user.put("product", prodcutInfoList);
            // 支付信息
            user.put("payInfo", MapUtils.asMap("payType", "15"));// 默认银行卡支付 FIXME
            userInfoList.add(user);
            transMsg.put("userInfo", userInfoList);
            return transMsg;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用23转4预提交报错:" + e.getMessage());
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
