package com.mrshiehx.xauth;

import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.textfield.TextInputLayout;
import com.mrshiehx.xauth.beans.AccountItem;
import com.mrshiehx.xauth.utils.Utils;

import org.apache.commons.codec.binary.Base32;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public abstract class AccountsAdapter extends BaseAdapter {
    HashMap<AccountItem, File> map;
    List<AccountItem> list;
    LayoutInflater mInflater;
    Context context;

    public AccountsAdapter(Context context, HashMap<AccountItem, File> map, List<AccountItem> list) {
        super();
        mInflater = LayoutInflater.from(context);
        this.map = map;
        this.list = list;
        this.context = context;
    }

    public abstract void export(AccountItem accountItem);

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.item_account, null);

            holder.nameTextView = convertView.findViewById(R.id.name);
            holder.issuerTextView = convertView.findViewById(R.id.issuer);
            holder.codeLeftTextView = convertView.findViewById(R.id.code_left);
            holder.codeRightTextView = convertView.findViewById(R.id.code_right);
            holder.timeLeftTextView = convertView.findViewById(R.id.time_left);
            holder.moreImageButton = convertView.findViewById(R.id.more);

            // 再通过setTag的形式和view绑定
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        AccountItem account = list.get(position);
        holder.nameTextView.setText(account.getStrippedName());
        if (!Utils.isEmpty(account.issuer)) {
            holder.issuerTextView.setVisibility(View.VISIBLE);
            holder.issuerTextView.setText(account.issuer);
        } else {
            holder.issuerTextView.setVisibility(View.GONE);
            holder.issuerTextView.setText("");
        }
        String code = account.getOrGenerateCode();
        holder.codeLeftTextView.setText(code.substring(0, 3));
        holder.codeRightTextView.setText(code.substring(3, 6));
        holder.timeLeftTextView.setText(String.format("%ss", account.getTimeLeft()));
        holder.moreImageButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, v);
            popup.getMenuInflater().inflate(R.menu.menu_item_more, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) {
                    final View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_account, null);
                    TextInputLayout accountInputLayout = dialogView.findViewById(R.id.account_name_input_layout);
                    EditText accountEditText = dialogView.findViewById(R.id.account_name);
                    EditText issuerEditText = dialogView.findViewById(R.id.issuer);

                    TextInputLayout secretInputLayout = dialogView.findViewById(R.id.key_value_input_layout);

                    AlertDialog.Builder builder = new AlertDialog.Builder(context)
                            .setView(dialogView)
                            .setTitle(context.getString(R.string.dialog_edit_account_title))
                            .setNegativeButton(context.getString(android.R.string.cancel), null)
                            .setPositiveButton(context.getString(R.string.dialog_button_confirm), null);

                    accountInputLayout.setErrorEnabled(true);
                    secretInputLayout.setVisibility(View.GONE);

                    accountEditText.setText(account.name);
                    issuerEditText.setText(!Utils.isEmpty(account.issuer) ? account.issuer : "");

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
                        }
                    });

                    AlertDialog dialog = builder.show();
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v2 -> {
                        if (accountEditText.getText().length() == 0) {
                            accountInputLayout.setError(context.getString(R.string.dialog_add_account_account_empty));
                            return;
                        }

                        String oldName = account.name;
                        String oldIssuer = !Utils.isEmpty(account.issuer) ? account.issuer : "";
                        String name = accountEditText.getText().toString();
                        String issuer = issuerEditText.getText().toString();

                        if (Objects.equals(oldName, name) && Objects.equals(oldIssuer, issuer)) {
                            dialog.dismiss();
                            return;
                        }

                        File accountsDir = new File(context.getFilesDir(), "accounts");
                        File newAccountFile = new File(accountsDir, (!Utils.isEmpty(issuer) ? (new Base32().encodeToString(issuer.getBytes()) + "/") : "") + new Base32().encodeToString(name.getBytes()));

                        File old = map.get(account);
                        if (old != null) {
                            if (newAccountFile.exists()) {
                                Toast.makeText(context, R.string.toast_already_exist_same_name_account, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            newAccountFile.getParentFile().mkdirs();
                            if (!old.renameTo(newAccountFile)) {
                                Toast.makeText(context, R.string.toast_failed_to_edit, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            map.put(account, newAccountFile);
                            account.name = name;
                            account.issuer = issuer;
                            notifyDataSetChanged();
                        }
                        dialog.dismiss();
                    });
                    return true;
                } else if (id == R.id.action_export) {
                    export(account);
                    return true;
                } else if (id == R.id.action_delete) {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.dialog_notice_title)
                            .setMessage(String.format(context.getString(R.string.dialog_delete_message_wd), account.getStrippedName()))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.dialog_button_confirm, (dialog, which) -> {
                                File file = map.remove(account);
                                if (file != null)
                                    if (!file.delete()) {
                                        Toast.makeText(context, R.string.toast_failed_to_delete, Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                list.remove(account);
                                notifyDataSetChanged();
                            })
                            .show();
                    return true;
                }
                return false;
            });
            popup.show();
        });
        return convertView;
    }

    public static class ViewHolder {
        public TextView nameTextView;
        public TextView issuerTextView;
        public TextView codeLeftTextView;
        public TextView codeRightTextView;
        public TextView timeLeftTextView;
        public ImageButton moreImageButton;
    }
}