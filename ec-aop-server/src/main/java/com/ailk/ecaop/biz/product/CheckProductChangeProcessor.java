package com.ailk.ecaop.biz.product;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.sys.EcAopServerSysException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 产品查询变更校验接口
 * 1、网别为2/3G时，无特殊处理,直接通过全业务平台调用省份；
 * 2、网别为4G时，做产品信息查询时，AOP通过查询数据库返回产品信息；用户做变更校验时，AOP调用三户接口返回信息。
 * @author wangmc
 */
@EcRocTag("checkProductChangeProcessor")
public class CheckProductChangeProcessor extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];
    private static final String[] PARAM_ARRAY = { "ecaop.ecsb.ccpq.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping" };// 全业务接口,三户接口
    LanUtils lan = new LanUtils();
    DealNewCbssProduct n25Dao = new DealNewCbssProduct();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        String businessType = (String) msg.get("businessType");
        // 校验传参的正确性
        if ("01".equals(businessType) && IsEmptyUtils.isEmpty(msg.get("productId"))) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "信息查询操作请传入产品ID");
        }
        else if ("02".equals(businessType) && IsEmptyUtils.isEmpty(msg.get("serialNumber"))) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "变更校验操作请传入服务号码");
        }

        String netType = (String) msg.get("netType");
        Map retMap = new HashMap();
        // 2,3G操作直接调用全业务接口
        if ("01".equals(netType) || "02".equals(netType)) {
            try {
                msg.put("provinceId", msg.get("province"));
                body.put("msg", msg);
                exchange.getIn().setBody(body);
                lan.preData(pmp[0], exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.productchgser");
                lan.xml2Json("ecaop.trades.ccpq.template", exchange);
            }
            catch (EcAopServerSysException e) {
                e.printStackTrace();
                throw new EcAopServerBizException(e.getCode(), "调用下游接口返回失败:" + e.getMessage());
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用下游接口返回失败:" + e.getMessage());
            }
            Map ret = exchange.getOut().getBody(Map.class);
            List<Map> respInfos = (List<Map>) ret.get("respInfo");
            if (!IsEmptyUtils.isEmpty(respInfos)) {
                retMap.putAll(respInfos.get(0));
            }
        }
        else if ("03".equals(netType)) {// 4G
            if ("01".equals(businessType)) {// 产品信息查询,直接查询数据库
                retMap = queryProductInfo(msg);
            }
            else if ("02".equals(businessType)) {// 变更校验,查询三户信息,并处理返回
                retMap = getThreePartProductInfo(exchange, msg);
            }
            else {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "传入的[操作类型]参数有误,businessType:"
                        + businessType);
            }
        }
        else {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "传入的[网别]参数有误,netType:" + netType);
        }
        DefaultMessage out = new DefaultMessage();
        out.setBody(retMap);
        exchange.setOut(out);
    }

    /**
     * 根据产品ID去库里查询产品下的所有元素
     * @param msg
     * @return
     */
    private Map queryProductInfo(Map msg) {
        String productId = (String) msg.get("productId");
        Map inMap = MapUtils.asMap("PRODUCT_ID", productId, "PROVINCE_CODE", "00" + msg.get("province"));
        inMap.put("EPARCHY_CODE", ChangeCodeUtils.changeEparchy(msg));
        List<Map> productInfo = n25Dao.queryAllPackageByProductId(inMap);
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "根据传入的产品ID [" + productId
                    + "]未查询到产品信息,请确认产品ID");
        }
        Map retMap = new HashMap();
        retMap.put("productId", productInfo.get(0).get("PRODUCT_ID"));
        retMap.put("productName", productInfo.get(0).get("PRODUCT_NAME"));
        retMap.put("startDate", productInfo.get(0).get("PRODUCT_START_DATE"));
        retMap.put("endDate", productInfo.get(0).get("PRODUCT_END_DATE"));

        Map<Object, Map> packageInfos = new HashMap<Object, Map>();// (包ID,没有元素信息的包信息)

        List<String> packageIds = new ArrayList<String>();// 包ID,用于获取包下的元素信息
        for (Map product : productInfo) {
            Map packageInfo = new HashMap();
            packageInfo.put("packageId", product.get("PACKAGE_ID"));
            packageInfo.put("packageName", product.get("PACKAGE_NAME"));
            packageInfo.put("packageType", "0" + product.get("PACKAGE_TYPE_CODE"));// 包类型,库里面是一位的 FIXME
            packageInfo.put("startDate", product.get("PACKAGE_START_DATE"));
            packageInfo.put("endDate", product.get("PACKAGE_END_DATE"));
            packageInfo.put("isDefault", product.get("DEFAULT_TAG"));
            packageInfo.put("isRequired", product.get("FORCE_TAG"));

            packageInfos.put(product.get("PACKAGE_ID"), packageInfo);
            packageIds.add(String.valueOf(product.get("PACKAGE_ID")));// 后面统一查询
        }

        // 获取所有包中的所有元素信息
        List<Map> elements = n25Dao.queryAllElementByPackageId(MapUtils.asMap("packageIds", packageIds));
        // List<Map> elements = n25Dao.queryTest(MapUtils.asMap("packageIds", packageIds));

        List<Map> packages = new ArrayList<Map>();// 放所有包的信息(包中含有元素信息)
        List<Map> elementInfos = new ArrayList<Map>();
        Object packageId = elements.get(0).get("PACKAGE_ID");
        for (Map element : elements) {
            if (!packageId.equals(element.get("PACKAGE_ID"))) {// 如果是新的包,将上一个包ID中的元素信息放到原包信息MAP中
                Map packageInfo = packageInfos.get(packageId);
                packageInfo.put("elementInfo", elementInfos);
                packages.add(packageInfo);
                elementInfos = new ArrayList<Map>();
                packageId = elements.get(0).get("PACKAGE_ID");
            }
            Map elementInfo = new HashMap();
            elementInfo.put("elementCode", element.get("ELEMENT_ID"));
            elementInfo.put("elementName", element.get("ELEMENT_NAME"));
            elementInfo.put("elementType", element.get("ELEMENT_TYPE"));
            elementInfo.put("startDate", element.get("START_DATE"));
            elementInfo.put("endDate", element.get("END_DATE"));
            elementInfo.put("isRequired", element.get("DEFAULT_TAG"));
            elementInfo.put("isDefault", element.get("FORCE_TAG"));
            elementInfos.add(elementInfo);
        }
        if (!IsEmptyUtils.isEmpty(elementInfos)) {
            Map packageInfo = packageInfos.get(packageId);
            packageInfo.put("elementInfo", elementInfos);
            packages.add(packageInfo);
        }
        retMap.put("packageInfo", packages);
        return retMap;
    }

    /**
     * 查询三户
     * @param exchange
     * @param msg
     * @return
     * @throws Exception
     */
    public Map getThreePartProductInfo(Exchange exchange, Map msg) throws Exception {
        // INFO_TAG第27位传1，获取other_info节点：create by zhaok 08/30
        Map threePartMap = MapUtils.asMap("getMode", "1111111111100013111000000110001", "serialNumber",
                msg.get("serialNumber"), "tradeTypeCode", "9999");
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        MapUtils.arrayPut(threePartMap, msg, copyArray);

        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        lan.preData(pmp[1], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);

        Map out = threePartExchange.getOut().getBody(Map.class);

        // 校验是否没有返回三户信息
        String[] infoKeys = new String[] { "userInfo" };
        Map errorDetail = MapUtils.asMap("userInfo", "用户信息");
        Map temp = new HashMap();
        for (String infoKey : infoKeys) {
            if (IsEmptyUtils.isEmpty(out.get(infoKey))) {
                throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调三户未返回" + errorDetail.get(infoKey));
            }
            temp.put(infoKey, ((List<Map>) out.get(infoKey)).get(0));
        }
        // 校验是否没返回老产品信息
        if (IsEmptyUtils.isEmpty(((Map) temp.get("userInfo")).get("productInfo"))) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调三户未返回产品信息");
        }

        Map userInfo = (Map) temp.get("userInfo");
        // Map custInfo = (Map) temp.get("custInfo");
        // Map acctInfo = (Map) temp.get("acctInfo");
        List<Map> productInfo = (List<Map>) userInfo.get("productInfo");
        Map retMap = new HashMap();
        retMap.put("productId", userInfo.get("productId"));
        retMap.put("productName", userInfo.get("productName"));
        // 获取产品的生效失效时间
        retMap = getProductEffectiveDate(retMap, productInfo, userInfo.get("productId"));
        // 获取套餐资费
        List<Map> productFee = n25Dao.qryProductFee(MapUtils.asMap("PRODUCT_ID", userInfo.get("productId")));
        if (IsEmptyUtils.isEmpty(productFee)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "从产品表未获取到产品[" + userInfo.get("productId")
                    + "]的费用信息");
        }
        retMap.put("packagePrice", String.valueOf(productFee.get(0).get("NEWPRODUCTFEE")));// 套餐资费 1-是,0-否
        retMap.put("isContract", "0");// 是否含有合约 1-是,0-否
        if (!IsEmptyUtils.isEmpty(userInfo.get("productInfo"))) {
            // 是否存在生效的合约
            List<Map> productInfoList = (List<Map>) userInfo.get("productInfo");
            for (Map activity : productInfoList) {
                if ("50".contains(activity.get("productMode").toString())) {
                    String activityEndDate = (String) activity.get("productInactiveTime");
                    if (GetDateUtils.getDate().compareTo(activityEndDate) < 0) {
                        retMap.put("isContract", "1");
                        break;
                    }
                }
            }
        }
        // 获取最低消费和是否靓号字段 qryProductFee在处理包信息中
        retMap.put("isTransnet", "0");// 是否允许转网别 0-否,1-是 4G不允许转23G
        retMap.put("isTransresourcestype", "0");// 是否允许转付费类型 0-否,1-是 先默认否 FIXME
        retMap.put("isNicenum", "0");// 默认为0,非靓号,后面再处理
        retMap.put("lowlimitFee", "0");// 低销默认为0

        retMap.put("isExistproduct", "0");// 是否存在未生效产品 0-否,1-是
        if (!IsEmptyUtils.isEmpty(userInfo.get("nextProductId"))
                && !userInfo.get("productId").equals(userInfo.get("nextProductId"))) {
            retMap.put("isExistproduct", "1");
            retMap.put("changedOrderNum", "");// 未生效产品省分订单号...哪里取 TODO
            retMap.put("changedProductId", userInfo.get("nextProductId"));
            retMap.put("changedProductName", userInfo.get("nextProductName"));
        }
        retMap = dealPackageInfoByThreePart(retMap, userInfo);
        // 获取产品企业编码以及产品别名 create by zhaok 08/30
        List<Map> otherInfoList = (List<Map>) userInfo.get("otherInfo");
        if (!IsEmptyUtils.isEmpty(otherInfoList)) {
            for (Map otherInfo : otherInfoList) {
                String rsrvValueCode = (String) otherInfo.get("rsrvValueCode");
                String startDate = (String) otherInfo.get("startDate");
                String endDate = (String) otherInfo.get("endDate");
                int resultBef = GetDateUtils.getDate().compareTo(startDate);
                int resultAft = GetDateUtils.getDate().compareTo(endDate);
                // 当rsrvValueCode为TIBM且结束时间为20501231235959返回
                if ("TIBM".equals(rsrvValueCode) && resultBef > 0 && resultAft < 0) {
                    // 企业编码
                    String rsrvValue = (String) otherInfo.get("rsrvValue");
                    // 产品别名
                    String rsrvStr1 = (String) otherInfo.get("rsrvStr1");
                    // 当这两个值都不为空时 ,返回
                    if (!IsEmptyUtils.isEmpty(rsrvValue) && !IsEmptyUtils.isEmpty(rsrvStr1)) {
                        // 生效编码
                        retMap.put("companyId", rsrvValue);
                        // 生效别名
                        retMap.put("productNameX", rsrvStr1);
                    }
                }
                else if ("TIBM".equals(rsrvValueCode) && resultBef < 0) {
                    // 等待产品别名
                    String rsrvStr1 = (String) otherInfo.get("rsrvStr1");
                    if (!IsEmptyUtils.isEmpty(rsrvStr1)) {
                        retMap.put("changedProductName", rsrvStr1);
                    }
                }
            }
        }

        // 是否团购用户标识改造 0-团购用户,1-非团购用户
        List<Map> paraList = (List<Map>) out.get("para");
        if (!IsEmptyUtils.isEmpty(paraList)) {
            for (Map para : paraList) {
                if ("WHETHER_GROUP_USER".equals(para.get("paraId"))
                        && "0|1".contains(String.valueOf(para.get("paraValue")))) {
                    retMap.put("grpUserFlag", para.get("paraValue"));
                }
            }
        }

        return retMap;
    }

    /**
     * 获取三户返回的主产品的生效失效时间
     * @param retMap
     * @param productInfo
     * @param productId
     * @return
     */
    private Map getProductEffectiveDate(Map retMap, List<Map> productInfo, Object productId) {
        if (!IsEmptyUtils.isEmpty(productInfo)) {
            for (Map product : productInfo) {
                if (productId.equals(product.get("productId"))) {
                    retMap.put("startDate", product.get("productActiveTime"));
                    retMap.put("endDate", product.get("productInactiveTime"));
                    break;
                }
            }
        }
        return retMap;
    }

    /**
     * 处理三户返回的包信息
     * @param retMap
     * @param userInfo
     * @return
     */
    private Map dealPackageInfoByThreePart(Map retMap, Map userInfo) {
        List<Map> discntInfo = (List<Map>) userInfo.get("discntInfo");
        if (!IsEmptyUtils.isEmpty(discntInfo)) {
            Map<Object, Map> packageInfoMap = new HashMap<Object, Map>();
            for (Map dis : discntInfo) {
                Object packageId = dis.get("packageId");
                Map packInfo = new HashMap();
                List<Map> elementInfo = new ArrayList<Map>();
                // 获取已经拼过的值
                if (!IsEmptyUtils.isEmpty(packageInfoMap.get(packageId))) {
                    packInfo = packageInfoMap.get(packageId);
                    elementInfo = (List<Map>) packInfo.get("feeInfo");
                }
                else {
                    packInfo.put("packageId", packageId);
                    // 查库获取包的信息
                    List<Map> pacList = n25Dao.queryPackageInfoByPackage(MapUtils.asMap("PACKAGE_ID", packageId));
                    if (!IsEmptyUtils.isEmpty(pacList) && !IsEmptyUtils.isEmpty(pacList.get(0))) {
                        packInfo.put("packageName", pacList.get(0).get("PACKAGE_NAME"));
                        packInfo.put("packageType", pacList.get(0).get("PACKAGE_TYPE_CODE"));
                        packInfo.put("startDate", pacList.get(0).get("START_DATE"));
                        packInfo.put("endDate", pacList.get(0).get("END_DATE"));
                    }
                }
                Map eleMap = new HashMap();
                eleMap.put("feeCode", dis.get("discntCode"));
                eleMap.put("startDate", dis.get("startDate"));
                // 从库里获取资费的信息
                List<Map> disInfo = n25Dao.queryDiscntData(MapUtils.asMap("DISCNT_CODE", dis.get("discntCode")));
                if (!IsEmptyUtils.isEmpty(disInfo) && !IsEmptyUtils.isEmpty(disInfo.get(0))) {
                    eleMap.put("feeName", disInfo.get(0).get("DISCNT_NAME"));
                }
                eleMap.put("endDate", dis.get("endDate"));
                elementInfo.add(eleMap);
                packInfo.put("feeInfo", elementInfo);
                packageInfoMap.put(packageId, packInfo);

                // 获取最低消费,判断是否靓号
                List<Map> attrInfo = (List<Map>) dis.get("attrInfo");
                for (Map attr : attrInfo) {
                    if ("lowCost".equals(String.valueOf(attr.get("attrCode")))) {
                        retMap.put("lowlimitFee", attr.get("attrValue"));
                    }
                    if ("88888888|88888881".contains(String.valueOf(attr.get("attrCode")))
                            && Long.valueOf(dealDate4AddMonth(1, 1)) < Long.valueOf((String) (attr.get("endDate")))) {
                        retMap.put("isNicenum", "1");
                    }
                }
            }

            Set keySet = packageInfoMap.keySet();
            List<Map> packageInfos = new ArrayList<Map>();
            for (Object packagetId : keySet) {
                packageInfos.add(packageInfoMap.get(packagetId));
            }
            retMap.put("subproductInfo", packageInfos);
        }
        return retMap;
    }

    /**
     * 转换日期信息,从三户搬过来的
     * @param month
     * @param day
     * @return
     */
    private static String dealDate4AddMonth(int month, int day) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        cal.add(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return format.format(cal.getTime());
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
