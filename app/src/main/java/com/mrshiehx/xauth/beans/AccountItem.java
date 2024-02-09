package com.mrshiehx.xauth.beans;

import com.mrshiehx.xauth.CodeGenerator;
import com.mrshiehx.xauth.utils.Utils;

import java.io.Serializable;

public class AccountItem implements Serializable {//无需serialVersionUID
    public final CodeGenerator codeGenerator;
    public final long createdTime;

    public String name;
    public String issuer;

    private transient String code;
    private transient int timeLeft;

    public AccountItem(String name, String issuer, CodeGenerator codeGenerator, long createdTime) {
        this.name = name;
        this.issuer = issuer;
        this.codeGenerator = codeGenerator;
        this.createdTime = createdTime;
    }

    /**
     * Returns the account name with the {@code "issuer:"} prefix removed (if present), stripped of
     * any leading or trailing whitespace
     */
    public String getStrippedName() {
        if (Utils.isEmpty(issuer) || !name.startsWith(issuer + ":")) {
            return name.trim();
        }
        return name.substring(issuer.length() + 1).trim();
    }

    public String getOrGenerateCode() {
        int oldTimeLeft = timeLeft;
        if (getTimeLeft() < oldTimeLeft && !Utils.isEmpty(code))
            return code;
        else
            return code = this.codeGenerator.generateCode();
    }

    public int getTimeLeft() {
        int currentTimeSeconds = (int) (System.currentTimeMillis() / 1000);
        int mod = currentTimeSeconds % 30;
        return timeLeft = (30 - mod);
    }
}
