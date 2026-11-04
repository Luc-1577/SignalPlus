package com.example.ls

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    // MainScreen requests READ_PHONE_STATE/location permissions automatically on launch
    // (LifecycleStartEffect in MainScreen.kt). Without pre-granting them here, the system's
    // GrantPermissionsActivity pops up over MainActivity as soon as the test launches it,
    // so the Compose hierarchy under test is unmounted and ComposeTestRule can't find it
    // ("No compose hierarchies found"). Granting up front (order = 0, before the activity
    // is launched by composeRule) keeps MainActivity in the foreground.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        GrantPermissionRule.grant(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    } else {
        GrantPermissionRule.grant(Manifest.permission.READ_PHONE_STATE)
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun useAppContext() {
        // Context of the app under test.
        // NOTE: comparing against the literal "com.example.ls" is wrong for the debug
        // build type, which appends applicationIdSuffix = ".debug" (see app/build.gradle.kts),
        // making the real runtime package "com.example.ls.debug". Compare against
        // BuildConfig.APPLICATION_ID instead so this stays correct for any build variant.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun mainActivity_launchesAndShowsTitle() {
        // MainActivity must launch without crashing and the top bar wordmark
        // is rendered in every SignalUiState branch (Loading, PermissionRequired,
        // Success, Error) — see AppTopBar in MainScreen.kt. Wordmark's wrapping
        // Row uses Modifier.clearAndSetSemantics { contentDescription = "Signal+" }
        // (accessibility fix: TalkBack reads the full name, not just "Signal") —
        // that collapses the merged semantics tree to one node, so a text-based
        // lookup like onNodeWithText("Signal") only matches the unmerged tree and
        // fails here. Content-description is the stable, intended public surface.
        composeRule.onNodeWithContentDescription("Signal+").assertExists()
    }

    @Test
    fun bottomBarItem_doesNotDuplicateLabelAsIconDescription() {
        // Ícone da aba com contentDescription = null: o nó mesclado do item só
        // carrega o rótulo visível, então o TalkBack não lê "Histórico" duas vezes.
        val node = composeRule.onNodeWithText("Histórico").fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
    }
}
