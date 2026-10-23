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
            // The whole line is built before any of it is printed: a throw halfway used to leave an
            // unclosed object that glued itself to the next app's line, and the app lost both.
            try {
                PackageInfo p = pm.getPackageInfo(name, 0);
                ApplicationInfo ai = p.applicationInfo;
                if (ai == null) continue;
                StringBuilder line = new StringBuilder("{\"p\":");
                string(line, name);
                line.append(",\"l\":");
                string(line, ai.loadLabel(pm).toString());
                line.append(",\"v\":").append(versionCode(p));
                String icon = icon(pm, ai);
                if (icon != null) line.append(",\"i\":\"").append(icon).append('"');
                line.append("}\n");
                out.print(line);
            } catch (Throwable t) {
                // One unreadable app (gone since the list, broken resources, a drawable that runs the
                // helper out of memory) doesn't stop the rest.
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

    /**
     * The first artwork that loads and draws: the icon, the banner, then PackageManager's own pick.
     * TV-only apps often ship a banner and no icon, which would draw as the generic robot, so for
     * them the banner goes first.
     */
    private static String icon(PackageManager pm, ApplicationInfo ai) {
        int first = ai.icon == 0 && ai.banner != 0 ? BANNER : ICON;
        for (int kind : new int[] {first, first == ICON ? BANNER : ICON, APPLICATION_ICON}) {
            try {
                String png = render(art(pm, ai, kind));
                if (png != null) return png;
            } catch (Throwable t) {
                // Try the next one; the label is still worth sending without any.
            }
        }
        return null;
    }

    private static final int ICON = 0, BANNER = 1, APPLICATION_ICON = 2;

    private static Drawable art(PackageManager pm, ApplicationInfo ai, int kind) {
        switch (kind) {
            case ICON: return ai.loadIcon(pm);
            case BANNER: return ai.loadBanner(pm); // null when the app has none
            default: return pm.getApplicationIcon(ai);
        }
    }

    private static String render(Drawable d) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
        try {
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
            return Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
        } finally {
            b.recycle();
        }
    }

    /** JSON string, ASCII only: anything outside printable ASCII is a \\u escape. */
    private static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\');
                out.append(c);
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }
}
