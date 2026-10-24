package com.gamelist.variant.module;

import org.springframework.stereotype.Component;

import com.gamelist.variant.ArtifactKind;

/**
 * BIN（2352 扇区，单数据轨）→ CHD。
 * <p>
 * 必须配套 cue：chdman 靠轨道表定位扇区边界，裸 BIN 只会得到 0 轨空壳。
 */
@Component
public class BinToChdModule extends ChdmanModule {

    public BinToChdModule(Chdman chdman) {
        super(chdman, "bin2chd", ArtifactKind.RAW_2352, 40,
                "chdman createcd：需要配套 cue 提供轨道表，裸 BIN 会被拒绝");
    }
}
