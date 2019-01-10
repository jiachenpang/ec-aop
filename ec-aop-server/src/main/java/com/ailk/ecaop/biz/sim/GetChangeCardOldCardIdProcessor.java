package com.ailk.ecaop.biz.sim;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.processor.QryPhoneNumberAttrProcessor;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

/**
 * 补换卡业务调用写卡数据查询接口时,需要给下游下发该号码的原卡号,在该类中集中获取卡号,并发送到各个业务类中(cbss业务/南25和北六23G业务).
 * 4G业务直接调用cbss三户接口,获取原卡号
 * 23G业务需要先判断号码的原归属省份,再根据号码所在的省份调用对应的三户获取原卡号
 * @author wangmc
 * @date 2017-11-14
 */
@EcRocTag("getChangeCardOldCardIdProcessor")
public class GetChangeCardOldCardIdProcessor extends BaseAopProcessor implements ParamsAppliable {

    private LanUtils lan = new LanUtils();
    private String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    // cb三户,辽宁三户,北六三户,南25三户
    private static final String[] PARAM_ARRAY = { "ecaop.trade.cbss.checkUserParametersMapping",
            "ecaop.trade.n6.checkUserParametersMapping.91", "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.masb.chku.checkUserParametersMapping" };

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 补换卡业务查询卡数据时才需要查询号码的原卡号
        if (IsEmptyUtils.isEmpty(msg) || !"1".equals(msg.get("cardUseType"))) {
            return;
        }
        // 非割接省份不查询原卡号
        String simCardSwitch = EcAopConfigLoader.getStr("ecaop.global.param.simcard.province");
        if (StringUtils.isEmpty(simCardSwitch) || !simCardSwitch.contains(msg.get("province") + "")) {
            return;
        }
        Object oldSimCardId = "";
        // 1.cbss分支,直接调用cbss三户接口，根据省份路由，且SERVICE_CLASS_CODE为0141
        if ("2".equals(msg.get("opeSysType"))) {
            oldSimCardId = getOldSimCardIdFromCbss(exchange, msg);
        }
        else {
            // 1.查询手机号码归属省份
            Object numberProvince = getNumberRealProvince(exchange, msg);
            // 2.调用省份三户接口获取原卡号
            oldSimCardId = checkUserInfoByNumber(exchange, msg, numberProvince);
        }
        // 写卡数据查询的4G流程和北六3G流程都在北六AOP,在请求转发北六的时候,将卡号字段一起转给北六
        exchange.setProperty("oldSimCardId", oldSimCardId);
    }

    /**
     * 1.调用cBSS三户接口,获取号码对应的原卡号,cb要求SERVICE_CLASS_CODE为0141
     * @param exchange
     * @param msg
     * @return
     */
    private Object getOldSimCardIdFromCbss(Exchange exchange, Map msg) throws EcAopServerBizException {
        Map threePartMap = MapUtils.asMap("getMode", "1111111111100013111000000100001", "serialNumber",
                msg.get("numId"), "tradeTypeCode", "9999", "serviceClassCode", "0140");
        MapUtils.arrayPut(threePartMap, msg, copyArray);

        // 1.调用三户接口
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        try {
            lan.preData(pmp[0], threePartExchange);// ecaop.trade.cbss.checkUserParametersMapping
            CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "调用cbss三户接口出错:" + e.getMessage());
        }
        return getSimCardFromThreePart(threePartExchange);
    }

    /**
     * 2.调用号卡中心,获取号码的归属地
     * @param exchange
     * @param msg
     * @return
     */
    private Object getNumberRealProvince(Exchange exchange, Map msg) throws EcAopServerBizException {
        // 调用aop已有的号码归属地查询接口
        Map numberMap = MapUtils.asMap("number", msg.get("numId"));
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, numberMap);
        try {
            new QryPhoneNumberAttrProcessor().process(threePartExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
            String errorCode = "9999";
            String detail = e.getMessage();
            if (e instanceof EcAopServerBizException) {
                errorCode = ((EcAopServerBizException) e).getCode();
                detail = ((EcAopServerBizException) e).getDetail();
            }
            throw new EcAopServerBizException(errorCode, "获取手机号码[" + msg.get("numId") + "]归属地出错:" + detail);
        }
        Map retMap = threePartExchange.getOut().getBody(Map.class);
        // 如果为查询到,则使用发起方省份编码
        if (IsEmptyUtils.isEmpty(retMap.get("province"))) {
            return msg.get("province");
        }
        return retMap.get("province");
    }

    /**
     * 3.直接调用省份的三户接口获取卡号信息
     * @param exchange
     * @param msg
     * @param numberProvince
     * @return
     * @throws Exception
     */
    private Object checkUserInfoByNumber(Exchange exchange, Map msg, Object numberProvince) throws Exception {
        Map threePartMap = MapUtils.asMap("serialNumber", msg.get("numId"), "getMode",
                "101001101010001001000000000000", "tradeTypeCode", "9999", "serviceClassCode", "0040");
        MapUtils.arrayPut(threePartMap, msg, copyArray);

        // 使用号码对应的真实省份来判断调用北六还是南25三户接口
        String provinceCode = String.valueOf(numberProvince);
        Exchange threeExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        if ("17|18|11|76|91|97".contains(provinceCode)) {
            // threePartMap.put("getMode", "1111100000000001");// 山东北六E要单调三户接口获取活动信息，所以修改getMode跟tradeTypeCode
            // threeExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
            // 辽宁省份三户请求单独模板，带上参数serviceClassCode
            if ("91".equals(provinceCode)) {
                lan.preData(pmp[1], threeExchange);
            }
            else {
                lan.preData(pmp[2], threeExchange);
            }
            CallEngine.wsCall(threeExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + provinceCode);
            lan.xml2Json("ecaop.trades.cbss.threePart.template", threeExchange);
        }
        else {
            lan.preData(pmp[3], threeExchange);
            CallEngine.wsCall(threeExchange, "ecaop.comm.conf.url.osn.services.usrser");
            lan.xml2Json("ecaop.masb.chku.checkUserTemplate", threeExchange);
        }
        return getSimCardFromThreePart(threeExchange);
    }

    /**
     * 从用户信息中获取卡号
     * @param exchange
     * @return
     */
    private Object getSimCardFromThreePart(Exchange exchange) {
        Map retMap = exchange.getOut().getBody(Map.class);
        if (IsEmptyUtils.isEmpty(retMap) || IsEmptyUtils.isEmpty(retMap.get("userInfo"))) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "三户接口未返回用户信息");
        }

        // 获取号码对应的sim卡号,cbss和北六三户返回节点为list,南25三户返回节点为Map
        Map user = null;
        if (retMap.get("userInfo") instanceof List) {
            user = ((List<Map>) retMap.get("userInfo")).get(0);
        }
        else if (retMap.get("userInfo") instanceof Map) {
            user = (Map) retMap.get("userInfo");
        }
        if (IsEmptyUtils.isEmpty(user)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "三户接口未返回用户信息");
        }

        List<Map> resInfo = (List<Map>) user.get("resInfo");
        if (null != resInfo && (!resInfo.isEmpty())) {
            for (Map temp : resInfo) {
                if ("1".equals(temp.get("resTypeCode"))) {// 资源类型为1,即为卡号
                    return temp.get("resCode");
                }
            }
        }
        else if (null != user.get("sinCardNo")) {// SIM/USIM卡号
            return user.get("sinCardNo");
        }
        return "";
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
