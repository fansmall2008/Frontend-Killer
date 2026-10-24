package com.gamelist.variant.module;

import org.springframework.stereotype.Component;

import com.gamelist.variant.ArtifactKind;

/** GDI（Dreamcast GD-ROM）→ CHD：.gdi 自带轨道索引，指向的原始轨一并交给 chdman */
@Component
public class GdiToChdModule extends ChdmanModule {

    public GdiToChdModule(Chdman chdman) {
        super(chdman, "gdi2chd", ArtifactKind.GDI, 40,
                "chdman createcd：GD-ROM 轨道索引，Flycast 可直接读 CHD");
    }
}
