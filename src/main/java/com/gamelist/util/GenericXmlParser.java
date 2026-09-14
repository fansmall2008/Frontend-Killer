package com.gamelist.util;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gamelist.model.ParsedDataFile;
import com.gamelist.model.TemplateV3;

/**
 * 通用 XML 数据文件解析器。
 * <p>
 * 将 XML 格式的数据文件（如 gamelist.xml）解析为统一的 {@link ParsedDataFile} 结构。
 * <p>
 * 解析规则：
 * <ul>
 *   <li>系统信息：从模板指定的系统标签（如 "provider"）中提取子元素作为 key-value</li>
 *   <li>游戏信息：每个游戏标签（如 "game"）下的子元素作为 key-value 对</li>
 *   <li>标签名统一转为小写，作为 key</li>
 *   <li>标签文本内容作为 value</li>
 *   <li>标签属性（如 id）以 "attr_" 为前缀存入（如 "attr_id"）</li>
 * </ul>
 */
public class GenericXmlParser {

    /**
     * 解析 XML 数据文件。
     *
     * @param file     XML 文件
     * @param template v3 模板（需提供 system.tag 和 game.gameStartMarker 配置）
     * @return 解析结果
     */
    public static ParsedDataFile parse(File file, TemplateV3 template) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // 禁用外部实体，防止 XXE 攻击
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();

        ParsedDataFile result = new ParsedDataFile();

        // 确定系统标签名和游戏标签名
        String systemTag = "provider";  // 默认
        String gameTag = "game";        // 默认

        if (template.getSystem() != null) {
            if (template.getSystem().getSystemTag() != null) {
                systemTag = template.getSystem().getSystemTag();
            }
        }
        if (template.getGame() != null && template.getGame().getGameStartMarker() != null) {
            gameTag = template.getGame().getGameStartMarker();
        }

        Element root = doc.getDocumentElement();
        NodeList rootChildren = root.getChildNodes();

        for (int i = 0; i < rootChildren.getLength(); i++) {
            Node node = rootChildren.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element elem = (Element) node;
            String tagName = elem.getTagName().toLowerCase();

            if (tagName.equals(systemTag.toLowerCase())) {
                // 系统信息标签：提取所有子元素作为系统字段
                extractElementChildren(elem, result.getSystemFields());
            } else if (tagName.equals(gameTag.toLowerCase())) {
                // 游戏标签：提取所有子元素作为游戏字段
                Map<String, String> gameFields = new LinkedHashMap<>();
                // 添加属性（如 id）
                if (elem.hasAttributes()) {
                    for (int a = 0; a < elem.getAttributes().getLength(); a++) {
                        Node attr = elem.getAttributes().item(a);
                        gameFields.put("attr_" + attr.getNodeName().toLowerCase(), attr.getNodeValue());
                    }
                }
                extractElementChildren(elem, gameFields);
                result.addGame(gameFields);
            }
        }

        return result;
    }

    /**
     * 提取元素的所有子元素为 key-value 对。
     * 只取直接子元素，不递归（XML 数据文件通常是两层结构）。
     */
    private static void extractElementChildren(Element parent, Map<String, String> target) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;

            Element childElem = (Element) child;
            String key = childElem.getTagName().toLowerCase();
            String value = childElem.getTextContent();
            if (value != null) {
                value = value.trim();
            }
            target.put(key, value);
        }
    }

    /**
     * 简化版：直接指定游戏标签名解析。
     */
    public static ParsedDataFile parse(File file, String gameTag) throws Exception {
        TemplateV3 mockTemplate = new TemplateV3();

        TemplateV3.SystemMapping sysMapping = new TemplateV3.SystemMapping();
        sysMapping.setGameStartMarker("provider");
        mockTemplate.setSystem(sysMapping);

        TemplateV3.GameMapping gameMapping = new TemplateV3.GameMapping();
        gameMapping.setGameStartMarker(gameTag);
        mockTemplate.setGame(gameMapping);

        return parse(file, mockTemplate);
    }
}
