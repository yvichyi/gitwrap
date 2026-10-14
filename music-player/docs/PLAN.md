# 横屏音乐播放器 · 开发规划（v0，待确认）

一个接入网易云音乐的第三方 Android 播放器。竖屏是常规的音乐 App，横过来进入 QQ 音乐风格的横屏播放页。

## 1. 已确认的需求

| 项目 | 决定 |
|---|---|
| 技术栈 | Kotlin + Jetpack Compose + Media3 (ExoPlayer) |
| 网易云接入 | App 内直连，本地实现 eapi/weapi 加密，不需要服务器 |
| 设备 | 手机，竖屏和横屏都支持 |
| 最低版本 | Android 10 (API 29)，目标 API 35 |
| 代码位置 | `gitwrap/music-player/` |
| 账号 | 用户有黑胶 VIP，按 VIP 音质设计 |

## 2. 参考来源和许可证

- **Mineradio**（[XxHuberrr/Mineradio](https://github.com/XxHuberrr/Mineradio)，GPL-3.0，Electron 桌面端）：它的网易云功能全部来自 `NeteaseCloudMusicApi` 这个 npm 包。从 Mineradio 只借鉴**思路**，不复制代码，以免整个 App 被迫改用 GPL：
  - 音质降级链：`jymaster → hires → lossless → exhigh → standard`
  - 根据 `fee` 和 `freeTrialInfo` 区分各种播放失败原因（需要登录、只有试听、需要 VIP、需要单独购买、没有版权），并给出对应提示
  - 扫码登录后持久化 cookie
- **NeteaseCloudMusicApi 4.32.0**（MIT）：用作接口协议的参考，在 Kotlin 里重新实现：
  - `eapi`：`AES-128-ECB(key="e82ckenh8dichen8", "{path}-36cd479b6b5-{json}-36cd479b6b5-{md5("nobody{path}use{json}md5forencrypt")}")`，结果转成大写 hex，POST 到 `https://interface.music.163.com/eapi/...`
  - `weapi`：两层 AES-CBC 加密，再用 RSA（无填充）加密随机密钥，作为备用
  - Cookie 里要带上 `os / appver / osver / deviceId / channel / _ntes_nuid / WNMCID / NMTID`；登录后再加 `MUSIC_U` 和 `__csrf`
- **测试向量**：在这个容器里用 Node 调用 NeteaseCloudMusicApi 的 `crypto.js` 生成标准结果，再让 Kotlin 实现的输出逐字节对比，保证加密实现正确。

## 3. 扫码登录流程

```
POST /api/login/qrcode/unikey {type:3}             → unikey
生成二维码  https://music.163.com/login?codekey={unikey}
每 2 秒轮询 POST /api/login/qrcode/client/login {key, type:3}
  801 等待扫码 → 802 已扫码待确认（显示对方头像和昵称）→ 803 成功，拿到 Set-Cookie (MUSIC_U)
  800 二维码过期 → 自动刷新
成功后用 /api/w/nuser/account/get 和 /api/vipmall/... 获取昵称和 VIP 状态
```

**难点：App 和网易云装在同一部手机上，没法用这部手机扫自己屏幕上的码。** 打算这样解决：
1. 提供「保存二维码到相册」按钮，然后在网易云 App 里用「扫一扫 → 相册」识别。这是主要方式。
2. 有平板或第二部手机时，直接扫屏幕。
3. 备用：手动粘贴 `MUSIC_U` cookie，放在高级选项里。

Cookie 存在 `EncryptedSharedPreferences` 里，不打日志，也不会离开设备。

## 4. 架构

一个 `app` 模块，按包划分，暂时不拆多模块，等代码变大再说。

```
music-player/
  app/src/main/java/.../
    core/netease/    加密(eapi/weapi)、OkHttp 客户端、Cookie 管理、接口定义
    core/model/      Song / Album / Artist / Playlist / Lyrics 等数据模型
    data/            Repository：账号、曲库、搜索、歌词、播放地址解析
    playback/        MediaSessionService + ExoPlayer、播放队列、音质降级、缓存
    lyrics/          LRC / YRC（逐字）/ 翻译 / 罗马音 解析与对齐
    ui/              Compose 页面、主题、动态取色、横竖屏布局
  app/src/test/      单元测试（加密、歌词解析、降级逻辑）
```

| 用途 | 选型 |
|---|---|
| 依赖注入 | Hilt |
| 网络 | OkHttp + kotlinx.serialization |
| 图片 | Coil 3 |
| 取色 | androidx.palette |
| 二维码 | zxing-core |
| 设置存储 | DataStore |
| 本地数据 | Room（播放历史、歌单缓存） |
| 播放 | Media3 ExoPlayer + MediaSessionService + SimpleCache |
| 截图测试 | Roborazzi（在 JVM 上渲染 Compose，不需要模拟器就能出 UI 截图） |

### 播放的关键设计
- **播放地址到用的时候才解析**：网易云的播放地址大约 20 分钟后失效，所以队列里只存歌曲 ID，用 `ResolvingDataSource` 在真正播放时调用 `/api/song/enhance/player/url/v1` 换取地址。
- **音质选择**：默认「无损」，可以在设置里改。如果拿不到选中的音质，就按 2 节的降级链往下试。「超清母带」需要 SVIP，会按账号状态显示或隐藏。
- **边听边缓存**：缓存键是 `歌曲ID+音质`，上限 1–2 GB，可设置。
- **后台和系统控制**：MediaSession 自带通知栏、锁屏、蓝牙和线控按键支持，另外处理音频焦点和耳机拔出自动暂停。
- **私人 FM**：队列快播完时自动补充下一批歌曲。

## 5. 页面和交互

### 竖屏
- 底部三个标签：**首页**（每日推荐、私人 FM、推荐歌单）、**搜索**、**我的**（我喜欢的音乐、创建和收藏的歌单）
- 底部常驻迷你播放条，点开或上滑进入竖屏播放页
- 歌单、专辑、歌手详情页使用同一套歌曲列表组件

### 横屏播放页（本项目的重点）
```
┌───────────────────────────────────────────────────────────────┐
│  ⌄                                         无损 · 1/32  ⋯       │
│                                                               │
│   ┌──────────┐        上一句歌词（半透明，轻微模糊）            │
│   │          │                                              │
│   │   封面    │      ▶ 当前这句 歌词逐字填色          ◀        │
│   │          │        current line translation              │
│   └──────────┘                                              │
│   歌名 ♡              下一句歌词                               │
│   歌手 · 专辑           ...                                    │
│                                                               │
│  01:12 ━━━━━━━━━━●──────────────── 04:03                      │
│        ⇄    ⏮     ⏯     ⏭    ☰                                 │
└───────────────────────────────────────────────────────────────┘
```
- **背景**：从封面取 3 种颜色，绘制缓慢流动的渐变色块，上面叠一层大幅模糊的封面。API 31 以上用 `RenderEffect` 实时模糊，29–30 用预先模糊好的缩略图。
- **歌词**：当前句加粗放大，用 YRC 逐字时间轴做从左到右的渐变填色；其他行颜色变淡，离当前行越远越模糊。切换行时用弹簧动画滚动。点某一行跳转到对应位置。翻译显示在下方，可以开关。手动滑动歌词 3 秒后自动回到当前行。
- **封面**：由播放页皮肤决定，默认旋转黑胶（见下方「皮肤」）。
- **沉浸模式**：5 秒不操作就隐藏顶栏和控制区，只留封面和歌词，点一下恢复。播放页可以设置保持屏幕常亮。
- **进入方式**：在播放页时横过手机，自动切换到横屏布局；在其他页面横屏时，这些页面使用平板式的双栏布局。

### 主题与皮肤（M0.5 已完成）
- **主题**决定整个 App 的视觉语言：配色、字体、形状、卡片边缘、背景、进度条、播放按钮、歌词排版和动效手感。共五个：澜（默认）、墨、樱、霓（只有深色）、暮。设计令牌放在 `ui/theme/Style.kt`。
- **播放页样式**可以和任意主题组合：旋转黑胶（默认）、方形封面、沉浸歌词、复古卡带。
- 加一个新主题：在 `Skins.kt` 加一项，在 `Style.kt` 写一份令牌和配色，在 `Backdrop.kt` / `CoverArt.kt` 各加一个绘制函数。`ContrastTest` 会检查文字对比度是否达标。
- M4 会加上「跟随封面取色」。

## 6. 用到的接口（首版）

| 功能 | 接口 |
|---|---|
| 登录 | `login/qrcode/unikey`、`login/qrcode/client/login`、`w/nuser/account/get`、`logout` |
| 我的 | `user/playlist`、`song/like/get`(likelist)、`song/like` |
| 首页 | `v3/discovery/recommend/songs`、`v1/discovery/recommend/resource`、`v1/radio/get`(私人 FM) |
| 详情 | `v6/playlist/detail` + `v3/song/detail`（分批获取）、`v1/album/{id}`、`v1/artist/{id}`、`artist/top/song` |
| 搜索 | `cloudsearch/pc`（单曲、专辑、歌手、歌单）、`search/suggest/web` |
| 播放 | `song/enhance/player/url/v1`、`song/lyric/v1`（lrc、tlyric、romalrc、yrc） |

## 7. 里程碑（每一期都会产出一个可以安装的 debug APK）

| 阶段 | 内容 | 怎么验证 |
|---|---|---|
| **M0 骨架** ✅ | Gradle 工程、Android SDK 安装脚本、皮肤系统、导航框架、占位页面、演示播放页、截图测试 | `assembleDebug` 编译通过，单元测试和 8 张截图通过 |
| **M0.5 设计重做** ✅ | 五套完整主题、容器变形展开、惯性唱片、波浪歌词与扫色、旋转按钮、沉浸横屏，首页/搜索/我的/登录/设置重新设计 | 单元测试（含文字对比度检查）和 25 张截图通过 |
| **M0.6 旗舰精修** ✅ | 设计规则写进令牌（字号、间距、单一强调色、自绘线性图标），「墨」逐页精修并设为默认 | 单元测试和 36 张截图通过 |
| **M0.7 「暮」精修 + 横屏重做** ✅ | 横屏改为舞台加统一控制栏、闲置时舞台放大；次要卡片去掉投影；「暮」的落日位置、四种封面、磁带标签和计数器 | 单元测试和 45 张截图通过 |
| **M1 接口层 + 登录** | eapi/weapi 加密、Cookie 管理、扫码登录页、账号信息 | 加密结果和 Node 生成的测试向量逐字节一致；在容器里调用不需要登录的接口（如搜索）做冒烟测试 |
| **M2 播放核心** | MediaSessionService、播放队列、播放地址解析、音质降级、通知栏、迷你播放条、竖屏播放页 | 单元测试加上你在真机上试用 |
| **M3 内容页** | 首页、我的、歌单、专辑、歌手、搜索 | Roborazzi 截图 |
| **M4 横屏播放页** | 歌词引擎（LRC/YRC/翻译）、流动背景、逐字填色、沉浸模式 | 歌词解析单元测试，横屏截图给你看 |
| **M5 打磨** | 缓存设置、私人 FM、各种错误和空状态、动效调整、release 签名配置 | 你在真机上完整试用 |

## 8. 风险和限制

- **非官方接口**：网易云随时可能修改接口或加强风控（比如返回 `-460` 或要求行为验证）。接口代码集中放在 `core/netease`，出问题时只需修改这一处。
- **账号安全**：使用第三方客户端有一定的风控风险，所以只模拟常规的 PC 客户端请求，不做高频请求。
- **没有模拟器**：这里只能编译、跑单元测试和截图测试。真机上的流畅度、后台保活、蓝牙控制需要你装上 APK 后实际测试并反馈。
- **只供个人使用**：不对外发布，也不做任何绕过付费的功能。
- **ROM 后台限制**：MIUI、ColorOS 等系统可能杀后台，在设置里提供「加入电池白名单」的引导。
