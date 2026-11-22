package com.gamelist.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.gamelist.model.TemplateV3.MediaResizeConfig;

/**
 * 媒体图片导出时的通用缩放器 — 纯 JDK {@link ImageIO}，不引外部依赖。
 * <p>
 * 对任意来源尺寸/格式都成立：等比（fit）/补边（pad）/拉伸（stretch）缩放到目标框，
 * 输出宽高向下取到 {@code divisor} 的倍数（USB Loader GX 的 ÷4 铁律即 divisor=4），
 * 再按 {@code format} 重编码。源不可解码（非图片/损坏）时返回 {@code false}，调用方跳过该图。
 */
public final class ImageResizer {

    private ImageResizer() {}

    /**
     * 按 {@code cfg} 处理源图并写到 {@code target}（含父目录创建）。
     *
     * @return {@code true}=成功产出；{@code false}=源不可读/无法解码（调用方应跳过并告警）
     * @throws IOException 写出失败（磁盘/权限等）
     */
    public static boolean process(Path source, Path target, MediaResizeConfig cfg) throws IOException {
        BufferedImage src = ImageIO.read(source.toFile());
        if (src == null) {
            return false; // 非图片或已损坏，交回调用方跳过
        }
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        int boxW = cfg.getWidth() > 0 ? cfg.getWidth() : srcW;
        int boxH = cfg.getHeight() > 0 ? cfg.getHeight() : srcH;
        String mode = cfg.getMode() == null ? "fit" : cfg.getMode().trim().toLowerCase();

        // 等比内缩到目标框的系数；只缩不放时封顶 1
        double scale = Math.min((double) boxW / srcW, (double) boxH / srcH);
        if (!cfg.isUpscale() && scale > 1) {
            scale = 1;
        }

        int contentW;
        int contentH;
        int canvasW;
        int canvasH;
        if ("stretch".equals(mode)) {
            canvasW = snap(boxW, cfg);
            canvasH = snap(boxH, cfg);
            contentW = canvasW;
            contentH = canvasH;
        } else if ("pad".equals(mode)) {
            contentW = snap((int) Math.round(srcW * scale), cfg);
            contentH = snap((int) Math.round(srcH * scale), cfg);
            canvasW = Math.max(snap(boxW, cfg), contentW);
            canvasH = Math.max(snap(boxH, cfg), contentH);
        } else { // fit（默认）：画面即画布，尺寸随等比结果
            canvasW = snap((int) Math.round(srcW * scale), cfg);
            canvasH = snap((int) Math.round(srcH * scale), cfg);
            contentW = canvasW;
            contentH = canvasH;
        }
        if (canvasW <= 0) canvasW = 1;
        if (canvasH <= 0) canvasH = 1;

        String fmt = resolveFormat(cfg, source);
        boolean jpeg = "jpg".equals(fmt) || "jpeg".equals(fmt);
        boolean hasAlpha = !jpeg;
        int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(canvasW, canvasH, imageType);
        Graphics2D g = out.createGraphics();
        try {
            if (!hasAlpha) {
                g.setColor(Color.WHITE); // jpg 不支持透明，铺白底
                g.fillRect(0, 0, canvasW, canvasH);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = (canvasW - contentW) / 2;
            int y = (canvasH - contentH) / 2;
            g.drawImage(src, x, y, contentW, contentH, null);
        } finally {
            g.dispose();
        }

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(out, jpeg ? "jpg" : fmt, target.toFile())) {
            throw new IOException("无可用图片写出器: " + fmt);
        }
        return true;
    }

    /** 宽高向下取到 divisor 的倍数（divisor<=1 原样返回）；结果不足一个倍数时抬到一个倍数 */
    private static int snap(int value, MediaResizeConfig cfg) {
        int d = cfg.getDivisor();
        if (d > 1) {
            value = (value / d) * d;
            if (value <= 0) {
                value = d;
            }
        }
        return value;
    }

    /** 目标格式：cfg.format 优先，否则按源扩展名保持（jpeg 归一为 jpg，取不到默认 png） */
    private static String resolveFormat(MediaResizeConfig cfg, Path source) {
        String f = cfg.getFormat();
        if (f != null && !f.isBlank()) {
            String t = f.trim().toLowerCase();
            return "jpeg".equals(t) ? "jpg" : t;
        }
        String name = source.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1) : "png";
        return "jpeg".equals(ext) ? "jpg" : ext;
    }
}
