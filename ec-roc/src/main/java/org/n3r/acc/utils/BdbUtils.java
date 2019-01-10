package org.n3r.acc.utils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.sleepycat.je.Database;
import com.sleepycat.je.Environment;
import org.apache.commons.lang3.StringUtils;
import org.n3r.bdb.Bdbs;
import org.n3r.core.io.RFile;
import org.n3r.core.util.ParamsUtils;

import java.io.File;

public class BdbUtils {
    public static Database applyParams(String[] params, boolean cleanDir) {
        String envPath = ParamsUtils.getStr(params, 0, null);
        if (StringUtils.isEmpty(envPath))
            throw new RuntimeException("first param should be set as bdb path");

        String dbName = ParamsUtils.getStr(params, 1, null);
        if (StringUtils.isEmpty(dbName))
            throw new RuntimeException("second param should be set as bdb dbname");

        if (cleanDir) RFile.cleanDir(envPath);

        Environment environment = Bdbs.openEnv(new File(envPath), 1024 * 1024);
        return Bdbs.openDb(environment, dbName);
    }

    public static void writeMeta(String[] params, String meta) {
        String envPath = ParamsUtils.getStr(params, 0, null);
        String dbName = ParamsUtils.getStr(params, 1, null);
        File file = new File(envPath, dbName + ".meta");
        RFile.writeStringToFile(meta, file);
    }

    public static String readMeta(String[] params) {
        String envPath = ParamsUtils.getStr(params, 0, null);
        String dbName = ParamsUtils.getStr(params, 1, null);
        File file = new File(envPath, dbName + ".meta");
        return RFile.readFileToString(file);
    }

    public static void closeDbAndEvn(Database database) {
        Environment environment = database.getEnvironment();
        Bdbs.closeDb(database);
        Bdbs.closeEnv(environment, false);
    }

    public static String[] readFieldsName(String[] params) {
        String envPath = ParamsUtils.getStr(params, 0, null);
        String dbName = ParamsUtils.getStr(params, 1, null);
        File file = new File(envPath, dbName + ".fields");
        String fields = RFile.readFileToString(file);
        Iterable<String> fieldsName = Splitter.on(',').trimResults().split(fields);
        return Iterables.toArray(fieldsName, String.class);
    }


    public static void writeFieldsName(String[] params, String[] outputFields) {
        String envPath = ParamsUtils.getStr(params, 0, null);
        String dbName = ParamsUtils.getStr(params, 1, null);
        File file = new File(envPath, dbName + ".fields");

        String fields = Joiner.on(',').join(outputFields);
        RFile.writeStringToFile(fields, file);
    }
}
