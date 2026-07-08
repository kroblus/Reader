package com.lightreader.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lightreader.app.core.model.AppLanguage
import java.util.Locale

data class UiText(@param:StringRes val resId: Int, val args: List<Any> = emptyList())

fun uiText(@StringRes resId: Int, vararg args: Any) = UiText(resId, args.toList())

@Composable
fun UiText.asString(): String = stringResource(resId, *args.toTypedArray())

@Composable
fun LocalizedApp(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        baseContext.localizedContext(language)
    }
    val localizedConfiguration = remember(localizedContext) {
        Configuration(localizedContext.resources.configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        content()
    }
}

private fun Context.localizedContext(language: AppLanguage): Context {
    if (language == AppLanguage.SYSTEM) return this
    val locale = when (language) {
        AppLanguage.ZH_CN -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.EN -> Locale.ENGLISH
        AppLanguage.SYSTEM -> Locale.getDefault()
    }
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(locale))
    }
    return createConfigurationContext(configuration)
}
