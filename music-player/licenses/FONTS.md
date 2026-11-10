# 打包进澜音的字体

这几款字体都按 SIL Open Font License 1.1 授权，打包在 `app/src/main/res/font`。除龙藏体按常用字裁剪过（见下表），其余都原样打包，没有修改。许可证全文见 `fusion-pixel-LICENSE.txt` 里的 OFL 正文，也可以在 <https://openfontlicense.org> 查看。

| 文件 | 字体 | 版权 | 用在 |
|---|---|---|---|
| `doto_bold.ttf`、`doto_black.ttf` | Doto（圆点、Bold / Black） | Copyright 2024 The Doto Project Authors (https://github.com/oliverlalan/Doto) | 「点」的数字和英文标签 |
| `space_mono_regular.ttf`、`space_mono_bold.ttf` | Space Mono | Copyright 2016 The Space Mono Project Authors (https://github.com/googlefonts/spacemono) | 「机」「酸」的标签和计数 |
| `archivo_black.ttf` | Archivo Black | Copyright 2017 The Archivo Black Project Authors (https://github.com/Omnibus-Type/ArchivoBlack) | 「酸」的标题 |
| `tilt_neon.ttf` | Tilt Neon（Regular） | Copyright 2019 The Tilt Project Authors (https://github.com/googlefonts/Tilt-Fonts) | 「霓」招牌上的英文和数字，像弯成字的霓虹灯管 |
| `dm_serif_display.ttf`、`dm_serif_display_italic.ttf` | DM Serif Display（Regular / Italic） | Copyright 2014 - 2017 Adobe Systems Incorporated (http://www.adobe.com/), with Reserved Font Name 'Source'. Copyright 2019 Google LLC. | 「暮」明信片上的英文、日期和数字 |
| `long_cang.ttf` | Long Cang（龙藏体，2.001 版，只保留 GB2312 一级字库的 3755 个常用字和英文、标点，别的字回落到系统字体；字体本身没有保留字体名） | Copyright 2018 The LongCang Project Authors (https://github.com/googlefonts/longcang) | 「暮」的铅笔字：批注、编号和白边上手写的歌名 |
| `fusion_pixel.ttf` | Fusion Pixel（缝合像素字体，2022-04-05 版） | Copyright (c) 2022, TakWolf (https://takwolf.com)，Reserved Font Name "Fusion Pixel"；它合并的各字体的版权与许可见 `fusion-pixel-LICENSE.txt` | 「游」的全部文字，中文也是像素字 |

## 图标

澜音的图标（`app/src/main/res/drawable/ic_launcher_foreground.xml`）里的两个字是从下面两款字体取的字形轮廓，转成了矢量路径画进图标，没有打包字体文件。两款字体都按 SIL Open Font License 1.1 授权，用字形做图形是许可允许的。

| 字 | 字体 | 版权 |
|---|---|---|
| 澜 | Zhi Mang Xing（志莽行书） | Copyright 2018 The Zhi Mang Xing Project Authors (https://github.com/googlefonts/zhimangxing) |
| 音 | Noto Serif SC（Black） | Copyright 2017 Adobe Systems Incorporated (http://www.adobe.com/), with Reserved Font Name 'Source'；Noto is a trademark of Google LLC |
