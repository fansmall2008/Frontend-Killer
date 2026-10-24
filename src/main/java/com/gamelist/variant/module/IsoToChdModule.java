package com.gamelist.variant.module;

import org.springframework.stereotype.Component;

import com.gamelist.variant.ArtifactKind;

/**
 * ISO（2048 扇区）→ CHD。
 * <p>
 * 单文件自带扇区布局，直接交给 chdman；同步头与 EDC/ECC 由 chdman 合成，
 * 因此这一条不是字节级往返无损。
 */
@Component
public class IsoToChdModule extends ChdmanModule {

    public IsoToChdModule(Chdman chdman) {
        super(chdman, "iso2chd", ArtifactKind.ISO_2048, 40,
                "chdman createcd：2048 扇区输入，同步头与 EDC/ECC 由 chdman 合成");
    }
}
