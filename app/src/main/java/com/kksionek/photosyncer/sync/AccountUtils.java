package com.kksionek.photosyncer.sync;

import android.accounts.Account;

public class AccountUtils {
    public static final String ACCOUNT_NAME = "NAZWA";
    public static final String ACCOUNT_TYPE = "com.kksionek.photosyncer";

    public static final String CONTENT_AUTHORITY = "com.kksionek.photosyncer";

    public static Account getAccount() {
        return new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    }
}
