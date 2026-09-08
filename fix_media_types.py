import re

# 读取文件
with open('src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 旧的媒体类型映射代码
old_media = '''            // 其他
            case "minicon" -> "minicon";
            case "icon" -> "icon";
            case "steamgrid", "steam-grid" -> "steamgrid";
            case "physical", "physical-media" -> "physical";
            case "manual", "manuel" -> "manual";
            case "cartridge", "map" -> "cartridge";
            case "trailer" -> "trailer";
            
            default -> null;'''

# 新的媒体类型映射代码（添加 photo, illustration, intro）
new_media = '''            // 其他
            case "minicon" -> "minicon";
            case "icon" -> "icon";
            case "steamgrid", "steam-grid" -> "steamgrid";
            case "physical", "physical-media" -> "physical";
            case "manual", "manuel" -> "manual";
            case "cartridge", "map" -> "cartridge";
            case "trailer" -> "trailer";
            
            // 新增支持的媒体类型
            case "photo" -> "photo";
            case "illustration" -> "illustration";
            case "intro" -> "intro";
            
            default -> null;'''

# 替换
content = content.replace(old_media, new_media)

# 写回文件
with open('src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")