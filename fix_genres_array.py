import re

# 读取文件
with open('src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 旧的 genres 解析代码（合并成字符串）
old_genres = '''                        // 游戏类型 - genres 是数组，每个元素有 noms 数组
                        if (gameData.has("genres")) {
                            JsonNode genres = gameData.get("genres");
                            StringBuilder genreStr = new StringBuilder();
                            if (genres.isArray()) {
                                for (JsonNode genre : genres) {
                                    if (genre.has("noms") && genre.get("noms").isArray()) {
                                        JsonNode nomsArray = genre.get("noms");
                                        String genreName = null;
                                        for (JsonNode nomItem : nomsArray) {
                                            if (nomItem.has("langue") && nomItem.has("text")) {
                                                String langue = nomItem.get("langue").asText();
                                                if ("en".equals(langue)) {
                                                    genreName = nomItem.get("text").asText();
                                                    break;
                                                } else if ("zh".equals(langue) && genreName == null) {
                                                    genreName = nomItem.get("text").asText();
                                                } else if (genreName == null) {
                                                    genreName = nomItem.get("text").asText();
                                                }
                                            }
                                        }
                                        if (genreName != null) {
                                            if (genreStr.length() > 0) genreStr.append(", ");
                                            genreStr.append(genreName);
                                        }
                                    }
                                }
                            }
                            if (genreStr.length() > 0) {
                                result.put("genre", genreStr.toString());
                            }
                        }'''

# 新的 genres 解析代码（返回数组）
new_genres = '''                        // 游戏类型 - genres 是数组，每个元素有 noms 数组
                        if (gameData.has("genres")) {
                            JsonNode genres = gameData.get("genres");
                            java.util.List<String> genreList = new java.util.ArrayList<>();
                            if (genres.isArray()) {
                                for (JsonNode genre : genres) {
                                    if (genre.has("noms") && genre.get("noms").isArray()) {
                                        JsonNode nomsArray = genre.get("noms");
                                        String genreName = null;
                                        for (JsonNode nomItem : nomsArray) {
                                            if (nomItem.has("langue") && nomItem.has("text")) {
                                                String langue = nomItem.get("langue").asText();
                                                if ("en".equals(langue)) {
                                                    genreName = nomItem.get("text").asText();
                                                    break;
                                                } else if ("zh".equals(langue) && genreName == null) {
                                                    genreName = nomItem.get("text").asText();
                                                } else if (genreName == null) {
                                                    genreName = nomItem.get("text").asText();
                                                }
                                            }
                                        }
                                        if (genreName != null && !genreList.contains(genreName)) {
                                            genreList.add(genreName);
                                        }
                                    }
                                }
                            }
                            // 同时设置数组格式和逗号分隔格式，兼容前端
                            if (!genreList.isEmpty()) {
                                result.put("genres", genreList);
                                result.put("genre", String.join(", ", genreList));
                            }
                        }'''

# 替换
content = content.replace(old_genres, new_genres)

# 写回文件
with open('src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")