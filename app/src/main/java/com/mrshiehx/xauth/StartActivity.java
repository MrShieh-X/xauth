package com.mrshiehx.xauth;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.mrshiehx.xauth.beans.AccountItem;
import com.mrshiehx.xauth.beans.AccountSerializable;
import com.mrshiehx.xauth.utils.RSA;
import com.mrshiehx.xauth.utils.SerialUtils;
import com.mrshiehx.xauth.utils.SystemAuthenticator;
import com.mrshiehx.xauth.utils.Utils;

import org.apache.commons.codec.binary.Base32;

import java.io.File;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class StartActivity extends AppCompatActivity {
    private final StartActivity activity = this;
    private final Context context = this;
    private SystemAuthenticator systemAuthenticator;
    private ProgressDialog loading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPreferences = getSharedPreferences("xauth", MODE_PRIVATE);

        loading = new ProgressDialog(activity);
        loading.setMessage(getString(R.string.dialog_loading_message));
        loading.setCancelable(false);
        loading.show();

        systemAuthenticator = new SystemAuthenticator(activity);
        systemAuthenticator.dialogCancelable = false;
        systemAuthenticator.dialogMessage = getString(R.string.dialog_authentication_by_system_first_message);
        systemAuthenticator.keyName = MainActivity.KEY_NAME;
        if (Utils.isEmpty(sharedPreferences.getString("publicKey", "")) || Utils.isEmpty(sharedPreferences.getString("privateKey", ""))) {
            try {
                KeyPair keyPair = RSA.generateKeyPair();
                byte[] publicKey = RSA.publicToBytes(keyPair.getPublic());
                byte[] privateKey = RSA.privateToBytes(keyPair.getPrivate());
                systemAuthenticator.purpose = SystemAuthenticator.Purpose.ENCRYPT;
                systemAuthenticator.data = privateKey;
                systemAuthenticator.callback = (result, ivToStore) -> {
                    sharedPreferences.edit()
                            .putString("publicKey", Base64.encodeToString(publicKey, Base64.URL_SAFE))
                            .putString("privateKey", Base64.encodeToString(result, Base64.URL_SAFE))
                            .putString("privateKeyIv", Base64.encodeToString(ivToStore, Base64.URL_SAFE))
                            .apply();
                    startActivity(new Intent().setClass(context, MainActivity.class));//两种情况：1.新用户：没有任何账号，不用加载；2.有账号：但加密账号的密钥绝对不同（因为随机生成），也不用加载
                    finish();
                };
            } catch (Exception e) {
                e.printStackTrace();
                new AlertDialog.Builder(activity).setTitle(R.string.dialog_unable_to_use_title).setMessage(getString(R.string.dialog_unable_to_use_unable_to_generate_keys_message_wd, e)).setCancelable(false).show();
                return;
            }
        } else {
            systemAuthenticator.purpose = SystemAuthenticator.Purpose.DECRYPT;
            systemAuthenticator.data = Base64.decode(sharedPreferences.getString("privateKey", ""), Base64.URL_SAFE);
            systemAuthenticator.iv = Base64.decode(sharedPreferences.getString("privateKeyIv", ""), Base64.URL_SAFE);

            systemAuthenticator.callback = (result, ivToStore) -> {
                HashMap<AccountItem, File> map = new HashMap<>();
                File accountsDir = new File(getFilesDir(), "accounts");
                List<File> files = new LinkedList<>();
                listAllSubFilesAndDeleteEmptyFolder(accountsDir, files);
                Traversal:
                if (files.size() > 0) {
                    PrivateKey privateKey;
                    try {
                        privateKey = RSA.loadPrivateKey(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(activity, R.string.toast_failed_to_load_private_key, Toast.LENGTH_SHORT).show();
                        break Traversal;
                    }

                    for (File account : files) {
                        try {
                            AccountSerializable object = SerialUtils.fromByteArray(RSA.decrypt(Utils.toByteArray(account), privateKey));

                            String[] split = account.getAbsolutePath().substring(accountsDir.getAbsolutePath().length() + 1).split("/");
                            String name;
                            String issuer;
                            Base32 base32 = new Base32();
                            if (split.length == 1) {
                                issuer = null;
                                name = new String(base32.decode(split[0]));
                            } else {
                                issuer = new String(base32.decode(split[0]));
                                name = new String(base32.decode(split[1]));
                            }
                            map.put(new AccountItem(name, issuer, object.getCodeGenerator(), object.createdTime), account);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                Intent intent = new Intent();
                intent.setClass(context, MainActivity.class);
                intent.putExtra("accounts", map);
                startActivity(intent);
                finish();
            };
        }
        systemAuthenticator.start();
        systemAuthenticator.dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> finish());
    }

    private static void listAllSubFilesAndDeleteEmptyFolder(File folder, List<File> files) {
        File[] listFiles = folder.listFiles();
        if (listFiles == null || listFiles.length == 0) {
            folder.delete();
            return;
        }
        for (File file : listFiles)
            if (file.isFile())
                files.add(file);
            else
                listAllSubFilesAndDeleteEmptyFolder(file, files);
    }
}
