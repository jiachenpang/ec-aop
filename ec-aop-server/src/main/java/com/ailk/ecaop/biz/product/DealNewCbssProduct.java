package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.esql.Esql;

import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.NewProductUtils;

/**
 * 从3GE中心库获取4G产品信息
 */
public class DealNewCbssProduct {

    final static String ERROR_CODE = "9999";
    final static String MAIN_PRODUCT_TAG = "0";

    // Esql dao = DaoEngine.get3GeDao("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql");

    /**
     * 新产品逻辑中对中间表进行操作的公共方法,通过产品id在td_b_trans_change表查出来的元素,根据其默认标识DEFAULT_TAG对传进来的元素集进行操作.
     * DEFAULT_TAG = 1 (该元素不要下发) 将该元素从传进来的结果集中剔除;
     * DEFAULT_TAG = 0 (该元素需要下发) 将该元素加入到传进来的结果集中.
     * @param packageElement
     * @param inMap 要含有PRODUCT_ID,PRODUCT_MODE,PROVINCE_CODE,EPARCHY_CODE
     *            APPKEY(不必须)
     * @return
     */
    private List<Map> dealTransChangeProduct(List<Map> packageElement, Map inMap) {
        List<Map> changePackageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("queryProductElementByChange")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(changePackageElement)) {
            return packageElement;
        }
        return NewProductUtils.dealProductChange(packageElement, changePackageElement);
    }

    /**
     * 根据产品Id和省份编码获取产品类型(ProductMode)-不涉及中间表-仅流量包订购退订使用
     * @param inMap
     * @return
     */
    public Object queryProductModeByCommodityId(Map inMap) {
        List<Map> productList = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("queryProductModeByProductId")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(productList)) {
            throw new EcAopServerBizException(ERROR_CODE, "产品[" + inMap.get("PRODUCT_ID") + "]不存在");
        }
        return productList.get(0).get("PRODUCT_MODE");
    }

    /**
     * 根据产品commodityId查询活动id--不涉及中间表 根据产品活动ID、省份编码查询产品ID 仅TradeManagerUtils中的新产品处理逻辑中使用
     * @param inMap
     * @return
     */
    public Object queryActivityByCommodityId(Map inMap) {
        List<String> productList = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("queryActivityByProductId")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(productList)) {
            throw new EcAopServerBizException(ERROR_CODE, "产品[" + inMap.get("PRODUCT_ID") + "]不存在");
        }
        return productList.get(0);
    }

    /**
     * 获取产品id下的默认下发元素 -- 涉及中间表,重点改造 TODO:已修改 使用较多
     * @param inMap
     * @return
     */
    public List<Map> qryDefaultPackageElement(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryDefaultPackageElement")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(packageElement)) {
            // throw new EcAopServerBizException("9999", "根据产品[" + inMap.get("PRODUCT_ID") + "]未获取到附加包及包元素信息!");
            return new ArrayList<Map>();
        }
        return dealTransChangeProduct(packageElement, inMap);
    }

    /**
     * 从TD_B_PRODUCT表获取产品生效失效时间处理字段--不涉及中间表
     * @param inMap
     * @return
     */
    public Map qryProductInfo(Map inMap) {
        List<Map> productList = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryProductInfo").params(inMap)
                .execute();
        if (IsEmptyUtils.isEmpty(productList)) {
            throw new EcAopServerBizException(ERROR_CODE, "产品[" + inMap.get("COMMODITY_ID") + "]不存在");
        }
        return productList.get(0);
    }

    /**
     * 融合业务服务变更以及TradeManagerUtils的新产品处理逻辑中(仅为宽带包年验证)在用,查询产品下的默认元素-涉及中间表 TODO:已修改
     * @param inMap
     * @return
     */
    public List<Map> qryDefaultPackageElementEcs(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryDefaultPackageElementEcs")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(packageElement)) {
            throw new EcAopServerBizException("9999", "根据产品[" + inMap.get("PRODUCT_ID") + "]未获取到附加包及包元素信息!");
        }
        return dealTransChangeProduct(packageElement, inMap);
    }

    /**
     * 4G老用户优惠购机使用,处理活动时使用(包含对A类元素的操作,A-终端)-涉及中间表 TODO:已修改
     * @param inMap
     * @return
     */
    public List<Map> qryDefaultPackageElementOlduActivity(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryDefaultPackageElementOlduActivity")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(packageElement)) {
            throw new EcAopServerBizException("9999", "根据产品[" + inMap.get("PRODUCT_ID") + "]未获取到活动的元素信息!");
        }
        return dealTransChangeProduct(packageElement, inMap);
    }

    /**
     * TradeMannagerUtils.preProductInfo() 新产品处理公共方法使用,查询IPTV或者互联网电视的产品信息
     * 仅根据产品ID和省份编码进行查询-涉及中间表么? TODO:已修改
     * @param inMap
     * @return
     */
    public List queryIptvOrIntertvProductInfo(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("selIptvOrIntertvProduct")
                .params(inMap).execute();
        return dealTransChangeProduct(packageElement, inMap);
    }

    /**
     * 从TD_B_DISCNT表获取资费的属性信息，可用于计算资费的生效失效时间-不涉及中间表
     * @param inMap
     * @return
     */
    public Map qryDiscntAttr(Map inMap) {
        List<Map> discntInfo = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryDiscntAttr").params(inMap)
                .execute();
        if (IsEmptyUtils.isEmpty(discntInfo)) {
            throw new EcAopServerBizException(ERROR_CODE, "资费编码[" + inMap.get("DISCNT_CODE") + "]不存在或已失效");
        }
        return discntInfo.get(0);
    }

    /**
     * 从TD_B_PRODUCT , TD_B_PTYPE_PRODUCT获取产品数据-不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> queryProductAndPtypeProduct(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectProductAndPtypeProduct").params(inMap).execute();
    }

    /**
     * 根据资费和产品id 获取库里的产品数据 -- 仅23转4和套餐变更的新产品逻辑(N25ChgToCbssProduct)使用-获取产品下的特定元素-不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> qryPackageElementByElement(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryPackageElementByElement")
                .params(inMap).execute();
        return packageElement;
    }

    /**
     * 根据资费和产品id 获取库里的产品数据 获取资费的信息,目前仅新产品流程处理类NewProductUtils中使用,不过中间表 by wangmc 20170320
     * @param inMap
     * @return
     */
    public List<Map> qryPackageElementByElementNew(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryPackageElementByElement")
                .params(inMap).execute();
        return packageElement;
    }

    /**
     * 查询sp属性 -- 查询sp不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> querySPServiceAttr(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("qrySpAttr").params(inMap).execute();
    }

    /**
     * 查询套内的sp属性--目前仅套餐变更用
     * @param inMap
     * @return
     */
    public List<Map> newQuerySPServiceAttr(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("newQrySpAttrFor").params(inMap).execute();
    }

    /**
     * 查询有归属产品的sp属性信息 -- 查询sp不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> querySpAttrByProduct(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("qrySpByProduct").params(inMap).execute();
    }

    /**
     * 查询无归属产品的sp属性信息 -- 查询sp不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> querySpAttrBySpSrvId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("qrySpBySpSrvId").params(inMap).execute();
    }

    /**
     * 附加包 查询外围在产品下传进来的附加包的属性信息,根据产品包元素查询,不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> queryPackageElementInfo(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryPackageElement").params(inMap)
                .execute();
        return packageElement;

    }

    /**
     * 固话单装新规范 把规范里开户时可选包下全部节点下发--不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> queryPackageAllElement(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("queryPackageAllElement").params(inMap).execute();
    }

    /**
     * 宽带新装开户资费新库 -根据资费id和省份查询产品信息
     * @param inMap
     * @return
     */
    public List<Map> selBroadItemByBroadDiscntId4CB(Map inMap) {
        return new Esql("ecaop_connect_CB").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("qryBroadItemByBroadDiscntId4CB").params(inMap).execute();
    }

    /**
     * 宽带新装开户资费新库 -根据资费id和省份查询产品信息,不涉及中间表
     * @param inMap
     * @return
     */
    public List<Map> queryDistProductInfoByElementId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selDistProductInfoByElementId").params(inMap).execute();
    }

    /**
     * 宽带新装开户资费新库 -根据资费id和省份查询产品信息
     * @param inMap
     * @return
     */
    public List<Map> queryDistProductInfoByBroadDiscntId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selDistProductInfoByBroadDiscntId").params(inMap).execute();
    }

    /**
     * 查询下发产品所关联的服务 流量包订购退订中使用资费查询是否有关联服务,不涉及中间表
     * @param inMap
     * @return
     */
    public List selectServiceTraffic(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectServiceTraffic").params(inMap).execute();
    }

    /**
     * 从Td_b_Package_Element_reli表中获取服务需关联下发的元素,不涉及中间表
     * @param inMap
     * @return
     */
    public List queryProductSvcInfoByElementIdProductId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selProductInfoSvcByElementIdProductId").params(inMap).execute();
    }

    /**
     * 查询产品的费用,不涉及中间表
     * @param inMap
     * @return
     */
    public List qryProductFee(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("qryProductFee").params(inMap).execute();
    }

    /**
     * 用元素集合查元素信息++,根据附加包中的元素获取元素信息,不涉及中间表
     * 2017-1-18 by Zeng
     * @param inMap
     * @return
     */
    public List<Map> queryAppProductByEleList(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectAppProductByEleList").params(inMap).execute();

    }

    /**
     * 查询产品元素并去掉TD_B_COMMODITY_TRANS_LIMIT中的元素++,sql查出的结果集仅去product_id和product_mode字段 不涉及中间表
     * 2017-1-18 by Zeng
     * @param inMap
     * @return
     */
    public List<Map> queryProductInfoWithLimit(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectProductInfoWithLimit").params(inMap).execute();

    }

    /**
     * 根据非默认元素ID和产品ID查询包Id及生失效信息++,仅移网套餐变更用,查询国际长途和国际漫游的元素属性,以及需要继承的资费,不涉及中间表 FIXME
     * 2017-2-15 by Zeng
     * @param inMap
     * @return
     */
    public List<Map> queryPackageIdByNoDefaultElementId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectPackageIdByNoDefaultElementId").params(inMap).execute();
    }

    /**
     * 从TD_B_DISCNT获取资费的生失效时间数据 by wangmc 20170302 不涉及中间表
     * @param inMap
     * @return
     */
    public List queryDiscntData(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectDiscntData").params(inMap).execute();
    }

    /**
     * 从TD_B_PRODUCT表获取PRODUCT_ID,PRODUCT_MODE字段 by wangmc 20170302
     * 只是用来获取产品id和产品类型字段,以及确认附加产品是否存在
     * @param inMap
     * @return
     */
    public List<Map> qryProductInfoByProductTable(Map inMap) {
        List<Map> productList = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryProductInfoByProductTable")
                .params(inMap).execute();
        return productList;
    }

    /**
     * 获取包中元素数目上限
     * @param inParam
     * @return
     */
    public List selectPkgMaxNum(Map inParam) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectPackageMaxNum").params(inParam).execute();
    }

    /**
     * 获取无依赖关系sp产品
     * @param inMap
     * @return
     */
    public List queryProductItemInfoForSp(Map inMap) {
        // inMap.put("PID", Integer.parseInt(inMap.get("PID").toString()));
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selProductItemInfoForSp").params(inMap).execute();
    }

    /**
     * 获取有产品依赖关系的sp产品
     * @param inMap
     * @return
     */
    // public List queryProductItemInfoForSpIn(Map inMap) {
    // inMap.put("PID", Integer.parseInt(inMap.get("PID").toString()));
    // return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
    // .id("selProductItemInfoForSpIn").params(inMap).execute();
    // }

    /**
     * 通过用户传入的速率获取元素信息
     * @param inMap
     * @return
     */
    public Map qryNewProductSpeed(Map inMap) {
        List<Map> speedList = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("qryNewProductSpeed").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(speedList)) {
            throw new EcAopServerBizException("9999", "在产品[" + inMap.get("PRODUCT_ID") + "]下未查询到用户选择的速率元素["
                    + inMap.get("ELEMENT_ID") + "]");
        }
        return speedList.get(0);
    }

    /**
     * 根据资费属性获取产品信息,加上产品ID
     * @param inMap
     * @return
     */
    public List queryProductInfoByElementIdProductId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selProductInfoByElementIdProductId").params(inMap).execute();
    }

    /**
     * 根据资费属性获取产品信息,加上产品ID,去除默认标识限制,只查询存不存在 by wangmc 20170414 融合IPTV 问题
     * @return
     */
    public List<Map> queryProdInfoByElementIdAndProdId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selProductInfoByElementIdAndProductId").params(inMap).execute();
    }

    /**
     * 通过元素ID获取产品信息，此处将ELEMENT_ID设为灵活的，可由外围直接传入。
     * @param inMap
     * @return
     */
    public List qryProductInfoByElementId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selProductInfoByElementId").params(inMap).execute();
    }

    /**
     * 从TD_B_DISCNT获取资费的详细数据
     * 该方法专用于固话或者宽带包年产品资费
     * @param inMap
     * @return
     */
    public List queryBrdOrLineDiscntInfo(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selectBrdOrLineDiscntInfo").params(inMap).execute();
    }

    /**
     * 查询附加产品的生效、失效时间的处理类型 by wangmc 20170320
     * @param inMap
     * @return
     */
    public List queryAddProductEndDateType(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selAddProductEndDateType").params(inMap).execute();
    }

    /**
     * 根据产品ID获取产品的属性信息,目前仅移网套餐变更新流程使用 by wagmc 20170331
     * @param inMap-PRODUCT_ID,PRODUCT_MODE,PROVINCE_CODE,EPARCHY_CODE
     * @return
     *         PRODUCT_ID,PRODUCT_MODE,BRAND_CODE,PRODUCT_TYPE_CODE
     */
    public Map queryProductInfoByProductId(Map inMap) {
        List<Map> productInfo = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("selProductInfoByProductId")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException("9999", "根据产品ID [" + inMap.get("PRODUCT_ID") + "]未查询到该省份地市授权的产品信息");
        }
        return productInfo.get(0);
    }

    /**
     * 根据产品id获取产品下的所有包信息,ECS自助新接口使用 by wangmc 20170504
     * @param inMap
     * @return
     */
    public List<Map> queryAllPackageByProductId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selAllPackageByProductId").params(inMap).execute();
    }

    /**
     * 根据包id获取包下的所有元素信息,ECS自助新接口使用 by wangmc 20170504
     * @param inMap
     * @return
     */
    public List<Map> queryAllElementByPackageId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selAllElementByPackageId").params(inMap).execute();
    }

    /**
     * 根据包id获取包信息,ECS自助新接口使用 by wangmc 20170509
     * @param inMap
     * @return
     */
    public List<Map> queryPackageInfoByPackage(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selPackageInfoByPackage").params(inMap).execute();
    }

    /**
     * RHQ2017052300058-TDM卡(2I卡)老用户升级包继承处理2.0_获取需要处理的产品ID
     * 配置在TD_B_COMMODITY_TRANS_ITEM表 by wangmc 20171017
     * @param inMap
     * @return
     */
    public String queryNeedKeepDisProduct(Map inMap) {
        List<String> result = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selNeedKeepDisProduct").params(inMap).execute();
        return String.valueOf(result.get(0));
    }

    /**
     * RHQ2017052300058-TDM卡(2I卡)老用户升级包继承处理.获取产品下的升级资费
     * 配置在TD_B_COMMODITY_TRANS_ITEM表 by wangmc 20170621
     * @param inMap
     * @return
     */
    public List<Map> queryNeedKeepDiscnt(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selNeedKeepDiscnt").params(inMap).execute();
    }

    /**
     * RHQ2017052300058-TDM卡(2I卡)老用户升级包继承处理.获取产品下的升级资费和原资费
     * 配置在TD_B_COMMODITY_TRANS_ITEM表 by wangmc 20170621
     * @param inMap
     * @return
     */
    public List<Map> query2INeedDealDiscnt(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("sel2INeedDealDiscnt").params(inMap).execute();
    }

    /**
     * RHQ2018010200002-TDM卡(2I卡)老用户升级包继承处理4.0_获取要选择的继承等级数据
     * 配置在TD_B_COMMODITY_TRANS_ITEM表 by wangmc 20171017
     * @param inMap-PRODUCT_ID,PROVINCE_CODE
     * @return
     */
    public List<Map> queryChooseKeepLevelData(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selChooseKeepLevelData").params(inMap).execute();
    }

    /**
     * 通过活动编码与活动ID确认外围选择的参数一致性
     * @param inMap
     * @return
     */
    public void qryProductInfoByProductType(Map inMap) {
        List<Map> productInfo = new Esql("ecaop_connect_3GE")
                .useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql").id("qryProductInfoByProductType")
                .params(inMap).execute();
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException("9999", "根据活动编码 [" + inMap.get("ACTIVITY_ID") + "]和活动ID["
                    + inMap.get("PRODUCT_ID") + "]未查询到对应的产品信息");
        }
    }

    /**
     * 4G开户查询主产品下需要计算有效期的资费配置 by wangmc 20180125
     * @param inMap
     * @return
     */
    public List<String> querySpealDealDiscnt(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("selSpealDealDiscnt").params(inMap).execute();
    }

    /**
     * 移网4G套餐变更接口处理退订特定产品时,需要退订特定的附加产品 by wangmc 20180329
     * @param inMap
     * @return
     */
    public List<Map> querySpecialDealPriduct(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/cbssProductNew.esql")
                .id("querySpecialDealPriduct").params(inMap).execute();
    }

}
