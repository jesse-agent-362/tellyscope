// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.helper;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs on the TV as the shell user, through {@code app_process}, and prints one JSON object per
 * app: {@code {"p":package,"l":label,"v":versionCode,"i":base64 PNG}}. Arguments are the packages
 * to describe, or {@code --all}. It only reads through PackageManager: no writes, no permissions,
 * no network. The app pushes it, runs it once and deletes it.
 */
public final class Labels {
    private static final int ICON_PX = 96;

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        PackageManager pm = systemContext().getPackageManager();
        PrintStream out = new PrintStream(System.out, false, "UTF-8");

        List<String> names = new ArrayList<>();
        if (args.length == 1 && args[0].equals("--all")) {
            for (PackageInfo p : pm.getInstalledPackages(0)) names.add(p.packageName);
        } else {
            for (String a : args) names.add(a);
        }
        for (String name : names) {
            try {
                PackageInfo p = pm.getPackageInfo(name, 0);
                ApplicationInfo ai = p.applicationInfo;
                if (ai == null) continue;
                out.print("{\"p\":");
                string(out, name);
                out.print(",\"l\":");
                string(out, ai.loadLabel(pm).toString());
                out.print(",\"v\":");
                out.print(versionCode(p));
                // TV-only apps often ship a banner and no icon, which would draw as the generic robot.
                Drawable art = ai.icon == 0 && ai.banner != 0 ? ai.loadBanner(pm) : ai.loadIcon(pm);
                String icon = icon(art);
                if (icon != null) {
                    out.print(",\"i\":\"");
                    out.print(icon);
                    out.print('"');
                }
                out.print("}\n");
            } catch (Exception e) {
                // One unreadable app (gone since the list, broken resources) doesn't stop the rest.
            }
        }
        out.flush();
    }

    /** No Context in a bare app_process; build the system one the way the framework does for itself. */
    private static Context systemContext() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Constructor<?> ctor = at.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object thread = ctor.newInstance();
        Field current = at.getDeclaredField("sCurrentActivityThread");
        current.setAccessible(true);
        current.set(null, thread);
        Method get = at.getDeclaredMethod("getSystemContext");
        return (Context) get.invoke(thread);
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo p) {
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    private static String icon(Drawable d) {
        if (d == null) return null;
        try {
            Bitmap b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            // Fit, centred: a 16:9 banner keeps its shape instead of being squashed square.
            int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
            if (w > 0 && h > 0 && w != h) {
                int fw = w > h ? ICON_PX : ICON_PX * w / h;
                int fh = w > h ? ICON_PX * h / w : ICON_PX;
                d.setBounds((ICON_PX - fw) / 2, (ICON_PX - fh) / 2, (ICON_PX + fw) / 2, (ICON_PX + fh) / 2);
            } else {
                d.setBounds(0, 0, ICON_PX, ICON_PX);
            }
            d.draw(new Canvas(b));
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, png);
            b.recycle();
            return Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null; // the label is still worth sending
        }
    }

    /** JSON string, ASCII only: anything outside printable ASCII is a \\u escape. */
    private static void string(PrintStream out, String s) {
        out.print('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.print('\\');
                out.print(c);
            } else if (c < 0x20 || c > 0x7e) {
                out.print(String.format("\\u%04x", (int) c));
            } else {
                out.print(c);
            }
        }
        out.print('"');
    }
}
