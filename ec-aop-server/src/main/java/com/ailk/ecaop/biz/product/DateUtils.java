/**
 * 此工具用于且仅用于处理产品的相关时间，请勿随意更改
 */
package com.ailk.ecaop.biz.product;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {

    final static String PATTERN = "yyyyMMddHHmmss";

    /**
     * 获取当前时间
     * 
     * @return
     */
    public static String getDate() {
        SimpleDateFormat format = new SimpleDateFormat(PATTERN);
        return format.format(new Date());
    }

    /**
     * 返回某月的第一天
     * 
     * @return
     */
    private static String getSomeMonthFirstDay(int monthOffset) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MONTH, monthOffset);
        return format.format(calendar.getTime()) + "000000";
    }

    /**
     * 返回次月第一天
     * 
     * @return
     */
    public static String getNextMonthFirstDay() {
        return getSomeMonthFirstDay(1);
    }

    /**返回本月的最后一天
     * 
     */
    public static String getThisMonthLastDay() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Calendar ca = Calendar.getInstance();
        ca.set(Calendar.DAY_OF_MONTH, ca.getActualMaximum(Calendar.DAY_OF_MONTH));
        String last = format.format(ca.getTime()) + "235959";
        return last;
    }

    /**
     * 返回当月第一天
     * 
     * @return
     */
    public static String getMonthFirstDay() {
        return getSomeMonthFirstDay(0);
    }

    public static String max(String date1, String date2) {
        return date1.compareTo(date2) > 0 ? date1 : date2;
    }

    public static Date max(Date date1, Date date2) {
        return date1.after(date2) ? date1 : date2;
    }

    public static String min(String date1, String date2) {
        return date1.compareTo(date2) < 0 ? date1 : date2;
    }

    public static Date min(Date date1, Date date2) {
        return date1.before(date2) ? date1 : date2;
    }

    /**
     * 计算两个日期相差月份数
     * 如果前一个日期小于后一个日期，则返回负数
     * 
     * @param one 第一个日期数，作为基准,处理时,添加了秒,存在23:59:59场景
     * @param two 第二个日期数，作为比较
     * @return 两个日期相差月份数
     */
    public static int diffMonths(Date one, Date two) {
        Calendar calendar = Calendar.getInstance();
        // 得到第一个日期的年分和月份数
        calendar.setTime(one);
        calendar.add(Calendar.SECOND, 1);
        int yearOne = calendar.get(Calendar.YEAR);
        int monthOne = calendar.get(Calendar.MONDAY);
        // 得到第二个日期的年份和月份
        calendar.setTime(two);
        int yearTwo = calendar.get(Calendar.YEAR);
        int monthTwo = calendar.get(Calendar.MONDAY);
        return (yearOne - yearTwo) * 12 + monthOne - monthTwo;
    }

    public static int diffMonths(String one, String two) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN);
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

    /**
     * 在现有时间基础上,添加若干秒
     * 
     * @param date
     * @param seconds
     * @return
     * @throws Exception
     */
    public static String addSeconds(String date, int seconds) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(sdf.parse(date));
        calendar.add(Calendar.SECOND, seconds);
        return sdf.format(calendar.getTime());
    }

    /**
     * 返回前一天的最后时刻
     * 如:输入20161014205131,返回20161013235959
     * 如:输入20161031235959,返回20161031235959
     * 
     * @param date
     * @return
     * @throws Exception
     */
    public static String getBeforeDayLastTime(String date) throws Exception {
        return addSeconds(floor(addSeconds(date, 1)), -1);
    }

    /**
     * 时间处理,向上取整
     * 如:输入20161014205131,返回20161014235959
     * 
     * @param date
     * @return
     */
    public static String ceiling(String date) {
        return date.substring(0, 8) + "235959";
    }

    /**
     * 时间处理,向下取整
     * 如:输入20161014205131,返回20161014000000
     * 
     * @param date
     * @return
     */
    public static String lastDay(String date) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(sdf.parse(date));
        calendar.add(Calendar.MONTH, 0);
        return sdf.format(calendar.getTime());
    }

    /**
     * 增加一些月份
     * 
     * @param date
     * @param months
     * @return
     * @throws Exception
     */
    public static String addMonths(String date, String months) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(sdf.parse(date));
        calendar.add(Calendar.MONTH, Integer.parseInt(months));
        return sdf.format(calendar.getTime());
    }

    /**
     * 时间处理,向下取整
     * 如:输入20161014205131,返回20161014000000
     * 
     * @param date
     * @return
     */
    public static String floor(String date) {
        return date.substring(0, 8) + "000000";
    }

    /**
     * 获取特定时间的当月最后一刻
     * 
     * @param date
     * @return
     * @throws Exception
     */
    public static String theMonthLastTime(String date) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(PATTERN);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(sdf.parse(date));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return sdf.format(calendar.getTime());

    }

    public static void main(String[] args) throws Exception {
        System.out.println(addSeconds("20170831235959", -1));
    }
}
