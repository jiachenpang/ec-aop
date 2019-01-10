package org.n3r.core.io;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class RFile {
    public static void cleanDir(String dir) {
        cleanDir(new File(dir));
    }

    public static void cleanDir(File path) {
        try {
            if (path.exists()) FileUtils.cleanDirectory(path);
        } catch (IOException e) {
            // Ingore
        }
    }


    public static String readFileToString(File file) {
        try {
            return FileUtils.readFileToString(file, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeStringToFile(String meta, File file) {
        try {
            FileUtils.writeStringToFile(file, meta);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File createTempFile(String prefix, String suffix) {
        try {
            return File.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
