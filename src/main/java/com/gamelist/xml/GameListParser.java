package com.gamelist.xml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.PlatformType;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

public class GameListParser {
    
    private static final Logger logger = LoggerFactory.getLogger(GameListParser.class);
    
    public static GameListXml parseGameList(File xmlFile) throws JAXBException {
        logger.info("开始解析XML文件: {}", xmlFile.getAbsolutePath());
        logger.info("文件存在: {}", xmlFile.exists());
        logger.info("文件可读: {}", xmlFile.canRead());
        logger.info("文件大小: {} bytes", xmlFile.length());
        logger.info("文件绝对路径: {}", xmlFile.getAbsolutePath());
        logger.info("文件权限: 可读={}, 可写={}, 可执行={}", xmlFile.canRead(), xmlFile.canWrite(), xmlFile.canExecute());
        
        // 尝试读取文件的前几行，检查文件内容
        try {
            logger.info("尝试读取文件内容...");
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(xmlFile))) {
                String line;
                int lineCount = 0;
                while ((line = br.readLine()) != null && lineCount < 10) {
                    logger.info("文件内容行 {}: {}", lineCount + 1, line);
                    lineCount++;
                }
                if (lineCount >= 10) {
                    logger.info("文件内容超过10行，已截断...");
                }
            }
        } catch (Exception e) {
            logger.error("读取文件内容失败: {}", e.getMessage(), e);
        }
        
        // 直接使用FileInputStream读取文件，避免Reader可能的编码问题
        try (java.io.FileInputStream fis = new java.io.FileInputStream(xmlFile)) {
            logger.info("创建JAXB上下文...");
            JAXBContext jaxbContext = JAXBContext.newInstance(GameListXml.class);
            logger.info("JAXB上下文创建成功: {}", jaxbContext);
            
            logger.info("创建解组器...");
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            logger.info("解组器创建成功: {}", jaxbUnmarshaller);
            
            logger.info("开始解组XML...");
            try {
                Object unmarshalledObject = jaxbUnmarshaller.unmarshal(fis);
                logger.info("解组完成，结果类型: {}", unmarshalledObject.getClass().getName());
                
                if (!(unmarshalledObject instanceof GameListXml)) {
                    logger.error("解组结果不是GameListXml类型，而是: {}", unmarshalledObject.getClass().getName());
                    throw new JAXBException("解组结果类型错误: " + unmarshalledObject.getClass().getName());
                }
                
                GameListXml result = (GameListXml) unmarshalledObject;
                logger.info("转换为GameListXml成功！");
                
                // 添加空指针检查
                if (result.getProvider() != null) {
                    logger.info("Provider不为null");
                    if (result.getProvider().getSystem() != null) {
                        logger.info("平台名称: {}", result.getProvider().getSystem());
                    } else {
                        logger.info("平台名称: null");
                    }
                    if (result.getProvider().getSoftware() != null) {
                        logger.info("软件名称: {}", result.getProvider().getSoftware());
                    } else {
                        logger.info("软件名称: null");
                    }
                    if (result.getProvider().getDatabase() != null) {
                        logger.info("数据库名称: {}", result.getProvider().getDatabase());
                    } else {
                        logger.info("数据库名称: null");
                    }
                    if (result.getProvider().getWeb() != null) {
                        logger.info("Web地址: {}", result.getProvider().getWeb());
                    } else {
                        logger.info("Web地址: null");
                    }
                } else {
                    logger.info("Provider为null");
                }
                
                if (result.getGame() != null) {
                    logger.info("游戏列表不为null，游戏数量: {}", result.getGame().size());
                    // 检查每个游戏的name字段
                    int gameIndex = 0;
                    for (GameListXml.GameXml gameXml : result.getGame()) {
                        gameIndex++;
                        if (gameXml.getName() == null || gameXml.getName().isEmpty()) {
                            logger.warn("游戏 #{} 的name字段为null或空", gameIndex);
                            // 尝试定位具体行号
                            locateNameNullLine(xmlFile, gameIndex);
                        }
                    }
                } else {
                    logger.info("游戏列表为null");
                }
                
                return result;
            } catch (JAXBException e) {
                logger.error("JAXB解组失败: {}", e.getMessage());
                logger.error("JAXB异常详细信息:", e);
                // 尝试获取更详细的错误信息
                Throwable cause = e.getCause();
                if (cause != null) {
                    logger.error("JAXB异常原因: {}", cause.getMessage());
                    logger.error("JAXB异常原因详细信息:", cause);
                }
                throw e;
            }
        } catch (JAXBException e) {
            logger.error("JAXB解析失败: {}", e.getMessage(), e);
            // 确保JAXBException有明确的错误消息
            String errorMessage = e.getMessage() != null ? e.getMessage() : "JAXB解析失败";
            throw new JAXBException(errorMessage, e);
        } catch (FileNotFoundException e) {
            logger.error("文件未找到: {}", e.getMessage(), e);
            throw new JAXBException("文件未找到: " + xmlFile.getAbsolutePath(), e);
        } catch (IOException e) {
            logger.error("IO异常: {}", e.getMessage(), e);
            throw new JAXBException("IO异常: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("解析失败: {}", e.getMessage(), e);
            // 确保所有异常都有明确的错误消息
            String errorMessage = e.getMessage() != null ? e.getMessage() : "解析失败";
            throw new JAXBException("解析失败: " + errorMessage, e);
        }
    }
    
    /**
     * 定位XML文件中name为null的游戏条目所在的行号
     * @param xmlFile XML文件
     * @param gameIndex 游戏索引（从1开始）
     */
    private static void locateNameNullLine(File xmlFile, int gameIndex) {
        try {
            logger.info("开始定位游戏 #{} 的name为null的行号", gameIndex);
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(xmlFile))) {
                String line;
                int lineCount = 0;
                int gameCount = 0;
                boolean inGameTag = false;
                boolean foundNameTag = false;
                
                while ((line = br.readLine()) != null) {
                    lineCount++;
                    
                    // 检查游戏标签
                    if (line.trim().startsWith("<game")) {
                        gameCount++;
                        inGameTag = true;
                        foundNameTag = false;
                        logger.debug("找到游戏 #{} 开始于行 {}", gameCount, lineCount);
                    }
                    
                    // 如果是当前游戏，检查name标签
                    if (gameCount == gameIndex && inGameTag) {
                        if (line.trim().startsWith("<name")) {
                            foundNameTag = true;
                            String nameContent = line.trim();
                            // 检查name标签内容
                            if (nameContent.equals("<name/>")) {
                                logger.warn("游戏 #{} 的name标签为空，位于行 {}", gameIndex, lineCount);
                            } else if (nameContent.startsWith("<name>") && nameContent.endsWith("</name>") && 
                                       nameContent.length() <= 11) { // <name></name>
                                logger.warn("游戏 #{} 的name标签内容为空，位于行 {}", gameIndex, lineCount);
                            }
                        }
                    }
                    
                    // 检查游戏结束标签
                    if (inGameTag && line.trim().equals("</game>")) {
                        inGameTag = false;
                        // 如果当前游戏没有找到name标签
                        if (gameCount == gameIndex && !foundNameTag) {
                            logger.warn("游戏 #{} 缺少name标签，结束于行 {}", gameIndex, lineCount);
                        }
                    }
                }
                
                logger.info("定位完成，共找到 {} 个游戏条目", gameCount);
            }
        } catch (Exception e) {
            logger.error("定位行号失败: {}", e.getMessage(), e);
        }
    }
    
    public static GameListXml parseGameList(String xmlFilePath) throws JAXBException {
        return parseGameList(new File(xmlFilePath));
    }
    
    /**
     * 识别平台类型
     * @param gameListXml 解析后的GameListXml对象
     * @return 识别的平台类型
     */
    public static String detectPlatformType(GameListXml gameListXml) {
        if (gameListXml == null) {
            return PlatformType.GENERIC;
        }
        
        // 首先检查Provider信息
        if (gameListXml.getProvider() != null) {
            // 检查System字段
            if (gameListXml.getProvider().getSystem() != null) {
                String platformType = PlatformType.detectPlatformType(gameListXml.getProvider().getSystem());
                if (!PlatformType.GENERIC.equals(platformType)) {
                    logger.info("通过System字段识别平台类型: {}", platformType);
                    return platformType;
                }
            }
            
            // 检查Software字段
            if (gameListXml.getProvider().getSoftware() != null) {
                String platformType = PlatformType.detectPlatformType(gameListXml.getProvider().getSoftware());
                if (!PlatformType.GENERIC.equals(platformType)) {
                    logger.info("通过Software字段识别平台类型: {}", platformType);
                    return platformType;
                }
            }
        }
        
        // 检查游戏条目是否有平台特定字段
        if (gameListXml.getGame() != null && !gameListXml.getGame().isEmpty()) {
            GameListXml.GameXml firstGame = gameListXml.getGame().get(0);
            
            // 检查RetroBat特定字段
            if (firstGame.getHash() != null) {
                logger.info("通过hash字段识别平台类型: retrobat");
                return PlatformType.RETROBAT;
            }
        }
        
        logger.info("未识别到特定平台类型，使用通用类型");
        return PlatformType.GENERIC;
    }
    
    /**
     * 解析游戏列表XML文件并识别平台类型
     * @param xmlFile XML文件
     * @return 包含平台类型的解析结果
     * @throws JAXBException 解析异常
     */
    public static PlatformSpecificGameList parseGameListWithPlatformDetection(File xmlFile) throws JAXBException {
        GameListXml gameListXml = parseGameList(xmlFile);
        String platformType = detectPlatformType(gameListXml);
        return new PlatformSpecificGameList(gameListXml, platformType);
    }
    
    /**
     * 平台特定的游戏列表结果
     */
    public static class PlatformSpecificGameList {
        private final GameListXml gameListXml;
        private final String platformType;
        
        public PlatformSpecificGameList(GameListXml gameListXml, String platformType) {
            this.gameListXml = gameListXml;
            this.platformType = platformType;
        }
        
        public GameListXml getGameListXml() {
            return gameListXml;
        }
        
        public String getPlatformType() {
            return platformType;
        }
    }
    
    // 测试方法
    public static void main(String[] args) {
        try {
            logger.info("开始解析XML文件...");
            // 测试atari5200 XML文件
            String atari5200Path = "Y:\\retrobat\\atari5200\\gamelist.xml";
            logger.info("测试文件路径: {}", atari5200Path);
            PlatformSpecificGameList result = parseGameListWithPlatformDetection(new File(atari5200Path));
            logger.info("解析成功！");
            logger.info("识别的平台类型: {}", result.getPlatformType());
            
            // 输出解析结果
            GameListXml gameListXml = result.getGameListXml();
            if (gameListXml.getProvider() != null) {
                logger.info("平台名称: {}", gameListXml.getProvider().getSystem());
                logger.info("软件名称: {}", gameListXml.getProvider().getSoftware());
                logger.info("数据库: {}", gameListXml.getProvider().getDatabase());
                logger.info("Web: {}", gameListXml.getProvider().getWeb());
            }
            if (gameListXml.getGame() != null) {
                logger.info("游戏数量: {}", gameListXml.getGame().size());
                if (!gameListXml.getGame().isEmpty()) {
                    logger.info("第一个游戏: {}", gameListXml.getGame().get(0).getName());
                }
            }
            
        } catch (Exception e) {
            logger.error("解析失败: {}", e.getMessage(), e);
        }
    }
}
