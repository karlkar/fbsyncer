package com.kksionek.photosyncer.view

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.model.SecurePreferences
import com.kksionek.photosyncer.sync.AccountUtils
import com.kksionek.photosyncer.sync.SyncAdapter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.ArrayList
import java.util.HashMap

class MainActivity : AppCompatActivity() {
    private lateinit var realmUi: Realm

    private val isSmartManagerInstalled: Boolean
        get() = try {
            packageManager.getPackageInfo("com.samsung.android.sm", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        realmUi = Realm.getDefaultInstance()

        showPermissionRequestScreen()
    }

    override fun onDestroy() {
        realmUi.close()
        super.onDestroy()
    }

    private fun showPermissionRequestScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            ).any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        ) {
            questionTextView.setText(R.string.activity_main_grant_contacts_access_message)
            questionButton.apply {
                setText(R.string.activity_main_button_grant_contacts_access)
                setOnClickListener {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS
                        ),
                        REQUEST_PERMISSIONS_CONTACTS
                    )
                }
            }
        } else {
            showFbLoginScreen()
        }
    }

    private fun showFbLoginScreen() {
        val prefs = SecurePreferences(baseContext, "tmp", "NotificationHandler", true)
        if (prefs.getString("PREF_LOGIN").isNullOrEmpty()
            && prefs.getString("PREF_PASSWORD").isNullOrEmpty()
        ) {
            questionTextView.setText(R.string.activity_main_facebook_permission_request_message)
            showProgress(false)
            questionButton.apply {
                setText(R.string.activity_main_button_login_facebook)
                setOnClickListener {
                    showProgress(true)
                    val login = fbLogin.text.toString()
                    val pass = fbPass.text.toString()

                    if (login.isNotEmpty() && pass.isNotEmpty()) {
                        prefs.put("PREF_LOGIN", login)
                        prefs.put("PREF_PASSWORD", pass)
                        SyncAdapter.fbLogin(
                            OkHttpClient.Builder()
                                .cookieJar(object : CookieJar {
                                    private val cookieStore = HashMap<String, List<Cookie>>()

                                    override fun saveFromResponse(
                                        url: HttpUrl,
                                        cookies: List<Cookie>
                                    ) {
                                        cookieStore[url.host] = cookies
                                    }

                                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                                        return cookieStore[url.host] ?: emptyList()
                                    }
                                })
                                .build(), baseContext
                        )
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                {
                                    loginProgress.visibility = View.GONE
                                    showWhitelistScreen()
                                },
                                {
                                    showProgress(false)
                                    prefs.clear()
                                    Toast.makeText(
                                        baseContext,
                                        R.string.activity_main_login_failed_toast,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                })
                    } else {
                        showProgress(false)
                        Toast.makeText(
                            baseContext,
                            R.string.activity_main_login_failed_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            showWhitelistScreen()
        }
    }

    private fun showProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        fbLoginForm.visibility = if (show) View.GONE else View.VISIBLE
        fbLoginForm.animate()
            .setDuration(shortAnimTime.toLong())
            .alpha((if (show) 0 else 1).toFloat())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fbLoginForm.visibility = if (show) View.GONE else View.VISIBLE
                }
            })

        loginProgress.visibility = if (show) View.VISIBLE else View.GONE
        loginProgress.animate()
            .setDuration(shortAnimTime.toLong())
            .alpha((if (show) 1 else 0).toFloat())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    loginProgress.visibility = if (show) View.VISIBLE else View.GONE
                }
            })
    }

    private fun showWhitelistScreen() {
        if (isSmartManagerInstalled) {
            questionTextView.setText(R.string.activity_main_whitelist_message)
            questionButton.apply {
                setText(R.string.activity_main_whitelist_btn)
                setOnClickListener {
                    val intent = Intent()
                    intent.component = ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                    try {
                        startActivity(intent)
                    } catch (ex: ActivityNotFoundException) {
                        Log.d(TAG, "showWhitelistScreen: Strange... Application wasn't detected...")
                    }

                    showSyncScreen()
                }
            }
        } else {
            showSyncScreen()
        }
    }

    private fun showSyncScreen() {
        questionTextView.setText(R.string.activity_main_prerequisites_fulfilled_message)
        questionButton.apply {
            setText(R.string.activity_main_button_sync)
            setOnClickListener {
                isEnabled = false
                if (!AccountUtils.isAccountCreated(baseContext)) {
                    val account = AccountUtils.createAccount(context)
                    if (account != null) {
                        ContentResolver.requestSync(
                            account,
                            AccountUtils.CONTENT_AUTHORITY,
                            Bundle()
                        )
                        val tabIntent = Intent(context, TabActivity::class.java)
                        startActivity(tabIntent)
                        finish()
                    } else {
                        Log.e(
                            TAG,
                            "showSyncScreen: I couldn't create a new account. That's strange"
                        )
                        finish()
                    }
                } else {
                    ContentResolver.requestSync(
                        AccountUtils.account,
                        AccountUtils.CONTENT_AUTHORITY,
                        Bundle()
                    )
                    val tabIntent = Intent(context, TabActivity::class.java)
                    startActivity(tabIntent)
                    finish()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_WHITELIST_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                showSyncScreen()
            } else {
                Toast.makeText(
                    this,
                    R.string.activity_main_whitelist_not_granted_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSIONS_CONTACTS) {
            if (grantResults.size == 2
                && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            ) {
                showFbLoginScreen()
            } else {
                Toast.makeText(
                    this,
                    R.string.activity_main_permission_rejected_message,
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    companion object {

        private const val REQUEST_PERMISSIONS_CONTACTS = 4444
        private const val REQUEST_WHITELIST_PERMISSION = 1616

        private const val TAG = "MainActivity"
    }
}
