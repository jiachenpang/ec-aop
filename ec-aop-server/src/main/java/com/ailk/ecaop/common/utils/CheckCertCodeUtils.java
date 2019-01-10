/**********************************************************************************************************************
 * ************************************身****份****证****号****码****校****验***********************************************
 * 1、支持15位和18位校验
 * 2、号码的结构 公民身份号码是特征组合码，由十七位数字本体码和一位校 验码组成。排列顺序从左至右依次为：六位数字地址码，八位数字出生日期码，三位数字顺序码和一位数字校验码。
 * 3、地址码 表示编码对象常住户口所在县(市、旗、区)的行政区划代码， 按GB/T2260的规定执行。
 * 4、出生日期码 表示编码对象出生的年、月、日，按GB/T7408的规定执行， 年、月、日代码之间不用分隔符。
 * 5、顺序码 表示在同一地址码所标识的区域范围内，对同年、同月、同 日出生的人编定的顺序号，顺序码的奇数分配给男性，偶数分配 给女性。
 * 6、校验码 (1)十七位数字本体码加权求和公式 S = Ai * Wi, i = 2, , 18 Y = mod(S, 11) i:
 * 表示号码字符从右至左包括校验码字符在内的位置序号 Ai:表示第i位置上的身份证号码字符值 Wi:表示第i位置上的加权因子 i: 18 17 16 15
 * 14 13 12 11 10 9 8 7 6 5 4 3 2 1 Wi: 7 9 10 5 8 4 2 1 6 3 7 9 10 5 8 4 2 1
 * (2)校验码字符值的计算 Y: 0 1 2 3 4 5 6 7 8 9 10 校验码: 1 0 X 9 8 7 6 5 4 3 2 四、举例如下：
 * 北京市朝阳区: 11010519491231002X 广东省汕头市: 440524188001010014
 * 15位的身份证号 dddddd yymmdd xx p 18位的身份证号 dddddd yyyymmdd xx p y
 * 其中dddddd为地址码（省地县三级）18位中的和15位中的不完全相同 yyyymmdd yymmdd 为出生年月日 xx顺号类编码 p性别
 * 18位中末尾的y为校验码 将前17位的ascii码值经位移、异或运算结果不在0-9的令其为x
 * 注意点：
 * 如果18位身份证中包含字母，需要将其转换为大写
 * 使用Calendar获取月份，需要进行+1操作
 **********************************************************************************************************************/
package com.ailk.ecaop.common.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;

public class CheckCertCodeUtils {

    // 省份行政区域对应关系
    private final Map provMap = MapUtils.asMap("11", "北京", "12", "天津", "13", "河北", "14", "山西", "15", "内蒙古", "21", "辽宁",
            "22", "吉林", "23", "黑龙江", "31", "上海", "32", "江苏", "33", "浙江", "34", "安徽", "35", "福建", "36", "江西",
            "37", "山东", "41", "河南", "42", "湖北", "43", "湖南", "44", "广东", "45", "广西", "46", "海南", "50", "重庆",
            "51", "四川", "52", "贵州", "53", "云南", "54", "西藏", "61", "陕西", "62", "甘肃", "63", "青海", "64", "宁夏",
            "65", "新疆", "71", "台湾", "81", "香港", "82", "澳门", "91", "国外");

    // 18位身份证所需要的权重
    private final int[] Wi = { 7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2, 1 };

    // 18位身份证验证位
    private final char[] Vi = { '1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2' };

    // 月份和天数对应关系
    private Map monthDay = MapUtils.asMap("01", "31", "02", null, "03", "31", "04", "30", "05", "31", "06", "30", "07",
            "31", "08", "31", "09", "30", "10", "31", "11", "30", "12", "31");

    public void CheckCertCode(String certCode) {
        certCode = certCode.toUpperCase();
        int certLen = CheckLength(certCode);
        CheckCertMatch(certCode);
        CheckCertProvince(certCode);
        CheckYMD(GetYMD(certCode, certLen));
        Check18CertCodeValidFlag(certCode, certLen);
    };

    /**
     * 校验身份证长度是否符合要求
     * 
     * @param certCode
     */
    private int CheckLength(String certCode) {
        int certLen = certCode.length();
        if (15 != certLen && 18 != certLen) {
            throw new EcAopServerBizException("9999", "证件号码:" + certCode + "不为15或18位");
        }
        return certLen;
    }

    /**
     * 校验身份证是否符合15、18位数字或17位数字+字母x或X
     * 
     * @param certCode
     */
    private void CheckCertMatch(String certCode) {
        String matchRegx = "^\\d{15}|\\d{17}[0-9xX]$";
        if (!certCode.matches(matchRegx)) {
            throw new EcAopServerBizException("9999", "证件号码:" + certCode + "不合法");
        }
    }

