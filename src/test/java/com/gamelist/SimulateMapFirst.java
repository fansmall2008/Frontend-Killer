package com.gamelist;

import java.util.*;
import com.gamelist.util.TemplateExpressionEngine;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;

/**
 * 模拟测试 mapFirst() 对实际 genre 值的处理结果
 */
public class SimulateMapFirst {
    
    public static void main(String[] args) {
        // 从 API 获取的实际 genre 值（部分样本）
        String[] testGenres = {
            "Action / Adventure, Action",
            "Beat'em Up, Fighting",
            "Racing, Driving",
            "Racing, Driving / Racing, Racing, Driving",
            "Platform, Adventure",
            "Shoot'em Up / Vertical, Shoot'em Up",
            "Shooter / Run and Gun, Shooter",
            "Sports / Multisports, Sports",
            "Puzzle, Various",
            "Fighting, Beat'em Up",
            "Role Playing Game",
            "Strategy",
            "Music and Dancing"
        };
        
        System.out.println("=== mapFirst(genre) 模拟测试结果 ===\n");
        
        for (String genre : testGenres) {
            Game game = new Game();
            game.setGenre(genre);
            
            Platform platform = new Platform();
            platform.setName("Test Platform");
            
            Map<String, String> vars = new HashMap<>();
            
            String expr = "mapFirst(genre)";
            String result = TemplateExpressionEngine.evaluate(expr, 
                new TemplateExpressionEngine.Context(game, platform, vars));
            
            System.out.printf("genre: %-50s → mapFirst: %s%n", 
                "\"" + genre + "\"", 
                result != null ? "\"" + result + "\"" : "null");
        }
        
        System.out.println("\n=== 预期 vs 实际 ===");
        System.out.println("期望：所有结果都是单个类别缩写（如 ACT, FTG, RAC）");
        System.out.println("若出现组合（如 ACT-AVG），说明 mapFirst 逻辑有误");
    }
}
