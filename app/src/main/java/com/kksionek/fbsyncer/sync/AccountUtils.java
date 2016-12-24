package com.kksionek.fbsyncer.sync;

import android.accounts.Account;

public class AccountUtils {
    public static final String ACCOUNT_NAME = "NAZWA";
    public static final String ACCOUNT_TYPE = "com.kksionek.fbsyncer";

    public static final String CONTENT_AUTHORITY = "com.kksionek.fbsyncer";

    public static Account getAccount() {
        return new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    }
}
