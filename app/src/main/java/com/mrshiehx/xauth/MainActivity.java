package com.mrshiehx.xauth;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.textfield.TextInputLayout;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.zxing.WriterException;
import com.mrshiehx.xauth.auth.Authenticator;
import com.mrshiehx.xauth.beans.AccountItem;
import com.mrshiehx.xauth.beans.AccountSerializable;
import com.mrshiehx.xauth.beans.GoogleAuth;
import com.mrshiehx.xauth.qrcode.encode.QRCodeCreator;
import com.mrshiehx.xauth.utils.Base32String;
import com.mrshiehx.xauth.utils.PermissionRequester;
import com.mrshiehx.xauth.utils.RSA;
import com.mrshiehx.xauth.utils.SerialUtils;
import com.mrshiehx.xauth.utils.SystemAuthenticator;
import com.mrshiehx.xauth.utils.Utils;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.PrivateKey;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    public static final String KEY_NAME = "XAUTH_ENCRYPTION_KEY";

    private final Context context = this;
    private final MainActivity activity = this;

    private FloatingActionButton fabScan;
    private FloatingActionButton fabInput;
    private FloatingActionMenu fabButtons;
    private ListView listView;

    private AccountsAdapter adapter;

    private final HashMap<AccountItem, File> map = new HashMap<>();
    private final List<AccountItem> list = new LinkedList<>();

    private SystemAuthenticator systemAuthenticator;
    private PermissionRequester permissionRequesterForScan;
    private final ActivityResultLauncher<Intent> scanQRCodeActivityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null) return;
                String text = data.getStringExtra("codedContent").trim();
                File accountsDir = new File(getFilesDir(), "accounts");
                if (text.startsWith("otpauth-migration://offline?data=")) {
                    String s = text.substring("otpauth-migration://offline?data=".length());

                    byte[] decoded;
                    GoogleAuth.MigrationPayload migrationPayload;
                    try {
                        decoded = Base64.decodeBase64(URLDecoder.decode(s, "UTF-8"));
                        migrationPayload = GoogleAuth.MigrationPayload.parseFrom(decoded);
                    } catch (UnsupportedEncodingException | InvalidProtocolBufferException e) {
                        e.printStackTrace();
                        new AlertDialog.Builder(context).setTitle(R.string.dialog_failed_to_decode_title).setMessage(e.toString()).show();
                        return;
                    }

                    for (GoogleAuth.MigrationPayload.OtpParameters otpParameters : migrationPayload.getOtpParametersList()) {
                        AccountSerializable accountSerializable = new AccountSerializable(text, new String(new Base32().encode(otpParameters.getSecret().toByteArray())), System.currentTimeMillis());
                        AccountItem accountItem = new AccountItem(otpParameters.getName(), otpParameters.getIssuer(), accountSerializable.getCodeGenerator(), accountSerializable.createdTime);

                        File accountFile = new File(accountsDir, (!Utils.isEmpty(otpParameters.getIssuer()) ? (new Base32().encodeToString(otpParameters.getIssuer().getBytes()) + "/") : "") + new Base32().encodeToString(otpParameters.getName().getBytes()));
                        if (accountFile.exists()) {
                            Toast.makeText(context, R.string.toast_already_exist_same_name_account, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            Utils.bytes2File(RSA.encrypt(SerialUtils.toByteArray(accountSerializable), RSA.loadPublicKey(android.util.Base64.decode(getSharedPreferences("xauth", MODE_PRIVATE).getString("publicKey", ""), android.util.Base64.URL_SAFE))), accountFile);
                            map.put(accountItem, accountFile);
                            list.add(accountItem);
                        } catch (Exception e) {
                            e.printStackTrace();
                            new AlertDialog.Builder(context).setTitle(R.string.dialog_failed_to_add_title).setMessage(getString(R.string.dialog_failed_to_add_message_wd, otpParameters.getName(), e)).show();
                        }
                    }
                    adapter.notifyDataSetChanged();
                } else if (text.startsWith("otpauth://totp/")) {
                    Uri uri = Uri.parse(text);
                    String name = String.valueOf(validateAndGetNameInPath(uri.getPath()));
                    String issuer = uri.getQueryParameter("issuer");
                    String secret = uri.getQueryParameter("secret");
                    if (Utils.isEmpty(secret)) {
                        Toast.makeText(context, R.string.toast_incomplete_link_missing_secret, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AccountSerializable accountSerializable = new AccountSerializable(text, secret, System.currentTimeMillis());
                    AccountItem accountItem = new AccountItem(name, issuer, accountSerializable.getCodeGenerator(), accountSerializable.createdTime);
                    File accountFile = new File(accountsDir, (!Utils.isEmpty(issuer) ? (new Base32().encodeToString(issuer.getBytes()) + "/") : "") + new Base32().encodeToString(name.getBytes()));
                    if (accountFile.exists()) {
                        Toast.makeText(context, R.string.toast_already_exist_same_name_account, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        Utils.bytes2File(RSA.encrypt(SerialUtils.toByteArray(accountSerializable), RSA.loadPublicKey(Base64.decodeBase64(getSharedPreferences("xauth", MODE_PRIVATE).getString("publicKey", "")))), accountFile);
                        map.put(accountItem, accountFile);
                        list.add(accountItem);
                        adapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        e.printStackTrace();
                        new AlertDialog.Builder(context).setTitle(R.string.dialog_failed_to_add_title).setMessage(getString(R.string.dialog_failed_to_add_message_wd, name, e)).show();
                    }
                } else {
                    Toast.makeText(context, R.string.toast_this_format_is_not_supported, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        permissionRequesterForScan = new PermissionRequester(this, Manifest.permission.CAMERA, args -> scanQRCodeActivityLauncher.launch(new Intent(this, ScanQRCodeActivity.class)));
        systemAuthenticator = new SystemAuthenticator(activity);

        fabButtons = findViewById(R.id.fab_buttons);
        fabScan = findViewById(R.id.fab_scan);
        fabInput = findViewById(R.id.fab_input);
        listView = findViewById(R.id.list_view);

        Intent intent = getIntent();
        Object mapObject = intent.getSerializableExtra("accounts");
        if (mapObject != null) {
            map.putAll((HashMap<AccountItem, File>) mapObject);
        }

        list.addAll(map.keySet());
        list.sort(Comparator.comparingLong(o -> o.createdTime));
        listView.setAdapter(adapter = new AccountsAdapter(this, map, list) {
            @Override
            public void export(AccountItem accountItem) {
                MainActivity.this.export(accountItem);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Utils.copy(context, ((AccountItem) parent.getAdapter().getItem(position)).getOrGenerateCode());
            Toast.makeText(context, getText(R.string.toast_successfully_copied), Toast.LENGTH_SHORT).show();
            return true;
        });

        fabScan.setOnClickListener(v -> {
            fabButtons.close(true);
            permissionRequesterForScan.request();
        });

        fabInput.setOnClickListener(v -> {
            fabButtons.close(true);
            final View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_account, null);
            TextInputLayout accountInputLayout = dialogView.findViewById(R.id.account_name_input_layout);
            TextInputLayout secretInputLayout = dialogView.findViewById(R.id.key_value_input_layout);
            EditText accountEditText = dialogView.findViewById(R.id.account_name);
            EditText issuerEditText = dialogView.findViewById(R.id.issuer);
            EditText secretEditText = dialogView.findViewById(R.id.key_value);

            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .setTitle(getString(R.string.dialog_add_account_title))
                    .setNegativeButton(activity.getResources().getString(android.R.string.cancel), null)
                    .setPositiveButton(getResources().getString(R.string.dialog_button_confirm), null);

            accountInputLayout.setErrorEnabled(true);
            secretInputLayout.setErrorEnabled(true);


            accountEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    accountInputLayout.setError(null);
                    secretInputLayout.setError(null);
                }
            });
            secretEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    accountInputLayout.setError(null);
                    secretInputLayout.setError(null);
                }
            });

            AlertDialog dialog = builder.show();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v2 -> {
                if (accountEditText.getText().length() == 0) {
                    accountInputLayout.setError(getString(R.string.dialog_add_account_account_empty));
                    return;
                }
                if (secretEditText.getText().length() == 0) {
                    secretInputLayout.setError(getString(R.string.dialog_add_account_enter_key_too_short));
                    return;
                }

                String secret = secretEditText.getText().toString().replace('1', 'I').replace('0', 'O');
                try {
                    byte[] decoded = Base32String.decode(secret);
                    if (decoded.length < 10) {
                        // If the user is trying to submit a key that's too short, then
                        // display a message saying it's too short.
                        secretInputLayout.setError(getString(R.string.dialog_add_account_enter_key_too_short));
                        return;
                    } else {
                        secretInputLayout.setError(null);
                    }
                } catch (Base32String.DecodingException e) {
                    secretInputLayout.setError(getString(R.string.dialog_add_account_enter_key_illegal_char));
                    return;
                }

                String name = accountEditText.getText().toString();
                String issuer = issuerEditText.getText().toString();

                AccountSerializable accountSerializable = new AccountSerializable(null, secret, System.currentTimeMillis());
                AccountItem accountItem = new AccountItem(name, issuer, accountSerializable.getCodeGenerator(), accountSerializable.createdTime);

                File accountsDir = new File(getFilesDir(), "accounts");
                File accountFile = new File(accountsDir, (!Utils.isEmpty(issuer) ? (new Base32().encodeToString(issuer.getBytes()) + "/") : "") + new Base32().encodeToString(name.getBytes()));
                if (accountFile.exists()) {
                    Toast.makeText(context, R.string.toast_already_exist_same_name_account, Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    Utils.bytes2File(RSA.encrypt(SerialUtils.toByteArray(accountSerializable), RSA.loadPublicKey(Base64.decodeBase64(getSharedPreferences("xauth", MODE_PRIVATE).getString("publicKey", "")))), accountFile);
                    map.put(accountItem, accountFile);
                    list.add(accountItem);
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                    new AlertDialog.Builder(context).setTitle(R.string.dialog_failed_to_add_title).setMessage(getString(R.string.dialog_failed_to_add_message_wd, name, e)).show();
                }
            });
        });


        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (adapter.getCount() > 0)
                    runOnUiThread(adapter::notifyDataSetChanged);
            }
        }, 500, 500);
    }

    private void export(AccountItem accountItem) {
        SharedPreferences sharedPreferences = getSharedPreferences("xauth", MODE_PRIVATE);
        systemAuthenticator.purpose = SystemAuthenticator.Purpose.DECRYPT;
        systemAuthenticator.dialogCancelable = true;
        systemAuthenticator.dialogMessage = getString(R.string.dialog_authentication_by_system_message_with_suggestion_of_using_password);
        systemAuthenticator.keyName = KEY_NAME;

        systemAuthenticator.data = android.util.Base64.decode(sharedPreferences.getString("privateKey", ""), android.util.Base64.URL_SAFE);
        systemAuthenticator.iv = android.util.Base64.decode(sharedPreferences.getString("privateKeyIv", ""), android.util.Base64.URL_SAFE);

        systemAuthenticator.callback = (result, ivToStore) -> {
            PrivateKey privateKey;
            try {
                privateKey = RSA.loadPrivateKey(result);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activity, R.string.toast_failed_to_load_private_key, Toast.LENGTH_SHORT).show();
                return;
            }

            String uri;
            try {
                uri = Authenticator.getQrCodeText(((AccountSerializable) SerialUtils.fromByteArray(RSA.decrypt(Utils.toByteArray(map.get(accountItem)), privateKey))).getSecretKey(), accountItem.name, accountItem.issuer);
            } catch (Exception e) {
                e.printStackTrace();
                new AlertDialog.Builder(context).setTitle(R.string.dialog_failed_to_generate_title).setMessage(e.toString()).show();
                return;
            }
            Bitmap qrcode;
            try {
                qrcode = QRCodeCreator.createQRCode(uri, 1280, 1280);
            } catch (WriterException e) {
                e.printStackTrace();
                new AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_notice_title)
                        .setMessage(R.string.dialog_failed_to_generate_qrcode_message)
                        .setNegativeButton(R.string.dialog_button_close, null)
                        .setPositiveButton(R.string.dialog_failed_to_generate_qrcode_copy, null)
                        .show().getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                            Utils.copy(context, uri);
                            Toast.makeText(context, getText(R.string.toast_successfully_copied), Toast.LENGTH_SHORT).show();
                        });
                return;
            }

            ImageView imageView = new ImageView(context);
            imageView.setImageBitmap(qrcode);
            int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 21, getResources().getDisplayMetrics());
            imageView.setPadding(px, 0, px, 0);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.dialog_export_qrcode_title);
            builder.setView(imageView);
            builder.setCancelable(false);
            builder.setNegativeButton(R.string.dialog_button_close, null);
            builder.setPositiveButton(R.string.dialog_failed_to_generate_qrcode_copy, null);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);

            builder.setOnDismissListener(dialog -> getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));

            builder.show().getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                Utils.copy(context, uri);
                Toast.makeText(context, getText(R.string.toast_successfully_copied), Toast.LENGTH_SHORT).show();
            });
        };
        systemAuthenticator.start();
    }

    private static String validateAndGetNameInPath(String path) {
        if (path == null || !path.startsWith("/")) {
            return null;
        }
        // path is "/name", so remove leading "/", and trailing white spaces
        String name = path.substring(1).trim();
        if (name.length() == 0) {
            return null; // only white spaces.
        }
        return name;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_about_title)
                    .setMessage(R.string.dialog_about_message)
                    .setPositiveButton(R.string.dialog_about_visit_github_of_this_application, (dialog, which) -> goToWebsite("https://github.com/MrShieh-X/xauth"))
                    .setNegativeButton(R.string.dialog_about_visit_gitee_of_this_application, (dialog, which) -> goToWebsite("https://gitee.com/MrShiehX/xauth"))
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void goToWebsite(String url) {
        Intent intent = new Intent();
        intent.setData(Uri.parse(url));
        intent.setAction(Intent.ACTION_VIEW);
        startActivity(intent);
    }
}