package com.rp.dedup.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocaleManagerTest {

    @Before
    fun setUp() {
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setApplicationLocales(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic(AppCompatDelegate::class)
    }

    @Test
    fun testLanguageOptionDataClass() {
        val option = LanguageOption("English", "en")
        assertEquals("English", option.name)
        assertEquals("en", option.code)

        val copy = option.copy(name = "Spanish", code = "es")
        assertEquals("Spanish", copy.name)
        assertEquals("es", copy.code)
    }

    @Test
    fun testGetSupportedLanguages() {
        val languages = LocaleManager.getSupportedLanguages()
        assertEquals(3, languages.size)
        assertEquals("system", languages[0].code)
        assertEquals("en", languages[1].code)
        assertEquals("hi", languages[2].code)
    }

    @Test
    fun testApplyLocaleSystem() {
        LocaleManager.applyLocale("system")
        verify { AppCompatDelegate.setApplicationLocales(
            withArg {
                assertTrue(it.isEmpty)
            }
        ) }
    }

    @Test
    fun testApplyLocaleEmpty() {
        LocaleManager.applyLocale("")
        verify { AppCompatDelegate.setApplicationLocales(
            withArg {
                assertTrue(it.isEmpty)
            }
        ) }
    }

    @Test
    fun testApplyLocaleSpecificLanguage() {
        LocaleManager.applyLocale("en")
        verify { AppCompatDelegate.setApplicationLocales(
            withArg {
                assertEquals("en", it.toLanguageTags())
            }
        ) }

        LocaleManager.applyLocale("hi")
        verify { AppCompatDelegate.setApplicationLocales(
            withArg {
                assertEquals("hi", it.toLanguageTags())
            }
        ) }
    }
}
