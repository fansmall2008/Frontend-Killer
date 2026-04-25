package com.gamelist.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    
    private static final Logger logger = LoggerFactory.getLogger(LogController.class);
    private static final String LOG_FILE_PATH = "logs/application.log";
    
    @GetMapping("/realtime")
    public ResponseEntity<List<String>> getRealtimeLogs(
            @RequestParam(defaultValue = "100") int lines) {
        try {
            File logFile = new File(LOG_FILE_PATH);
            if (!logFile.exists()) {
                List<String> emptyLogs = new ArrayList<>();
                emptyLogs.add("日志文件不存在，可能还没有生成");
                return ResponseEntity.ok(emptyLogs);
            }
            
            List<String> logLines = readLastLines(logFile, lines);
            return ResponseEntity.ok(logLines);
        } catch (Exception e) {
            logger.error("获取实时日志失败", e);
            List<String> errorLogs = new ArrayList<>();
            errorLogs.add("获取日志失败: " + e.getMessage());
            return ResponseEntity.ok(errorLogs);
        }
    }
    
    private List<String> readLastLines(File file, int lines) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            
            // 使用环形缓冲区存储最后N行
            String[] buffer = new String[lines];
            int count = 0;
            String line;
            
            while ((line = reader.readLine()) != null) {
                buffer[count % lines] = line;
                count++;
            }
            
            // 从缓冲区中提取有效行
            int start = count > lines ? count % lines : 0;
            int end = count > lines ? lines : count;
            
            for (int i = 0; i < end; i++) {
                int index = (start + i) % lines;
                if (buffer[index] != null) {
                    result.add(buffer[index]);
                }
            }
        }
        return result;
    }
    
    @GetMapping("/status")
    public ResponseEntity<Boolean> getLogFileStatus() {
        File logFile = new File(LOG_FILE_PATH);
        return ResponseEntity.ok(logFile.exists() && logFile.canRead());
    }
}