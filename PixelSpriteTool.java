import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * PixelSpriteTool — 把 AI 生成的"伪像素"概念图转成正宗像素精灵。
 *
 * 处理管线（对每张图）：
 *   1. 边缘泛洪抠图：从四边泛洪填充识别背景色（自动适配绿底/洋红底），
 *      只清除与边界连通的背景像素，保留角色内部的同色像素。
 *   2. 裁剪到角色包围盒。
 *   3. 降采样到目标网格（保比例，box 滤波按 alpha 加权）。
 *   4. alpha 二值化（阈值 128）→ 干净硬边，消除半透明毛刺。
 *   5. Median-Cut 限色量化到 <= maxColors 色。
 *   6. 输出透明精灵 PNG + 整数倍放大(棋盘底)预览 PNG。
 *
 * 用法：
 *   java PixelSpriteTool <inDir> <outDir> <grid> <maxColors> <tolerance> <upscale> name1 name2 ...
 *   输入为 <inDir>/<name>.png，输出 <outDir>/<name>.png 与 <outDir>/<name>_preview.png
 */
public class PixelSpriteTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.out.println("usage: java PixelSpriteTool <inDir> <outDir> <grid> <maxColors> <tolerance> <upscale> name1 name2 ...");
            return;
        }
        String inDir = args[0];
        String outDir = args[1];
        int grid = Integer.parseInt(args[2]);
        int maxColors = Integer.parseInt(args[3]);
        int tolerance = Integer.parseInt(args[4]);
        int upscale = Integer.parseInt(args[5]);
        new File(outDir).mkdirs();

        for (int i = 6; i < args.length; i++) {
            String name = args[i];
            File in = new File(inDir, name + ".png");
            if (!in.exists()) {
                System.out.println("[skip] missing " + in);
                continue;
            }
            process(in, new File(outDir, name + ".png"), new File(outDir, name + "_preview.png"),
                    grid, maxColors, tolerance, upscale);
        }
    }

    static void process(File in, File outSprite, File outPreview,
                        int grid, int maxColors, int tolerance, int upscale) throws Exception {
        BufferedImage src = toARGB(ImageIO.read(in));
        int w = src.getWidth(), h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        // 1) 背景色 = 四角 8x8 采样均值
        int bg = avgCorners(px, w, h);
        int bgR = (bg >> 16) & 0xFF, bgG = (bg >> 8) & 0xFF, bgB = bg & 0xFF;

        // 2) 从边界泛洪填充，标记背景 -> alpha 0
        boolean[] bgMask = new boolean[w * h];
        boolean[] queued = new boolean[w * h];
        Deque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            if (!queued[x]) { q.add(x); queued[x] = true; }
            int b = (h - 1) * w + x;
            if (!queued[b]) { q.add(b); queued[b] = true; }
        }
        for (int y = 0; y < h; y++) {
            int l = y * w;
            if (!queued[l]) { q.add(l); queued[l] = true; }
            int r = y * w + (w - 1);
            if (!queued[r]) { q.add(r); queued[r] = true; }
        }
        // BFS
        while (!q.isEmpty()) {
            int idx = q.poll();
            int x = idx % w, y = idx / w;
            int c = px[idx];
            int a = (c >>> 24) & 0xFF;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            if (a < 8 || closeTo(r, g, b, bgR, bgG, bgB, tolerance)) {
                bgMask[idx] = true;
                if (x > 0 && !queued[idx - 1]) { q.add(idx - 1); queued[idx - 1] = true; }
                if (x < w - 1 && !queued[idx + 1]) { q.add(idx + 1); queued[idx + 1] = true; }
                if (y > 0 && !queued[idx - w]) { q.add(idx - w); queued[idx - w] = true; }
                if (y < h - 1 && !queued[idx + w]) { q.add(idx + w); queued[idx + w] = true; }
            }
        }
        for (int i = 0; i < px.length; i++) if (bgMask[i]) px[i] = 0; // transparent

        // 3) 包围盒
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (((px[y * w + x] >>> 24) & 0xFF) > 8) {
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) { System.out.println("[skip] empty after keying: " + in); return; }
        int cw = maxX - minX + 1, ch = maxY - minY + 1;

        // 4) 降采样到 grid（保比例，box 滤波按 alpha 加权）
        double scale = (double) grid / Math.max(cw, ch);
        int tw = Math.max(1, (int) Math.round(cw * scale));
        int th = Math.max(1, (int) Math.round(ch * scale));
        int[] small = downscale(px, w, minX, minY, cw, ch, tw, th);

        // 5) alpha 二值化
        for (int i = 0; i < small.length; i++) {
            int a = (small[i] >>> 24) & 0xFF;
            small[i] = (a >= 128) ? (small[i] | 0xFF000000) : 0;
        }

        // 6) Median-Cut 量化
        List<Integer> opaque = new ArrayList<>();
        for (int c : small) if (((c >>> 24) & 0xFF) > 0) opaque.add(c & 0x00FFFFFF);
        int[] palette = medianCut(opaque, maxColors);
        for (int i = 0; i < small.length; i++) {
            if (((small[i] >>> 24) & 0xFF) > 0) {
                int rgb = nearest(small[i] & 0x00FFFFFF, palette);
                small[i] = 0xFF000000 | rgb;
            }
        }

        // 输出精灵
        BufferedImage sprite = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        sprite.setRGB(0, 0, tw, th, small, 0, tw);
        ImageIO.write(sprite, "png", outSprite);

        // 预览：整数放大 + 棋盘底
        BufferedImage preview = nearestUpscale(sprite, upscale);
        BufferedImage matted = checkerboard(preview, Math.max(1, upscale));
        ImageIO.write(matted, "png", outPreview);

        System.out.printf("[ok] %-8s %dx%d -> %dx%d, colors=%d%n",
                in.getName(), w, h, tw, th, palette.length);
    }

    // ---- helpers ----

    static BufferedImage toARGB(BufferedImage img) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(img, 0, 0, null);
        return out;
    }

    static int avgCorners(int[] px, int w, int h) {
        int n = 0, r = 0, g = 0, b = 0;
        int[][] corners = {{0, 0}, {w - 8, 0}, {0, h - 8}, {w - 8, h - 8}};
        for (int[] c : corners) {
            for (int y = c[1]; y < Math.min(h, c[1] + 8); y++)
                for (int x = c[0]; x < Math.min(w, c[0] + 8); x++) {
                    int p = px[y * w + x];
                    r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
                }
        }
        return ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    static boolean closeTo(int r, int g, int b, int br, int bg, int bb, int tol) {
        int dr = r - br, dg = g - bg, db = b - bb;
        return dr * dr + dg * dg + db * db <= tol * tol;
    }

    static int[] downscale(int[] px, int srcStride, int ox, int oy, int sw, int sh, int tw, int th) {
        int[] out = new int[tw * th];
        double sx = (double) sw / tw, sy = (double) sh / th;
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                int x0 = ox + (int) (tx * sx), x1 = ox + (int) Math.ceil((tx + 1) * sx);
                int y0 = oy + (int) (ty * sy), y1 = oy + (int) Math.ceil((ty + 1) * sy);
                x1 = Math.max(x1, x0 + 1); y1 = Math.max(y1, y0 + 1);
                long sa = 0, sr = 0, sg = 0, sb = 0, wa = 0;
                for (int y = y0; y < y1 && y < oy + sh; y++) {
                    for (int x = x0; x < x1 && x < ox + sw; x++) {
                        int c = px[y * srcStride + x];
                        int a = (c >>> 24) & 0xFF;
                        if (a == 0) { sa += 0; continue; }
                        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                        sr += r * a; sg += g * a; sb += b * a; wa += a; sa += a;
                    }
                }
                int area = (x1 - x0) * (y1 - y0);
                int avgA = (int) (sa / area);
                if (wa == 0) { out[ty * tw + tx] = 0; continue; }
                int r = (int) (sr / wa), g = (int) (sg / wa), b = (int) (sb / wa);
                out[ty * tw + tx] = (avgA << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    static int[] medianCut(List<Integer> pixels, int maxColors) {
        if (pixels.isEmpty()) return new int[]{0};
        List<int[]> boxes = new ArrayList<>(); // each box: {from,to} indices into a working array
        Integer[] arr = pixels.toArray(new Integer[0]);
        boxes.add(new int[]{0, arr.length});
        while (boxes.size() < maxColors) {
            // pick box with largest volume & splittable
            int best = -1; long bestRange = -1; int bestChan = 0;
            int[] min = new int[3], max = new int[3];
            for (int bi = 0; bi < boxes.size(); bi++) {
                int[] bx = boxes.get(bi);
                if (bx[1] - bx[0] < 2) continue;
                computeRange(arr, bx[0], bx[1], min, max);
                int rr = max[0] - min[0], rg = max[1] - min[1], rb = max[2] - min[2];
                int range = Math.max(rr, Math.max(rg, rb));
                int chan = (rr >= rg && rr >= rb) ? 0 : (rg >= rb ? 1 : 2);
                if (range > bestRange) { bestRange = range; best = bi; bestChan = chan; }
            }
            if (best < 0) break;
            int[] bx = boxes.get(best);
            final int chan = bestChan;
            Arrays.sort(arr, bx[0], bx[1], (p1, p2) -> Integer.compare(channel(p1, chan), channel(p2, chan)));
            int mid = (bx[0] + bx[1]) / 2;
            boxes.set(best, new int[]{bx[0], mid});
            boxes.add(new int[]{mid, bx[1]});
        }
        int[] pal = new int[boxes.size()];
        for (int bi = 0; bi < boxes.size(); bi++) {
            int[] bx = boxes.get(bi);
            long r = 0, g = 0, b = 0; int n = 0;
            for (int i = bx[0]; i < bx[1]; i++) {
                int c = arr[i];
                r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n++;
            }
            if (n == 0) n = 1;
            pal[bi] = ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        }
        return pal;
    }

    static void computeRange(Integer[] arr, int from, int to, int[] min, int[] max) {
        min[0] = min[1] = min[2] = 255; max[0] = max[1] = max[2] = 0;
        for (int i = from; i < to; i++) {
            int c = arr[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            min[0] = Math.min(min[0], r); max[0] = Math.max(max[0], r);
            min[1] = Math.min(min[1], g); max[1] = Math.max(max[1], g);
            min[2] = Math.min(min[2], b); max[2] = Math.max(max[2], b);
        }
    }

    static int channel(int rgb, int chan) {
        return chan == 0 ? ((rgb >> 16) & 0xFF) : chan == 1 ? ((rgb >> 8) & 0xFF) : (rgb & 0xFF);
    }

    static int nearest(int rgb, int[] palette) {
        int br = (rgb >> 16) & 0xFF, bg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
        int best = palette[0], bestD = Integer.MAX_VALUE;
        for (int p : palette) {
            int dr = br - ((p >> 16) & 0xFF), dg = bg - ((p >> 8) & 0xFF), db = bb - (p & 0xFF);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    static BufferedImage nearestUpscale(BufferedImage src, int f) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w * f, h * f, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = src.getRGB(x, y);
            for (int dy = 0; dy < f; dy++) for (int dx = 0; dx < f; dx++)
                out.setRGB(x * f + dx, y * f + dy, c);
        }
        return out;
    }

    static BufferedImage checkerboard(BufferedImage src, int cell) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean light = (((x / cell) + (y / cell)) & 1) == 0;
            int bgc = light ? 0xDDDDDD : 0xAAAAAA;
            int c = src.getRGB(x, y);
            int a = (c >>> 24) & 0xFF;
            if (a == 0) { out.setRGB(x, y, bgc); continue; }
            int r = (((c >> 16) & 0xFF) * a + ((bgc >> 16) & 0xFF) * (255 - a)) / 255;
            int g = (((c >> 8) & 0xFF) * a + ((bgc >> 8) & 0xFF) * (255 - a)) / 255;
            int b = ((c & 0xFF) * a + (bgc & 0xFF) * (255 - a)) / 255;
            out.setRGB(x, y, (r << 16) | (g << 8) | b);
        }
        return out;
    }
}
