package com.lightreader.app

import android.app.Application

class ReaderApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
