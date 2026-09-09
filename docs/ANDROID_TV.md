# 安卓电视版 (Android TV) 实施计划

## Context

为 Nowen Video 项目开发 Android TV 版本，以支持在电视上使用。项目已有 Android 手机版（Kotlin + Jetpack Compose + Media3），但完全没有 TV 相关代码。本计划旨在复用现有 core 层，仅新增 TV 专用 UI 模块，实现 10-foot 遥控器交互体验。

## 方案概述

采用**复用 core 层 + 新建 TV feature 模块**的方案：
- `core:data`、`core:model` 网络层和数据模型完全复用
- `core:designsystem` 设计 token 复用，仅调整间距
- 新建 `feature:tv` 模块，专门处理 TV 界面和 D-pad 交互
- 播放器逻辑复用，仅重写 UI 控制层

## 实施步骤

### 阶段 1：工程基础设施（1-2 天）

#### 1.1 新增 TV 依赖
**文件**: `android/gradle/libs.versions.toml`

新增：
```toml
[dependencies]
androidx-tv = { group = "androidx.tv", name = "foundation", version = "1.0.0" }
androidx-tv-material = { group = "androidx.tv", name = "material", version = "1.0.0" }
```

#### 1.2 创建 TV 模块
**新建目录结构**:
```
android/feature/tv/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── java/com/nowen/video/v2/feature/tv/
        ├── TvFeatureModule.kt          # Hilt 模块
        ├── TvApp.kt                    # TV 版 NowenApp
        └── ...
```

**文件**: `android/settings.gradle.kts`
- 在 `include(":feature:main")` 后新增 `include(":feature:tv")`

**文件**: `android/feature/tv/build.gradle.kts`
- 依赖 `:core:data`、`:core:model`、`:core:designsystem`
- 依赖 `androidx.tv.foundation` 和 `androidx.tv.material`

#### 1.3 TV Manifest 配置
**文件**: `android/app/src/main/AndroidManifest.xml`

新增 TV Leanback 相关声明：
```xml
<!-- TV 特性声明 -->
<uses-feature
    android:name="android.software.leanback"
    android:required="false" />

<!-- TV Launcher Activity Alias -->
<activity-alias
    android:name=".TvMainActivity"
    android:targetActivity=".MainActivity"
    android:enabled="false"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity-alias>
```

#### 1.4 TV Banner 资源
**新建**: `android/app/src/main/res/drawable/tv_banner.xml`
- TV 应用商店展示图标，尺寸 320x180

**文件**: `android/app/src/main/res/values/themes.xml`
- 新增 TV 主题 `Theme.Nowen.Tv`，继承 `Theme.Leanback` 或 `Material3.TvTheme`

---

### 阶段 2：TV 导航框架（2-3 天）

#### 2.1 TV 主壳组件
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/TvMainShell.kt`

功能：
- 横向侧边栏导航（替代手机版底部导航）
- 4 个主 tab：首页、影视库、搜索、我的
- 大间距、大字号，适合遥控器操作
- 复用 `core:designsystem` 的 `NowenColors`

关键实现：
```kotlin
@Composable
fun TvMainShell() {
    // 侧边栏导航
    Row {
        TvNavigationRail(
            selectedTab = selectedTab,
            onTabSelected = { /* 切换 tab */ }
        )
        // 主内容区
        TvNavHost(
            navController = tvNavController,
            modifier = Modifier.focusable()
        )
    }
}

@Composable
fun TvNavigationRail() {
    // 大按钮（最小 64dp 高）
    // D-pad 焦点管理
    // 焦点链配置
}
```

#### 2.2 TV 路由配置
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/TvNavHost.kt`

路由结构：
- `home` - TV 首页
- `library` - 影视库
- `search` - 搜索页
- `profile` - 个人中心
- `detail/{mediaId}` - 详情页
- `series/{seriesId}` - 剧集详情
- `player/{mediaId}` - 播放器
- `settings` - 设置页

#### 2.3 焦点管理系统
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/components/TvFocusManager.kt`

功能：
- 定义全局焦点策略
- 处理焦点移动（上下左右）
- 管理焦点环（focus cycle）
- TV 专属 `Modifier.focusable()` 扩展

---

### 阶段 3：TV 页面实现（5-7 天）

#### 3.1 TV 首页
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvHomeScreen.kt`

功能：
- Hero 横滑 banner（自动播放预告）
- 横向媒体分栏（热门推荐、继续观看、收藏等）
- 大卡片布局，适合 D-pad 导航
- 长按 OK 查看详情

关键组件复用：
- `core:designsystem` 的 `MediaPosterCard`
- `core:data` 的 `NowenRepository.home()`

