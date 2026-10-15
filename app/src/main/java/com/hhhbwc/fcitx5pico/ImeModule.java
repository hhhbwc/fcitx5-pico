package com.hhhbwc.fcitx5pico;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.view.Display;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * fcitx5-pico 输入法增强模块（只适配 fcitx5，不碰分辨率）。
 *
 * <p>屏幕分辨率由 pico-resfix 负责；本模块只解决两件事：
 *
 * <p>一、默认输入法保活——三层拦截 PICO 把默认输入法改回讯飞：
 * systemext 的 Settings.Secure 写入、键盘弹出时的主动纠偏、system_server 的
 * IMMS 切换/持久化/启用列表三条路径。属性 persist.fcitx5pico.keepalive=0 可关闭。
 *
 * <p>二、键盘高度自适应——fcitx5 按"百分比 × 键盘服务看到的屏幕高度"计算键盘高度，
 * 在 PICO 上这个基准是主屏（约 1128px）而不是键盘窗口（1x=460px，2x=920px），
 * 导致键盘溢出或比例失调。本模块钩住 InputView.getKeyboardHeightPx，
 * 改用键盘窗口所在 display 的实际高度 × 百分比，窗口被 resfix 调成任何分辨率，
 * 键盘都按同一 dp 比例渲染，不再变形。属性 persist.fcitx5pico.heightfix=0 可关闭。
 */