    /**
     * 校验身份证前两位是否符合既定省份编码规范
     * 
     * @param certCode
     */
    private void CheckCertProvince(String certCode) {
        String province = certCode.substring(0, 2);
        if (null == provMap.get(province)) {
            throw new EcAopServerBizException("9999", "证件号码:" + certCode + "前2位不是有效的行政区划代码");
        }
    }

    /**
     * 通过证件号码获取年月日信息
     * 
     * @param certCode
     * @param certLen
     * @return
     */
    private Map GetYMD(String certCode, int certLen) {
        return 15 == certLen ? Get15YMD(certCode) : Get18YMD(certCode);
    }

    /**
     * 获取15位身份证的年月日信息
     * 
     * @param certCode
     * @return
     */
    private Map Get15YMD(String certCode) {
        Map dataMap = new HashMap();
        dataMap.put("year", "19" + certCode.subSequence(6, 8));
        dataMap.put("month", certCode.subSequence(8, 10));
        dataMap.put("day", certCode.subSequence(10, 12));
        return dataMap;
    }

    /**
     * 获取18位身份证的年月日信息
     * 
     * @param certCode
     * @return
     */
    private Map Get18YMD(String certCode) {
        Map dataMap = new HashMap();
        dataMap.put("year", certCode.subSequence(6, 10));
        dataMap.put("month", certCode.subSequence(10, 12));
        dataMap.put("day", certCode.subSequence(12, 14));
        return dataMap;
    }

    /**
     * 校验获取到的年月日信息
     * 
     * @param inMap
     */
    private void CheckYMD(Map inMap) {
        String year = inMap.get("year").toString();
        String month = inMap.get("month").toString();
        String day = inMap.get("day").toString();
        CheckYear(year);
        CheckMonth(month);
        CheckDay(year, month, day);
    }

    /**
     * 校验出生年份和1850的大小关系
     * 
     * @param year
     */
    private void CheckYear(String year) {
        if (year.compareTo("1850") < 0) {
            throw new EcAopServerBizException("9999", "出生年份:" + year + "早于1850年");
        }
    }

    /**
     * 校验年份是否在[01,12]范围内
     * 
     * @param month
     */
    private void CheckMonth(String month) {
        if (month.compareTo("12") > 0 || month.compareTo("01") < 0) {
            throw new EcAopServerBizException("9999", "出生月份:" + month + "不合法,不在[01,12]区间范围");
        }
    }

    /**
     * 校验日期天数是否合法
     * 
     * @param year
     * @param month
     * @param day
     */
    private void CheckDay(String year, String month, String day) {
        if (day.compareTo("01") < 0) {
            throw new EcAopServerBizException("9999", "出生日期:" + month + "月" + day + "日不合法");
        }
        Object certDay = monthDay.get(month);

        // 月份为二月时,根据是否为闰年,赋予不同的天数
        if (null == certDay) {
            certDay = true == IsLeapYear(year) ? "29" : "28";
        }
        if (day.compareTo(certDay.toString()) > 0) {
            throw new EcAopServerBizException("9999", "出生日期:" + month + "月" + day + "日不合法");
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        String nowYear = String.valueOf(cal.get(Calendar.YEAR));
        if (nowYear.compareTo(year) < 0) {
            throw new EcAopServerBizException("9999", "出生年份:" + year + "晚于当前年份");
        }
        String nowMonth = String.valueOf(cal.get(Calendar.MONTH) + 1);
        if (nowYear.equals(year) && nowMonth.compareTo(month) < 0) {
            throw new EcAopServerBizException("9999", "出生年月:" + year + "年" + month + "月晚于当前年月");
        }
        String nowDay = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
        if (nowYear.equals(year) && nowMonth.equals(month) && nowDay.compareTo(day) < 0) {
            throw new EcAopServerBizException("9999", "出生日期:" + year + "年" + month + "月" + day + "日晚于当前时间");
        }
    }

    /**
     * 判断一年是否为闰年
     * 
     * @param year
     * @return
     */
    private boolean IsLeapYear(String year) {
        int yearInteger = Integer.valueOf(year);
        if (yearInteger % 4 == 0 && yearInteger % 100 != 0 || yearInteger % 400 == 0) {
            return true;
        }
        return false;
    }

    /**
     * 校验18位身份证的验证位
     * 
     * @param certCode
     */
    private void Check18CertCodeValidFlag(String certCode, int certLen) {
        if (18 != certLen) {
            return;
        }
        String verify = certCode.substring(17, 18);
        if (!verify.equals(GetVerify(certCode))) {
            throw new EcAopServerBizException("9999", "输入的身份证号最末尾的数字验证码错误");
        }
    }

    /**
     * 按照前17位模拟生成校验位
     * 
     * @param certCode
     * @return
     */
    private String GetVerify(String certCode) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += Integer.valueOf(certCode.substring(i, i + 1)) * Wi[i];
        }
        return String.valueOf(Vi[sum % 11]);
    }
}
