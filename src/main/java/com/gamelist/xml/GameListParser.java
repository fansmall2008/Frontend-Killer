package com.gamelist.xml;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.PlatformType;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

public class GameListParser {
    
    private static final Logger logger = LoggerFactory.getLogger(GameListParser.class);
    
    // BOM标记常量
    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] BOM_UTF16BE = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] BOM_UTF16LE = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] BOM_UTF32BE = {(byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF};
    private static final byte[] BOM_UTF32LE = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00};
    
    /**
     * 检测文件的BOM标记并返回对应的字符集
     * @param bis 文件输入流（已包装为BufferedInputStream，支持mark/reset）
     * @return 检测到的字符集，如果未检测到BOM则返回null
     * @throws IOException
     */
    private static Charset detectBOM(BufferedInputStream bis) throws IOException {
        bis.mark(8);
        
        try {
            byte[] bomBuffer = new byte[4];
            int bytesRead = bis.read(bomBuffer);
            
            if (bytesRead >= 3 && Arrays.equals(Arrays.copyOf(bomBuffer, 3), BOM_UTF8)) {
                logger.info("检测到UTF-8 BOM");
                return StandardCharsets.UTF_8;
            } else if (bytesRead >= 2 && Arrays.equals(Arrays.copyOf(bomBuffer, 2), BOM_UTF16BE)) {
                logger.info("检测到UTF-16BE BOM");
                return StandardCharsets.UTF_16BE;
            } else if (bytesRead >= 2 && Arrays.equals(Arrays.copyOf(bomBuffer, 2), BOM_UTF16LE)) {
                logger.info("检测到UTF-16LE BOM");
                return StandardCharsets.UTF_16LE;
            } else if (bytesRead >= 4 && Arrays.equals(bomBuffer, BOM_UTF32BE)) {
                logger.info("检测到UTF-32BE BOM");
                return Charset.forName("UTF-32BE");
            } else if (bytesRead >= 4 && Arrays.equals(bomBuffer, BOM_UTF32LE)) {
                logger.info("检测到UTF-32LE BOM");
                return Charset.forName("UTF-32LE");
            }
            
            return null;
        } finally {
            bis.reset();
        }
    }
    
    /**
     * 从XML声明中提取编码信息
     * @param bis 文件输入流（已包装为BufferedInputStream）
     * @param charset 当前使用的字符集（用于读取XML声明）
     * @return 提取到的编码名称，如果未找到则返回null
     * @throws IOException
     */
    private static String extractEncodingFromXmlDeclaration(BufferedInputStream bis, Charset charset) throws IOException {
        bis.mark(1024);
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(bis, charset))) {
            String firstLine = br.readLine();
            if (firstLine != null && firstLine.startsWith("<?xml")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");
                java.util.regex.Matcher matcher = pattern.matcher(firstLine);
                if (matcher.find()) {
                    String encoding = matcher.group(1);
                    logger.info("从XML声明中提取到编码: {}", encoding);
                    return encoding;
                }
            }
            return null;
        } finally {
            bis.reset();
        }
    }
    
    /**
     * 自动检测文件编码
     * @param xmlFile XML文件
     * @return 检测到的字符集
     */
    private static Charset detectFileEncoding(File xmlFile) {
        logger.info("-------- 开始检测文件编码 --------");
        
        try (FileInputStream fis = new FileInputStream(xmlFile);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            // 设置足够大的缓冲区以支持mark/reset
            bis.mark(8192);
            
            // 1. 首先检测BOM标记
            Charset bomCharset = detectBOM(bis);
            if (bomCharset != null) {
                logger.info("通过BOM检测确定编码: {}", bomCharset.name());
                return bomCharset;
            }
            
            // 2. 如果没有BOM，尝试从XML声明中提取编码
            String declaredEncoding = extractEncodingFromXmlDeclaration(bis, StandardCharsets.UTF_8);
            if (declaredEncoding != null && !declaredEncoding.isEmpty()) {
                try {
                    Charset declaredCharset = Charset.forName(declaredEncoding);
                    logger.info("通过XML声明确定编码: {}", declaredCharset.name());
                    return declaredCharset;
                } catch (Exception e) {
                    logger.warn("XML声明中的编码 {} 无效，使用默认UTF-8", declaredEncoding);
                }
            }
            
            // 3. 默认使用UTF-8
            logger.info("未检测到BOM，XML声明中也未指定编码，使用默认UTF-8");
            return StandardCharsets.UTF_8;
            
        } catch (IOException e) {
            logger.warn("检测文件编码时发生错误，使用默认UTF-8: {}", e.getMessage());
            return StandardCharsets.UTF_8;
        }
    }
    
    /**
     * 尝试使用多种编码解析XML文件
     * @param xmlFile XML文件
     * @param charsets 要尝试的字符集列表
     * @return 解析成功的GameListXml对象
     * @throws JAXBException 如果所有编码都失败
     */
    private static GameListXml tryParseWithEncodings(File xmlFile, List<Charset> charsets) throws JAXBException {
        JAXBException lastException = null;
        
        for (Charset charset : charsets) {
            try {
                logger.info("尝试使用编码 {} 解析XML文件", charset.name());
                return parseWithCharset(xmlFile, charset);
            } catch (JAXBException e) {
                lastException = e;
                logger.warn("使用编码 {} 解析失败: {}", charset.name(), e.getMessage());
            }
        }
        
        throw lastException != null ? lastException : new JAXBException("无法解析XML文件");
    }
    
    /**
     * 使用指定字符集解析XML文件
     */
    private static GameListXml parseWithCharset(File xmlFile, Charset charset) throws JAXBException {
        long startTime = System.currentTimeMillis();
        
        try (FileInputStream fis = new FileInputStream(xmlFile);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            // 设置足够大的缓冲区以支持mark/reset
            bis.mark(8192);
            
            // 跳过BOM（如果存在）
            skipBOM(bis, charset);
            
            logger.info("-------- 创建JAXB上下文 --------");
            JAXBContext jaxbContext = JAXBContext.newInstance(GameListXml.class);
            logger.info("JAXB上下文创建成功，类: {}", jaxbContext.getClass().getName());
            
            logger.info("-------- 创建解组器 --------");
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            logger.info("解组器创建成功，类: {}", jaxbUnmarshaller.getClass().getName());
            
            // 设置事件处理器，忽略未知元素错误
            try {
                // 设置JAXB忽略未知元素
                jaxbUnmarshaller.setProperty("org.glassfish.jaxb.runtime.unmarshaller.ignore.unknown.element", true);
                
                jaxbUnmarshaller.setEventHandler(new jakarta.xml.bind.ValidationEventHandler() {
                    @Override
                    public boolean handleEvent(jakarta.xml.bind.ValidationEvent event) {
                        // 只记录严重级别为ERROR或FATAL_ERROR的事件
                        if (event.getSeverity() >= jakarta.xml.bind.ValidationEvent.ERROR) {
                            logger.warn("JAXB验证事件 - 严重级别: {}, 消息: {}", 
                                event.getSeverity() == jakarta.xml.bind.ValidationEvent.WARNING ? "WARNING" :
                                event.getSeverity() == jakarta.xml.bind.ValidationEvent.ERROR ? "ERROR" : "FATAL_ERROR",
                                event.getMessage());
                            if (event.getLocator() != null) {
                                logger.warn("  - 行号: {}, 列号: {}", 
                                    event.getLocator().getLineNumber(), 
                                    event.getLocator().getColumnNumber());
                            }
                        }
                        return true; // 继续解析，忽略未知元素
                    }
                });
                logger.info("已设置JAXB验证事件处理器（忽略未知元素）");
            } catch (Exception e) {
                logger.warn("设置验证事件处理器失败: {}", e.getMessage());
            }
            
            logger.info("-------- 开始解组XML --------");
            
            // 直接使用InputStream传给JAXB，让JAXB根据XML声明处理编码
            Object unmarshalledObject = jaxbUnmarshaller.unmarshal(bis);
            long parseTime = System.currentTimeMillis() - startTime;
            logger.info("解组完成！耗时: {} ms", parseTime);
            logger.info("解组结果类型: {}", unmarshalledObject.getClass().getName());
            
            if (!(unmarshalledObject instanceof GameListXml)) {
                logger.error("解组结果不是GameListXml类型，而是: {}", unmarshalledObject.getClass().getName());
                throw new JAXBException("解组结果类型错误: " + unmarshalledObject.getClass().getName());
            }
            
            GameListXml result = (GameListXml) unmarshalledObject;
            logger.info("转换为GameListXml成功！");
            
            // 添加空指针检查
            logger.info("-------- 解析结果检查 --------");
            if (result.getProvider() != null) {
                logger.info("Provider: 非null");
                logger.info("  - System: {}", result.getProvider().getSystem() != null ? result.getProvider().getSystem() : "null");
                logger.info("  - Software: {}", result.getProvider().getSoftware() != null ? result.getProvider().getSoftware() : "null");
                logger.info("  - Database: {}", result.getProvider().getDatabase() != null ? result.getProvider().getDatabase() : "null");
                logger.info("  - Web: {}", result.getProvider().getWeb() != null ? result.getProvider().getWeb() : "null");
            } else {
                logger.info("Provider: null");
            }
            
            if (result.getGame() != null) {
                logger.info("游戏列表: 非null，数量: {}", result.getGame().size());
                // 检查前5个游戏
                int gameIndex = 0;
                for (GameListXml.GameXml gameXml : result.getGame()) {
                    gameIndex++;
                    logger.debug("游戏 #{}: name={}, path={}", gameIndex, 
                        gameXml.getName() != null ? gameXml.getName() : "null",
                        gameXml.getPath() != null ? gameXml.getPath() : "null");
                    if (gameXml.getName() == null || gameXml.getName().isEmpty()) {
                        logger.warn("游戏 #{} 的name字段为null或空", gameIndex);
                        locateNameNullLine(xmlFile, gameIndex);
                    }
                    if (gameIndex >= 5) break; // 只记录前5个
                }
                if (result.getGame().size() > 5) {
                    logger.info("... 还有 {} 个游戏未记录", result.getGame().size() - 5);
                }
            } else {
                logger.info("游戏列表: null");
            }
            
            logger.info("========== XML解析完成 ==========");
            return result;
            
        } catch (JAXBException e) {
            throw e;
        } catch (FileNotFoundException e) {
            logger.error("文件未找到: {}", e.getMessage(), e);
            throw new JAXBException("文件未找到: " + xmlFile.getAbsolutePath(), e);
        } catch (IOException e) {
            logger.error("IO异常: {}", e.getMessage(), e);
            throw new JAXBException("IO异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 跳过文件开头的BOM标记
     * @param bis 文件输入流（已包装为BufferedInputStream）
     * @param charset 字符集
     * @throws IOException
     */
    private static void skipBOM(BufferedInputStream bis, Charset charset) throws IOException {
        bis.mark(8);
        
        byte[] bomBuffer = new byte[4];
        int bytesRead = bis.read(bomBuffer);
        
        String charsetName = charset.name().toUpperCase();
        
        if (charsetName.startsWith("UTF-8") && bytesRead >= 3 && 
            bomBuffer[0] == (byte) 0xEF && bomBuffer[1] == (byte) 0xBB && bomBuffer[2] == (byte) 0xBF) {
            logger.debug("跳过UTF-8 BOM");
            return;
        } else if ((charsetName.startsWith("UTF-16BE") || charsetName.startsWith("UTF-16")) && 
                   bytesRead >= 2 && bomBuffer[0] == (byte) 0xFE && bomBuffer[1] == (byte) 0xFF) {
            logger.debug("跳过UTF-16BE BOM");
            return;
        } else if ((charsetName.startsWith("UTF-16LE")) && 
                   bytesRead >= 2 && bomBuffer[0] == (byte) 0xFF && bomBuffer[1] == (byte) 0xFE) {
            logger.debug("跳过UTF-16LE BOM");
            return;
        } else if (charsetName.startsWith("UTF-32BE") && bytesRead >= 4 &&
                   bomBuffer[0] == (byte) 0x00 && bomBuffer[1] == (byte) 0x00 && 
                   bomBuffer[2] == (byte) 0xFE && bomBuffer[3] == (byte) 0xFF) {
            logger.debug("跳过UTF-32BE BOM");
            return;
        } else if (charsetName.startsWith("UTF-32LE") && bytesRead >= 4 &&
                   bomBuffer[0] == (byte) 0xFF && bomBuffer[1] == (byte) 0xFE && 
                   bomBuffer[2] == (byte) 0x00 && bomBuffer[3] == (byte) 0x00) {
            logger.debug("跳过UTF-32LE BOM");
            return;
        }
        
        bis.reset();
    }
    
    public static GameListXml parseGameList(File xmlFile) throws JAXBException {
        long startTime = System.currentTimeMillis();
        logger.info("========== 开始解析XML文件 ==========");
        logger.info("文件路径: {}", xmlFile.getAbsolutePath());
        logger.info("文件存在: {}", xmlFile.exists());
        logger.info("文件可读: {}", xmlFile.canRead());
        logger.info("文件大小: {} bytes", xmlFile.length());
        logger.info("文件权限: 可读={}, 可写={}, 可执行={}", xmlFile.canRead(), xmlFile.canWrite(), xmlFile.canExecute());
        
        // 1. 首先检测文件编码
        Charset detectedCharset = detectFileEncoding(xmlFile);
        logger.info("检测到的文件编码: {}", detectedCharset.name());
        
        // 2. 构建尝试编码列表：检测到的编码 + 备选编码
        List<Charset> charsetsToTry = Arrays.asList(
            detectedCharset,
            StandardCharsets.UTF_8,
            StandardCharsets.UTF_16LE,
            StandardCharsets.UTF_16BE,
            Charset.forName("UTF-32LE"),
            Charset.forName("UTF-32BE"),
            Charset.forName("GBK"),
            Charset.forName("GB2312")
        );
        
        // 3. 尝试使用多种编码解析
        GameListXml result = null;
        JAXBException lastException = null;
        
        for (int i = 0; i < charsetsToTry.size(); i++) {
            Charset charset = charsetsToTry.get(i);
            try {
                logger.info("-------- 尝试第 {} 种编码: {} --------", i + 1, charset.name());
                result = parseWithCharset(xmlFile, charset);
                logger.info("使用编码 {} 解析成功！", charset.name());
                break; // 解析成功，退出循环
            } catch (JAXBException e) {
                lastException = e;
                logger.warn("使用编码 {} 解析失败，继续尝试下一种...", charset.name());
            }
        }
        
        if (result != null) {
            long parseTime = System.currentTimeMillis() - startTime;
            logger.info("========== XML解析完成 ==========");
            logger.info("总耗时: {} ms", parseTime);
            return result;
        } else {
            // 所有编码都尝试失败，抛出最后一个异常
            logger.error("========== 所有编码解析均失败 ==========");
            if (lastException != null) {
                // 打印完整堆栈
                logger.error("-------- 最后一次异常堆栈 --------");
                try (java.io.StringWriter sw = new java.io.StringWriter();
                     java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
                    lastException.printStackTrace(pw);
                    logger.error("堆栈信息:\n{}", sw.toString());
                } catch (java.io.IOException ex) {
                    logger.error("记录异常堆栈失败: {}", ex.getMessage());
                }
                throw lastException;
            } else {
                throw new JAXBException("无法解析XML文件：所有编码尝试均失败");
            }
        }
    }
    
    /**
     * 定位XML文件中错误行的内容
     * @param xmlFile XML文件
     * @param lineNumber 错误行号
     */
    private static void locateErrorLine(File xmlFile, int lineNumber) {
        if (lineNumber <= 0) {
            logger.warn("无效的行号: {}", lineNumber);
            return;
        }
        
        try {
            logger.info("-------- 定位错误行内容 --------");
            logger.info("错误行号: {}", lineNumber);
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            new java.io.FileInputStream(xmlFile), 
                            java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                int currentLine = 0;
                
                // 读取到错误行附近
                while ((line = br.readLine()) != null) {
                    currentLine++;
                    
                    // 显示错误行及其前后各2行
                    if (currentLine >= lineNumber - 2 && currentLine <= lineNumber + 2) {
                        String marker = currentLine == lineNumber ? " >> " : "    ";
                        logger.info("{}{}: {}", marker, currentLine, line);
                    }
                    
                    if (currentLine > lineNumber + 2) {
                        break;
                    }
                }
                
                logger.info("文件总行数: {}", currentLine);
            }
        } catch (Exception e) {
            logger.error("定位错误行失败: {}", e.getMessage(), e);
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
