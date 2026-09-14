package com.gamelist.util;

import java.io.File;

public class PathUtil {

    private static final String DOCKER_DATA_PATH = "/data";
    private static final String LOCAL_DATA_PATH = "./data";

    /**
     * 检测是否运行在 Docker 容器中
     * 通过检查 /.dockerenv 文件或 /proc/1/cgroup 中的 docker 标识
     */
    private static boolean isRunningInDocker() {
        // 最可靠的方式：检查 /.dockerenv 文件
        if (new File("/.dockerenv").exists()) {
            return true;
        }
        // 备用方式：检查 cgroup
        try {
            File cgroup = new File("/proc/1/cgroup");
            if (cgroup.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(cgroup.toPath()));
                return content.contains("docker") || content.contains("containerd");
            }
        } catch (Exception e) {
            // 忽略
        }
        return false;
    }

    public static String getRulesPath() {
        if (isRunningInDocker()) {
            return DOCKER_DATA_PATH + "/rules";
        }
        return LOCAL_DATA_PATH + "/rules";
    }

    public static String getDataPath() {
        if (isRunningInDocker()) {
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