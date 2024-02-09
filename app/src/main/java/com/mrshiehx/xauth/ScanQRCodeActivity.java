package com.mrshiehx.xauth;


import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.mrshiehx.xauth.qrcode.android.BeepManager;
import com.mrshiehx.xauth.qrcode.android.InactivityTimer;
import com.mrshiehx.xauth.qrcode.android.ScanQRCodeActivityHandler;
import com.mrshiehx.xauth.qrcode.camera.CameraManager;
import com.mrshiehx.xauth.qrcode.decode.QRCodeDecoder;
import com.mrshiehx.xauth.qrcode.view.ViewfinderView;
import com.mrshiehx.xauth.utils.FileChooser;
import com.mrshiehx.xauth.utils.PermissionRequester;
import com.mrshiehx.xauth.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * 这个activity打开相机，在后台线程做常规的扫描；它绘制了一个结果view来帮助正确地显示条形码，在扫描的时候显示反馈信息，
 * 然后在扫描成功的时候覆盖扫描结果
 */
public class ScanQRCodeActivity extends AppCompatActivity implements
        SurfaceHolder.Callback {
    private final ScanQRCodeActivity activity = this;
    private static final String TAG = ScanQRCodeActivity.class.getSimpleName();

    private boolean flashlightStatus;
    private LinearLayout flashlight;
    // 相机控制
    private CameraManager cameraManager;
    private ScanQRCodeActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    //private IntentSource source;
    private Collection<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType, ?> decodeHints;
    private String characterSet;
    // 电量控制
    private InactivityTimer inactivityTimer;
    // 声音、震动控制
    private BeepManager beepManager;

    private ProgressDialog scanning;

    private final PermissionRequester permissionRequesterForFromAlbum =
            new PermissionRequester(this, Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE, args -> /*fileChooser*/getFileChooser().choose());

    // Registers a photo picker activity launcher in single-select mode.
    //registerForActivityResult用法参考：https://27house.cn/archives/2210
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            ContentResolver resolver = getApplicationContext().getContentResolver();
            try (InputStream stream = resolver.openInputStream(uri)) {
                scanning.show();
                byte[] bytes = Utils.inputStream2ByteArray(stream);
                new Thread(() -> {
                    handleLocal(bytes);
                    runOnUiThread(scanning::cancel);
                }).start();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(activity, getString(R.string.toast_failed_to_open_file_wd, e.toString()), Toast.LENGTH_SHORT).show();
                scanning.dismiss();
            }
        }
    });

    private final FileChooser fileChooser = new FileChooser(activity, "image/*", new FileChooser.OnChoseFile() {
        @Override
        public void execute(String filePath) {
            scanning.show();
            new Thread(() -> {
                try {
                    handleLocal(Utils.toByteArray(new File(filePath)));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(activity, getString(R.string.toast_failed_to_open_file_wd, e.toString()), Toast.LENGTH_SHORT).show());
                }
                runOnUiThread(scanning::cancel);
            }).start();
        }
    });

    public FileChooser getFileChooser() {
        return fileChooser;
    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    void makeStatusBarBeTranslucent() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

    }

    /**
     * OnCreate中初始化一些辅助类，如 InactivityTimer（休眠）、Beep（声音）以及 AmbientLight（闪光灯）
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        makeStatusBarBeTranslucent();
        // 保持Activity处于唤醒状态
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_scan_qrcode);

        hasSurface = false;

        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);

        flashlight = findViewById(R.id.scan_qrcode_flashlight);
        flashlight.setOnClickListener(v -> {
            if (cameraManager != null && cameraManager.getCamera() != null) {
                int id;
                if (flashlightStatus) {
                    id = R.drawable.for_know_off;
                } else {
                    id = R.drawable.for_know_on;
                }
                if (Utils.setFlashLight(cameraManager.getCamera(), !flashlightStatus)) {
                    flashlightStatus = !flashlightStatus;
                    flashlight.setBackgroundResource(id);
                }
            }
        });

        scanning = new ProgressDialog(this);
        scanning.setCancelable(false);
        scanning.setMessage(getText(R.string.dialog_scanning_qrcode_message));

        ImageButton imageButton_back = (ImageButton) findViewById(R.id.scan_qrcode_back);
        imageButton_back.setOnClickListener(v -> finish());

        TextView fromAlbum = findViewById(R.id.scan_qrcode_choose_from_album);
        fromAlbum.setOnClickListener((v) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            } else {
                permissionRequesterForFromAlbum.request();
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        // CameraManager必须在这里初始化，而不是在onCreate()中。
        // 这是必须的，因为当我们第一次进入时需要显示帮助页，我们并不想打开Camera,测量屏幕大小
        // 当扫描框的尺寸不正确时会出现bug
        cameraManager = new CameraManager(getApplication());

        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        handler = null;

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // activity在paused时但不会stopped,因此surface仍旧存在；
            // surfaceCreated()不会调用，因此在这里初始化camera
            initCamera(surfaceHolder);
        } else {
            // 重置callback，等待surfaceCreated()来初始化camera
            surfaceHolder.addCallback(this);
        }

        beepManager.updatePrefs();
        inactivityTimer.onResume();

        //source = IntentSource.NONE;
        decodeFormats = null;
        characterSet = null;

        flashlightStatus = Utils.isFlashLightOn(cameraManager.getCamera());
        if (flashlightStatus) {
            flashlight.setBackgroundResource(R.drawable.for_know_on);
        } else {
            flashlight.setBackgroundResource(R.drawable.for_know_off);
        }
        decodeHints = QRCodeDecoder.HINTS;
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    /**
     * 扫描成功，处理反馈信息
     */
    public void handleDecodeFromCamera(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();

        boolean fromLiveScan = barcode != null;
        //这里处理解码完成后的结果，此处将参数回传到Activity处理
        if (fromLiveScan) {
            beepManager.playBeepSoundAndVibrate();

            /*Toast.makeText(this, "扫描成功", Toast.LENGTH_SHORT).show();*/

            Intent intent = getIntent();
            intent.putExtra("codedContent", rawResult.getText());
            intent.putExtra("takenByCamera", true);
            //intent.putExtra("codedBitmap", barcode);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    /**
     * 初始化Camera
     */
    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            return;
        }
        try {
            // 打开Camera硬件设备
            cameraManager.openDriver(surfaceHolder);
            // 创建一个handler来打开预览，并抛出一个运行时异常
            if (handler == null) {
                handler = new ScanQRCodeActivityHandler(this, decodeFormats,
                        decodeHints, characterSet, cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    /**
     * 显示底层错误信息并退出应用
     */
    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.dialog_exception_camera_framework_bug));
        builder.show();
    }

    void handleLocal(byte[] bytes) {
        Intent intent = getIntent();
        try {
            intent.putExtra("codedContent", QRCodeDecoder.syncDecodeQRCode(bytes));
        } catch (NotFoundException e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(activity, R.string.toast_failed_to_scan_qrcode, Toast.LENGTH_SHORT).show());
            return;
        }
        intent.putExtra("takenByCamera", false);
        //intent.putExtra("codedBitmap", barcode);
        setResult(RESULT_OK, intent);
        if (cameraManager != null) cameraManager.closeDriver();
        finish();
    }
}
