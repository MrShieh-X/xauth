package com.mrshiehx.xauth.utils;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.mrshiehx.xauth.R;

import java.util.Arrays;

/**
 * 权限请求工具<br/><br/>
 * 用法：<br/>
 * 1.先在 onCreate 或更早新建本类实例<br/>
 * 2.使用时：<br/>
 * （选，用于传递参数）<code>{REQUESTER}.setArguments(new Object[]{...arguments...});</code><br/>
 * <code>{REQUESTER}.request();</code>
 *
 * @author MrShiehX
 */
public class PermissionRequester {
    private final ComponentActivity activity;
    private final String permission;
    private final OnGranted onGranted;

    private final ActivityResultLauncher<Intent> settingsLauncher;
    private final ActivityResultLauncher<String> requestingPermissionLauncher;

    private Object[] arguments;

    private AlertDialog dialog;

    public PermissionRequester(ComponentActivity activity, String permission, OnGranted onGranted) {
        this.activity = activity;
        this.permission = permission;
        this.onGranted = onGranted;

        this.settingsLauncher = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), activityResult -> backFromSettings());
        this.requestingPermissionLauncher = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::backFromRequestingPermission);
    }

    public void request() {
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            startRequestPermission();
        } else {
            onGranted.execute(arguments);
        }
    }

    public void setArguments(Object[] arguments) {
        this.arguments = Arrays.copyOf(arguments, arguments.length);
    }

    public Object[] getArguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    private void startRequestPermission() {
        boolean shouldShowRequestPermissionRationale = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            shouldShowRequestPermissionRationale = activity.shouldShowRequestPermissionRationale(permission);
        }
        if (!shouldShowRequestPermissionRationale) {
            requestingPermissionLauncher.launch(permission);
        } else {
            showDialogTipUserGoToAppSettings();
        }
    }

    private void showDialogTipUserGoToAppSettings() {
        AlertDialog.Builder dialog_no_permissions = new AlertDialog.Builder(activity);
        dialog_no_permissions.setTitle(activity.getString(R.string.dialog_no_permissions_title, Utils.getPermissionName(activity, permission)))
                .setMessage(activity.getString(R.string.dialog_no_permissions_message, Utils.getPermissionName(activity, permission)))
                .setPositiveButton(activity.getString(R.string.dialog_no_permissions_button_gotosettings), (dialog, which) -> goToAppSetting())
                .setNegativeButton(activity.getString(android.R.string.cancel), /*(dialog, which) -> onDenied.execute()*/null);
        dialog = dialog_no_permissions.show();
    }

    private void goToAppSetting() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        settingsLauncher.launch(intent);
    }

    private void backFromSettings() {
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            showDialogTipUserGoToAppSettings();
        } else {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            onGranted.execute(arguments);
        }
    }

    public void backFromRequestingPermission(boolean granted) {
        if (!granted) {
            showDialogTipUserGoToAppSettings();
        } else {
            onGranted.execute(arguments);
        }
    }

    public interface OnGranted {
        void execute(Object[] args);
    }
}
