package com.ailk.ecaop.biz.product;

import java.util.ArrayList;
import java.util.List;

import org.n3r.ecaop.core.conf.EcAopConfigLoader;

/**
 * 用于存放一号多卡默认产品，每个数组第一个元素为所属上一级元素，比如资费数组的第一个元素为该数组资费所属的包
 * 
 * @author Administrator
 */
public class OneNumWithMoreCardProduct {

    public List<String[]> getCbssProductInfo() {
        List<String[]> productInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String productStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.product" + i);
            if (null == productStr)
                return productInfo;
            productInfo.add(productStr.split("\\|"));
        }
        return productInfo;
    }

    public List<String[]> getN6ProductInfo() {
        List<String[]> productInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String productStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.N6product" + i);
            if (null == productStr)
                return productInfo;
            productInfo.add(productStr.split("\\|"));
        }
        return productInfo;
    }

    public List<String[]> getCbssPackageInfo() {
        List<String[]> packageInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String packageStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.package" + i);
            if (null == packageStr)
                return packageInfo;
            packageInfo.add(packageStr.split("\\|"));
        }
        return packageInfo;
    }

    public List<String[]> getN6PackageInfo() {
        List<String[]> packageInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String packageStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.N6package" + i);
            if (null == packageStr)
                return packageInfo;
            packageInfo.add(packageStr.split("\\|"));
        }
        return packageInfo;
    }

    public List<String[]> getCbssDiscntInfo() {
        List<String[]> discntInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String discntStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.discnt" + i);
            if (null == discntStr)
                return discntInfo;
            discntInfo.add(discntStr.split("\\|"));
        }
        return discntInfo;
    }

    public List<String[]> getN6DiscntInfo() {
        List<String[]> discntInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String discntStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.N6discnt" + i);
            if (null == discntStr)
                return discntInfo;
            discntInfo.add(discntStr.split("\\|"));
        }
        return discntInfo;
    }

    public List<String[]> getCbssServiceInfo() {
        List<String[]> serviceInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String serviceStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.service" + i);
            if (null == serviceStr)
                return serviceInfo;
            serviceInfo.add(serviceStr.split("\\|"));
        }
        return serviceInfo;
    }

    public List<String[]> getN6ServiceInfo() {
        List<String[]> serviceInfo = new ArrayList<String[]>();
        for (int i = 1; i < 20; i++) {
            String serviceStr = EcAopConfigLoader.getStr("ecaop.global.param.product.oneNumWithMoreCard.N6service" + i);
            if (null == serviceStr)
                return serviceInfo;
            serviceInfo.add(serviceStr.split("\\|"));
        }
        return serviceInfo;
    }
}
