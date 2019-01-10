package org.n3r.acc.compare;

import com.google.common.collect.Maps;

import java.util.Map;

public class CompareContext {
    public static final String ACCOUNT_DAY = "accountDay";
    public static final String ACCOUNT_TYPE = "accountType";
    public static final String PROVINCE_CODE = "provinceCode";

    private String accountDay;   // 对账日期, yyyyMMdd
    private String accountType;  // 对账类型
    private String provinceCode; // 省份编码
    private Map<String, String> context = Maps.newHashMap();

    public void put(String contextKey, String contextValue) {
        context.put(contextKey, contextValue);
    }

    public String getAccountDay() {
        return accountDay;
    }

    public void setAccountDay(String accountDay) {
        this.accountDay = accountDay;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    public Map<String, String> getContext() {
        Map<String, String> returnContext = Maps.newHashMap(context);

        returnContext.put(ACCOUNT_DAY, accountDay);
        returnContext.put(ACCOUNT_TYPE, accountType);
        returnContext.put(PROVINCE_CODE, provinceCode);

        return returnContext;
    }
}
