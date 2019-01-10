package com.ailk.ecaop.biz.sub.mixprodchg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.exception.request.EcAopRequestException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.biz.product.DealNewCbssProduct;
import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgBroadBandUser;
import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgFixedPhoneUser;
import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgMixUser;
import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgPhoneUser;
import com.ailk.ecaop.biz.sub.mixprodchg.entry.MixProductChgSuperUser;
import com.ailk.ecaop.biz.sub.mixprodchg.utils.MixProductChgThread;
import com.ailk.ecaop.biz.sub.mixprodchg.utils.MixThreePartInfoThread;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.dao.product.lan.LanOpenApp4GDao;

/**
 * 用于融合共享版用户变更为冰淇淋融合套餐
 * 沃受理-->AOP-->cbss
 * create by zhaok on 2018-07-26 18:02:18
 */
@EcRocTag("mixProductChgApp")
public class MixProductChgAppProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanOpenApp4GDao dao = new LanOpenApp4GDao();
    DealNewCbssProduct n25Dao = new DealNewCbssProduct();
    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = {"ecaop.masb.chph.gifa.ParametersMapping",
            "ecaop.trade.cbss.checkUserParametersMapping", "ecaop.trades.sccc.cancelPre.paramtersmapping",
            "ecaop.trades.city.cancelPre.paramtersmapping"};
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[PARAM_ARRAY.length];

    // 报错描述时的用户类型获取
    private final String[] userTypes = new String[]{"虚拟", "移网", "宽带", "固话"};
    static ExecutorService pool = Executors.newFixedThreadPool(12);

    @Override
    public void process(Exchange exchange) throws Exception {

        // 1.将外围传入的转化为四个成员的信息
        List<MixProductChgSuperUser> users = initUsers(exchange);

        // 并发执行每个成员的预提交业务(调三户->处理产品->拼装预提交参数->调用预提交接口)
        callPreSub(users);

        // 处理所有的预提交的返回
        preResult(exchange, users);
    }

    /**
     * 根据msg的参数,初始化四个成员信息
     * @param exchange
     * @return
     */
    public List<MixProductChgSuperUser> initUsers(Exchange exchange) {
        // 最多四个成员
        List<MixProductChgSuperUser> users = new ArrayList<MixProductChgSuperUser>(4);

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        // 融合订单ID 所有成员的subscribe_id
        String mixTradeId = (String) GetSeqUtil.getSeqFromCb(pmp[0], ExchangeUtils.ofCopy(exchange, msg),
                "seq_trade_id", 1).get(0);

        // 传固网号码时，区号必传
        if (null != msg.get("fixedNumber")) {
            if (null == msg.get("areaCode")) {
                throw new EcAopServerBizException("9999", "传固网号码时，区号必传");
            }
        }
        // 0-虚拟号码,1-移网成员号码,2-宽带号码,3-固话号码(需要在前加区号)
        String[] userNumberKeys = new String[]{"mixSerialNumber", "serialNumber", "broadBandNumber", "fixedNumber"};
        // 初始化用户信息时,会直接初始化产品信息
        for (int i = 0; i < 4; i++) {
            String serialNumber = (String) msg.get(userNumberKeys[i]);
            if (IsEmptyUtils.isEmpty(serialNumber)) {
                if (i < 2) {
                    throw new EcAopRequestException(userTypes[i] + "号码必传,请检查参数");
                }
                continue;
            }
            // 固话号码需要拼装区号
            serialNumber = 3 == i ? msg.get("areaCode") + serialNumber : serialNumber;
            switch (i) {
            case 0 :
                users.add(new MixProductChgMixUser(exchange, msg, pmp, serialNumber, mixTradeId, "0"));
                break;
            case 1 :
                users.add(new MixProductChgPhoneUser(exchange, msg, pmp, serialNumber, mixTradeId, "1"));
                break;
            case 2 :
                users.add(new MixProductChgBroadBandUser(exchange, msg, pmp, serialNumber, mixTradeId, "2"));
                break;
            case 3 :
                users.add(new MixProductChgFixedPhoneUser(exchange, msg, pmp, serialNumber, mixTradeId, "3"));
                break;
            }
        }
        return users;
    }

    /**
     * 并发执行调用预提交的线程任务
     * @param users
     * @throws Exception
     */
    private void callPreSub(List<MixProductChgSuperUser> users) throws Exception {

        // 根据用户数量创建并发任务
        int userNum = users.size();
        // 并发调用预提交业务
        Callable[] callables = new Callable[userNum];

        Future[] futures = new Future[userNum];
        // 先调所有三户
        for (int i = 0; i < userNum; i++) {
            callables[i] = new MixThreePartInfoThread(users.get(i));
            futures[i] = pool.submit(callables[i]);
        }
        // 统一进行取值操作,防止get()的阻塞操作延迟返回
        List<Map> result = new ArrayList<Map>();
        for (int i = 0; i < userNum; i++) {
            try {
                result.add((Map) futures[i].get(3, TimeUnit.SECONDS));
            }
            catch (TimeoutException e) {
                e.printStackTrace(); //超时异常
                futures[i].cancel(true); //超时后取消任务
                throw new EcAopServerBizException("9999", "请求超时！");
            }

        }

        // 先调虚拟预提交
        users.get(0).flowControl();
        // 产品封装、预提交
        for (int i = 1; i < userNum; i++) {
            callables[i] = new MixProductChgThread(users.get(i));
            futures[i] = pool.submit(callables[i]);
        }

        for (int i = 1; i < userNum; i++) {
            try {
                result.add((Map) futures[i].get(4, TimeUnit.SECONDS));
            }
            catch (TimeoutException e) {
                e.printStackTrace(); //超时异常
                futures[i].cancel(true); //超时后取消任务
                throw new EcAopServerBizException("9999", "请求超时！");
            }
        }
    }

    /**
     * 合并预提交的返回内容,返回给外围
     * @param exchange
     * @param users
     */
    private void preResult(Exchange exchange, List<MixProductChgSuperUser> users) {
        Integer totalFee = 0;
        String bssOrderId = "";
        String provOrderId = "";
        List<Map> subOrderInfo = new ArrayList<Map>();
        for (MixProductChgSuperUser user : users) {
            if ("0".equals(user.getUserType())) {
                continue;
            }
            Map out = user.getPreSubRet();
            List<Map> rspInfo = (List) out.get("rspInfo");

            for (Map rspMap : rspInfo) {
                List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
                if (null != provinceOrderInfo && provinceOrderInfo.size() >= 2) {
                    ELKAopLogger.logStr(exchange.getProperty(Exchange.APPTX) + "处理预提交返回：" + rspMap);
                    bssOrderId = (String) rspMap.get("bssOrderId");
                    provOrderId = (String) rspMap.get("provOrderId");

                    // 费用计算
                    for (Map provinceOrder : provinceOrderInfo) {
                        Map subOrderMap = new HashMap();
                        Object subOrderId = provinceOrder.get("subProvinceOrderId");
                        subOrderInfo.add(MapUtils.asMap("subOrderId", subOrderId));
                        totalFee = totalFee + Integer.valueOf((String) provinceOrder.get("totalFee"));
                        List<Map> preFeeInfoRsp = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                        if (null == preFeeInfoRsp || preFeeInfoRsp.isEmpty()) {
                            continue;
                        }

                        List<Map> fList = dealFee(preFeeInfoRsp);
                        if (!IsEmptyUtils.isEmpty(fList)) {
                            subOrderInfo.add(MapUtils.asMap("feeInfo", fList));
                        }
                    }
                }
            }
        }
        Message message = new DefaultMessage();
        Map realOut = new HashMap();
        realOut.put("provOrderId", provOrderId);
        realOut.put("bssOrderId", bssOrderId);
        realOut.put("totalFee", totalFee + "");
        realOut.put("subOrderInfo", subOrderInfo);
        message.setBody(realOut);
        exchange.setOut(message);

    }

    private List<Map> dealFee(List<Map> feeList) {
        List<Map> retFeeList = new ArrayList<Map>();
        for (Map fee : feeList) {
            Map retFee = new HashMap();
            retFee.put("feeId", fee.get("feeTypeCode") + "");
            retFee.put("feeCategory", fee.get("feeMode") + "");
            retFee.put("feeDes", fee.get("feeTypeName") + "");
            if (null != fee.get("maxDerateFee")) {
                retFee.put("maxRelief", fee.get("maxDerateFee"));
            }
            retFee.put("origFee", fee.get("fee"));
            retFeeList.add(retFee);
        }
        return retFeeList;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

}
