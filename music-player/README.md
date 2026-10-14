# 澜音

接入网易云音乐的第三方 Android 播放器：竖屏是常规音乐 App，横过来就是 QQ 音乐风格的横屏播放页。

> 当前进度：**M0.8 横屏充实，歌词变小**。界面、主题、动效和横屏播放页都可以体验，播放的是假数据，还不能登录或出声。完整路线见 [docs/PLAN.md](docs/PLAN.md)。

![旗舰主题「墨」](docs/screenshots/ink.jpg)

![「墨」横屏，深色与浅色](docs/screenshots/ink-landscape.jpg)

![「暮」](docs/screenshots/dusk.jpg)

![五个主题的首页](docs/screenshots/themes-home.jpg)

![横屏播放页：封面外圈是随音乐跳动的频谱，右栏是歌名、歌词和下一首；第二张是闲置后的桌面时钟模式](docs/screenshots/themes-landscape.jpg)

![迷你播放条展开成播放页的过程，以及设置页](docs/screenshots/motion-and-more.jpg)

## 主题

每个主题是一整套视觉语言，不只是换颜色：

| 主题 | 风格 | 卡片 | 进度条 | 播放按钮 | 背景 |
|---|---|---|---|---|---|
| **澜** | 深海、毛玻璃 | 磨砂玻璃、大圆角 | 播放时会起伏的波浪线 | 带色彩光晕的圆 | 漂移的光斑和底部浪涌 |
| **墨**（默认，旗舰） | 黑白、留白、衬线字，唯一的颜色是朱红印章 | 不用卡片，只用发丝线分隔；底栏贴边 | 发丝线 | 线框圆 | 围着唱片的一笔圆相 |
| **樱** | 粉紫、软糯 | 柔和投影、超大圆角 | 渐变胶囊 | 渐变软方块 | 飘落的花瓣 |
| **霓** | 赛博霓虹，只有深色 | 切角、霓虹描边 | LED 灯段 | 发光圆环 | 透视网格和扫描线 |
| **暮** | 七十年代暖色 | 粗描边；只有主角卡片带硬投影，按下会陷进阴影里 | 斜纹磁带条，时间是磁带计数器 | 复古方钮 | 条纹落日和胶片颗粒；封面有彩虹、落日、波浪、雏菊四种 |

所有主题共用一套规则：四级字号、页边距、一屏一个强调色、同一族自绘线性图标（线宽随主题变化）。

主题之外，还可以单独选**播放页样式**：旋转黑胶（默认）、方形封面、沉浸歌词、复古卡带。

### 动效
- 点迷你播放条，它会展开成播放页（容器变形），唱片从缩略图长成大唱片；下拉或系统返回手势会让播放页跟手缩小。
- 唱片有惯性：播放时加速转起，暂停时慢慢停下；唱臂带回弹落下。
- 歌词逐行像波浪一样跟上，当前行略微放大并卡拉 OK 式扫色；字号可以在设置里选小 / 中 / 大（默认 18sp：中文字比同字号的英文更满，主流 App 的歌词也大多在 16–18sp）；可以拖动歌词浏览，松手后自动回到当前行，点某一行跳到那里。
- 横屏是「舞台加一条控制栏」：左边是封面，外圈一圈频谱随音乐跳动，每个主题画法不同（墨是刻度线，澜是双层波浪，樱是小圆点，霓是发光光柱，暮是 VU 表灯块；现在是模拟数据，M2 接上真实音频）；右栏从上到下是歌名、歌手、音质标签、歌词和「下一首」，当前歌词和封面中心对齐；顶栏显示正在播放的歌单和第几首。进度和播放控制合成底部一条栏，样式和迷你播放条一致。横屏时隐藏系统栏，5 秒不碰就收起控件，舞台放大，只留一道进度细线，右上角换成桌面时钟；右上角按钮可以直接旋转屏幕，也可以在设置里打开「进入播放页时自动横屏」。

## 编译

需要 JDK 17+。

```bash
./scripts/setup-android-sdk.sh      # 安装 Android SDK 到 ~/android-sdk，并写 local.properties
./gradlew assembleDebug             # → app/build/outputs/apk/debug/app-debug.apk（未压缩，约 60 MB）
./gradlew assemblePreview           # → app/build/outputs/apk/preview/app-preview.apk（R8 压缩，约 3 MB，debug 签名，可直接安装）
./gradlew testDebugUnitTest         # 单元测试
./gradlew recordRoborazziDebug      # 在 JVM 上渲染五个主题的界面截图 → app/build/outputs/roborazzi/
```

如果 Maven Central 限流（HTTP 429），可以改走 Google 的镜像：

```bash
MAVEN_MIRROR=google ./scripts/setup-android-sdk.sh
export ROBOLECTRIC_REPO_URL=https://maven-central.storage-download.googleapis.com/maven2
```

## 说明

仅供个人使用。用的是网易云的非官方接口，不包含任何绕过会员或付费的功能，VIP 歌曲需要登录的账号本身有对应权限。