public final class ImeModule implements IXposedHookLoadPackage {
    static final String TAG = "Fcitx5Pico";
    private static final String IFLY_PKG = "com.iflytek.inputmethod.pico";
    private static final String KEEP_TARGET =
            "org.fcitx.fcitx5.android/.input.FcitxInputMethodService";
    private static final String KEEP_PROP = "persist.fcitx5pico.keepalive";
    private static final String HEIGHTFIX_PROP = "persist.fcitx5pico.heightfix";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if ("com.picovr.systemext".equals(lp.packageName)) {
            installSystemExtKeepAlive(lp);
            installRePinHook(lp);
        } else if ("android".equals(lp.packageName) && "android".equals(lp.processName)) {
            installSystemServerKeepAlive(lp);
        } else if ("org.fcitx.fcitx5.android".equals(lp.packageName)) {
            installKeyboardHeightFix(lp);
        }
    }

    // ==================================================================
    // 一、默认输入法保活
    // ==================================================================

    /** systemext：NativeShell 直接写 secure 设置的路径。 */
    private static void installSystemExtKeepAlive(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> secure = XposedHelpers.findClass(
                    "android.provider.Settings$Secure", lp.classLoader);
            XC_MethodHook guard = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!keepAliveEnabled()) return;
                    int valueIndex = -1;
                    for (int i = 0; i < param.args.length; i++) {
                        if ("default_input_method".equals(param.args[i])) {
                            valueIndex = i + 1;
                            break;
                        }
                    }
                    if (valueIndex < 0 || valueIndex >= param.args.length) return;
                    String requested = param.args[valueIndex] instanceof String
                            ? (String) param.args[valueIndex] : null;
                    if (!isIflyIme(requested)) return;
                    if (KEEP_TARGET.equals(requested)) return;
                    param.args[valueIndex] = KEEP_TARGET;
                    log("keepalive systemext write " + KEEP_TARGET);
                }
            };
            XposedHelpers.findAndHookMethod(secure, "putStringForUser",
                    android.content.ContentResolver.class, String.class, String.class,
                    int.class, guard);
            XposedHelpers.findAndHookMethod(secure, "putStringForUser",
                    android.content.ContentResolver.class, String.class, String.class,
                    String.class, boolean.class, int.class, guard);
            log("installed systemext keepalive hook");
        } catch (Throwable t) {
            log("failed to install systemext keepalive hook", t);
        }
    }

    /** systemext：键盘每次弹出时纠偏——默认漂移到讯飞就写回 fcitx5。 */
    private static void installRePinHook(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> appContainer = XposedHelpers.findClass(
                    "com.bytedance.nativeshell.appmanager.AppContainer", lp.classLoader);
            Class<?> window = XposedHelpers.findClass(
                    "com.bytedance.nativeshell.appmanager.widget.InputMethodWindow",
                    lp.classLoader);
            XposedHelpers.findAndHookMethod(window, "showInputMethod",
                    appContainer, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            rePinDefault();
                        }
                    });
            log("installed re-pin hook");
        } catch (Throwable t) {
            log("failed to install re-pin hook", t);
        }
    }

    /** system_server：IMMS 三条把讯飞设回默认的路径。 */
    private static void installSystemServerKeepAlive(XC_LoadPackage.LoadPackageParam lp) {
        final Class<?> imms, settings;
        try {
            imms = XposedHelpers.findClass(
                    "com.android.server.inputmethod.InputMethodManagerService", lp.classLoader);
            settings = XposedHelpers.findClass(
                    "com.android.server.inputmethod.InputMethodUtils$InputMethodSettings",
                    lp.classLoader);
        } catch (Throwable t) {
            log("keepalive: failed to resolve IMMS classes", t);
            return;
        }

        hook("keepalive IMMS.setInputMethodLocked", () -> XposedHelpers.findAndHookMethod(
                imms, "setInputMethodLocked", String.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!keepAliveEnabled()) return;
                        String requested = param.args[0] instanceof String
                                ? (String) param.args[0] : null;
                        if (!isIflyIme(requested)) return;
                        if (KEEP_TARGET.equals(requested)) return;
                        if (!imeInstalled(param.thisObject, KEEP_TARGET)) return;
                        param.args[0] = KEEP_TARGET;
                        log("keepalive " + requested + " -> " + KEEP_TARGET);
                    }
                }));

        hook("keepalive putSelectedInputMethod", () -> XposedHelpers.findAndHookMethod(
                settings, "putSelectedInputMethod", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!keepAliveEnabled()) return;
                        String requested = param.args[0] instanceof String
                                ? (String) param.args[0] : null;
                        if (!isIflyIme(requested)) return;
                        if (KEEP_TARGET.equals(requested)) return;
                        param.args[0] = KEEP_TARGET;
                        log("keepalive persisted default " + KEEP_TARGET);
                    }
                }));

        hook("keepalive putEnabledInputMethodsStr", () -> XposedHelpers.findAndHookMethod(
                settings, "putEnabledInputMethodsStr", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!keepAliveEnabled()) return;
                        String enabled = param.args[0] instanceof String
                                ? (String) param.args[0] : "";
                        if (enabled.contains(KEEP_TARGET)) return;
                        param.args[0] = enabled.isEmpty() ? KEEP_TARGET
                                : enabled + ":" + KEEP_TARGET;
                        log("keepalive kept enabled: " + KEEP_TARGET);
                    }
                }));
    }

    /** 键盘弹出时的主动纠偏：默认漂移到讯飞就写回 fcitx5。 */
    private static void rePinDefault() {
        try {
            Object resolver = systemContentResolver();
            if (resolver == null) return;
            Class<?> secure = XposedHelpers.findClass("android.provider.Settings$Secure", null);
            Object current = XposedHelpers.callStaticMethod(secure, "getString",
                    resolver, "default_input_method");
            if (!(current instanceof String) || !isIflyIme((String) current)) return;
            if (KEEP_TARGET.equals(current)) return;
            XposedHelpers.callStaticMethod(secure, "putStringForUser",
                    resolver, "default_input_method", KEEP_TARGET, 0);
            log("keepalive re-pinned default -> " + KEEP_TARGET);
        } catch (Throwable t) {
            log("rePinDefault failed", t);
        }
    }

    // ==================================================================
    // 二、键盘高度自适应（fcitx5 进程内）
    // ==================================================================

    /**
     * InputView.keyboardHeightPx = 百分比 × 基准屏高。在 PICO 上 fcitx5 看到的
     * 基准是主屏而非键盘窗口，这里改为键盘窗口所在 display 的实际高度 × 百分比。
     * display 来自 view 自身，resfix 无论把键盘屏 resize 成 1x/2x/3x 都自动跟随。
     */
    private static void installKeyboardHeightFix(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> inputView = XposedHelpers.findClass(
                    "org.fcitx.fcitx5.android.input.InputView", lp.classLoader);
            XposedHelpers.findAndHookMethod(inputView, "getKeyboardHeightPx",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!propEnabled(HEIGHTFIX_PROP)) return;
                                Object fixed = fixedHeight(param.thisObject);
                                if (fixed != null) {
                                    if (fixed != param.getResult()) param.setResult(fixed);
                                } else {
                                    scheduleAttachRecompute((View) param.thisObject);
                                }
                            } catch (Throwable t) {
                                log("heightfix failed", t);
                            }
                        }
                    });
            log("installed keyboard-height hook");
        } catch (Throwable t) {
            log("failed to install keyboard-height hook", t);
        }
    }

    private static Integer fixedHeight(Object thisObject) {
        View view = (View) thisObject;
        Display display = view.getDisplay();
        if (display == null) {
            if (!loggedPreAttach) {
                loggedPreAttach = true;
                log("heightfix: first call pre-attach; queueing recompute");
            }
            return null;
        }
        Point size = new Point();
        display.getRealSize(size);
        if (size.y <= 0) return null;
        int percent = heightPercent(view);
        if (percent <= 0 || percent > 100) return null;
        int fixed = size.y * percent / 100;
        if (!loggedComputed) {
            loggedComputed = true;
            log("heightfix: display=" + size.x + "x" + size.y
                    + " percent=" + percent + " -> " + fixed + "px");
        }
        return fixed;
    }

    /**
     * The first getKeyboardHeightPx() call happens while the view is still detached, when
     * view.getDisplay() cannot answer and the stock display-of-the-service base applies.
     * Queue a recompute via View.post: on a detached view it runs exactly at
     * dispatchAttachedToWindow, once the keyboard display is known.
     */
    private static void scheduleAttachRecompute(View view) {
        synchronized (recomputeQueued) {
            if (recomputeQueued.put(view, Boolean.TRUE) != null) return;
        }
        view.post(() -> {
            try {
                XposedHelpers.callMethod(view, "updateKeyboardSize");
            } catch (Throwable t) {
                log("attach recompute failed", t);
            }
        });
    }

    private static final java.util.Map<View, Boolean> recomputeQueued =
            new java.util.WeakHashMap<>();
    private static volatile boolean loggedPreAttach;
    private static volatile boolean loggedComputed;

    /** 百分比直接读 fcitx5 的 SharedPreferences，横竖屏各取对应键，设置改动实时生效。 */
    private static int heightPercent(View view) {
        try {
            Context context = view.getContext().getApplicationContext();
            SharedPreferences sp = context.getSharedPreferences(
                    "org.fcitx.fcitx5.android_preferences", Context.MODE_PRIVATE);
            boolean landscape = context.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            return landscape
                    ? sp.getInt("keyboard_height_percent_landscape", 75)
                    : sp.getInt("keyboard_height_percent", 75);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private static boolean keepAliveEnabled() {
        return propEnabled(KEEP_PROP);
    }

    private static boolean propEnabled(String prop) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class)
                    .invoke(null, prop, "1");
            return !"0".equals(v);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isIflyIme(String imeId) {
        return imeId != null && imeId.startsWith(IFLY_PKG + "/");
    }

    /** 目标键盘必须已安装（IMMS 的方法表里存在），避免重定向到不存在的输入法。 */
    private static boolean imeInstalled(Object imms, String imeId) {
        try {
            Object map = XposedHelpers.getObjectField(imms, "mMethodMap");
            return map instanceof java.util.Map && ((java.util.Map<?, ?>) map).containsKey(imeId);
        } catch (Throwable t) {
            return true;
        }
    }

    private static Object systemContentResolver() {
        try {
            Object activityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentActivityThread");
            Object systemContext = activityThread == null ? null
                    : XposedHelpers.callMethod(activityThread, "getSystemContext");
            return systemContext == null ? null
                    : XposedHelpers.callMethod(systemContext, "getContentResolver");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static void hook(String name, Runnable install) {
        try {
            install.run();
        } catch (Throwable t) {
            log("failed to install " + name, t);
        }
    }

    private static void log(String message, Throwable error) {
        XposedBridge.log(TAG + ": " + message + (error == null ? "" : " (" + error + ")"));
    }
}
