# fcitx5-pico

[fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) 的 PICO 4（A8110，Android 10）适配 fork：上游键盘源码原样保留在本仓库，另含一个独立的输入法增强模块。

## ime-module —— fcitx5 专用输入法增强模块

独立可安装的 Xposed 模块（Vector 遗留桥，applicationId `com.hhhbwc.fcitx5pico`），只做两件事，**不含任何分辨率逻辑**：

1. **默认输入法保活** —— 三层拦截 PICO 把默认输入法改回讯飞：
   - systemext 的 `Settings.Secure` 写入守卫；
   - 键盘每次弹出时主动纠偏；
   - system_server 的 IMMS 切换 / 持久化 / 启用列表三条路径。
2. **键盘高度自适应** —— 钩住 fcitx5 的 `InputView.getKeyboardHeightPx`，把「百分比 × 主屏高度」改为「百分比 × 键盘窗口实际高度」：键盘屏无论被 pico-resfix 调成 1x / 2x / 3x，键盘都按同一 dp 比例完整渲染，不再溢出或变形。

- 开关：`persist.fcitx5pico.keepalive=0` 关保活；`persist.fcitx5pico.heightfix=0` 关高度自适应
- Vector 作用域：`com.picovr.systemext` / `android` / `org.fcitx.fcitx5.android`
- 构建：`cd ime-module && ./gradlew assembleRelease`（详见 [ime-module/README.md](ime-module/README.md)）

## 与 pico-resfix 搭配

| 职责 | 归属 |
|---|---|
| 2D 应用 + 键盘屏**分辨率解锁**（1x/1.5x/2x/3x、密度，GUI 配置） | [hhhbw/pico-resfix](https://github.com/hhhbwc/pico-resfix) |
| 默认输入法保活、键盘高度比例修正（fcitx5 专用） | 本仓库 `ime-module` |

两者都是 Vector（LSPosed 兼容框架）模块，职责互不重叠，搭配使用。

## 仓库结构

- `ime-module/` —— 上述独立模块（自带 Gradle 构建，不参与键盘主工程编译）
- 其余目录 —— 上游 fcitx5-android 源码（当前未改动；PICO 相关的键盘补丁今后落在本仓库）

## 上游

键盘本体即 [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)：Fcitx5 输入法框架的 Android 移植。下载渠道、构建环境与开发文档见 [README_upstream.md](README_upstream.md)。
