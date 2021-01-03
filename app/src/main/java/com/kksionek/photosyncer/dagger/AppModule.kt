package com.kksionek.photosyncer.dagger

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kksionek.photosyncer.gateway.AcceptLanguageInterceptor
import com.kksionek.photosyncer.gateway.FacebookEndpoint
import com.kksionek.photosyncer.gateway.UserAgentInterceptor
import com.kksionek.photosyncer.repository.*
import com.kksionek.photosyncer.sync.WorkManagerController
import com.kksionek.photosyncer.sync.WorkManagerControllerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.*

@InstallIn(ApplicationComponent::class)
@Module
class AppModule {

    @Provides
    fun provideSecureStorage(@ApplicationContext appContext: Context): SecureStorage =
        JetpackSecureStorage(appContext)

    @Provides
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics =
        FirebaseCrashlytics.getInstance()

    @Provides
    fun provideUserAgentInterceptor(): UserAgentInterceptor =
        UserAgentInterceptor("Mozilla/5.0 (Linux; U; Android 2.3.6; en-us; Nexus S Build/GRK39F) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1")

    @Provides
    fun provideAcceptLanguageInterceptor(): AcceptLanguageInterceptor =
        AcceptLanguageInterceptor()

    @Provides
    fun provideOkHttpClient(
        userAgentInterceptor: UserAgentInterceptor,
        acceptLanguageInterceptor: AcceptLanguageInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(acceptLanguageInterceptor)
            .addInterceptor(loggingInterceptor)
            .cookieJar(object : CookieJar {
                private val cookieStore = HashMap<String, List<Cookie>>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    cookieStore[url.host].orEmpty()
            })
            .build()
    }

    @Provides
    fun provideRoomDatabase(@ApplicationContext appContext: Context): MappingDatabase =
        Room.databaseBuilder(appContext, MappingDatabase::class.java, "mappingDatabase").build()

    @Provides
    fun provideContactDao(database: MappingDatabase): ContactDao =
        database.contactDao()

    @Provides
    fun provideFriendDao(database: MappingDatabase): FriendDao =
        database.friendDao()

    @Provides
    fun provideContactsRepository(contactsRepositoryImpl: ContactsRepositoryImpl): ContactsRepository =
        contactsRepositoryImpl

    @Provides
    fun provideFriendRepository(friendRepositoryImpl: FriendRepositoryImpl): FriendRepository =
        friendRepositoryImpl

    @Provides
    fun provideWorkManagerController(workManagerControllerImpl: WorkManagerControllerImpl): WorkManagerController =
        workManagerControllerImpl

    @Provides
    fun provideSharedPreferences(@ApplicationContext appContext: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(appContext)

    @Provides
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("https://m.facebook.com")
            .build()
    }

    @Provides
    fun provideFacebookEndpoint(retrofit: Retrofit): FacebookEndpoint {
        return retrofit.create(FacebookEndpoint::class.java)
    }
}