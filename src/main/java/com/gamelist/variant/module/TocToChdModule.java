package com.gamelist.variant.module;

import org.springframework.stereotype.Component;

import com.gamelist.variant.ArtifactKind;

/** TOC（cdrdao 镜像）→ CHD：TOC 声明轨道，DATAFILE 指向的轨道文件一并打包 */
@Component
public class TocToChdModule extends ChdmanModule {

    public TocToChdModule(Chdman chdman) {
        super(chdman, "toc2chd", ArtifactKind.TOC, 40,
                "chdman createcd：cdrdao 镜像，DATAFILE 引用的轨道一并打包");
    }
}
