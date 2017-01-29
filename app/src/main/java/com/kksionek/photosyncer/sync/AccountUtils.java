package com.kksionek.photosyncer.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class AccountUtils {
    public static final String ACCOUNT_NAME = "Photo Syncer";
    public static final String ACCOUNT_TYPE = "com.kksionek.photosyncer";

    public static final String CONTENT_AUTHORITY = "com.kksionek.photosyncer";

    private static final String PREF_ACCOUNT_CREATED = "ACCOUNT_CREATED";

    public static Account getAccount() {
        return new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    }

    public static boolean isAccountCreated(Context ctx) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sharedPreferences.getBoolean("ACCOUNT_CREATED", false);
    }

    public static Account createAccount(Context ctx) {
        AccountManager systemService = (AccountManager) ctx.getSystemService(ctx.ACCOUNT_SERVICE);
        Account account = AccountUtils.getAccount();
        if (systemService.addAccountExplicitly(account, null, null)) {
            ContentResolver.setIsSyncable(account, AccountUtils.CONTENT_AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, AccountUtils.CONTENT_AUTHORITY, true);
            ContentResolver.addPeriodicSync(account, AccountUtils.CONTENT_AUTHORITY, new Bundle(),
                    24 * 60 * 60);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
            sharedPreferences.edit().putBoolean(PREF_ACCOUNT_CREATED, true).apply();
            return account;
        } else
            return null;
    }
}
