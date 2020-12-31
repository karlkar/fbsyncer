package com.kksionek.photosyncer.dagger

import android.content.Context
import androidx.room.Room
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kksionek.photosyncer.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
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
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
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
}