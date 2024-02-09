package com.mrshiehx.xauth.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class Utils {
    public static boolean isEmpty(@Nullable CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean isNumber(String value) {
        if (isEmpty(value)) return false;
        for (char a : value.toCharArray())
            if (a < '0' || a > '9') return false;
        return true;
    }

    public static CharSequence getPermissionName(Context context, String permission) {
        PackageManager pm = context.getPackageManager();
        try {
            //PermissionGroupInfo groupInfo = pm.getPermissionGroupInfo(info.group, 0);
            return pm.getPermissionInfo(permission, 0).loadLabel(pm);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return permission;
    }

    public static boolean isFlashLightOn(Camera camera) {
        if (camera == null) {
            return false;
        }
        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) {
            return false;
        }
        return Camera.Parameters.FLASH_MODE_TORCH.equals(parameters.getFlashMode());
    }


    public static boolean setFlashLight(Camera camera, boolean open) {
        if (camera == null) {
            return false;
        }
        Camera.Parameters parameters = camera.getParameters();
        if (parameters == null) {
            return false;
        }
        List<String> flashModes = parameters.getSupportedFlashModes();
        // Check if camera flash exists
        if (null == flashModes || 0 == flashModes.size()) {
            // Use the screen as a flashlight (next best thing)
            return false;
        }
        String flashMode = parameters.getFlashMode();
        if (open) {
            if (Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                return true;
            }
            // Turn on the flash
            if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(parameters);
                return true;
            } else {
                return false;
            }
        } else {
            if (Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                return true;
            }
            // Turn on the flash
            if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
                return true;
            } else
                return false;
        }
    }

    public static String inputStream2String(InputStream stream) throws IOException {
        try (InputStream is = stream) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (true) {
                int len = is.read(buf);
                if (len == -1)
                    break;
                result.write(buf, 0, len);
            }
            return result.toString(UTF_8.name());
        }
    }

    public static byte[] inputStream2ByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }


    public static File createFile(File file) throws IOException {
        return createFile(file, true);
    }

    public static File createFile(File file, boolean delete) throws IOException {
        if (file.getParentFile() != null) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
        }
        if (delete && file.exists()) file.delete();
        if (!file.exists()) {
            file.createNewFile();
        }
        return file;
    }

    public static byte[] toByteArray(File file) throws IOException {
        FileChannel fc = new RandomAccessFile(file, "r").getChannel();
        MappedByteBuffer byteBuffer = fc.map(FileChannel.MapMode.READ_ONLY, 0,
                fc.size()).load();
        //(byteBuffer.isLoaded());
        byte[] result = new byte[(int) fc.size()];
        if (byteBuffer.remaining() > 0) {
            // System.out.println("remain");
            byteBuffer.get(result, 0, byteBuffer.remaining());
        }
        fc.close();
        return result;
    }

    public static void bytes2File(byte[] bytes, File file) throws IOException {
        Utils.createFile(file);

        FileOutputStream fileOutputStream = new FileOutputStream(file, false);
        fileOutputStream.write(bytes, 0, bytes.length);
        fileOutputStream.flush();
        fileOutputStream.close();
    }

    public static void copy(Context context, CharSequence content) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("simple text", content);//这里的第一个参数label通常设置为null
        clipboard.setPrimaryClip(clip);
    }
}
