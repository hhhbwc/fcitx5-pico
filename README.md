# fcitx5-pico

**PICO 4（A8110，Android 10）专用输入法增强模块** —— 一个 Xposed 模块工程（Vector 遗留桥，applicationId `com.hhhbwc.fcitx5pico`），同时保留上游 [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) 键盘源码（见 [`fcitx5-android/`](fcitx5-android/)）。

## 功能（只适配 fcitx5，不含任何分辨率逻辑）

1. **默认输入法保活** —— 三层拦截 PICO 把默认输入法改回讯飞：
   - systemext 的 `Settings.Secure` 写入守卫；
   - 键盘每次弹出时主动纠偏；
   - system_server 的 IMMS 切换 / 持久化 / 启用列表三条路径。
2. **键盘高度自适应** —— 钩住 fcitx5 的 `InputView.getKeyboardHeightPx`，把「百分比 × 主屏高度」改为「百分比 × 键盘窗口实际高度」：键盘屏无论被 pico-resfix 调成 1x / 2x / 3x，键盘都按同一 dp 比例完整渲染，不再溢出或变形。

## 开关与作用域

| 属性 | 默认 | 说明 |
|---|---|---|
| `persist.fcitx5pico.keepalive` | 1 | 0 = 关闭保活 |
| `persist.fcitx5pico.heightfix` | 1 | 0 = 关闭高度自适应 |

Vector 作用域：`com.picovr.systemext` / `android` / `org.fcitx.fcitx5.android`。

## 构建与安装

```powershell
git clone https://github.com/hhhbwc/fcitx5-pico.git
cd fcitx5-pico
.\gradlew assembleRelease
# 产物：app\build\outputs\apk\release\app-release.apk
adb install app\build\outputs\apk\release\app-release.apk
# 重装请先 adb uninstall（避开 Vector 的模块 DEX 缓存），再在 Vector 里启用并勾选作用域
```

默认使用 `keystore/debug.keystore` 调试签名；正式发布请改 `app/build.gradle` 的 `signingConfigs.release` 或设环境变量 `FCITX5PICO_KEYSTORE`。

## 与 pico-resfix 搭配

| 职责 | 归属 |
|---|---|
| 2D 应用 + 键盘屏**分辨率解锁**（1x/1.5x/2x/3x、密度，GUI 配置，改动重启头显生效） | [hhhbw/pico-resfix](https://github.com/hhhbwc/pico-resfix) |
| 默认输入法保活、键盘高度比例修正（fcitx5 专用） | 本仓库 |

两者都是 Vector 模块，职责互不重叠，搭配使用。

## 版本

- 1.0.2 — 修复顶部工具栏（KawaiiBar，40dp 固定）被裁：键盘高度只约束键网格，网格偏高会把工具栏顶出窗口；现在网格高度会扣除工具栏 + 底部 padding + 导航栏 inset，永不超过窗口剩余空间。
- 1.0.1 — 修复键盘高度自适应：首次高度计算发生在视图 attach 之前（拿不到键盘屏），改为 attach 时刻自动重算；新增一次性诊断日志 `heightfix: display=WxH percent=P -> Hpx`。
- 1.0.0 — 首个版本。

## 仓库结构

- **根目录** —— 模块工程（`app/` + Gradle 构建）
- [`fcitx5-android/`](fcitx5-android/) —— 上游 fcitx5-android 键盘源码，原样留档（当前未改动；PICO 相关的键盘补丁今后落在本仓库）。其下载渠道、构建环境与开发文档见 [fcitx5-android/README.md](fcitx5-android/README.md)。注意：其中的 git 子模块路径已按新位置调整，构建键盘本体需在 `fcitx5-android/` 目录内进行。