#### 3.2 TV 详情页
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvDetailScreen.kt`

功能：
- 横版 Hero（替代手机版竖版）
- 横向演员列表
- 焦点导向的剧情介绍
- D-pad 选择集数/季

可复用：
- `android/feature/main/src/main/java/com/nowen/video/v2/feature/main/DetailWorkspace.kt` 中的组件（MobileDetailHero、DetailTabStrip 等）需要 TV 化改造

#### 3.3 TV 搜索页
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvSearchScreen.kt`

功能：
- 大搜索框
- 虚拟键盘（TV 专用）
- 历史搜索记录
- 搜索结果横向列表

#### 3.4 TV 影视库
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvLibraryScreen.kt`

功能：
- 横向分类浏览
- 焦点网格布局
- D-pad 大范围移动

---

### 阶段 4：TV 播放器（3-5 天）

#### 4.1 TV 播放器核心
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvPlayerScreen.kt`

功能：
- 复用 `PlayerViewModel` 播放逻辑（**不需要修改**）
- 替换触摸手势为 D-pad 控制：
  - 方向键左右：seek ±10s
  - 方向键上下：音量 ±10%
  - OK：播放/暂停
  - 长按 OK：显示播放信息
  - 返回：退出播放
- 全屏播放，默认横屏

关键实现：
```kotlin
@Composable
fun TvPlayerScreen(mediaId: String) {
    val playerViewModel: PlayerViewModel = viewModel()
    val uiState by playerViewModel.uiState.collectAsState()

    // 使用 ExoPlayer（Media3），不改变 PlayerViewModel
    TvExoPlayer(
        player = playerViewModel.player,
        modifier = Modifier.focusable()
    )

    // TV 专用控制层
    TvPlayerControls(
        uiState = uiState,
        onPlayPause = { playerViewModel.togglePlayPause() },
        onSeek = { playerViewModel.seek(it) },
        onVolumeChange = { playerViewModel.setVolume(it) }
    )

    // D-pad 事件处理
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
```

#### 4.2 TV 播放器控件
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/components/TvPlayerControls.kt`

功能：
- 大按钮播放/暂停
- 进度条（支持 D-pad 左右调节）
- 集数选择（剧集）
- 字幕/音轨切换

---

### 阶段 5：TV 设置与个人中心（1-2 天）

#### 5.1 TV 设置页
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvSettingsScreen.kt`

功能：
- 服务器管理
- 播放设置（默认质量、字幕语言）
- 外观设置（仅暗色主题）
- 退出登录

#### 5.2 TV 个人中心
**新建**: `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvProfileScreen.kt`

功能：
- 收藏历史
- 离线下载管理
- 头像和用户名显示

---

### 阶段 6：构建变体配置（1 天）

#### 6.1 App 模块 TV 变体
**文件**: `android/app/build.gradle.kts`

新增：
```kotlin
flavorDimensions += "platform"
productFlavors {
    create("phone") {
        dimension = "platform"
        // 手机版配置
    }
    create("tv") {
        dimension = "platform"
        applicationIdSuffix = ".tv"
        // TV 版配置
    }
}
```

#### 6.2 TV 构建优化
- TV 变体排除 CameraX、ML Kit 等触屏专属依赖
- TV 变体启用 leanback 特性

---

## 关键文件清单

### 需要修改的文件
| 文件 | 修改内容 |
|------|----------|
| `android/gradle/libs.versions.toml` | 新增 tv-material 依赖 |
| `android/settings.gradle.kts` | 新增 `:feature:tv` 模块 |
| `android/app/build.gradle.kts` | 新增 TV productFlavor |
| `android/app/src/main/AndroidManifest.xml` | TV leanback 声明 + activity-alias |
| `android/app/src/main/res/values/themes.xml` | 新增 TV 主题 |
| `android/app/src/main/res/drawable/tv_banner.xml` | TV banner 图标（新建） |

### 需要新建的文件（核心）
| 文件 | 作用 |
|------|------|
| `android/feature/tv/build.gradle.kts` | TV 模块构建配置 |
| `android/feature/tv/src/main/AndroidManifest.xml` | TV 模块清单 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/TvFeatureModule.kt` | Hilt 模块 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/TvApp.kt` | TV 版根组件 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/TvMainShell.kt` | TV 主壳 + 导航 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/TvNavHost.kt` | TV 路由配置 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvHomeScreen.kt` | TV 首页 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvDetailScreen.kt` | TV 详情页 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvPlayerScreen.kt` | TV 播放器 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvSearchScreen.kt` | TV 搜索页 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvLibraryScreen.kt` | TV 影视库 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvSettingsScreen.kt` | TV 设置页 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/screens/TvProfileScreen.kt` | TV 个人中心 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/components/TvFocusManager.kt` | 焦点管理 |
| `android/feature/tv/src/main/java/com/nowen/video/v2/feature/tv/components/TvPlayerControls.kt` | TV 播放控件 |

