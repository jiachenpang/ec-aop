package com.ailk.ecaop.dao.product.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.n3r.esql.Esql;

public class LanOpenApp4GDao {

    // Esql openApp4G = DaoEngine.getOracleDao("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql");
    // Esql openApp4G = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql");

    public List<Map> qryResourceInfoFromSop(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qryResourceInfoFromSop").params(inMap).execute();
    }

    public List<Map> qryDefaultPackageElement(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qryDefaultPackageElement").params(inMap).execute();
    }

    public List querycycId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql").id("selCycId")
                .params(inMap).execute();
    }

    public String getCycId(Map inMap) {
        List<Map> result = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selCycId").params(inMap).execute();
        return (String) (result.size() > 0 ? result.get(0).get("CYCLE_ID") : "");
    }

    public List queryEparchyCode(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selEparchyCode").params(inMap).execute();
    }

    public List queryCbAccessType(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selCBaccessType").params(inMap).execute();
    }

    public List queryCbAccessTypeByPro(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selCbssAccessType").params(inMap).execute();
    }

    public List queryCbAccessTypeIsExist(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selCbssAccessTypeIsExist").params(inMap).execute();
    }

    public List queryPayModeCode(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selPayModeCode").params(inMap).execute();
    }

    public List queryProductInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfo").params(inMap).execute();
    }

    public List queryProductInfoForSD(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoForSD").params(inMap).execute();
    }

    public List queryActivityProductInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selActivityProductInfo").params(inMap).execute();
    }

    public List queryProductInfoByElementId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoByElementId").params(inMap).execute();
    }

    public List queryProductInfoByBroadDiscntId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoByBroadDiscntId").params(inMap).execute();
    }

    public List queryProductInfoByElementIdProductId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoByElementIdProductId").params(inMap).execute();
    }

    public List queryProductSvcInfoByElementIdProductId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoSvcByElementIdProductId").params(inMap).execute();
    }

    public List queryProductInfoByElementInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoByElementInfo").params(inMap).execute();
    }

    public List queryUnDefProductInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selUnDefProductInfo").params(inMap).execute();
    }

    public List queryProductItemInfo(Map inMap) {
        inMap.put("PID", Integer.parseInt(inMap.get("PID").toString()));
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductItemInfo").params(inMap).execute();
    }

    public List queryProductItemInfoForSp(Map inMap) {
        inMap.put("PID", Integer.parseInt(inMap.get("PID").toString()));
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductItemInfoForSp").params(inMap).execute();
    }

    public List queryProductItemInfoForSpIn(Map inMap) {
        inMap.put("PID", Integer.parseInt(inMap.get("PID").toString()));
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductItemInfoForSpIn").params(inMap).execute();
    }

    public List queryProductItemInfoNoPtype(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectProductItemNoPtype").params(inMap).execute();
    }

    public List queryAddProductInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selAddProduct").params(inMap).execute();
    }

    public List queryIptvOrIntertvProductInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selIptvOrIntertvProduct").params(inMap).execute();
    }

    public List queryUnDefAddProductInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selUnDefProduct").params(inMap).execute();
    }

    public List queryActProductInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selActProductInfo").params(inMap).execute();
    }

    public List queryAddProductEndDate(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selAddProductEndDate").params(inMap).execute();
    }

    public List queryDistProductInfoByElementId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selDistProductInfoByElementId").params(inMap).execute();
    }

    /**
     * 获取Trans表信息（关联TD_B_COMMODITY_TRANS_LIMIT表）
     * @param inMap
     * @return
     */
    public List queryProductInfoWithLimit(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoWithLimit").params(inMap).execute();
    }

    /**
     * 获取号码操作信息。取NET_TYPE_CODE-->serCode
     * @param inparam
     * @return
     */
    public Map selectOperInfo(Map inparam) {
        ArrayList<Map> results = new Esql("ecaop_connect_Oracle")
                .useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql").id("selectOperInfo").params(inparam)
                .execute();
        return CollectionUtils.isEmpty(results) ? new HashMap() : results.get(0);
    }

    /**
     * 号码操作信息入库
     * @param param
     */
    public void insertOperInfo(Map param) {
        new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql").id("insertOperInfo")
                .params(param).execute();
    }

    /**
     * 仅获取地州编码
     * @param param
     * @return
     */
    public List selectEparchyCodeOnly(Map param) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectEparchyCodeOnly").params(param).execute();
    }

    public List selectPkgMaxNum(Map inParam) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectPackageMaxNum").params(inParam).execute();
    }

    public List queryPackageElementInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selPackageElement").params(inMap).execute();
    }

    public List queryPackageElementInfoForSD(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selPackageElementForSD").params(inMap).execute();
    }

    public List queryPackageElementInfoForSp(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selPackageElementForSp").params(inMap).execute();
    }

    /**
     * 流量包订购接口查询产品信息
     * @param inMap
     * @return
     */
    public List queryPackageElementInfoForTraffic(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selPackageElementForTraffic").params(inMap).execute();
    }

    public List queryPackageBySvcId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectPkgIdBySvcId").params(inMap).execute();
    }

    /**
     * 通过函数生成SEQ
     * @param inMap
     * @return
     */
    public String genSequence(Map inMap) {
        List result = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("genSeqId").params(inMap).execute();
        if (!CollectionUtils.isEmpty(result)) {
            return String.valueOf(result.get(0));
        }
        return "";
    }

    /**
     * 从TD_B_PRODUCT , TD_B_PTYPE_PRODUCT获取产品数据
     * @param inMap
     * @return
     */
    public List queryProductAndPtypeProduct(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectProductAndPtypeProduct").params(inMap).execute();
    }

    /**
     * 从TD_B_DISCNT获取资费的生失效时间数据
     * @param inMap
     * @return
     */
    public List queryDiscntDate(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectDiscntDate").params(inMap).execute();
    }

    /**
     * 从TF_B_TRADE_SOP获取终端串码数据
     * 针对老用户业务
     * @param inMap
     * @return
     */
    public List queryResInfoByTradeId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selResourceInfoByTradeId").params(inMap).execute();
    }
    /**
     * 从TF_B_TRADE_SOP获取终端串码数据
     * 针对23转4业务
     *
     * @param inMap
     * @return
     */
    public List query2324ResInfoByTradeId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("sel2324ResourceInfoByTradeId").params(inMap).execute();
    }

    /**
     * 从TD_B_DISCNT获取资费的详细数据
     * 该方法专用于固话或者宽带包年产品资费
     * @param inMap
     * @return
     */
    public List queryBrdOrLineDiscntInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectBrdOrLineDiscntInfo").params(inMap).execute();
    }

    /**
     * 获取TD_S_ITEM_SOP表中配置的附加参数（APPEND_MAP）
     * @param inMap
     * @return
     */
    public List queryAppendParam(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectAppendParam").params(inMap).execute();
    }

    /**
     * 获取TD_S_COMMPARA_SOP表数据
     * @param inMap
     * @return
     */
    public List queryCommParaSop(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selCommParaSop").params(inMap).execute();
    }

    /**
     * 获取TD_S_COMMPARA_SOP表数据
     * @param inMap
     * @return
     */
    public List queryCommParaSopN6AOP(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selCommParaSopN6AOP").params(inMap).execute();
    }

    public List queryAppProductByEleList(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectAppendProductByEleList").params(inMap).execute();
    }

    public List query3GeSeqId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("gen3GeSeqId").params(inMap).execute();
    }

    /**
     * @param inMap中参数有commodity_id
     * @return
     */
    public List queryMixProductTag(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selMixProductTag").params(inMap).execute();
    }

    /**
     * 
     */
    public void insertTrade(Map inMap) {
        new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql").id("synTrade2CBSS")
                .params(inMap).execute();
    }

    /**
     * 
     */
    public List selectTradeIds(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectTradeIds").params(inMap).execute();
    }

    /**
     * 固话同装部分的用户信息入库
     * @param inMap
     */
    public void insertThreePartInfo(Map inMap) {
        new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("insThreePartInfo").params(inMap).execute();
    }

    /**
     * 查询共线号码入库的三户信息
     * @param inMap
     * @return
     */
    public List queryThreePartInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selThreePartInfo").params(inMap).execute();
    }

    public List qryNetTypeCode(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qryNetTypeCode").params(inMap).execute();
    }

    /**
     * 获取白成卡标记
     * 2为白卡，其他默认是成卡
     * @param inMap
     * @return
     */

    public String queryRemote(Map inMap) {
        List<Map> result = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qryIsRemote").params(inMap).execute();
        if (null == result || 0 == result.size()) {
            return "1";
        }
        return (String) result.get(0).get("IS_REMOTE");
    }

    /**
     * 获取Iptv产品信息
     */
    public List queryProductIdByserviceId(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selProductInfoByserviceId").params(inMap).execute();
    }

    /**
     * 从TD_B_PRODUCT获取生效时间
     * @param inMap
     * @return
     */
    public List queryProductEffectInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("queryProductEffectInfo").params(inMap).execute();
    }

    /**
     * 查询下发产品所关联的服务
     * @param inMap
     * @return
     */
    public List selectServiceTraffic(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("selectServiceTraffic").params(inMap).execute();
    }

    /**
     * 查询需要删除的特殊资费
     * @param inMap
     * @return
     */
    public List qryDelSpecialDis(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qryDelSpecialDis").params(inMap).execute();
    }

    /**
     * 固话单装新规范 把规范里开户时可选包下全部节点下发 仅4G固话单装用,查询默认下发的包属性
     * @param inMap
     * @return
     */
    public List<Map> queryPackageAllElement(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("queryPackageAllElement").params(inMap).execute();
    }

    public List qryProductFee(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qryProductFee").params(inMap).execute();
    }

    /**
     * 在trans_item表查询需要做特殊处理的元素
     * @param inMap
     * @return
     */
    public List<Map> qryElementSpecialDeal(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qryElementSpecialDeal").params(inMap).execute();
    }

    /**
     * 从trans表查询特殊处理的元素信息
     * @param inMap
     * @return
     */
    public List<Map> qrySpecialElementInfo(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/n6/OpenApp4GQuery.esql")
                .id("qrySpecialELementInfo").params(inMap).execute();
    }
}
