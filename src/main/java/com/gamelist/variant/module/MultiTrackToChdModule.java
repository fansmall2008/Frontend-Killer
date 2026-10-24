package com.gamelist.variant.module;

import org.springframework.stereotype.Component;

import com.gamelist.variant.ArtifactKind;

/**
 * 多轨 BIN/CUE → CHD：PCE CD、Saturn、Dreamcast 这类“数据轨 + 音轨”的主要出口。
 * <p>
 * 块压缩容器装不下音轨，只有 CHD 这种带轨道表的容器能整体保留，这也是本模块存在的原因。
 */
@Component
public class MultiTrackToChdModule extends ChdmanModule {

    public MultiTrackToChdModule(Chdman chdman) {
        super(chdman, "multitrack2chd", ArtifactKind.RAW_2352_MULTI, 40,
                "chdman createcd：多轨/含音轨整盘打包，音轨原样保留");
    }
}