### 可直接复用的组件（无需修改）
| 组件 | 来源 |
|------|------|
| `PlayerViewModel` | `android/feature/main/` |
| `NowenRepository` | `android/core/data/` |
| `NowenApi` | `android/core/data/` |
| `NowenColors` / `NowenTheme` | `android/core/designsystem/` |
| `MediaPosterCard` | `android/core/designsystem/` |

## 验证方式

1. **本地构建测试**
   ```bash
   cd android
   ./gradlew assembleTvDebug
   ```

2. **模拟器测试**
   - 在 Android Studio 中创建 Android TV 模拟器（API 30+）
   - 运行 TV 变体：`./gradlew installTvDebug`
   - 使用遥控器测试所有导航和播放功能

3. **真机测试**
   - 在 TV 设备上安装 TV 版 APK
   - 测试 D-pad 导航、播放控制

4. **回归测试**
   - 确保手机版不受影响：`./gradlew assemblePhoneDebug`
   - 播放器逻辑与手机版一致

## 预估工期

| 阶段 | 内容 | 工期 |
|------|------|------|
| 1 | 工程基础设施 | 1-2 天 |
| 2 | TV 导航框架 | 2-3 天 |
| 3 | TV 页面实现 | 5-7 天 |
| 4 | TV 播放器 | 3-5 天 |
| 5 | TV 设置与个人中心 | 1-2 天 |
| 6 | 构建变体配置 | 1 天 |
| **总计** | | **约 2-3 周** |

## 手机版 vs 电视版核心区别

### 交互方式
| 维度 | 手机版 | 电视版 |
|------|--------|--------|
| 输入设备 | 触屏 | 遥控器（D-pad） |
| 核心操作 | 点击、滑动、长按、双击 | 方向键移动焦点、OK确认、返回/菜单 |
| 播放器手势 | 单击播放/暂停、双击快进快退、长按2x | D-pad左右seek、OK播放/暂停、长按显示信息 |

### UI布局
| 维度 | 手机版 | 电视版 |
|------|--------|--------|
| 导航 | 底部4-tab栏（Home/Library/Search/Profile） | 侧边栏或顶部横向导航，需更大点击目标 |
| 间距 | 紧凑 | 10-foot UI（更大间距，字号，遥控器导航） |
| 焦点系统 | 无（触屏直接操作） | 必须有焦点管理和焦点链 |
| 横屏 | 部分页面自适应 | 默认横屏，全局横屏 |

### 技术差异
| 维度 | 手机版 | 电视版 |
|------|--------|--------|
| Manifest | 无 leanback 声明 | 需声明 `android.software.leanback` |
| LAUNCHER | 标准 category | 需 TV category + banner 图标 |
| Camera/扫码 | 有（登录用） | TV版无意义，可禁用 |
| 播放器 | 复用 Media3/ExoPlayer | 复用，但控件层需重写 |

## AV1 软解兜底（libgav1）

`android/feature/tv/libs/media3-decoder-av1-1.5.1.aar` 为本地构建产物（Maven 无发布版），
播放器通过 `DefaultRenderersFactory.setEnableDecoderFallback(true) + EXTENSION_RENDERER_MODE_ON`
实现"硬解优先、软解兜底"。

### 重建 AAR 的方法

1. `git clone --depth 1 --branch 1.5.1 https://github.com/androidx/media.git`
2. 按 `libraries/decoder_av1/README.md` 拉取依赖并**钉版本**（关键，HEAD 会堆损坏）：
   - libgav1 → tag `v0.19.0`
   - abseil-cpp → tag `20240116.2`
   - cpu_features → 默认即可
3. 应用本地补丁（见下）
4. 写 `local.properties` 指向 SDK，`./gradlew :lib-decoder-av1:assembleRelease`

### 本地补丁内容（相对 media3 1.5.1 原始源码）

- `Gav1Decoder.java`：`gav1DecoderContext` 非 final；`release()` 关闭后置 0；
  `releaseOutputBuffer`/`release` 加 `synchronized` 并在上下文为 0 时跳过 `gav1ReleaseFrame`
- `gav1_jni.cc`：`gav1Close` 改为延迟 5s 销毁（僵尸列表），`gav1ReleaseFrame` 空上下文直接返回
- `jni/CMakeLists.txt`：x86/x86_64 ABI 强制 `LIBGAV1_ENABLE_AVX2/SSE4_1/OPTIMIZATIONS=OFF`（纯 C）

### 已知未解决问题

模拟器（Android TV x86 32 位）播放 AV1 时 libgav1 存在堆损坏：`JniContext` 在无任何
`gav1Close` 调用的情况下被清零，`gav1ReleaseFrame` 解引用 SIGSEGV，复现于播完换集时。
已排除：释放竞态（守卫+延迟销毁无效）、libgav1/abseil 版本错配（v0.19.0+20240116.2 仍崩）、
x86 汇编路径（纯 C 仍崩）。真机 arm64 是否受影响未验证。
