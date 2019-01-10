package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

/**
 * 产品处理的公用方法
 * @author wangmc
 *         2017-02-24
 */
public class NewProductUtils {

    // 去重时用来对比的key
    private static Map<String, String[]> keysMap;

    static {
        keysMap = new HashMap<String, String[]>();
        keysMap.put("discntList", new String[] { "productId", "packageId", "discntCode" });
        keysMap.put("svcList", new String[] { "productId", "packageId", "serviceId" });
        keysMap.put("spList", new String[] { "productId", "packageId", "spServiceId" });
        keysMap.put("productTypeList", new String[] { "productId", "productMode", "productTypeCode" });
        keysMap.put("productList", new String[] { "productId", "productMode", "brandCode" });
    }

    /**
     * 1,处理主产品资费的生失效时间 by wangmc 20170223
     * 可以手动传入产品或活动的生失效时间
     * @param discntId
     * @param monthFirstDay
     * @param monthLasttDay
     * @param productFlag N6-老产品逻辑,N25-新产品逻辑
     * @return
     */
    public static Map getMainProDiscntEffectTime(String discntId, String monthFirstDay, String monthLasttDay,
            String productFlag) {
        String monthLastDay = "";
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        List<Map> activityList = new ArrayList<Map>();
        if ("N6".equals(productFlag)) {
            LanOpenApp4GDao dao = new LanOpenApp4GDao();
            activityList = dao.queryDiscntDate(actiPeparam);
        }
        else if ("N25".equals(productFlag)) {
            DealNewCbssProduct n25Dao = new DealNewCbssProduct();
            activityList.add(n25Dao.qryDiscntAttr(actiPeparam));
        }
        Map activityListMap = null;
        if (activityList != null && activityList.size() > 0) {
            activityListMap = activityList.get(0);

        }
        else {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + discntId + "】的资费信息");

        }
        try {
            String resActivityper = String.valueOf(activityListMap.get("END_OFFSET"));// 失效效偏移时间
            String endUnit = String.valueOf(activityListMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endEnableTag = String.valueOf(activityListMap.get("END_ENABLE_TAG"));// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
            System.out.println("endEnableTag=============" + endEnableTag);
            String endDate = String.valueOf(activityListMap.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间

            String enableTag = String.valueOf(activityListMap.get("ENABLE_TAG"));// 针对开始时间 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = String.valueOf(activityListMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String startOffset = String.valueOf(activityListMap.get("START_OFFSET"));// 生效偏移时间
            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && "1".equals(enableTag) && !"null".equals(startOffset)
                    && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                // 先判断该资费是否已生效 by wangmc 20170223
                if (GetDateUtils.getDate().compareTo(endDate) > 0) {
                    actiPeparam.put("discntEndTag", "true");// 已失效
                    return actiPeparam;
                }
                monthLasttDay = endDate;
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1|2".contains(endEnableTag)) {// 开户时2跟一一样的，退订时END_ENABLE_TAG=2 且 end_mode=1
                                                      // 表示退订的时候为月底失效--河南陈亚丽（截图）
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(resActivityper)
                                + Integer.parseInt(startOffset));
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);
            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            actiPeparam.put("discntEndTag", "false");// 未失效
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }

    }

    /**
     * 3,处理主产品资费的生失效时间 by wangmc 20170223
     * 生效时间计算规则:ENABLE_TAG-0:取传入的开始时间和START_ABSOLUTE_DATE中的最大值(最晚值);为1时,根据START_UNIT和START_OFFSET依据当前时间计算偏移
     * 失效时间计算规则:END_ENABLE_TAG-0:END_ABSOLUTE_DATE有值取该值,无值取2050;为1时,根据END_UNIT和END_OFFSET以计算出的生效时间为基础计算偏移
     * @param discntId
     * @param monthFirstDay
     * @param monthLasttDay
     * @return
     */
    public static Map getMainProDiscntEffectTime4ChangePro(String discntId, String monthFirstDay, String monthLasttDay) {
        String monthLastDay = "";
        Map actiPeparam = new HashMap();
        actiPeparam.put("DISCNT_CODE", discntId);
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map activityMap = n25Dao.qryDiscntAttr(actiPeparam);
        if (IsEmptyUtils.isEmpty(activityMap)) {
            throw new EcAopServerBizException("9999", "在资费信息表中未查询到ID为【" + discntId + "】的资费信息");
        }
        try {
            String resActivityper = String.valueOf(activityMap.get("END_OFFSET"));// 失效效偏移时间
            String endUnit = String.valueOf(activityMap.get("END_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String endEnableTag = String.valueOf(activityMap.get("END_ENABLE_TAG"));// 针对结束时间 0-绝对时间（看开始时间） 1-相对时间
            System.out.println("endEnableTag=============" + endEnableTag);
            String endDate = String.valueOf(activityMap.get("END_ABSOLUTE_DATE"));// END_ENABLE_TAG为1时，需要才发此结束时间

            String enableTag = String.valueOf(activityMap.get("ENABLE_TAG"));// 针对开始时间 0-绝对时间（看开始时间） 1-相对时间
            String strStartUnit = String.valueOf(activityMap.get("START_UNIT"));// '0:天 1:自然天 2:月 3:自然月 4:年
            // 5:自然年';
            String startOffset = String.valueOf(activityMap.get("START_OFFSET"));// 生效偏移时间

            // 处理主资费的开始时间,若ENABLE_TAG为0,则取传入的生效时间和START_ABSOLUTE_DATE的最大值(最晚).传入生效时间应为当前时间或者次月1日,分别对应开户和套餐变更
            String startDate = String.valueOf(activityMap.get("START_ABSOLUTE_DATE"));
            if ("0".equals(enableTag) && !"null".equals(startDate)) {
                String transFirstDay = GetDateUtils.transDate(monthFirstDay, 14);
                if (transFirstDay.compareTo(startDate) < 0) {
                    monthFirstDay = startDate;
                }
            }

            // 如果值为空则进行默认处理
            if (!"null".equals(enableTag) && "1".equals(enableTag) && !"null".equals(startOffset)
                    && !"null".equals(strStartUnit)) {
                monthFirstDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(enableTag),
                        Integer.parseInt(strStartUnit), GetDateUtils.getSysdateFormat(), Integer.parseInt(startOffset));
            }
            if (!"null".equals(enableTag) && "0".equals(enableTag)) {
                startOffset = "0";
            }
            // 如果值为空则进行默认处理
            if (!"null".equals(endEnableTag) && "0".equals(endEnableTag) && !"null".equals(endDate)) {
                // 先判断该资费是否已生效 by wangmc 20170223
                if (GetDateUtils.getDate().compareTo(endDate) > 0) {
                    actiPeparam.put("discntEndTag", "true");// 已失效
                    return actiPeparam;
                }
                if (endDate.contains("20501231")) {
                    endDate = endDate.substring(0, 8) + "235959";
                }
                monthLasttDay = endDate;
            }
            if (!"null".equals(resActivityper) && !"null".equals(endUnit) && !"null".equals(endEnableTag)
                    && "1|2".contains(endEnableTag)) {// 开户时2跟一一样的，退订时END_ENABLE_TAG=2 且 end_mode=1
                                                      // 表示退订的时候为月底失效--河南陈亚丽（截图）
                monthLastDay = GetDateUtils.getSpecifyDateTime(Integer.parseInt(endEnableTag),
                        Integer.parseInt(endUnit), GetDateUtils.transDate(monthFirstDay, 19),/* GetDateUtils.
                                                                                              * getSysdateFormat(), */
                        Integer.parseInt(resActivityper)/* + Integer.parseInt(startOffset) */);
                // 结束月最后一天
                monthLasttDay = GetDateUtils.getSpecifyDateTime(1, 6, monthLastDay, -1);
                monthLasttDay = GetDateUtils.transDate(monthLasttDay, 14);
            }
            if (monthLasttDay.length() > 19) {
                monthLasttDay = monthLasttDay.substring(0, 19);
            }
            actiPeparam.put("monthLasttDay", monthLasttDay);
            actiPeparam.put("monthFirstDay", monthFirstDay);
            actiPeparam.put("discntEndTag", "false");// 未失效
            return actiPeparam;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "获取资费信息生失效时间失败，资费是：" + discntId);
        }

    }

    /**
     * 2,产品速率处理方法：如果外围没传或传的速率与库里默认速率一致，直接下发默认速率。若不一致则下发对应的速率。
     * @param productInfoResult
     * @param speed
     * @return
     */
    public List<Map> chooseSpeed(List<Map> productInfoResult, String speed) {
        // 如果当前速率为空，则直接下发productInfoResult里的速率
        if (IsEmptyUtils.isEmpty(speed)) {
            return productInfoResult;
        }
        // HSPA+(42M上网)-50105，(LTE)100M-50103,(4G+)300M-50107
        String speedId = "42".equals(speed) ? "50105" : "100".equals(speed) ? "50103" : "50107";
        // 取当前产品的默认速率

        for (int i = 0; i < productInfoResult.size(); i++) {
            String elementId = String.valueOf(productInfoResult.get(i).get("ELEMENT_ID"));
            if ("50103,50105,50107".contains(elementId)) {
                if (elementId.equals(speedId)) {
                    break;
                }
                Map speedParam = new HashMap();
                productInfoResult.remove(productInfoResult.get(i));
                speedParam.put("PRODUCT_ID", productInfoResult.get(i).get("PRODUCT_ID"));
                speedParam.put("ELEMENT_ID", speedId);// 此处为外围传入的速率
                productInfoResult.add(new DealNewCbssProduct().qryNewProductSpeed(speedParam));
            }
        }
        return productInfoResult;
    }

    public static void main(String[] args) {
        // List<Map> packageElement = new ArrayList<Map>();
        // packageElement.add(MapUtils.asMap("PRODUCT_ID", "111111111", "PRODUCT_MODE", "00", "PACKAGE_ID", "2222222",
        // "ELEMENT_TYPE_CODE", "D", "ELEMENT_ID", "3333333", "PROVINCE_CODE", "0010", "EPARCHY_CODE", "ZZZZ",
        // "DEFAULT_TAG", "0"));
        // List<Map> changePackageElement = new ArrayList<Map>();
        // changePackageElement.add(MapUtils.asMap("PRODUCT_ID", "111111111", "PRODUCT_MODE", "00", "PACKAGE_ID",
        // "2222222", "ELEMENT_TYPE_CODE", "D", "ELEMENT_ID", "3333333", "PROVINCE_CODE", "0010", "EPARCHY_CODE",
        // "ZZZZ", "DEFAULT_TAG", "0"));
        // // changePackageElement.add(MapUtils.asMap("PRODUCT_ID", "1111", "PRODUCT_MODE", "00", "PACKAGE_ID", "1111",
        // // "ELEMENT_TYPE_CODE", "D", "ELEMENT_ID", "3333333", "PROVINCE_CODE", "ZZZZ", "EPARCHY_CODE", "ZZZZ",
        // // "DEFAULT_TAG", "1", "ALL_PRODUCT_TAG", "ZZZZ"));
        // System.out.println(dealProductChange(packageElement, changePackageElement));
    }

    /**
     * 3,新产品逻辑中对中间表进行操作的公共方法,通过产品id在td_b_trans_change表查出来的元素,根据其默认标识DEFAULT_TAG对传进来的元素集进行操作.
     * DEFAULT_TAG = 1 (该元素不要下发) 将该元素从传进来的结果集中剔除;
     * DEFAULT_TAG = 0 (该元素需要下发) 将该元素加入到传进来的结果集中.
     * @author wangmc 20170319
     * @param changePackageElement
     * @return
     */
    public static List<Map> dealProductChange(List<Map> packageElement, List<Map> changePackageElement) {
        /**
         * 对产品根据按全国处理,按省份处理,按地市处理分组,过滤掉一个元素既有针对全国处理的数据又有针对某省的处理或者又有某地市的处理的情况
         * by wangmc 20170319
         */
        List<Map> countryDeal = new ArrayList<Map>();
        List<Map> provinceDeal = new ArrayList<Map>();
        List<Map> eparchyDeal = new ArrayList<Map>();
        // 1,根据省份地市编码分为:全国处理、特定省份处理、特定地市处理 三类
        for (int i = 0; i < changePackageElement.size(); i++) {
            Object dealProvince = changePackageElement.get(i).get("PROVINCE_CODE");
            Object dealEparchy = changePackageElement.get(i).get("EPARCHY_CODE");
            if (!"ZZZZ".equals(dealEparchy)) {// 针对特定地市处理
                eparchyDeal.add(changePackageElement.get(i));
            }
            else if (!"ZZZZ".equals(dealProvince)) {// 针对特定省份处理
                provinceDeal.add(changePackageElement.get(i));
            }
            else {// 针对全国处理
                countryDeal.add(changePackageElement.get(i));
            }
        }
        // 2,对同一个资费既有针对省份的处理又有针对全国处理的情况,剔除对全国的处理
        List<Map> countryRemove = new ArrayList<Map>();
        List<Map> provinceRemove = new ArrayList<Map>();
        for (int i = 0; i < countryDeal.size(); i++) {
            Map counInfo = countryDeal.get(i);
            for (int j = 0; j < provinceDeal.size(); j++) {
                Map proInfo = provinceDeal.get(j);
                if (counInfo.get("PRODUCT_ID").equals(proInfo.get("PRODUCT_ID"))
                        && counInfo.get("PRODUCT_MODE").equals(proInfo.get("PRODUCT_MODE"))
                        && counInfo.get("PACKAGE_ID").equals(proInfo.get("PACKAGE_ID"))
                        && counInfo.get("ELEMENT_TYPE_CODE").equals(proInfo.get("ELEMENT_TYPE_CODE"))
                        && counInfo.get("ELEMENT_ID").equals(proInfo.get("ELEMENT_ID"))) {

                    // 需去掉的针对全国处理
                    countryRemove.add(counInfo);
                }
            }
        }
        // 3,对同一个资费既有针对地市的处理又有针对省份处理的情况,剔除对省份的处理
        for (int i = 0; i < provinceDeal.size(); i++) {
            Map proInfo = provinceDeal.get(i);
            for (int j = 0; j < eparchyDeal.size(); j++) {
                Map epaMap = eparchyDeal.get(j);

                if (proInfo.get("PRODUCT_ID").equals(epaMap.get("PRODUCT_ID"))
                        && proInfo.get("PRODUCT_MODE").equals(epaMap.get("PRODUCT_MODE"))
                        && proInfo.get("PACKAGE_ID").equals(epaMap.get("PACKAGE_ID"))
                        && proInfo.get("ELEMENT_TYPE_CODE").equals(epaMap.get("ELEMENT_TYPE_CODE"))
                        && proInfo.get("ELEMENT_ID").equals(epaMap.get("ELEMENT_ID"))) {
                    // 需去掉的针对省份处理
                    provinceRemove.add(proInfo);
                }
            }
        }
        countryDeal.removeAll(countryRemove);
        provinceDeal.removeAll(provinceRemove);

        changePackageElement = new ArrayList<Map>();
        changePackageElement.addAll(countryDeal);
        changePackageElement.addAll(provinceDeal);
        changePackageElement.addAll(eparchyDeal);

        List<Map> addElement = new ArrayList<Map>();
        List<Map> removeElement = new ArrayList<Map>();
        for (Map elementInfo : changePackageElement) {
            if ("1".equals(elementInfo.get("DEFAULT_TAG")) && !IsEmptyUtils.isEmpty(packageElement)) {// 该元素不下发
                for (int i = 0; i < packageElement.size(); i++) {
                    // 针对全产品都要处理的元素
                    boolean isAllProduct = "ZZZZ".equals(elementInfo.get("ALL_PRODUCT_TAG"))
                            && elementInfo.get("ELEMENT_ID").equals(packageElement.get(i).get("ELEMENT_ID"));
                    boolean isThisElement = elementInfo.get("ELEMENT_ID").equals(
                            packageElement.get(i).get("ELEMENT_ID"))
                            && elementInfo.get("PACKAGE_ID").equals(packageElement.get(i).get("PACKAGE_ID"));
                    if (isAllProduct || isThisElement) {
                        removeElement.add(packageElement.get(i));
                        break;
                    }
                }
            }
            else if ("0".equals(elementInfo.get("DEFAULT_TAG"))) {// 该元素需要下发
                if ("ZZZZ".equals(elementInfo.get("ALL_PRODUCT_TAG"))) {// 标识该元素针对所有产品都下发
                    Map tempMap = new HashMap();
                    tempMap.put("PRODUCT_ID", packageElement.get(0).get("PRODUCT_ID"));
                    tempMap.put("ELEMENT_ID", elementInfo.get("ELEMENT_ID"));
                    tempMap.put("PROVINCE_CODE", elementInfo.get("PROVINCE_CODE"));
                    tempMap.put("EPARCHY_CODE", elementInfo.get("EPARCHY_CODE"));
                    // 因对全产品处理的元素数据中,产品ID和包ID均为1111,所以需要根据产品ID和元素ID到库中查询该元素的数据,若查询不到,则不添加
                    List<Map> addElementInfo = new DealNewCbssProduct().qryPackageElementByElementNew(tempMap);
                    if (!IsEmptyUtils.isEmpty(addElementInfo)) {
                        addElement.add(addElementInfo.get(0));
                    }
                }
                else {
                    addElement.add(elementInfo);
                }
            }
        }
        packageElement.removeAll(removeElement);
        packageElement.addAll(addElement);
        return packageElement;
    }

    /**
     * 该方法用于校验附加产品的失效时间 使用新库
     * 当返回值是Y时表示 附加产品的失效时间应该和合约保持一致
     * 当返回值是N时表示附加产品的失效时间是固定值需要自己计算
     * 返回值写死是X时，写死默认时间
     * 针对融合产品是必须传入对应cb侧的产品编码
     */
    public static String getEndDateType(String addProductId) {
        DealNewCbssProduct n25Dao = new DealNewCbssProduct();
        Map actProParam = new HashMap();
        String lengthType = "";
        actProParam.put("PRODUCT_ID", addProductId);
        List actProductInfo = n25Dao.queryAddProductEndDateType(actProParam);
        if (actProductInfo == null || actProductInfo.size() == 0) {
            lengthType = "X";
            return lengthType;
        }
        lengthType = (String) actProductInfo.get(0);
        return lengthType;
    }

    /**
     * 新的去重方法-wangmc 20170325
     * 重点:需要接收返回值
     * @param listMap
     * @param keys-需要去重的字段名集合的key
     *            discntList-资费节点, svcList-服务节点, spList-sp节点
     *            productTypeList-产品类型节点, productList-产品节点
     * @return
     */
    public static List<Map> newDealRepeat(List<Map> listMap, String checkKey) {
        List<Map> newList = new ArrayList<Map>();
        Map valueMap = new HashMap();
        for (int i = 0; i < listMap.size(); i++) {
            String values = "";
            for (String key : keysMap.get(checkKey)) { // 将需要对比的值拼起来,当做map的key
                values += listMap.get(i).get(key) + ",";
            }
            if (IsEmptyUtils.isEmpty(valueMap.get(values))) { // 该元素不重复
                valueMap.put(values, "true");
                newList.add(listMap.get(i));
            }
        }
        return newList;
    }

    /**
     * 新的去重方法的重载方法,可手动传入对比的字段名称-wangmc 20170410
     * 重点:需要接收返回值
     * @param listMap
     * @param checkRepeatKeys-比较是否重复的字段名
     * @return
     */
    public static List<Map> newDealRepeat(List<Map> listMap, String[] checkRepeatKeys) {
        List<Map> newList = new ArrayList<Map>();
        Map valueMap = new HashMap();
        for (int i = 0; i < listMap.size(); i++) {
            String values = "";
            for (String key : checkRepeatKeys) { // 将需要对比的值拼起来,当做map的key
                values += listMap.get(i).get(key) + ",";
            }
            if (IsEmptyUtils.isEmpty(valueMap.get(values))) { // 该元素不重复
                valueMap.put(values, "true");
                newList.add(listMap.get(i));
            }
        }
        return newList;
    }

    /**
     * 6.主资费的结束时间依据主产品的开始结束时间位基础,再按照表中配置偏移,by wnagmc 20170303 FIXME
     * @param dis 资费节点Map
     * @param specialDiscnt 需要特殊处理的资费配置
     * @param productId 主产品id
     * @param productFlag N25-新产品库,N6-老产品库
     * @return true-该元素已失效,不下发;false-该元素未失效,进行下发
     */
    public boolean dealMainProDiscntDate(Map dis, List<String> specialDiscnt, Object productId, String productFlag) {
        if (!IsEmptyUtils.isEmpty(specialDiscnt)) {
            String discntCode = String.valueOf(dis.get("discntCode"));
            for (String deal : specialDiscnt) {
                if (deal.contains(discntCode) || deal.contains(productId + "," + discntCode)) {
                    Map discntDateMap = getMainProDiscntEffectTime(dis.get("discntCode") + "", GetDateUtils.getDate(),
                            "", productFlag);
                    if ("true".equals(discntDateMap.get("discntEndTag"))) { // 该资费已失效
                        return true;
                    }
                    dis.put("activityStarTime", discntDateMap.get("monthFirstDay"));
                    dis.put("activityTime", discntDateMap.get("monthLasttDay"));
                    break;
                }
            }
        }
        return false;
    }

    /**
     * 7.新产品逻辑公共方法专用的获取主产品下要偏移处理的资费的方法 by wangmc 20181102
     * @param msg-需要有methodCode
     * @return
     */
    public List<String> qrySpealDealDiscnt4NewPreProductInfo(Map msg) {
        if (IsEmptyUtils.isEmpty(msg) || IsEmptyUtils.isEmpty(msg.get("GET_DEAL_DIS_METHOD"))) {
            return null;
        }
        // 数据库的key
        Map inMap = MapUtils.asMap("SELECT_FLAG", ("SPACIL_DISCNT_" + msg.get("GET_DEAL_DIS_METHOD")).toUpperCase()
                + "%");
        return new DealNewCbssProduct().querySpealDealDiscnt(inMap);
    }

}
