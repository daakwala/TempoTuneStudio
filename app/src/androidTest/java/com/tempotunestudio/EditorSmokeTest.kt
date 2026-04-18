package com.tempotunestudio

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests that verify the main UI is at least visible and not crashing.
 * These run on a real device/emulator in CI.
 */
@RunWith(AndroidJUnit4::class)
class EditorSmokeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun mainActivityLaunches() {
        // If the activity crashes on launch this test fails — baseline sanity check
        onView(withId(android.R.id.content)).check(matches(isDisplayed()))
    }
}
