package com.gamelist.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.config.ScraperSystemInitializer;

@RestController
@RequestMapping("/api/init")
public class InitController {

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getInitStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", ScraperSystemInitializer.getInitStatus());
        response.put("isReady", ScraperSystemInitializer.isReady());
        response.put("isInitializing", ScraperSystemInitializer.isInitializing());
        response.put("systemsCount", ScraperSystemInitializer.getSystemsCount());
        response.put("error", ScraperSystemInitializer.getLastError());

        int status = ScraperSystemInitializer.getInitStatus();
        String message;
        switch (status) {
            case ScraperSystemInitializer.STATUS_NOT_INIT:
                message = "Not initialized";
                break;
            case ScraperSystemInitializer.STATUS_INITIALIZING:
                message = "Fetching system info, please wait...";
                break;
            case ScraperSystemInitializer.STATUS_COMPLETED:
                message = "Initialization completed";
                break;
            case ScraperSystemInitializer.STATUS_FAILED:
                message = "Initialization failed: " + ScraperSystemInitializer.getLastError();
                break;
            default:
                message = "Unknown status";
        }
        response.put("message", message);

        return ResponseEntity.ok(response);
    }
}