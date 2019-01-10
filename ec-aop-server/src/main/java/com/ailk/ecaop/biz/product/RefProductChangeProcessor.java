package com.ailk.ecaop.biz.product;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.AopCall;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.cust.CheckOweByUserIdProcessors;
import com.ailk.ecaop.biz.cust.CheckOweProcessors;
import com.ailk.ecaop.biz.sub.TransToCbssProcessor;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.TransFeeUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * @author cuij
 * @version 创建时间：2017-5-9 下午4:32:17
 *          1、办理业务系统：BSS，即2G转3G（后付费、预付费），3G互转。无特殊处理。
 *          2、办理业务系统：CBSS，跨系统办理，即23转4，走老的23转4流程。
 *          3、办理业务系统：CBSS，不跨系统办理，即4G互转，走老的套餐服务变更流程，先调用套餐服务变更接口再调用开户处理提交接口。
 */
@EcRocTag("refProductChangeProcessor")
public class RefProductChangeProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[5];
    // 调用省份,正式提交,辽宁三户,北六三户,南25三户
    private static final String[] PARAM_ARRAY = { "ecaop.ecsb.mcps.ParametersMapping",
            "ecaop.trades.sccc.cancel.paramtersmapping", "ecaop.trade.n6.checkUserParametersMapping.91",
            "ecaop.trade.n6.checkUserParametersMapping", "ecaop.masb.chku.checkUserParametersMapping" };
    Logger log = Logger.getLogger(RefProductChangeProcessor.class);
    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "开始处理的时间:"
                + GetDateUtils.getSysdateFormat());
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 获取参数
        List<Map> userInfos = (List<Map>) msg.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfos)) {
            throw new EcAopRequestBizException("未下发用户信息,无法办理业务");
        }
        if (!"1,2".contains(String.valueOf(msg.get("opeSysType")))) {
            throw new EcAopRequestBizException("参数[办理业务系统]传值有误,请确认.opeSysType:" + msg.get("opeSysType"));
        }
        Map userInfo = userInfos.get(0);
        if ("1".equals(msg.get("opeSysType"))) {// BSS，即2G转3G（后付费、预付费），3G互转。无特殊处理。直接调用省份
            List<Map> subproductInfo = new ArrayList<Map>();
            Map productInfo = (Map) userInfo.get("productInfo");
            List<Map> packageInfos = (List<Map>) productInfo.get("packageInfo");
            for (int i = 0; i < packageInfos.size(); i++) {
                List<Map> elementInfos = (List<Map>) packageInfos.get(i).get("elementInfo");
                Map productitem = new HashMap();
                List<Map> feeInfo = new ArrayList<Map>();
                for (int j = 0; j < elementInfos.size(); j++) {
                    Map item = new HashMap();
                    item.put("startDate", elementInfos.get(j).get("startDate"));
                    item.put("feeCode", elementInfos.get(j).get("elementCode"));
                    item.put("feeName", elementInfos.get(j).get("elementName"));
                    item.put("endDate", elementInfos.get(j).get("endDate"));
                    feeInfo.add(item);
                }
                productitem.put("feeInfo", feeInfo);
                productitem.put("startDate", packageInfos.get(i).get("startDate"));
                productitem.put("packageType", packageInfos.get(i).get("packageType"));
                productitem.put("endDate", packageInfos.get(i).get("endDate"));
                productitem.put("packageId", packageInfos.get(i).get("packageId"));
                productitem.put("packageName", packageInfos.get(i).get("packageId"));
                subproductInfo.add(productitem);
            }
            msg.put("subproductInfo", subproductInfo);
            msg.put("priproductId", productInfo.get("priproductId"));
            msg.put("tarproductId", productInfo.get("tarproductId"));
            msg.put("orderId", msg.get("ordersId"));
            msg.put("endsystemId", "0" + msg.get("opeSysType"));
            msg.put("transresourcesType", userInfo.get("transresourcesType"));

            body.put("msg", msg);
            exchange.getIn().setBody(body);
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.productchgser");
            lan.xml2Json("ecaop.trads.mcps.template", exchange);

        }
        if ("2".equals(msg.get("opeSysType"))) {
            if (!"0,1".contains(String.valueOf(userInfo.get("isTransystem")))) {
                throw new EcAopRequestBizException("参数[是否跨系统办理]传值有误,请确认.isTransystem:" + userInfo.get("isTransystem"));
            }
            if ("0".equals(userInfo.get("isTransystem"))) {
                // CBSS，跨系统办理，即23转4，走老的23转4流程。

                // 1,调用省份三户接口,获取三户信息
                Map threePartRet = checkUserInfoByNumber(exchange, msg);
                // System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "调用完三户的时间:"
                // + GetDateUtils.getSysdateFormat());
                // String apptx = (String) body.get("apptx");
                // System.out.println(apptx + ",处理完三户返回内容:" + threePartRet);

                // 2,调用cb客户资料校验接口,获取号码的证件在cb是否有客户信息
                // long start = System.currentTimeMillis();
                // System.out.println(apptx + "调用客户资料校验开始时间:" + getTime());
                Map checkCustRet = checkCustToCbssByCertNum(exchange, msg, threePartRet);
                // System.out.println(apptx + "调用客户资料校验结束时间:" + getTime());
                // System.out.println(apptx + ",调用客户资料校验用时:" + (System.currentTimeMillis() - start) + "调用完客户资料校验:"
                // + checkCustRet);

                // 3,调用aop23转4的接口
                // start = System.currentTimeMillis();
                // System.out.println(apptx + "调用23转4接口的时间:" + getTime());
                Map preSubmitRet = transPreSubmit(exchange, msg, threePartRet, checkCustRet, userInfo);
                // System.out.println(apptx + "调用23转4接口结束时间:" + getTime());
                // System.out.println(apptx + ",调用23转4接口用时:" + (System.currentTimeMillis() - start) + "调用23转4接口的返回内容:"
                // + preSubmitRet);
                // 4,正式提交
                preOrderSubMessage(exchange, preSubmitRet, msg);

            }
            else if ("1".equals(userInfo.get("isTransystem"))) {
                // CBSS，不跨系统办理，即4G互转，走老的套餐服务变更流程，先调用套餐服务变更接口再调用开户处理提交接口。
                Map changeMap = MapUtils.asMap("ordersId", msg.get("ordersId"), "serialNumber",
                        msg.get("serialNumber"), "opeSysType", "2");
                MapUtils.arrayPut(changeMap, msg, MagicNumber.COPYARRAY);
                List<Map> packageElement = new ArrayList<Map>();
                Map productInfo = (Map) userInfo.get("productInfo");
                if (IsEmptyUtils.isEmpty(productInfo)) {
                    throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "4G互转未传入产品节点");
                }
                // 准备包信息节点
                List<Map> packageInfos = (List<Map>) productInfo.get("packageInfo");
                if (!IsEmptyUtils.isEmpty(packageInfos)) {
                    for (Map packageInfo : packageInfos) {
                        Map packageItem = new HashMap();
                        packageItem.put("packageId", packageInfo.get("packageId"));
                        packageItem.put("elementType", "D");
                        packageItem.put("modType", "0");
                        List<Map> elementInfos = (List<Map>) packageInfo.get("elementInfo");
                        for (Map elementInfo : elementInfos) {
                            packageItem.put("elementId", elementInfo.get("elementCode"));
                        }
                        packageElement.add(packageItem);
                    }
                }
                Object priproductId = productInfo.get("priproductId");// 原套餐ID
                Object tarproductId = productInfo.get("tarproductId");// 目标套餐ID
                if (IsEmptyUtils.isEmpty(priproductId) || IsEmptyUtils.isEmpty(tarproductId)) {
                    throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "变更产品没有传入原套餐或模板套餐,请检查");
                }
                // 准备产品信息
                List<Map> changeproductInfo = new ArrayList<Map>();
                Map oldProductItem = new HashMap();// 退订的产品
                oldProductItem.put("productId", priproductId);
                oldProductItem.put("productMode", "1");
                oldProductItem.put("optType", "01");

                Map newProductItem = new HashMap();// 订购的产品
                newProductItem.put("productId", tarproductId);
                newProductItem.put("productMode", "1");
                newProductItem.put("optType", "00");
                // 将包和元素放入订购信息中
                newProductItem.put("packageElement", packageElement);
                changeproductInfo.add(oldProductItem);
                changeproductInfo.add(newProductItem);
                changeMap.put("productInfo", changeproductInfo);
                Exchange presubmitExchange = ExchangeUtils.ofCopy(exchange, changeMap);
                // 调用CBSS预提交，并需要获取到预提交的返回信息
                NewChangeProduct4CBProcessor change = new NewChangeProduct4CBProcessor();
                change.applyParams(new String[] {});
                change.process(presubmitExchange);
                Map retMap = presubmitExchange.getOut().getBody(Map.class);
                System.out.println(body.get("apptx") + "套餐变更预提交返回:" + retMap);

                // 正式提交
                preOrderSubMessage(exchange, retMap, msg);

            }
            else {

            }
        }

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
        Object apptx = exchange.getIn().getBody(Map.class).get("apptx");
        // long start = System.currentTimeMillis();
        // System.out.println(apptx + ",调用AOP23转4接口之前,开始拼装参数的时间:" + getTime());
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
            if ("0001".equals(checkCustRet.get("code"))) {// 没有客户资料
                Map customerInfo = new HashMap();

                customerInfo.put("authTag", "0");// 鉴权标识 0：未鉴权,1：已鉴权 FIXME 从哪里取
                customerInfo.put("realNameType", "1");// 客户实名标识 0：实名,1：匿名 FIXME
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
                Map customerInfo = new HashMap();

                customerInfo.put("authTag", "0");// 鉴权标识 0：未鉴权,1：已鉴权 FIXME 从哪里取
                customerInfo.put("realNameType", "1");// 客户实名标识 0：实名,1：匿名 FIXME
                if ("0,1,3".contains((String) checkCustRet.get("certTag"))) {
                    customerInfo.put("realNameType", "0");// 客户实名标识 0：实名,1：匿名 FIXME
                }
                customerInfo.put("custType", "1"); // 新老客户标识 0：新增客户,1：老客户
                Map newCustomerInfo = new HashMap();
                newCustomerInfo.put("certType", checkCustRet.get("certType"));// 证件类型
                newCustomerInfo.put("certNum", checkCustRet.get("certNum"));// 证件号码
                newCustomerInfo.put("certAdress", checkCustRet.get("certAdress"));// 证件地址
                if (IsEmptyUtils.isEmpty(checkCustRet.get("certAdress"))) {
                    newCustomerInfo.put("certAdress", custInfo.get("certAddr"));// 证件地址
                }
                newCustomerInfo.put("customerName", checkCustRet.get("customerName"));// 客户姓名默认为证件名称
                newCustomerInfo.put("certExpireDate", checkCustRet.get("certExpireDate"));// 证件结束日期
                if (IsEmptyUtils.isEmpty(checkCustRet.get("certExpireDate"))) {
                    newCustomerInfo.put("certExpireDate", custInfo.get("certEndDate"));// 证件结束日期
                }
                newCustomerInfo.put("contactPerson", checkCustRet.get("contactPerson"));
                newCustomerInfo.put("contactPhone", checkCustRet.get("contactPhone"));// 暂时放该号码
                newCustomerInfo.put("contactAddress", newCustomerInfo.get("certAdress"));
                newCustomerInfo.put("custType", checkCustRet.get("customerType"));// 证件类型

                List<Map> newCustomer = new ArrayList<Map>();
                newCustomer.add(newCustomerInfo);

                customerInfo.put("newCustomerInfo", newCustomer);
                customer.add(customerInfo);
            }
            transMsg.put("customerInfo", customer);
            // 账户信息节点,先默认为老账户(生产上捞到的数据都没有传该节点)
            // List<Map> acctInfoList = new ArrayList<Map>();
            // Map acct = new HashMap();
            // acct.put("createOrExtendsAcct", "0");// 新建账户
            // acct.put("accountPayType", "10");// 账户付费方式,默认10-现金
            // acctInfoList.add(acct);
            // transMsg.put("acctInfo", acctInfoList);
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
            // System.out.println(apptx + ",调用AOP23转4接口之前,拼装参数结束的时间:" + getTime() + ",拼装参数用时:"
            // + (System.currentTimeMillis() - start));
            // 调用AOP的23转4接口
            // start = System.currentTimeMillis();
            Exchange tempExchange = ExchangeUtils.ofCopy(exchange, transMsg);
            // long end = System.currentTimeMillis();
            // System.out.println(apptx + ",copy参数用时:" + (end - start));
            long start = System.currentTimeMillis();
            TransToCbssProcessor trans = new TransToCbssProcessor();
            trans.applyParams(new String[] {});
            // long end1 = System.currentTimeMillis();
            // System.out.println(apptx + ",applyParams用时:" + (end - end1));
            // System.out.println(apptx + ",调用processor的开始时间:" + getTime());
            trans.process(tempExchange);
            System.out.println(apptx + "time,调用AOP23转4用时:" + (System.currentTimeMillis() - start));
            return tempExchange.getOut().getBody(Map.class);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用23转4预提交报错:" + e.getMessage());
        }
    }

    /**
     * 调用cbss的客户资料校验接口
     * @param exchange
     * @param msg
     * @param threePartMsg
     * @return
     * @throws Exception
     */
    private Map checkCustToCbssByCertNum(Exchange exchange, Map msg, Map threePartRet) throws Exception {
        try {

            Map custCheckMsg = new HashMap();
            MapUtils.arrayPut(custCheckMsg, msg, MagicNumber.COPYARRAY);
            custCheckMsg.put("checkType", "0");// 校验类型:证件校验
            custCheckMsg.put("opeSysType", "2");// 业务系统:CBSS
            custCheckMsg.put("serviceClassCode", "0000");// 号码类型:移动业务

            Map custInfo = (Map) threePartRet.get("custInfo");
            String certTypeCode = null;
            if ("17|18|11|76|91|97".contains((String) msg.get("province"))) {
                // 转换证件类型,在三户接口中,证件类型已经从cb转换为aop规范 create by zhaok 08/03
                certTypeCode = CertTypeChangeUtils.certTypeCbss2Mall(custInfo.get("certTypeCode") + "");
            }
            else {
                // 转换证件类型,在三户接口中,证件类型已经从全业务规范转换为aop规范
                certTypeCode = CertTypeChangeUtils.certTypeFbs2Mall(custInfo.get("certTypeCode") + "");
            }

            if (IsEmptyUtils.isEmpty(custInfo.get("certCode")) || IsEmptyUtils.isEmpty(custInfo.get("certTypeCode"))) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "三户接口未返回证件信息");
            }
            // 转换证件类型,在三户接口中,证件类型已经从全业务规范(或cb规范)转换为了aop规范,现在要转成cb规范
            // 调用北六aop暂时不用做转换
            // String certTypeCode = CertTypeChangeUtils.certTypeMall2Cbss(custInfo.get("certTypeCode") + "");
            custCheckMsg.put("certType", certTypeCode);// 证件类型
            custCheckMsg.put("certNum", custInfo.get("certCode"));// 证件号码
            custCheckMsg.put("certName", custInfo.get("custName"));// 证件姓名
            custCheckMsg.put("serType", "1");// 受理类型,默认后付费,不知道怎么取 FIXME
            Exchange tempExchange = ExchangeUtils.ofCopy(exchange, custCheckMsg);
            tempExchange.setMethod("ecaop.trades.query.comm.cust.check");// 设置为客户资料校验
            tempExchange.setMethodCode("cuck");// 设置为客户资料校验

            // 调用客户资料校验接口
            long start = System.currentTimeMillis();
            new TransReqParamsMappingProcessor().process(tempExchange);
            AopCall call = new AopCall();
            call.applyParams(new String[] { "ecaop.comm.conf.url.ec-aop.rest" });
            call.process(tempExchange);
            new CheckOweProcessors().process(tempExchange);
            new CheckOweByUserIdProcessors().process(tempExchange);
            System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "time,调用客户资料校验用时:"
                    + (System.currentTimeMillis() - start));
            Map retMap = JSON.parseObject(tempExchange.getOut().getBody() + "");
            // System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "调用客户资料校验接口 返回的结果是："
            // + tempExchange.getOut().getBody());
            // System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "调用客户资料校验接口 返回的结果是："
            // + JSON.parseObject(tempExchange.getOut().getBody() + ""));
            // System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "调用客户资料校验接口 返回的结果是："
            // + tempExchange.getOut().getBody(String.class));

            if (!IsEmptyUtils.isEmpty(retMap.get("code")) && !"0001".contains((String) retMap.get("code"))) {
                throw new EcAopServerBizException((String) retMap.get("code"), (String) retMap.get("detail"));
            }
            return retMap;

        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用CBSS客户资料校验报错:" + e.getMessage());
        }
    }

    /**
     * 直接调用省份的三户接口获取信息
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    private Map checkUserInfoByNumber(Exchange exchange, Map msg) throws Exception {
        msg.put("tradeTypeCode", "9999");
        // serviceClassCode字段上游必传 create by zhaok 7/26
        // msg.put("serviceClassCode", "0050");
        msg.put("getMode", "101001101010001001000000000000");
        // System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "调用三户之前的时间"
        // + GetDateUtils.getSysdateFormat());
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
                lan.preData(pmp[2], threeExchange);
            }
            else {
                lan.preData(pmp[3], threeExchange);
            }
            CallEngine.wsCall(threeExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + provinceCode);
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threeExchange);
            System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "time,调用省份三户用时:"
                    + (System.currentTimeMillis() - start));
        }
        else {
            long start = System.currentTimeMillis();
            lan.preData(pmp[4], threeExchange);
            CallEngine.wsCall(threeExchange, "ecaop.comm.conf.url.osn.services.usrser");
            lan.xml2Json("ecaop.masb.chku.checkUserTemplate", threeExchange);
            System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "time,调用省份三户用时:"
                    + (System.currentTimeMillis() - start));
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
     * 拼装正式提交参数
     * @param outMap
     * @param inMap
     * @param msg
     * @throws Exception
     */
    private void preOrderSubMessage(Exchange exchange, Map preSubmitRet, Map msg) {
        try {
            Map submitMsg = Maps.newHashMap();
            MapUtils.arrayPut(submitMsg, msg, MagicNumber.COPYARRAY);

            List<Map> subOrderSubReq = new ArrayList<Map>();

            // 23转4返回provOrderId为正式提交用的,bssOrderId是ESS订单号,但是两个值一样,totalFee一般为0
            submitMsg.put("provOrderId", preSubmitRet.get("bssOrderId"));
            // 套餐变更接口不返回provOrderId字段,默认取返回的bssOrderId字段
            Object orderNo = preSubmitRet.get("provOrderId");
            submitMsg.put("orderNo", IsEmptyUtils.isEmpty(orderNo) ? preSubmitRet.get("bssOrderId") : orderNo);
            Map subOrderSubMap = new HashMap();

            // 处理预提交返回的费用信息
            int totalFee = 0;
            if (!IsEmptyUtils.isEmpty(preSubmitRet.get("totalFee"))) {
                totalFee = (Integer) TransFeeUtils.transFee(preSubmitRet.get("totalFee"), 1);
            }
            List<Map> feeInfo = (ArrayList<Map>) preSubmitRet.get("feeInfo");
            if (!IsEmptyUtils.isEmpty(feeInfo)) {
                for (Map fee : feeInfo) {
                    fee.put("feeCategory", fee.get("feeCategory"));
                    fee.put("feeId", fee.get("feeId"));
                    fee.put("feeDes", fee.get("feeDes"));
                    // fee.put("feeCategory", fee.get("feeMode"));暂时注掉，应该是取错了。
                    // fee.put("feeId", fee.get("feeTypeCode"));
                    // fee.put("feeDes", fee.get("feeTypeName"));
                    fee.put("origFee", fee.get("oldFee"));
                    fee.put("isPay", "0");
                    fee.put("calculateTag", "N");
                    fee.put("payTag", "1");
                    fee.put("calculateId", GetSeqUtil.getSeqFromCb());
                    fee.put("calculateDate", DateUtils.getDate());
                }
                subOrderSubMap.put("feeInfo", feeInfo);
            }

            submitMsg.put("origTotalFee", totalFee);
            subOrderSubMap.put("subProvinceOrderId", submitMsg.get("provOrderId"));
            subOrderSubMap.put("subOrderId", submitMsg.get("orderNo"));
            subOrderSubReq.add(subOrderSubMap);
            submitMsg.put("subOrderSubReq", subOrderSubReq);

            long start = System.currentTimeMillis();
            Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMsg);
            lan.preData(pmp[1], submitExchange);
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            try {
                lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);
            }
            catch (Exception e) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "CBSS正式提交返回:" + e.getMessage());
            }
            System.out.println(exchange.getIn().getBody(Map.class).get("apptx") + "time,调用cbss正式提交用时:"
                    + (System.currentTimeMillis() - start));
            // 处理正式提交的返回,取预提交返回的订单号返回
            Message out = new DefaultMessage();
            out.setBody(MapUtils.asMap("provOrderId", submitMsg.get("provOrderId")));
            exchange.setOut(out);
        }
        catch (Exception e1) {
            e1.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用CBSS正式提交接口报错:" + e1.getMessage());
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

    /**
     * 获取到毫秒的时间,打桩用
     * @return
     */
    private static String getTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return format.format(new Date());
    }

    public static void main(String[] args) {
        System.out.println(getTime());
    }

}
