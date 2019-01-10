package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 不跨系统办理，即4G互转，走老的套餐服务变更流程，先调用套餐服务变更接口再调用开户处理提交接口。
 * @author wangmc 2017-08-14
 */
@EcRocTag("preDateToChgProductProcessor")
public class PreDateToChgProductProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object apptx = body.get("apptx");
        System.out.println(apptx + ",产品变更流程---> 套餐变更流程--------");

        Map changeMap = MapUtils.asMap("ordersId", msg.get("ordersId"), "serialNumber", msg.get("serialNumber"),
                "opeSysType", "2");
        MapUtils.arrayPut(changeMap, msg, MagicNumber.COPYARRAY);

        // 获取参数
        List<Map> userInfos = (List<Map>) msg.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfos)) {
            throw new EcAopRequestBizException("未下发用户信息,无法办理业务");
        }
        Map userInfo = userInfos.get(0);
        List<Map> packageElement = new ArrayList<Map>();
        Map productInfo = (Map) userInfo.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "4G互转未传入产品节点");
        }
        // 准备包信息节点
        List<Map> packageInfos = (List<Map>) productInfo.get("packageInfo");
        if (!IsEmptyUtils.isEmpty(packageInfos)) {
            for (Map packageInfo : packageInfos) {
                List<Map> elementInfos = (List<Map>) packageInfo.get("elementInfo");
                for (Map elementInfo : elementInfos) {
                    Map packageItem = new HashMap();
                    packageItem.put("packageId", packageInfo.get("packageId"));
                    packageItem.put("elementType", "D");
                    // 根据传进来的optType判断资费是否为退订的资费,00-订购,01-退订;默认订购,只有自由组合套餐时需要下发退订的资费
                    packageItem.put("modType", "01".equals(elementInfo.get("optType")) ? "1" : "0");
                    packageItem.put("elementId", elementInfo.get("elementCode"));
                    packageElement.add(packageItem);
                }
            }
        }
        Object priproductId = productInfo.get("priproductId");// 原套餐ID
        Object tarproductId = productInfo.get("tarproductId");// 目标套餐ID
        if (IsEmptyUtils.isEmpty(priproductId) || IsEmptyUtils.isEmpty(tarproductId)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "变更产品没有传入原套餐或模板套餐,请检查");
        }
        // 准备产品信息
        List<Map> changeproductInfo = new ArrayList<Map>();
        Map newProductItem = new HashMap();// 订购的产品
        newProductItem.put("productId", tarproductId);
        newProductItem.put("companyId", productInfo.get("tarcompanyId"));
        newProductItem.put("productNameX", productInfo.get("tarproductNameX"));
        newProductItem.put("productMode", "1");
        newProductItem.put("optType", "02"); // 默认按主产品内资费变更来做,下边判断订购退订主产品不一样时,再改为订购
        newProductItem.put("packageElement", packageElement);

        // 如果传入的订购产品与退订产品是同一个则做产品内资费变更
        if (!priproductId.equals(tarproductId)) {
            // 新主产品改为订购
            newProductItem.put("optType", "00");

            Map oldProductItem = new HashMap();// 退订的产品
            oldProductItem.put("productId", priproductId);
            oldProductItem.put("companyId", productInfo.get("pricompanyId"));
            oldProductItem.put("productNameX", productInfo.get("priproductNameX"));
            oldProductItem.put("productMode", "1");
            oldProductItem.put("optType", "01");
            changeproductInfo.add(oldProductItem);
        }

        // 将包和元素放入订购信息中
        changeproductInfo.add(newProductItem);
        changeMap.put("productInfo", changeproductInfo);
        // 如果有活动就把活动拼进去
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("activityInfo"))) {
            List<Map> activityInfoList = (List<Map>) msg.get("activityInfo");
            for (Map activityInfo : activityInfoList) {
                activityInfo.put("actPlanId", activityInfo.get("activityId"));
            }
            changeMap.put("activityInfo", activityInfoList);
        }
        body.put("msg", changeMap);
        exchange.getIn().setBody(body);
    }

}
