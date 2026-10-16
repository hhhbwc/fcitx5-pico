# fcitx5-pico

**PICO 4（A8110，Android 10）专用输入法增强模块** —— 一个 Xposed 模块工程（Vector 遗留桥，applicationId `com.hhhbwc.fcitx5pico`），同时保留上游 [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) 键盘源码（见 [`fcitx5-android/`](fcitx5-android/)）。

## 功能（只适配 fcitx5，不含任何分辨率逻辑）

1. **默认输入法保活** —— 三层拦截 PICO 把默认输入法改回讯飞：
   - systemext 的 `Settings.Secure` 写入守卫；
   - 键盘每次弹出时主动纠偏；
   - system_server 的 IMMS 切换 / 持久化 / 启用列表三条路径。
2. **键盘高度自适应** —— 钩住 fcitx5 的 `InputView.getKeyboardHeightPx`，把「百分比 × 主屏高度」改为「键盘窗口实际高度 − 工具栏 − 边距」的自适应填充：键盘永远精确填满面板、工具栏完整可见，1x / 2x / 3x 下键的 dp 尺寸完全一致，**无需调任何高度设置**。

## 开关与作用域

| 属性 | 默认 | 说明 |
|---|---|---|
| `persist.fcitx5pico.keepalive` | 1 | 0 = 关闭保活 |
| `persist.fcitx5pico.heightfix` | 1 | 0 = 关闭高度自适应 |

Vector 作用域：`com.picovr.systemext` / `android` / `org.fcitx.fcitx5.android`。

## 下载

从 [Releases](https://github.com/hhhbwc/fcitx5-pico/releases) 下载已签名的 `fcitx5-pico-v1.0.0.apk`。

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

- **1.0.0（首个公开版本）** —— 三层默认输入法保活 + 键盘高度完全自适应（含内部迭代 1.0.1–1.0.3 的全部修复：attach 后重算、工具栏/边距扣除、百分比路线废弃）。

## 许可证

本模块以 [GPL-3.0](LICENSE) 发布。`fcitx5-android/` 目录内的上游键盘源码遵循其原始许可证（详见 [fcitx5-android/](fcitx5-android/)）。

## 仓库结构

- **根目录** —— 模块工程（`app/` + Gradle 构建）
- [`fcitx5-android/`](fcitx5-android/) —— 上游 fcitx5-android 键盘源码，原样留档（当前未改动；PICO 相关的键盘补丁今后落在本仓库）。其下载渠道、构建环境与开发文档见 [fcitx5-android/README.md](fcitx5-android/README.md)。注意：其中的 git 子模块路径已按新位置调整，构建键盘本体需在 `fcitx5-android/` 目录内进行。
