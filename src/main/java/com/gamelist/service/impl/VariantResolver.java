package com.gamelist.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.GameVariantMapper;
import com.gamelist.model.Game;
import com.gamelist.model.GameVariant;
import com.gamelist.model.ResolvedVariant;
import com.gamelist.service.VariantCapabilityService;
import com.gamelist.service.VariantService;
import com.gamelist.util.ArchiveException;
import com.gamelist.util.ArchiveUtil;

/**
 * 导出时的副本解析：只读数据库 + 文件系统，不做任何格式转换。
 * <p>
 * 解析顺序：请求格式 → 系统偏好 → original；副本缺失/过期/失败时回退主文件。
 */
@Service
public class VariantResolver {

    private static final Logger logger = LoggerFactory.getLogger(VariantResolver.class);

    @Value("${app.variants.path:./data/variants}")
    private String variantRoot;

    @Autowired
    private GameVariantMapper variantMapper;

    @Autowired
    private VariantCapabilityService capabilityService;

    /**
     * 解析主文件路径（absolutePath 优先，回退 platformPath + path）。
     */
    public Path resolveMainPath(Game game) {
        if (game == null) {
            return null;
        }
        String absolutePath = game.getAbsolutePath();
        if (absolutePath != null && !absolutePath.isBlank()) {
            String first = firstLine(absolutePath);
            if (!first.isEmpty()) {
                return Paths.get(first);
            }
        }
        String platformPath = game.getPlatformPath();
        String gamePath = game.getPath();
        if (platformPath != null && !platformPath.isBlank()
                && gamePath != null && !gamePath.isBlank()) {
            return Paths.get(platformPath, stripFragment(firstLine(gamePath)));
        }
        return null;
    }

    /** 决定本次导出实际使用的格式（不支持时回退 original） */
    public String resolveTargetFormat(Integer systemId, String requestedFormat) {
        if (systemId == null) {
            return VariantService.FORMAT_ORIGINAL;
        }
        String target = requestedFormat;
        if (target == null || target.isBlank()) {
            target = capabilityService.getPreferredFormat(systemId);
        }
        target = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (target.isEmpty() || VariantService.FORMAT_ORIGINAL.equals(target)) {
            return VariantService.FORMAT_ORIGINAL;
        }
        if (!capabilityService.isSupported(systemId, target)) {
            logger.debug("系统不支持副本格式，回退主文件: systemId={}, format={}", systemId, target);
            return VariantService.FORMAT_ORIGINAL;
        }
        return target;
    }

    public ResolvedVariant resolve(Game game, Integer systemId, String requestedFormat) {
        String target = resolveTargetFormat(systemId, requestedFormat);
        Map<Long, List<GameVariant>> rows = loadRows(game != null ? List.of(game) : List.of());
        return resolveOne(game, target, rows.getOrDefault(game != null ? game.getId() : null, List.of()));
    }

    public Map<Long, ResolvedVariant> resolveBatch(List<Game> games, Integer systemId,
                                                   String requestedFormat) {
        Map<Long, ResolvedVariant> resolved = new HashMap<>();
        if (games == null || games.isEmpty()) {
            return resolved;
        }
        String target = resolveTargetFormat(systemId, requestedFormat);
        Map<Long, List<GameVariant>> rows = loadRows(games);
        for (Game game : games) {
            if (game == null || game.getId() == null) {
                continue;
            }
            resolved.put(game.getId(),
                    resolveOne(game, target, rows.getOrDefault(game.getId(), List.of())));
        }
        return resolved;
    }

    private ResolvedVariant resolveOne(Game game, String target, List<GameVariant> rows) {
        Path main = resolveMainPath(game);
        String mainExtension = extensionWithDot(main);

        if (VariantService.FORMAT_ORIGINAL.equals(target)) {
            return new ResolvedVariant(main, VariantService.FORMAT_ORIGINAL, mainExtension,
                    false, null, target, null);
        }
        if (game == null || game.getId() == null) {
            return fallback(main, mainExtension, target, "游戏记录缺少 ID");
        }

        GameVariant row = find(rows, target);
        if (row == null) {
            return fallback(main, mainExtension, target, "副本未生成");
        }
        if (!VariantService.STATUS_READY.equalsIgnoreCase(row.getStatus())) {
            return fallback(main, mainExtension, target, "副本状态为 " + row.getStatus());
        }

        Path variantFile = variantFile(row);
        if (variantFile == null || !Files.isRegularFile(variantFile)) {
            return fallback(main, mainExtension, target, "副本文件缺失");
        }

        if (main != null && Files.isRegularFile(main)) {
            try {
                long size = Files.size(main);
                long mtime = Files.getLastModifiedTime(main).toMillis();
                boolean stale = (row.getSourceSize() != null && row.getSourceSize() != size)
                        || (row.getSourceMtime() != null && row.getSourceMtime() != mtime);
                if (stale) {
                    return fallback(main, mainExtension, target, "主文件已变化，副本已过期");
                }
            } catch (IOException e) {
                logger.debug("校验副本源文件失败: {} ({})", main, e.getMessage());
            }
        }

        return new ResolvedVariant(variantFile, target, extensionWithDot(variantFile),
                true, row.getId(), target, null);
    }

    private ResolvedVariant fallback(Path main, String mainExtension, String target, String reason) {
        logger.debug("副本回退主文件: target={}, reason={}", target, reason);
        return new ResolvedVariant(main, VariantService.FORMAT_ORIGINAL, mainExtension,
                false, null, target, reason);
    }

    private GameVariant find(List<GameVariant> rows, String format) {
        for (GameVariant row : rows) {
            if (row.getFormat() != null && row.getFormat().equalsIgnoreCase(format)) {
                return row;
            }
        }
        return null;
    }

    private Map<Long, List<GameVariant>> loadRows(List<Game> games) {
        Map<Long, List<GameVariant>> rowsByGame = new HashMap<>();
        if (games == null || games.isEmpty()) {
            return rowsByGame;
        }
        List<Long> ids = new ArrayList<>();
        for (Game game : games) {
            if (game != null && game.getId() != null) {
                ids.add(game.getId());
            }
        }
        if (ids.isEmpty()) {
            return rowsByGame;
        }
        for (GameVariant row : variantMapper.selectByGameIds(ids)) {
            rowsByGame.computeIfAbsent(row.getGameId(), k -> new ArrayList<>()).add(row);
        }
        return rowsByGame;
    }

    private Path variantFile(GameVariant row) {
        if (row.getRelPath() == null || row.getRelPath().isBlank()) {
            return null;
        }
        try {
            Path root = Paths.get(variantRoot).toAbsolutePath().normalize();
            return ArchiveUtil.resolveInside(root, row.getRelPath());
        } catch (ArchiveException e) {
            logger.warn("副本路径非法: gameId={}, relPath={}", row.getGameId(), row.getRelPath());
            return null;
        }
    }

    private String extensionWithDot(Path path) {
        if (path == null) {
            return "";
        }
        String ext = ArchiveUtil.extensionOf(path.getFileName().toString());
        return ext.isEmpty() ? "" : "." + ext;
    }

    private String firstLine(String value) {
        return value.split("\\r?\\n")[0].trim();
    }

    private String stripFragment(String value) {
        int hash = value.indexOf('#');
        return hash >= 0 ? value.substring(0, hash) : value;
    }
}
