package com.mrshiehx.xauth.beans;

import com.mrshiehx.xauth.CodeGenerator;
import com.mrshiehx.xauth.auth.Authenticator;

import java.io.Serializable;

public class AccountSerializable implements Serializable {//无需serialVersionUID
    private final String originalUrl;
    private final String secretKey;
    private final CodeGenerator codeGenerator;
    public final long createdTime;

    public AccountSerializable(String originalUrl, String secretKey, long createdTime) {
        this.originalUrl = originalUrl;
        this.secretKey = secretKey;
        this.createdTime = createdTime;
        this.codeGenerator = () -> Authenticator.getCode(secretKey);
    }

    public CodeGenerator getCodeGenerator() {
        return codeGenerator;
    }

    public String getSecretKey() {
        return secretKey;
    }
}
