package com.gamelist.util;

import java.io.File;

public class PathUtil {

    private static final String DOCKER_DATA_PATH = "/data";
    private static final String LOCAL_DATA_PATH = "./data";

    public static String getRulesPath() {
        File dockerPath = new File(DOCKER_DATA_PATH);
        if (dockerPath.exists() && dockerPath.isDirectory() && dockerPath.canRead()) {
            return DOCKER_DATA_PATH + "/rules";
        }
        return LOCAL_DATA_PATH + "/rules";
    }

    public static String getDataPath() {
        File dockerPath = new File(DOCKER_DATA_PATH);
        if (dockerPath.exists() && dockerPath.isDirectory() && dockerPath.canRead()) {
            return DOCKER_DATA_PATH;
        }
        return LOCAL_DATA_PATH;
    }

    public static String getRomsPath() {
        File dockerRomPath = new File("/roms");
        if (dockerRomPath.exists() && dockerRomPath.isDirectory() && dockerRomPath.canRead()) {
            return "/roms";
        }
        File dataRomsPath = new File(getDataPath() + "/roms");
        if (dataRomsPath.exists() && dataRomsPath.isDirectory() && dataRomsPath.canRead()) {
            return getDataPath() + "/roms";
        }
        return "/roms";
    }

    public static String getOutputPath() {
        File dockerOutputPath = new File("/output");
        if (dockerOutputPath.exists() && dockerOutputPath.isDirectory() && dockerOutputPath.canWrite()) {
            return "/output";
        }
        return getDataPath() + "/output";
    }
}