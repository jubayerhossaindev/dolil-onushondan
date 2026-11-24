package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from default and Bengali resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Dolil Onusondhan", context.getString(R.string.app_name))

        val bnConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("bn"))
        }
        val bnContext = context.createConfigurationContext(bnConfig)
        assertEquals("দলিল অনুসন্ধান", bnContext.getString(R.string.app_name))
    }
}
