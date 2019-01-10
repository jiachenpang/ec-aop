package com.ailk.ecaop.dao.essbusiness;

/**
 * Created by Liu JiaDi on 2016/8/17.
 */

import java.util.List;
import java.util.Map;

import org.n3r.esql.Esql;

import com.ailk.ecaop.common.utils.IsEmptyUtils;

/**
 * 该类用于存储对3GE中心库数据的操作
 * @auther Liu JiaDi
 * @create 2016_08_17_11:23
 */
public class EssBusinessDao {

    // static Esql essDao = DaoEngine.get3GeDao("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql");

    // Esql essDao = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql");

    /**
     * 根据省份编码获取大区编码
     * @param inMap
     * @return
     */
    public Object selZoneCodeByProvince(Map inMap) {
        List retList = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selZoneCodeByProvince").params(inMap).execute();
        return retList.get(0);
    }

    /**
     * 根据订单号获取sysCode
     * @param inMap
     * @return
     */
    public List<String> selSysCodeByOrdersId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selSysCodeByOrdersId").params(inMap).execute();
    }

    /**
     * 根据省份编码获取对接信息
     * param_code 省份，
     * PARA_CODE1 是否对接号卡中心 0：不对接 1：对接 默认0
     * PARA_CODE2 是否全号段割接 0：非全量号段 1： 全量号段割接 默认0
     * @param inMap
     * @return
     */
    public List selRouteInfoByProvince(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selRouteInfoByProvince").params(inMap).execute();
    }

    /**
     * 根据号码或订单获取订单来源
     * @param inMap
     * @return
     */

    public Map selNetTypeByNumOrOrdersId(Map inMap) {
        List retList = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selNetTypeByNumOrOrdersId").params(inMap).execute();
        if (null == retList || retList.size() == 0) {
            return null;
        }
        else {
            return (Map) retList.get(0);
        }

    }

    /**
     * 根据省份编码和号段获取对接信息
     * @param inMap
     * @return
     */
    public List selRouteInfoByNum(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selRouteInfoByNum").params(inMap).execute();
    }

    /**
     * 根据省份编码获取转型渠道标记
     * @param inMap
     * @return
     */
    public int chkTranTagByChannCode(Map inMap) {
        List tranTag = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("chkTranTagByChannCode").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(tranTag)) {
            return 0;
        }
        return Integer.valueOf(tranTag.get(0).toString());
    }

    /**
     * 查询转型渠道终端锁定表的锁定数量
     * @param inMap
     * @return
     */
    public List selLockNumByMachCode(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selLockNumByMachCode").params(inMap).execute();
    }

    /**
     * 发起锁定请求时，转型渠道终端锁定表无请求机型编码及渠道的锁定信息时，新增一条记录，默认锁定数为0
     * @param inMap
     * @return
     */
    public void addLockNumInfoInTranLock(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("addLockNumInfoInTranLock").params(inMap).execute();
    }

    /**
     * 查询转型渠道串码表的串码空闲数量
     * @param inMap
     * @return
     */
    public int selIdleNumByMachCode(Map inMap) {
        List idleNum = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selIdleNumByMachCode").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(idleNum)) {
            return 0;
        }
        return Integer.valueOf(idleNum.get(0).toString());
    }

    /**
     * 在转型渠道终端锁定表增加锁定数量
     * @param inMap
     * @return
     */
    public void addLockNumInTranLock(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("addLockNumInTranLock").params(inMap).execute();
    }

    /**
     * 在转型渠道终端锁定表减少锁定数量
     * @param inMap
     * @return
     */
    public void decLockNumInTranLock(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("decLockNumInTranLock").params(inMap).execute();
    }

    /**
     * 记录锁定终端订单信息
     * @param inMap
     * @return
     */
    public void addLockOrderInfo(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("addLockOrderInfo").params(inMap).execute();
    }

    /**
     * 将自提终端表中锁定状态修改为释放状态
     * @param inMap
     * @return
     */
    public void updateLock2Release(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("updateLock2Release").params(inMap).execute();
    }

    /**
     * 查询自提终端表中相关关键字的锁定数量
     * @param inMap
     * @return
     */
    public int selLockOrderNum(Map inMap) {
        List lockOrderNum = new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("selLockOrderNum").params(inMap).execute();
        if (IsEmptyUtils.isEmpty(lockOrderNum)) {
            return 0;
        }
        return Integer.valueOf(lockOrderNum.get(0).toString());
    }

    /**
     * 记录转型渠道终端量管操作日志，记录增加锁定数量还是记录扣除锁定数量，操作系统，操作时间等核心数据
     * @param inMap
     * @return
     */
    public void insertLockInfoLog(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("insertLockInfoLog").params(inMap).execute();
    }

    public void insertResourceInfo(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("insertResourceInfo").params(inMap).execute();
    }

    public List<Map> qryResourceInfoFromSop(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("qryResourceInfoFromSop").params(inMap).execute();
    }

    public List<Map> qryResourceInfo4RollBack(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("qryResourceInfo4RollBack").params(inMap).execute();
    }

    public List<String> qryNumberByProvOrderId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("qryNumberByProvOrderId").params(inMap).execute();
    }

    public List<String> qryDiscountFlagByProvOrderId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("qryDiscountFlagByProvOrderId").params(inMap).execute();
    }

    /**
     * 裸机销售数据记录
     * @param inMap
     */
    public void insertTerSaleRecord(Map inMap) {
        new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("insertTerSaleRecord").params(inMap).execute();
    }

    /**
     * 裸机返销时查询销售记录
     * @param inMap
     * @return
     */
    public List<Map> qryTerSaleRecord(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("qryTerSaleRecord").params(inMap).execute();
    }

    /**
     * 3G换机接口根据号码和终端串号获取原销售订单
     * @param inMap
     * @return
     */
    public List<Map> qryOrderInfoByTerminalId(Map inMap) {
        return new Esql("ecaop_connect_3GE").useSqlFile("/com/ailk/ecaop/sql/3gess/EssBusinessInfo.esql")
                .id("qryOrderInfoByTerminalId").params(inMap).execute();
    }
}
