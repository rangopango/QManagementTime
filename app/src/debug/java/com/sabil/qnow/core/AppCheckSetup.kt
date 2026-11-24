package com.sabil.qnow.core

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Test builds: a debug token (registered for our test devices) passes App Check. */
object AppCheckSetup {
    fun install() = Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
