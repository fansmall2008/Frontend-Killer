import re

# 读取文件
with open('src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 找到要插入的位置
insert_code = '''                        }

                        // 从roms数组中找到与当前CRC匹配的ROM（处理多区域/多版本ROM）
                        if (gameData.has("roms") && gameData.get("roms").isArray()) {
                            JsonNode romsArray = gameData.get("roms");
                            String currentCrc = fileInfo.getCrc32();
                            if (currentCrc != null && !currentCrc.isEmpty()) {
                                String upperCrc = currentCrc.toUpperCase();
                                for (JsonNode romNode : romsArray) {
                                    if (romNode.has("romcrc")) {
                                        String romCrc = romNode.get("romcrc").asText().toUpperCase();
                                        if (upperCrc.equals(romCrc)) {
                                            // 找到匹配的ROM，记录其详细信息
                                            if (romNode.has("romfilename")) {
                                                result.put("romFileName", romNode.get("romfilename").asText());
                                            }
                                            if (romNode.has("romsize")) {
                                                result.put("romSize", romNode.get("romsize").asText());
                                            }
                                            // 记录ROM的区域信息
                                            if (romNode.has("regions")) {
                                                JsonNode regionsNode = romNode.get("regions");
                                                if (regionsNode.has("regions_shortname")) {
                                                    JsonNode shortNames = regionsNode.get("regions_shortname");
                                                    if (shortNames.isArray() && shortNames.size() > 0) {
                                                        StringBuilder regionsStr = new StringBuilder();
                                                        for (JsonNode region : shortNames) {
                                                            if (regionsStr.length() > 0) regionsStr.append(", ");
                                                            regionsStr.append(region.asText());
                                                        }
                                                        result.put("romRegions", regionsStr.toString());
                                                    }
                                                }
                                            }
                                            logger.debug("找到匹配的ROM: {}, CRC: {}", result.get("romFileName"), romCrc);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        // 媒体信息'''

# 替换
content = content.replace('                        }\n                        \n                        // 媒体信息', insert_code)

# 写回文件
with open('src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")