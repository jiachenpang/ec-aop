package com.ailk.ecaop.biz.sub.mixprodchg.entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 融合产品变更预提交接口_融合虚拟用户实体
 * @author wangmc
 * @date 2018-08-06
 */
public class MixProductChgMixUser extends MixProductChgSuperUser {

    public MixProductChgMixUser(Exchange exchange, Map msg, ParametersMappingProcessor[] pmp, String serialNumber,
            String mixTradeId, String userType) {
        super(exchange, msg, pmp, serialNumber, mixTradeId, userType);
    }

    @Override
    public Map flowControl() throws Exception {
        //System.out.println("this is mix user");
        ELKAopLogger.logStr("this is mix user");

        // 拼装产品节点
        preProductInfo();

        // 拼装ext节点-tradeRelation
        preExt();

        // 拼装base
        preBase();

        // 拼装其他产品节点
        preTradeOther();

        // 调预提交
        callPreSub();

        return null;
    }

    /**
     * 封装预提交参数
     */
    private void preCancelSubInfo() {
        // 拼装预提交参数
        Map body = exchange.getIn().getBody(Map.class);
        Map data = new HashMap();
        data.put("ext", ext);
        data.put("base", base);
        data.putAll(msg);
        body.put("msg", data);
    }

    // 拼装tradeItem、tradeSubItem
    public void preTradeOther() {
        String userId = (String) properties.get("userId");
        // 拼装tradeItem节点
        preTradeItem();
        // 拼装tradeSubItem 
        preTradeSubItem();
    }

    /**
     * 拼装tradeSubItem 
     */
    private void preTradeSubItem() {
        Map tradeSubItem = new HashMap();
        List<Map> tradeSubItemList = new ArrayList<Map>();
        String userId = (String) properties.get("userId");

        tradeSubItemList.add(lan.createTradeSubItemJ("PHOTO_OPTIMAL_FLAG", "0", userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_SN_A", msg.get("serialNumber"), userId));
        tradeSubItemList.add(lan.createTradeSubItemJ("ORGI_COMP_ID_A", msg.get("phoneUserId"), userId));

        tradeSubItem.put("item", tradeSubItemList);
        ext.put("tradeSubItem", tradeSubItem);
    }

    /**
     *拼装tradeItem节点
     */
    private void preTradeItem() {
        Map tradeItem = new HashMap();
        List<Map> tradeItemList = new ArrayList<Map>();
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("photoTagForBroad", "0"));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("DEVELOP_DEPART_ID", msg.get("channelId")));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("RELATION_TYPE_CODE_YH", "H007"));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("REAL_PHOTO_USER_NAME1", "9900112018061316535862661")); // TODO 先写死，不知道写啥
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("DEVELOP_STAFF_ID", msg.get("recomPersonId")));
        tradeItemList.add(new LanUtils().createAttrInfoNoTime("ALONE_TCS_COMP_INDEX", "4"));
        tradeItem.put("item", tradeItemList);
        ext.put("tradeItem", tradeItem);
    }
}
