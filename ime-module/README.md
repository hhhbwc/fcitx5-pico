# fcitx5-pico · ime-module

PICO 4（A8110，Android 10）上的 fcitx5 输入法增强模块（Xposed/Vector 遗留桥）。**不含分辨率功能**——键盘屏分辨率由 [pico-resfix](https://github.com/hhhbwc/pico-resfix) 负责，两者搭配使用。

## 功能

1. **默认输入法保活**（防讯飞回切）
   - systemext：拦截 `Settings.Secure.putStringForUser` 对 `default_input_method` 的讯飞写入
   - systemext：键盘每次弹出时检查默认值，漂移到讯飞立即写回 fcitx5
   - system_server：IMMS `setInputMethodLocked` / `putSelectedInputMethod` / `putEnabledInputMethodsStr` 三条路径（Vector 若未注入 system_server 则自动闲置，不影响）
2. **键盘高度自适应**（比例修正）
   - fcitx5 原生按「百分比 × 键盘服务看到的屏幕高度」算键盘高度，在 PICO 上基准是主屏（≈1128px）而非键盘窗口（1x=460px，2x=920px），导致溢出/比例失调
   - 本模块钩 `InputView.getKeyboardHeightPx`，改用**键盘窗口所在 display 的实际高度 × 百分比**，窗口被 pico-resfix 调成 1x/2x/3x 都自动跟随，dp 比例恒定
   - 百分比照常在 fcitx5 设置里改（横屏键 `keyboard_height_percent_landscape`，推荐 75–88）

## 开关

| 属性 | 默认 | 说明 |
|---|---|---|
| `persist.fcitx5pico.keepalive` | 1 | 0 = 关闭保活 |
| `persist.fcitx5pico.heightfix` | 1 | 0 = 关闭高度自适应 |

## 作用域（Scope）

`com.picovr.systemext`、`android`、`org.fcitx.fcitx5.android`

## 构建

```powershell
cd ime-module
.\gradlew assembleRelease
# 产物：app\build\outputs\apk\release\app-release.apk
```

默认使用仓库外 `pico-ime/imemodule/module.keystore` 调试签名，正式发布请改 `app/build.gradle` 的 `signingConfigs.release` 或用环境变量 `FCITX5PICO_KEYSTORE` 指定 keystore。

## 安装（root + Vector）

```powershell
adb install app\build\outputs\apk\release\app-release.apk
# Vector 管理器（或 /data/adb/modules/zygisk_vector/cli）启用本模块，
# 作用域勾选 systemext / android / fcitx5，然后重启
```

## 与 pico-resfix 的分工

| 职责 | 归属 |
|---|---|
| 按应用分辨率、键盘屏分辨率解锁（1x/2x/3x、密度） | pico-resfix |
| 默认输入法保活、键盘高度比例修正 | 本模块（fcitx5 专用） |
