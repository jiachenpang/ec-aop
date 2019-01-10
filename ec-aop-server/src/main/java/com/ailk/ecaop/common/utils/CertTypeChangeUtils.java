package com.ailk.ecaop.common.utils;

import com.ailk.ecaop.common.helper.MagicNumber;

/**
 * 此方法用于商城侧证件类型到全业务侧证件类型的转换
 * 商城侧证件类型的编码对应关系表
 * 01 15位身份证 ,02 18位身份证,03 驾驶证,04 军官证
 * 05 教师证,06 学生证,07 营业执照,08 护照
 * 09 武警身份证,10 港澳居民来往内地通行证,11 台湾居民来往大陆通行证
 * 12 户口本,13 组织机构代码证,14 事业单位法人证书,15 介绍信
 * 16 测试号码证件,17 社会团体法人登记证书,18 照会,99 其他
 * 当商城传过来的证件类型在规范中不存在时，不进行转换，直接按其他处理
 * 这样处理，如果商城新增证件类型，而全业务未添加，代码可以不进行改造
 * 还有一种情形,商城传递过来的证件类型不合法,也进行默认处理
 */
public class CertTypeChangeUtils {

    // 2017-1-9新增证件类别： 统一社会信用代码证33
    // AOP侧证件类型
    static String[] cert_Mall = { "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14",
            "15", "16", "17", "18", "99", "21", "33", "20", "34", "35" };

    // 全业务侧证件类型
    static String[] cert_Fbs = { "12", "01", "13", "05", "13", "13", "06", "02", "08", "10", "11", "09", "14", "15",
            "16", "17", "18", "19", "13", "21", "33", "20", "34", "35" };

    // cbss侧证件类型
    static String[] cert_Cbss = { "0", "1", "8", "3", "8", "8", "4", "2", "5", "6", "7", "9", "S", "R", "N", "J", "L",
            "U", "8", "M", "D", "T", "F", "G" };

    public static String certTypeMall2Fbs(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = MagicNumber.CERT_TYPE_CODE_OTHER;
        for (int i = 0; i < cert_Mall.length; i++) {
            if (certType.equals(cert_Mall[i])) {
                index = i;
                break;
            }
        }
        return cert_Fbs[index];
    }

    public static String certTypeMall2Cbss(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = 18;
        for (int i = 0; i < cert_Mall.length; i++) {
            if (certType.equals(cert_Mall[i])) {
                index = i;
                break;
            }
        }
        if ("99".contains(certType)) {
            index = MagicNumber.CERT_TYPE_CODE_OTHER;
        }
        return cert_Cbss[index];
    }

    public static String certTypeCbss2Mall(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = 18;
        for (int i = 0; i < cert_Cbss.length; i++) {
            if (certType.equals(cert_Cbss[i])) {
                index = i;
                break;
            }
        }
        if ("8".contains(certType)) {
            index = MagicNumber.CERT_TYPE_CODE_OTHER;
        }
        return cert_Mall[index];
    }

    public static String certTypeFbs2Mall(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = 18;
        for (int i = 0; i < cert_Fbs.length; i++) {
            if (certType.equals(cert_Fbs[i])) {
                index = i;
                break;
            }
        }
        if ("13".contains(certType)) {
            index = MagicNumber.CERT_TYPE_CODE_OTHER;
        }
        return cert_Mall[index];
    }

    public static void main(String[] args) {
        System.out.println(certTypeFbs2Mall("13"));
    }

    /**
     * 全业务证件类型转换为cb证件类型
     * @param certType
     * @return
     */
    public static String certTypeFbs2Cbss(String certType) {

        // 默认值为18,即:证件类型为其他
        int index = 18;
        for (int i = 0; i < cert_Fbs.length; i++) {
            if (certType.equals(cert_Fbs[i])) {
                index = i;
                break;
            }
        }
        if ("13".contains(certType)) {
            index = MagicNumber.CERT_TYPE_CODE_OTHER;
        }
        return cert_Cbss[index];
    }

}