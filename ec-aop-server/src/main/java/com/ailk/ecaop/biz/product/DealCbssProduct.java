/**
 * 一个附加产品，如果在td_b_promotion表中的length_type表中的值是Y，那就是取用户的合约期。如果该字段没有值或者值是N，
 * 那就是取product表的结束时间，要看end_enable_tag、 end_offset
 * 、end_unit、end_absolute_date这几个值。
 * end_enable_tag 失效方式，如果是0，就取end_absolute_date；
 * end_enable_tag为1的话，根据偏移量end_offset 、end_unit来计算；
 * 资费的时间查资费表，计算方式同上，也是根据end_enable_tag、 end_offset 、end_unit、end_absolute_date四个字段来判断。
 * 是否是促销产品的判断：td_b_promotion表里有就是，没有就不是；
 */
package com.ailk.ecaop.biz.product;

import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.esql.Esql;

import com.ailk.ecaop.common.utils.IsEmptyUtils;

/**
 * 处理CBSS产品工具类
 * 
 * @author Steven
 */
public class DealCbssProduct {

    final static String ERROR_CODE = "9999";
    //final static String MAIN_PRODUCT_TAG = "0";
    //Esql dao = DaoEngine.getOracleDao("/com/ailk/ecaop/sql/cbss/cbssProduct.esql");

    public Map qryDiscntAttr(Map inMap) {
        List<Map> discntInfo = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/cbss/cbssProduct.esql").id("qryDiscntAttr").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(discntInfo)) {
            throw new EcAopServerBizException(ERROR_CODE, "资费编码[" + inMap.get("ELEMENT_ID") + "]不存在或已失效");
        }
        if (1 != discntInfo.size()) {
            throw new EcAopServerBizException(ERROR_CODE, "根据资费编码[" + inMap.get("ELEMENT_ID") + "]获取到"
                    + discntInfo.size() + "条数据,应该为1条");
        }
        return discntInfo.get(0);
    }

    public List<Map> qryDefaultPackageElement(Map inMap) {
        List<Map> packageElement = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/cbss/cbssProduct.esql").id("qryDefaultPackageElement").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(packageElement)) {
            throw new EcAopServerBizException("9999", "根据产品[" + inMap.get("COMMODITY_ID") + "]未获取到附加包及包元素信息!");
        }
        return packageElement;
    }

    public Map qryProductInfo(Map inMap) {
        List<Map> productList = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/cbss/cbssProduct.esql").id("qryProductInfo").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(productList)) {
            throw new EcAopServerBizException(ERROR_CODE, "产品[" + inMap.get("COMMODITY_ID") + "]不存在");
        }
        return productList.get(0);
    }

    public List qryNoErrProductInfo(Map inMap) {
        List<Map> productList = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/cbss/cbssProduct.esql").id("qryProductInfo").params(inMap).execute();
        return productList;
    }

    public List queryPackageElementInfoForTraffic(Map inMap) {
        return new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/cbss/cbssProduct.esql").id("selPackageElementForTraffic").params(inMap).execute();
    }

    public Object queryProductModeByCommodityId(Map inMap) {
        List<String> productList = new Esql("ecaop_connect_Oracle").useSqlFile("/com/ailk/ecaop/sql/cbss/cbssProduct.esql").id("queryProductModeByCommodityId").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(productList)) {
            throw new EcAopServerBizException(ERROR_CODE, "产品[" + inMap.get("COMMODITY_ID") + "]不存在");
        }
        return productList.get(0);
    }
}
