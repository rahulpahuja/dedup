package com.rp.dedup.util

import android.app.Application

/**
 * Lightweight test application used in Robolectric tests that need a real
 * ApplicationContext (e.g. for ContentResolver / MediaStore) but must NOT
 * trigger DeDupApp.onCreate() side-effects such as WorkManager enqueue,
 * Firebase init, or Facebook SDK init.
 */
class TestApp : Application()
