/*
 *  Copyright (C) 2013 - 2014 Alexander "Evisceration" Martinz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.namelessrom.devicecontrol.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.Toast;

import com.stericson.roottools.RootTools;
import com.stericson.roottools.execution.CommandCapture;

import org.namelessrom.devicecontrol.Application;
import org.namelessrom.devicecontrol.Logger;
import org.namelessrom.devicecontrol.R;
import org.namelessrom.devicecontrol.database.DatabaseHandler;
import org.namelessrom.devicecontrol.database.TaskerItem;
import org.namelessrom.devicecontrol.objects.ShellOutput;
import org.namelessrom.devicecontrol.services.TaskerService;
import org.namelessrom.devicecontrol.utils.cmdprocessor.CMDProcessor;
import org.namelessrom.devicecontrol.utils.constants.DeviceConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.namelessrom.devicecontrol.objects.ShellOutput.OnShellOutputListener;

public class Utils implements DeviceConstants {

    private static final String[] BLACKLIST =
            Application.get().getStringArray(R.array.file_black_list);
    private static final String[] ENABLED_STATES = { "Y", "TRUE", "1", "255" };

    public static boolean isNameless() {
        return Application.get().getPackageManager().hasSystemFeature("org.namelessrom.android")
                || existsInFile(Scripts.BUILD_PROP, "ro.nameless.version");
    }

    public static boolean existsInFile(final String file, final String prop) {
        return !findPropValue(file, prop).isEmpty();
    }

    public static String findPropValue(final String file, final String prop) {
        try {
            return findPropValueOf(file, prop);
        } catch (Exception e) { return ""; }
    }

    private static String findPropValueOf(final String file, final String prop) throws Exception {
        final File f = new File(file);
        if (f.exists() && f.canRead()) {
            FileInputStream fis = null;
            InputStreamReader isr = null;
            BufferedReader br = null;

            try {
                fis = new FileInputStream(f);
                isr = new InputStreamReader(fis);
                br = new BufferedReader(isr);

                String s;
                while ((s = br.readLine()) != null) {
                    if (s.contains(prop)) return s.replace(prop + '=', "");
                }
            } finally {
                if (br != null) br.close();
                if (isr != null) isr.close();
                if (fis != null) fis.close();
            }
        }

        return "";
    }

    public static String loadFromAssets(final String path) throws Exception {
        final StringBuilder sb = new StringBuilder();

        InputStream htmlStream = null;
        InputStreamReader reader = null;
        BufferedReader br = null;
        try {
            htmlStream = Application.get().getAssets().open(path);
            reader = new InputStreamReader(htmlStream);
            br = new BufferedReader(reader);
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            if (br != null) br.close();
            if (reader != null) reader.close();
            if (htmlStream != null) htmlStream.close();
        }

        return sb.toString();
    }

    /**
     * Reads a single line from a file.
     *
     * @param sFile The file to read from.
     * @return The read string OR null if not existing.
     */
    public static String readOneLine(final String sFile) {
        return readOneLine(sFile, false);
    }

    @Nullable public static String readOneLine(final String sFile, final boolean trim) {
        if (fileExists(sFile)) {
            BufferedReader brBuffer;
            try {
                brBuffer = new BufferedReader(new FileReader(sFile), 512);
                try {
                    final String value = brBuffer.readLine();
                    return ((trim && value != null) ? value.trim() : value);
                } finally {
                    brBuffer.close();
                }
            } catch (Exception e) {
                return readFileViaShell(sFile, true);
            }
        }
        return null;
    }

    /**
     * Reads a file.
     *
     * @param sFile The file to read from.
     * @return The read string OR null if not existing.
     */
    @Nullable public static String readFile(final String sFile) {
        if (fileExists(sFile)) {
            FileReader reader = null;
            BufferedReader brBuffer = null;
            try {
                reader = new FileReader(sFile);
                brBuffer = new BufferedReader(reader, 512);

                final StringBuilder sb = new StringBuilder();
                String s;
                while ((s = brBuffer.readLine()) != null) {
                    sb.append(s).append('\n');
                }
                return sb.toString();
            } catch (Exception e) {
                return readFileViaShell(sFile, true);
            } finally {
                if (brBuffer != null) try { brBuffer.close(); } catch (Exception ignored) { }
                if (reader != null) try { reader.close(); } catch (Exception ignored) { }
            }
        }
        return null;
    }

    public static String readFileViaShell(final String filePath, final boolean useSu) {
        return readFileViaShell(filePath, useSu, false);
    }

    public static String readFileViaShell(final String filePath, final boolean useSu,
            final boolean trim) {
        final String command = String.format("cat %s;", filePath);
        final String result = useSu ? CMDProcessor.runSuCommand(command).getStdout()
                : CMDProcessor.runShellCommand(command).getStdout();
        return ((trim && result != null) ? result.trim() : result);
    }

    /**
     * Write a string value to the specified file.
     *
     * @param filename The filename
     * @param value    The value
     */
    public static boolean writeValue(final String filename, final String value) {
        if (fileExists(filename)) {
            try {
                final FileWriter fw = new FileWriter(filename);
                try {
                    fw.write(value);
                } finally {
                    fw.close();
                }
            } catch (IOException ignored) {
                writeValueViaShell(filename, value);
                return false;
            }
        }
        return true;
    }

    /**
     * Fallback if everything fails
     *
     * @param filename The file to write
     * @param value    The value to write
     */
    private static void writeValueViaShell(final String filename, final String value) {
        runRootCommand(Utils.getWriteCommand(filename, value));
    }

    /**
     * Check if the specified file exists.
     *
     * @param filename The filename
     * @return Whether the file exists or not
     */
    public static boolean fileExists(final String filename) {
        //noinspection SimplifiableIfStatement
        if (TextUtils.isEmpty(filename) || TextUtils.equals("-", filename)) {
            return false;
        }
        return new File(filename).exists();
    }

    /**
     * Check if one of the specified files exists.
     *
     * @param files The list of filenames
     * @return Whether one of the files exists or not
     */
    public static boolean fileExists(final String[] files) {
        for (final String s : files) { if (fileExists(s)) return true; }
        return false;
    }

    /**
     * Checks if the given paths in a string array are existing and returns the existing path.
     *
     * @param paths The string array, containing the file paths
     * @return The path of the existing file as string
     */
    public static String checkPaths(final String[] paths) {
        for (final String s : paths) {
            if (fileExists(s)) { return s; }
        }
        return "";
    }

    /**
     * Checks if the given path is existing and returns the existing path.
     *
     * @param path The file path
     * @return The path or an empty string if not existent
     */
    public static String checkPath(final String path) {
        if (fileExists(path)) { return path; }
        return "";
    }

    /**
     * Setup the directories for Device Control
     */
    public static void setupDirectories() {
        final String basePath = Application.get().getFilesDirectory();
        final String[] dirList = new String[]{ basePath + DC_LOG_DIR };
        File dir;
        for (final String s : dirList) {
            dir = new File(s);
            if (!dir.exists()) {
                Logger.v(Utils.class, String.format(
                        "setupDirectories: creating %s -> %s", s, dir.mkdirs()));
            }
        }
    }

    /**
     * Reads string array from file
     *
     * @param path File to read from
     * @return string array
     */
    @Nullable public static String[] readStringArray(final String path) {
        final String line = readOneLine(path);
        if (line != null) {
            return line.split(" ");
        }
        return null;
    }

    public static String[] listFiles(final String path, final boolean blacklist) {
        return listFiles(path, blacklist ? BLACKLIST : null);
    }

    public static String[] listFiles(final String path, final String[] blacklist) {
        final String output = execute(String.format("ls %s", path));
        Logger.v(Utils.class, "listFiles --> output: %s", output);
        if (TextUtils.isEmpty(output)) return new String[0];

        final String[] files = output.trim().split("\n");
        if (blacklist != null) {
            final ArrayList<String> filtered = new ArrayList<>();
            for (final String s : files) {
                if (!Utils.isFileBlacklisted(s, blacklist)) {
                    filtered.add(s);
                }
            }
            return filtered.toArray(new String[filtered.size()]);
        }
        return files;
    }

    public static boolean isFileBlacklisted(final String file, final String[] blacklist) {
        for (final String s : blacklist) {
            if (TextUtils.equals(s, file)) return true;
        }
        return false;
    }

    public static String getFileName(final String path) {
        if (TextUtils.isEmpty(path)) return "";
        final String[] splitted = path.trim().split("/");
        Logger.v(Utils.class, "getFileName(%s) --> %s", path, splitted[splitted.length - 1]);
        return splitted[splitted.length - 1];
    }

    public static String execute(final String command) {
        return new CMDProcessor().su.runWaitFor(command).stdout;
    }

    public static boolean getCommandResult(final String command) {
        return new CMDProcessor().su.runWaitFor(command).success();
    }

    public static String getCommandResult(final String command, final String def) {
        final String result = new CMDProcessor().su.runWaitFor(command).stdout;
        return (TextUtils.isEmpty(result) ? def : result);
    }

    public static void runRootCommand(final String command) {
        runRootCommand(command, false);
    }

    /**
     * Runs a shell command with root (super user) rights
     *
     * @param command The command to run
     * @param wait    If true, this command is blocking until execution finished
     */
    public static void runRootCommand(final String command, final boolean wait) {
        Logger.v("runRootCommand", "executing -> %s", command);
        final CommandCapture comm = new CommandCapture(0, false, command);
        try {
            RootTools.getShell(true).add(comm);
            if (wait) {
                //noinspection StatementWithEmptyBody
                while (comm.isExecuting()) {
                    // wait for it
                }
            }
        } catch (Exception e) {
            Logger.v(Utils.class, "runRootCommand: " + e.getMessage());
        }
    }

    public static void getCommandResult(final OnShellOutputListener listener, final String cmd) {
        getCommandResult(listener, -1, cmd);
    }

    public static void getCommandResult(final OnShellOutputListener listener, final int id,
            final String cmd) {
        getCommandResult(listener, id, cmd, false);
    }

    public static void getCommandResult(final OnShellOutputListener listener, final int ID,
            final String COMMAND, final boolean NEWLINE) {
        final StringBuilder sb = new StringBuilder();
        final CommandCapture comm = new CommandCapture(0, false, COMMAND) {
            @Override public void commandOutput(int id, String line) {
                sb.append(line);
                if (NEWLINE) {
                    sb.append('\n');
                }
            }

            @Override
            public void commandCompleted(int id, int exitcode) {
                final String result = sb.toString();
                Logger.v(Utils.class,
                        String.format("Shell output: %s", result));
                Application.HANDLER.post(new Runnable() {
                    @Override public void run() {
                        listener.onShellOutput(new ShellOutput(ID, sb.toString()));
                    }
                });
            }
        };
        try {
            RootTools.getShell(true).add(comm);
        } catch (Exception e) {
            Logger.v(Utils.class, "runRootCommand: " + e.getMessage());
        }
    }

    public static String getReadCommand(final String path) {
        return String.format("cat %s 2> /dev/null", path);
    }

    public static String getWriteCommand(final String path, final String value) {
        return String.format("chmod 644 %s;\n", path) +
                String.format("busybox echo \"%s\" > %s;\n", value, path);
    }

    public static void disableComponent(final String packageName, final String componentName) {
        toggleComponent(packageName, componentName, true);
    }

    public static void enableComponent(final String packageName, final String componentName) {
        toggleComponent(packageName, componentName, false);
    }

    public static void toggleComponent(final String packageName, final String componentName,
            final boolean disable) {
        toggleComponent(new ComponentName(packageName, componentName), disable);
    }

    public static void toggleComponent(final ComponentName component, final boolean disable) {
        final PackageManager pm = Application.get().getPackageManager();
        if (pm != null) {
            pm.setComponentEnabledSetting(component,
                    (disable
                            ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                    PackageManager.DONT_KILL_APP
            );
        }
    }

    public static boolean startTaskerService() {
        if (!PreferenceHelper.getBoolean(USE_TASKER)) return false;

        boolean enabled = false;
        final List<TaskerItem> taskerItemList = DatabaseHandler.getInstance().getAllTaskerItems("");
        for (final TaskerItem item : taskerItemList) {
            if (item.enabled) {
                enabled = true;
                break;
            }
        }

        final Intent tasker = new Intent(Application.get(), TaskerService.class);
        if (enabled) {
            tasker.setAction(TaskerService.ACTION_START);
            Logger.v(Utils.class, "Starting TaskerService");
        } else {
            tasker.setAction(TaskerService.ACTION_STOP);
            Logger.v(Utils.class, "Stopping TaskerService");
        }
        Application.get().startService(tasker);

        return enabled;
    }

    public static void stopTaskerService() {
        final Intent tasker = new Intent(Application.get(), TaskerService.class);
        tasker.setAction(TaskerService.ACTION_STOP);
        Application.get().startService(tasker);
    }

    public static boolean isEnabled(String s, final boolean contains) {
        if (s != null) {
            s = s.trim().toUpperCase();
            for (final String state : ENABLED_STATES) {
                if (contains) {
                    if (s.contains(state)) return true;
                } else {
                    if (s.equals(state)) return true;
                }
            }
        }
        return false;
    }

    public static String getBatteryHealth(final int healthInt) {
        int health;

        switch (healthInt) {
            case BatteryManager.BATTERY_HEALTH_COLD:
                health = R.string.cold;
                break;
            case BatteryManager.BATTERY_HEALTH_GOOD:
                health = R.string.good;
                break;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                health = R.string.dead;
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                health = R.string.over_voltage;
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                health = R.string.overheat;
                break;
            default:
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                health = R.string.unknown;
                break;
        }

        return Application.get().getString(health);
    }

    public static boolean remount(final String path, final String mode) {
        try {
            RootTools.remount(path, mode);
        } catch (Exception e) {
            Logger.v(Utils.class, String.format(
                    "Could not remount %s with \"%s\", error: %s", path, mode, e));
            return false;
        }
        return true;
    }

    public static String setPermissions(final String path, final String mask,
            final int user, final int group) {
        return String.format(
                "busybox chown %s.%s %s;busybox chmod %s %s;", user, group, path, mask, path);
    }

    public static void restartActivity(final Activity activity) {
        if (activity == null) return;
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        activity.finish();
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        activity.startActivity(activity.getIntent());
    }

    public static int tryParse(final String parse, final int def) {
        try { return Utils.parseInt(parse); } catch (Exception exc) { return def; }
    }

    public static Integer tryValueOf(final String value, final int def) {
        try { return Integer.valueOf(value); } catch (Exception exc) { return def; }
    }

    public static String humanReadableKiloByteCount(final long kilobytes) {
        if (kilobytes < 1024) return kilobytes + " kB";
        final int exp = (int) (Math.log(kilobytes) / Math.log(1024));
        return String.format("%.0f %sB", kilobytes / Math.pow(1024, exp),
                String.valueOf("MGTPE".charAt(exp - 1)));
    }

    public static String tryParseKiloByte(final String value, final int mult) {
        try {
            return humanReadableKiloByteCount(Long.parseLong(value) * mult);
        } catch (Exception exc) { return value; }
    }

    public static String getAndroidId() {
        return Settings.Secure.getString(
                Application.get().getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static void showToast(final Context context, final int resourceId) {
        showToast(context, Application.get().getString(resourceId));
    }

    public static void showToast(final Context context, final String string) {
        Application.HANDLER.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(context, string, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static String getDateAndTime() {
        return new SimpleDateFormat("yyyy-MM-dd.HH.mm.ss")
                .format(new Date(System.currentTimeMillis()));
    }

    public static int parseInt(final String integer) {
        return parseInt(integer, -1);
    }

    public static int parseInt(String integer, final int def) {
        try {
            if (integer != null) integer = integer.trim();
            return Integer.parseInt(integer);
        } catch (NumberFormatException exc) {
            Logger.e("Utils", String.format("parseInt(%s, %s)", integer, def), exc);
            return def;
        }
    }

    public static String getDate(final long time) {
        final Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time);
        return DateFormat.format("dd-MM-yyyy", cal).toString();
    }
}
