package com.mrshiehx.xauth.utils;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.mrshiehx.xauth.R;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * 因为构造函数进行了 {@link androidx.activity.ComponentActivity#registerForActivityResult(ActivityResultContract, ActivityResultCallback)} 和创建 PermissionRequester 实例的操作，所以请在 onCreate 或更早的地方创建本类实例，并重复使用。
 **/
public class SystemAuthenticator extends BiometricPrompt.AuthenticationCallback {
    private final FragmentActivity activity;

    public Purpose purpose;
    public boolean dialogCancelable;
    public CharSequence dialogMessage;
    public String keyName;
    public byte[] data;
    public byte[] iv;
    public Callback callback;

    private final KeyguardManager keyguardManager;
    //private final BiometricManager biometricManager;
    private final BiometricPrompt biometricPrompt;
    private final BiometricPrompt.PromptInfo promptInfoForFingerprint;

    private final ActivityResultLauncher<Intent> createConfirmDeviceCredentialLauncher1;
    private final ActivityResultLauncher<Intent> createConfirmDeviceCredentialLauncher2;
    private final PermissionRequester useBiometricRequester;

    public AlertDialog dialog;

    public SystemAuthenticator(FragmentActivity activity) {
        this(activity, null, false, null, null, null, null, null);
    }

    public SystemAuthenticator(FragmentActivity activity,
                               Purpose purpose,
                               boolean dialogCancelable,
                               CharSequence dialogMessage,
                               String keyName,
                               byte[] data,
                               byte[] iv/*解密时需要*/,
                               Callback callback) {
        this.activity = activity;
        this.purpose = purpose;
        this.dialogCancelable = dialogCancelable;
        this.dialogMessage = dialogMessage;
        this.keyName = keyName;
        this.data = data;
        this.iv = iv;
        this.callback = callback;

        this.keyguardManager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);

        //this.biometricManager = BiometricManager.from(activity);
        this.biometricPrompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), this);
        this.promptInfoForFingerprint = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.dialog_authentication_by_system_with_fingerprint_title))
                .setSubtitle(activity.getString(R.string.dialog_authentication_by_system_message))
                .setNegativeButtonText(activity.getString(R.string.dialog_authentication_by_system_use_other_authentication_methods))//connect with relation-0000 (Press Ctrl+F to search)
                .build();

        this.createConfirmDeviceCredentialLauncher1 = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // Challenge completed, proceed with using cipher
            if (result.getResultCode() == RESULT_OK) {
                FingerprintManager fingerprintManager = activity.getSystemService(FingerprintManager.class);
                boolean useFingerprint = fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();
                doDialog(useFingerprint);
            }
        });
        this.createConfirmDeviceCredentialLauncher2 = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // Challenge completed, proceed with using cipher
            if (result.getResultCode() == RESULT_OK) {
                tryAuthenticateByPassword();
            }
        });

        this.useBiometricRequester = new PermissionRequester(activity, Build.VERSION.SDK_INT >= 28 ? Manifest.permission.USE_BIOMETRIC : Manifest.permission.USE_FINGERPRINT, args -> {
            // Exceptions are unhandled within this snippet.
            try {
                Cipher cipher = getCipher();
                if (SystemAuthenticator.this.purpose == Purpose.ENCRYPT)
                    cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(SystemAuthenticator.this.keyName));
                else if (SystemAuthenticator.this.purpose == Purpose.DECRYPT)
                    cipher.init(Cipher.DECRYPT_MODE, getSecretKey(SystemAuthenticator.this.keyName), new IvParameterSpec(SystemAuthenticator.this.iv));
                biometricPrompt.authenticate(promptInfoForFingerprint, new BiometricPrompt.CryptoObject(cipher));
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activity, activity.getString(R.string.toast_failed_to_use_fingerprint_to_authenticate_wd, e), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void callback(byte[] result, byte[] ivToStore) {
        if (callback != null) callback.callback(result, ivToStore);
    }

    public void error(Throwable throwable) {
        new AlertDialog.Builder(activity).setTitle(R.string.dialog_authentication_or_crypt_failed_title).setMessage(throwable.toString()).show();
    }

    public void start() {
        if (!keyguardManager.isDeviceSecure()) {
            // Show a message that the user hasn't set up a lock screen.
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.dialog_device_not_secure_title)
                    .setMessage(R.string.dialog_device_not_secure_message)
                    .setCancelable(dialogCancelable)
                    .show();
            return;
        }

        FingerprintManager fingerprintManager = activity.getSystemService(FingerprintManager.class);
        //int canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_WEAK);
        boolean useFingerprint = fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();

        showDialog(useFingerprint);
        doDialog(useFingerprint);
    }

    private void showDialog(boolean useFingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.dialog_authentication_title);
        builder.setMessage(dialogMessage);
        builder.setCancelable(dialogCancelable);
        builder.setNeutralButton(android.R.string.cancel, null);
        builder.setPositiveButton(R.string.dialog_authentication_input_password, null);

        if (useFingerprint) {
            builder.setNegativeButton(R.string.dialog_authentication_fingerprint, null);
        } else {
            builder.setNegativeButton(null, null);
        }

        this.dialog = builder.show();
    }

    private void doDialog(boolean useFingerprint) {
        this.dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!generateKeyWithChecking(true)) return;
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(activity.getString(R.string.dialog_authentication_by_system_title), activity.getString(R.string.dialog_authentication_by_system_message_with_suggestion_of_using_password));
            createConfirmDeviceCredentialLauncher2.launch(intent);
            //tryAuthenticateByPassword();
        });

        if (useFingerprint) {
            this.dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> {
                /*if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                    Toast.makeText(activity, R.string.toast_biometric_error_hardware_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }*/
                if (!generateKeyWithChecking(false)) return;
                SystemAuthenticator.this.useBiometricRequester.request();
            });
            if (!generateKeyWithChecking(false)) return;
            SystemAuthenticator.this.useBiometricRequester.request();
        } else {
            if (!generateKeyWithChecking(true)) return;
            tryAuthenticateByPassword();
        }
    }

    private boolean generateKeyWithChecking(boolean isPassword) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(keyName)) {
                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                        keyName,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setInvalidatedByBiometricEnrollment(false)//needs Android 7.0(24)
                        .setUserAuthenticationRequired(true);
                if (isPassword) {
                    builder.setUserAuthenticationValidityDurationSeconds(5);//加这个代表使用密码
                }
                generateSecretKey(builder.build());
            }
            return true;
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException | IOException | KeyStoreException | CertificateException e) {
            e.printStackTrace();

            if (!Utils.isEmpty(e.getMessage()) && e.getMessage().startsWith("java.lang.IllegalStateException: At least one biometric")) {
                Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(activity.getString(R.string.dialog_authentication_by_system_title), activity.getString(R.string.dialog_authentication_by_system_message_with_suggestion_of_using_password));
                createConfirmDeviceCredentialLauncher1.launch(intent);
            } else {
                new AlertDialog.Builder(activity).setTitle(R.string.dialog_authentication_or_crypt_failed_title).setMessage(activity.getString(R.string.dialog_authentication_failed_to_generate_message_wd, e)).show();
            }
            return false;
        }
    }

    private void tryAuthenticateByPassword() {
        try {
            Cipher cipher = getCipher();
            SecretKey secretKey = getSecretKey(keyName);

            // Try encrypting something, it will only work if the user authenticated within
            // the last AUTHENTICATION_DURATION_SECONDS seconds.
            if (purpose == Purpose.ENCRYPT)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            else if (purpose == Purpose.DECRYPT)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

            byte[] result = cipher.doFinal(data);
            if (this.dialog != null) {
                this.dialog.dismiss();
                this.dialog = null;
            }
            if (purpose == Purpose.ENCRYPT) {
                byte[] iv = cipher.getIV();
                callback(result, iv);
            } else if (purpose == Purpose.DECRYPT) {
                callback(result, null);
            }
        } catch (UserNotAuthenticatedException e) {
            // User is not authenticated, let's authenticate with device credentials.
            e.printStackTrace();
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(activity.getString(R.string.dialog_authentication_by_system_title), activity.getString(R.string.dialog_authentication_by_system_message_with_suggestion_of_using_password));
            createConfirmDeviceCredentialLauncher2.launch(intent);
        } catch (KeyPermanentlyInvalidatedException e) {
            // This happens if the lock screen has been disabled or reset after the key was
            // generated after the key was generated.
            e.printStackTrace();
            new AlertDialog.Builder(activity).setMessage(e.toString()).show();
        } catch (BadPaddingException | IllegalBlockSizeException | KeyStoreException |
                CertificateException | UnrecoverableKeyException | IOException
                | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            error(e);
        }
    }

    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
        super.onAuthenticationError(errorCode, errString);
        if (!errString.equals(activity.getString(R.string.dialog_authentication_by_system_use_other_authentication_methods)))//connect with relation-0000 (Press Ctrl+F to search)
            Toast.makeText(activity, activity.getString(R.string.toast_fingerprint_authentication_error_wd, errString), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult authenticationResult) {
        super.onAuthenticationSucceeded(authenticationResult);

        Cipher cipher2 = authenticationResult.getCryptoObject().getCipher();
        byte[] result;

        try {
            result = cipher2.doFinal(data);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
            error(e);
            return;
        }
        if (this.dialog != null) {
            this.dialog.dismiss();
            this.dialog = null;
        }
        if (purpose == Purpose.ENCRYPT) {
            byte[] iv = cipher2.getIV();
            callback(result, iv);
        } else if (purpose == Purpose.DECRYPT) {
            callback(result, null);
        }
    }

    @Override
    public void onAuthenticationFailed() {
        super.onAuthenticationFailed();
        Toast.makeText(activity, R.string.toast_fingerprint_authentication_failed, Toast.LENGTH_SHORT).show();
    }

    public static void generateSecretKey(KeyGenParameterSpec keyGenParameterSpec) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(keyGenParameterSpec);
        keyGenerator.generateKey();
    }

    public static SecretKey getSecretKey(String keyName) throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");

        // Before the keystore can be accessed, it must be loaded.
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(keyName, null);
    }

    public static Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
    }

    public interface Callback {
        void callback(byte[] result, byte[] ivToStore);
    }

    public enum Purpose {
        ENCRYPT, DECRYPT
    }
}
