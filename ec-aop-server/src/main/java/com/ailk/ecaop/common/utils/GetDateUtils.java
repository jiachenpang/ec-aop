/**************************************************************************************************
 * 此工具类用于获取时间
 * 不传参数时,返回当前时间
 * 传参数时,返回当前时间与参数时间之和,入参单位为min
 * 返回的时间格式为yyyyMMddHHmmss,如20130517143023
 **/
package com.ailk.ecaop.common.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class GetDateUtils {

    /**
     * 获取当前时间
     * @return
     */
    public static String getDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        return format.format(new Date());
    }

    /**
     * 获取系统时间
     * return String 格式"yyyy-MM-dd HH:mm:ss"
     * @throws
     */
    public static String getSysdateFormat() {
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateFormat.format(now);
    }

    /**
     * 返回当前时间+minute
     * @param minute
     * @return
     */
    public static String getDate(int minute) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MINUTE, minute);
        return format.format(cal.getTime());
    }

    /**
     * 返回传入时间的N秒,负数为向前,正数为向后
     * @param minute
     * @return
     * @throws Exception
     */
    public static String getBefDate(String date, int second) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar cal = Calendar.getInstance();
        cal.setTime(format.parse(date));
        cal.add(Calendar.SECOND, second);
        return format.format(cal.getTime());
    }

    /**
     * 返回下一个月的第一天
     * @return
     */
    public static String getNextMonthDate() {
        Date date = null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat formatNow = new SimpleDateFormat("yyyy-MM");

        Calendar cal = Calendar.getInstance();
        String now = formatNow.format(new Date());
        try {
            date = formatNow.parse(now);
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        cal.setTime(date);
        cal.add(Calendar.MONTH, 1);
        return format.format(cal.getTime());
    }

    /**
     * 不同长度的日期格式转换
     * @param oldDateStr
     * @param to
     * @return
     * @throws Exception
     */
    public static String transDate(String oldDateStr, int to) throws Exception {

        String newDateStr = null;

        int from = oldDateStr.trim().length();
        if ("".equals(oldDateStr.trim())) {
            return oldDateStr;
        }

        if (from == to) {
            return oldDateStr;
        }

        SimpleDateFormat oldSdf = null;
        switch (from) {
        case 8:
            oldSdf = new SimpleDateFormat("yyyyMMdd");
            break;
        case 10:
            oldSdf = new SimpleDateFormat("yyyy-MM-dd");
            break;
        case 14:
            oldSdf = new SimpleDateFormat("yyyyMMddHHmmss");
            break;
        case 19:
            oldSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            break;
        default:
            throw new Exception("此方法不支持原时间长度的转换:" + oldDateStr);

        }

        SimpleDateFormat newSdf = null;
        switch (to) {
        case 8:
            newSdf = new SimpleDateFormat("yyyyMMdd");
            break;
        case 10:
            newSdf = new SimpleDateFormat("yyyy-MM-dd");
            break;
        case 14:
            newSdf = new SimpleDateFormat("yyyyMMddHHmmss");
            break;
        case 19:
            newSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            break;
        default:
            throw new Exception("此方法不支持时间长度为[" + to + "]的转换！");

        }

        try {
            newDateStr = newSdf.format(oldSdf.parse(oldDateStr.trim()));
        }
        catch (Exception e) {
            throw new Exception(" 时间字符串长度转换出错！");
        }

        return newDateStr.substring(0, to);
    }

    /**
     * @param enableTag 生效方式：0-绝对生效时间，1-相对生效时间
     * @param mode 生效偏移单位：0:天 1:自然天 2:月 3:自然月 4:年 5:自然年 6:秒
     * @param datetime
     * @param amount 生效偏移值
     * @return
     */
    public static String getSpecifyDateTime(int enableTag, int mode, String datetime, int amount) {
        String result = null;
        if (enableTag == 0 || amount == 0) {
            result = datetime;
        }
        else {
            switch (mode) {
            case 0:
                result = rollDateStr(datetime, "yyyy-MM-dd HH:mm:ss", Calendar.DATE, amount);
                break;
            case 1:
                result = rollDateStr(datetime.substring(0, 10) + " 00:00:00", "yyyy-MM-dd HH:mm:ss", Calendar.DATE,
                        amount);
                break;
            case 2:
                result = rollDateStr(datetime, "yyyy-MM-dd HH:mm:ss", Calendar.MONTH, amount);
                break;
            case 3:
                result = rollDateStr(datetime.substring(0, 10) + " 00:00:00", "yyyy-MM-dd HH:mm:ss", Calendar.MONTH,
                        amount).substring(0, 8)
                        + "01 00:00:00";
                break;
            case 4:
                result = rollDateStr(datetime, "yyyy-MM-dd HH:mm:ss", Calendar.YEAR, amount);
                break;
            case 5:
                result = rollDateStr(datetime.substring(0, 10) + " 00:00:00", "yyyy-MM-dd HH:mm:ss", Calendar.YEAR,
                        amount).substring(0, 5)
                        + "01-01 00:00:00";
                break;
            case 6:
                result = rollDateStr(datetime, "yyyy-MM-dd HH:mm:ss", Calendar.SECOND, amount);
                break;
            case 7:
                result = rollDateStr(datetime, "yyyyMMddHHmmss", Calendar.SECOND, amount);
                break;
            }
        }
        return result;
    }

    /**
     * 将一个指定格式的日期,增加day天后以原有格式返回
     * @param date
     * @param pattern 详细参见SimpleDateFormat
     * @param field 详细参见Calendar
     * @param amount
     * @return
     */
    public static String rollDateStr(String datetime, String pattern, int field, int amount) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        Date date = null;
        try {
            date = sdf.parse(datetime);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Calendar cd = Calendar.getInstance();
        cd.setTime(date);
        cd.add(field, amount);
        date = cd.getTime();
        return sdf.format(date);
    }

    /**
     * 获取当月最后一天(yyyyMMddHHmmss) 带参数
     * @param time
     * @param pattern
     * @return
     *         zhousq
     */
    public static String getMonthLastDayFormat1(String endData) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = null;
        try {
            date = sdf.parse(endData);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.roll(Calendar.DAY_OF_MONTH, -1);
        return new SimpleDateFormat("yyyyMMddHHmmss").format(cal.getTime());
    }

    /**
     * 获取原资费结束时间
     * 获取变更资费的开始时间
     * @author zhousq
     */
    public static String getNextDiscntStartDate(String startDate, int cycle) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String data = GetDateUtils.rollDateStr(startDate, "yyyyMMddHHmmss", Calendar.MONTH, cycle);
        String startData = data.substring(0, 10) + "235959";// 获取原资费结束时间
        Calendar calendar = Calendar.getInstance();// 获取日历实例
        try {
            calendar.setTime(sdf.parse(startData));
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH) + 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        return new SimpleDateFormat("yyyyMMddHHmmss").format(calendar.getTime());
    }

    /**
     * 获取当月最后一天
     * @return
     */
    public static String getMonthLastDay() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        // cal.add(Calendar.MONTH, 1);
        cal.roll(Calendar.DAY_OF_MONTH, -1);
        return (new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime()) + " 23:59:59");
    }

    /**
     * 获取当月最后一天(yyyyMMddHHmmss)
     * @param time
     * @param pattern
     * @return
     */
    public static String getMonthLastDayFormat() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.roll(Calendar.DAY_OF_MONTH, -1);
        return (new SimpleDateFormat("yyyyMMddHHmmss").format(cal.getTime()));
    }

    /**
     * 获取下月第一天0时0分0秒时间
     * @param time
     * @param pattern
     * @return
     */
    public static String getNextMonthFirstDayFormat() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) + 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return (new SimpleDateFormat("yyyyMMddHHmmss").format(cal.getTime()));
    }

    /**
     * 取某个日期加X月的前一天
     * @author yangzg
     */
    public static String getDueTime(String endDate, String pattern, int field, int monthnum) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        Date date = null;
        try {
            date = sdf.parse(endDate);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(field, monthnum);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        date = calendar.getTime();
        return sdf.format(date);
    }

    /**
     * 获取资费过期时间的次月1日0点0分0秒
     * @author yangzg
     */
    public static String getNextDiscntDate(String endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar calendar = Calendar.getInstance();// 获取日历实例
        try {
            calendar.setTime(sdf.parse(endDate));
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH) + 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        return (new SimpleDateFormat("yyyyMMddHHmmss").format(calendar.getTime()));
    }

    /**
     * 获取下月最后一天23时59分59秒时间
     * @return
     */
    public static String getNextMonthLastDayFormat() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) + 1);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        return (new SimpleDateFormat("yyyyMMddHHmmss").format(cal.getTime()));
    }

    /**
     * 获取当前时间前一天的23:59:59
     * @param time
     * @param pattern
     * @return
     */
    public static String getEndDateFormat() {

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return new SimpleDateFormat("yyyyMMddHHmmss").format(cal.getTime());
    }

    /**
     * 获取当前时间次日的00:00:00
     * @param time
     * @param pattern
     * @return
     */
    public static String getNextDateFormat() {

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return new SimpleDateFormat("yyyyMMddHHmmss").format(cal.getTime());
    }

    /**
     * 根据传入月数计算时间
     * @param time
     * @param pattern
     * @return
     */
    public static String getEndDateFormat(int mouthNum) {

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) + mouthNum);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return (new SimpleDateFormat("yyyyMMddHHmmss").format(cal.getTime()));
    }

    /**
     * 将日期格式为yyyy-MM-dd HH:mm:ss改为yyyyMMddHHmmss
     * @param time
     * @param pattern
     * @return
     */
    public static String TransDate(String time, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        Date date = null;
        try {
            date = sdf.parse(time);
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf.format(date);
    }

    /**
     * 将日期格式为yyyyMMddHHmmss改为yyyy-MM-dd HH:mm:ss
     * @param time
     * @param pattern
     * @return
     */
    public static String TransDateFormat(String time, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        Date date = null;
        try {
            date = sdf.parse(time);
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    /**
     * 计算两个日期相差月份数
     * 如果前一个日期小于后一个日期，则返回负数
     * @param one 第一个日期数，作为基准,处理时,添加了秒,存在23:59:59场景
     * @param two 第二个日期数，作为比较
     * @return 两个日期相差月份数
     */
    public static int diffMonths(String one, String two) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        Calendar calendar = Calendar.getInstance();
        // 得到第一个日期的年分和月份数
        calendar.setTime(sdf.parse(one));
        calendar.add(Calendar.SECOND, 1);
        int yearOne = calendar.get(Calendar.YEAR);
        int monthOne = calendar.get(Calendar.MONDAY);
        // 得到第二个日期的年份和月份
        calendar.setTime(sdf.parse(two));
        int yearTwo = calendar.get(Calendar.YEAR);
        int monthTwo = calendar.get(Calendar.MONDAY);
        return (yearOne - yearTwo) * 12 + monthOne - monthTwo;
    }

}
