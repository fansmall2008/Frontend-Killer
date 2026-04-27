package com.gamelist.xml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PegasusMetadataParser {

    /**
     * 解析metadata.pegasus.txt文件
     */
    public static PegasusMetadata parseMetadata(File file) throws IOException {
        PegasusMetadata metadata = new PegasusMetadata();
        List<PegasusMetadata.Collection> collections = new ArrayList<>();
        
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
             java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, java.nio.charset.StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            PegasusMetadata.Collection currentCollection = null;
            PegasusMetadata.Game currentGame = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.startsWith("collection:")) {
                    // 开始新的collection
                    if (currentCollection != null && currentGame != null) {
                        currentCollection.getGames().add(currentGame);
                        currentGame = null;
                    }
                    if (currentCollection != null) {
                        collections.add(currentCollection);
                    }
                    
                    currentCollection = new PegasusMetadata.Collection();
                    String collectionName = line.substring("collection:".length()).trim();
                    currentCollection.setName(collectionName);
                    
                } else if (line.startsWith("sort-by:")) {
                    // 处理sort-by字段
                    if (currentCollection != null) {
                        currentCollection.setSortBy(line.substring("sort-by:".length()).trim());
                    } else if (currentGame != null) {
                        currentGame.setSortBy(line.substring("sort-by:".length()).trim());
                    }
                    
                } else if (line.startsWith("launch:")) {
                    // 处理launch字段
                    if (currentCollection != null) {
                        currentCollection.setLaunch(line.substring("launch:".length()).trim());
                    }
                    
                } else if (line.startsWith("game:")) {
                    // 开始新的game
                    if (currentGame != null) {
                        currentCollection.getGames().add(currentGame);
                    }
                    
                    currentGame = new PegasusMetadata.Game();
                    String gameName = line.substring("game:".length()).trim();
                    currentGame.setName(gameName);
                    
                } else if (line.startsWith("file:")) {
                    // 处理file字段
                    if (currentGame != null) {
                        currentGame.setFile(line.substring("file:".length()).trim());
                    }
                    
                } else if (line.startsWith("developer:")) {
                    // 处理developer字段
                    if (currentGame != null) {
                        currentGame.setDeveloper(line.substring("developer:".length()).trim());
                    }
                    
                } else if (line.startsWith("description:")) {
                    // 处理description字段
                    if (currentGame != null) {
                        String description = line.substring("description:".length()).trim();
                        // 读取后续行，直到遇到下一个字段
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            if (line.contains(":")) break;
                            description += " " + line;
                        }
                        currentGame.setDescription(description);
                    }
                }
            }
            
            // 处理最后一个game和collection
            if (currentGame != null) {
                currentCollection.getGames().add(currentGame);
            }
            if (currentCollection != null) {
                collections.add(currentCollection);
            }
        }
        
        metadata.setCollections(collections);
        return metadata;
    }
}
