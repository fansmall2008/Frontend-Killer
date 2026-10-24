package com.gamelist.variant.module;

import org.springframework.stereotype.Component;

import com.gamelist.variant.ArtifactKind;

/** NRG（Nero 镜像）→ CHD：单文件自带轨道表，不需要额外索引 */
@Component
public class NrgToChdModule extends ChdmanModule {

    public NrgToChdModule(Chdman chdman) {
        super(chdman, "nrg2chd", ArtifactKind.NRG, 40,
                "chdman createcd：Nero 镜像自带轨道表，单文件直转");
    }
}
