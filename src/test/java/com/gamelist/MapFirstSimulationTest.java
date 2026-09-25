package com.gamelist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.util.TemplateExpressionEngine;

/**
 * 模拟测试 mapFirst() 对实际 genre 值的处理结果
 */
public class MapFirstSimulationTest {

    @Test
    void simulateMapFirstWithRealGenres() {
        // 从 API 获取的实际 genre 值（部分样本）
        String[][] testCases = {
            // {genre, expectedFirstMapping}
            {"Action / Adventure, Action", "ACT"},
            {"Beat'em Up, Fighting", "FTG"},
            {"Racing, Driving", "RAC"},
            {"Platform, Adventure", "ACT"},
            {"Shoot'em Up / Vertical, Shoot'em Up", "STG"},
            {"Shooter / Run and Gun, Shooter", "STG"},
            {"Sports / Multisports, Sports", "SPG"},
            {"Puzzle, Various", "PUZ"},
            {"Fighting, Beat'em Up", "FTG"},
            {"Role Playing Game", "RPG"},
            {"Strategy", "SLG"},
            {"Music and Dancing", "MSC"},
        };

        System.out.println("\n=== mapFirst(genre) 模拟测试结果 ===\n");

        for (String[] tc : testCases) {
            String genre = tc[0];
            String expected = tc[1];

            Game game = new Game();
            game.setGenre(genre);

            Platform platform = new Platform();
            platform.setName("Test Platform");

            Map<String, String> vars = new HashMap<>();

            String expr = "mapFirst(genre)";
            String result = TemplateExpressionEngine.evaluate(expr,
                new TemplateExpressionEngine.Context(game, platform, vars));

            boolean pass = expected.equals(result);
            System.out.printf("%s genre: %-45s → mapFirst: %-10s (expected: %s)%n",
                pass ? "✓" : "✗",
                "\"" + genre + "\"",
                result != null ? "\"" + result + "\"" : "null",
                "\"" + expected + "\"");

            if (!pass) {
                System.out.printf("   不匹配！实际返回: %s%n", result);
            }
        }
    }
}
