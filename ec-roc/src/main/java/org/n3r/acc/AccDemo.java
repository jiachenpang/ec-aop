package org.n3r.acc;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.compare.CompareContext;
import org.n3r.acc.compare.DataCompare;
import org.n3r.acc.process.DataProcessor;

import java.io.File;
import java.io.IOException;

public class AccDemo {
    public static void main(String[] args) throws IOException {
        CompareContext compareContext = new CompareContext();
        parseContextFromArgs(args, compareContext);

        FileUtils.deleteDirectory(new File(".\\bdb\\accdemo"));

        DataProcessor.fromSpec("demoAcc", compareContext).process();
        DataCompare.fromSpec("demoAcc", compareContext).compare();
    }

    private static void parseContextFromArgs(String[] args, CompareContext compareContext) {
        for (int i = 0, ii = args.length; i < ii; ++i) {
            String arg = args[i];
            String key = StringUtils.substringBefore(arg, "=");
            String value = StringUtils.substringAfter(arg, "=");
            compareContext.put(key ,value);
        }

        if (args.length == 0) {
            compareContext.setAccountDay("20130619");
            compareContext.setAccountType("BB");
            compareContext.setProvinceCode("17");
        }
    }
}
