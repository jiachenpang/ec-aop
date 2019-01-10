package com.ailk.ecaop.dao.product.lan;

import java.util.List;
import java.util.Map;

import org.n3r.ecaop.dao.base.DaoEngine;
import org.n3r.esql.Esql;

public class LanProductInfoQuery {

    Esql prdDao = DaoEngine.getMySqlDao("/com/ailk/ecaop/sql/prd/LanProductInfoQuery.esql");

    public List queryPrdAccMod(Map inMap) {
        return prdDao.id("selCommodityAccessMod").params(inMap).execute();
    }

    public List queryAllPrdAccMod(Map inMap) {
        return prdDao.id("selAllCommodityAccessMod").params(inMap).execute();
    }

    public List queryAllPrdAccModCb(Map inMap) {
        return prdDao.id("selAllCommodityInfo").params(inMap).execute();
    }
}
