package com.neeva.app.history

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neeva.app.BaseBrowserTest
import com.neeva.app.NeevaActivity
import com.neeva.app.R
import com.neeva.app.SkipFirstRunRule
import com.neeva.app.WebpageServingRule
import com.neeva.app.appnav.AppNavDestination
import com.neeva.app.clickOnNodeWithContentDescription
import com.neeva.app.clickOnNodeWithTag
import com.neeva.app.clickOnNodeWithText
import com.neeva.app.expectTabListState
import com.neeva.app.getString
import com.neeva.app.loadUrlInCurrentTab
import com.neeva.app.onBackPressed
import com.neeva.app.openCardGrid
import com.neeva.app.openOverflowMenuAndClickItem
import com.neeva.app.tapOnBrowserView
import com.neeva.app.typeIntoUrlBar
import com.neeva.app.waitFor
import com.neeva.app.waitForActivityStartup
import com.neeva.app.waitForAssertion
import com.neeva.app.waitForNavDestination
import com.neeva.app.waitForNodeWithTag
import com.neeva.app.waitForNodeWithText
import com.neeva.app.waitForTitle
import com.neeva.app.waitForUrl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

@RunWith(AndroidJUnit4::class)
class HistoryUITest : BaseBrowserTest() {
    private val testUrl = WebpageServingRule.urlFor("big_link_element.html")

    @get:Rule
    val skipFirstRunRule = SkipFirstRunRule()

    @get:Rule(order = 10000)
    val androidComposeRule = createAndroidComposeRule<NeevaActivity>()

    private fun visitMultipleSites() {
        androidComposeRule.apply {
            // Load the test webpage up in the existing tab.
            loadUrlInCurrentTab(testUrl)
            waitForTitle("Page 1")

            // Navigate a couple of times so that we can add entries into history.
            tapOnBrowserView()
            waitForUrl("$testUrl?page_index=2")
            waitForTitle("Page 2")

            tapOnBrowserView()
            waitForUrl("$testUrl?page_index=3")
            waitForTitle("Page 3")

            tapOnBrowserView()
            waitForUrl("$testUrl?page_index=4")
            waitForTitle("Page 4")

            activity.webLayerModel.currentBrowser.activeTabModel.apply {
                expectThat(navigationInfoFlow.value.canGoBackward).isTrue()
                expectThat(navigationInfoFlow.value.canGoForward).isFalse()
            }
            expectTabListState(isIncognito = false, regularTabCount = 1)
        }
    }

    @Before
    override fun setUp() {
        super.setUp()
        androidComposeRule.apply {
            activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitForActivityStartup()
            expectTabListState(isIncognito = false, regularTabCount = 1)
        }
    }

    @Test
    fun deleteItemFromHistory() {
        androidComposeRule.apply {
            visitMultipleSites()

            // Open up history.
            openOverflowMenuAndClickItem(R.string.history)
            val page1Node = waitForNodeWithText("Page 1").assertIsDisplayed()
            val page2Node = waitForNodeWithText("Page 2").assertIsDisplayed()
            val page3Node = waitForNodeWithText("Page 3").assertIsDisplayed()
            val page4Node = waitForNodeWithText("Page 4").assertIsDisplayed()

            // Delete some items from history.
            clickOnNodeWithContentDescription(
                activity.resources.getString(R.string.history_delete, "Page 1")
            )
            clickOnNodeWithContentDescription(
                activity.resources.getString(R.string.history_delete, "Page 3")
            )

            page2Node.assertIsDisplayed()
            page4Node.assertIsDisplayed()
            waitForNodeToDisappear(page1Node)
            waitForNodeToDisappear(page3Node)
        }
    }

    /**
     * Wait for the given node to disappear.  We have to check for both of these conditions because
     * it seems to be racy.
     */
    private fun waitForNodeToDisappear(node: SemanticsNodeInteraction) {
        androidComposeRule.apply {
            waitFor {
                val doesNotExist = try {
                    node.assertDoesNotExist()
                    true
                } catch (e: AssertionError) {
                    false
                }

                val isNotDisplayed = try {
                    node.assertIsNotDisplayed()
                    true
                } catch (e: AssertionError) {
                    false
                }
                doesNotExist || isNotDisplayed
            }
        }
    }

    @Test
    fun visitUrlFromHistory() {
        androidComposeRule.apply {
            visitMultipleSites()

            // Open up history.
            openOverflowMenuAndClickItem(R.string.history)
            waitForNodeWithText("Page 1").assertIsDisplayed()
            waitForNodeWithText("Page 2").assertIsDisplayed()
            waitForNodeWithText("Page 3").assertIsDisplayed()
            waitForNodeWithText("Page 4").assertIsDisplayed()

            // Click on one of the items in history.  It should create a new tab for that URL.
            waitForNodeWithText("Page 1").performClick()
            waitForUrl(testUrl)
            waitForTitle("Page 1")
            activity.webLayerModel.currentBrowser.activeTabModel.apply {
                expectThat(navigationInfoFlow.value.canGoBackward).isFalse()
                expectThat(navigationInfoFlow.value.canGoForward).isFalse()
            }
            expectTabListState(isIncognito = false, regularTabCount = 2)

            openCardGrid(false)

            // The tab should be selected in the card grid.
            waitForAssertion {
                // If we don't use an unmerged tree, then the containers all get collapsed and
                // we can't find the selected tag.
                onAllNodesWithTag("TabCard", useUnmergedTree = true)
                    .filter(hasAnyDescendant(hasTestTag("SelectedTabCard")))
                    .filter(hasAnyDescendant(hasText("Page 1")))
                    .assertCountEquals(1)
                    .onFirst()
                    .assertIsDisplayed()
            }

            waitForAssertion {
                onAllNodesWithTag("TabCard", useUnmergedTree = true)
                    .filter(hasAnyDescendant(hasText("Page 4")))
                    .assertCountEquals(1)
                    .onFirst()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun clearAllHistory() {
        androidComposeRule.apply {
            visitMultipleSites()

            // Open up history.
            openOverflowMenuAndClickItem(R.string.history)
            waitForNodeWithText("Page 1").assertIsDisplayed()
            waitForNodeWithText("Page 2").assertIsDisplayed()
            waitForNodeWithText("Page 3").assertIsDisplayed()
            waitForNodeWithText("Page 4").assertIsDisplayed()

            // Clear the user's history.
            clickOnNodeWithText(getString(R.string.settings_clear_browsing_data))
            waitForNavDestination(AppNavDestination.CLEAR_BROWSING_SETTINGS)
            clickOnNodeWithText(getString(R.string.settings_clear_selected_data_on_device))
            clickOnNodeWithText(getString(R.string.clear_browsing_everything))
            clickOnNodeWithText(getString(R.string.clear_browsing_clear_data))

            // Go back to history.
            onBackPressed()
            waitForNavDestination(AppNavDestination.HISTORY)
            waitForAssertion { onNodeWithText("Page 1").assertDoesNotExist() }
            waitForAssertion { onNodeWithText("Page 2").assertDoesNotExist() }
            waitForAssertion { onNodeWithText("Page 3").assertDoesNotExist() }
            waitForAssertion { onNodeWithText("Page 4").assertDoesNotExist() }

            // Go back to the browser.
            onBackPressed()
            waitForNavDestination(AppNavDestination.BROWSER)
        }
    }

    @Test
    fun clearAllHistoryRemovesSuggestions() {
        androidComposeRule.apply {
            visitMultipleSites()

            // Confirm that all the visited sites show up as suggestions when we type into the URL
            // bar.  Because the keyboard is visible, and because SuggestionPane is a LazyColumn, we
            // can't assert that all the nodes are in the Composition and resort to just checking
            // for a few.
            clickOnNodeWithTag("LocationLabel")
            typeIntoUrlBar("Page")
            val suggestionListNode = waitForNodeWithTag("SuggestionList").assertIsDisplayed()
            waitForNodeWithText(getString(R.string.history)).assertIsDisplayed()
            waitForNodeWithText("Page 1").assertExists()
            waitForNodeWithText("Page 2").assertExists()

            // Open up the history UI.
            onBackPressed()
            waitForNodeToDisappear(suggestionListNode)
            openOverflowMenuAndClickItem(R.string.history)
            waitForNavDestination(AppNavDestination.HISTORY)

            // Clear the user's history.
            clickOnNodeWithText(getString(R.string.settings_clear_browsing_data))
            waitForNavDestination(AppNavDestination.CLEAR_BROWSING_SETTINGS)
            clickOnNodeWithText(getString(R.string.settings_clear_selected_data_on_device))
            clickOnNodeWithText(getString(R.string.clear_browsing_everything))
            clickOnNodeWithText(getString(R.string.clear_browsing_clear_data))

            // Go back to the browser.
            onBackPressed()
            waitForNavDestination(AppNavDestination.HISTORY)
            onBackPressed()
            waitForNavDestination(AppNavDestination.BROWSER)

            // Because history is cleared, there are no suggestions to show the user and they should
            // be stuck on the Zero Query page.
            clickOnNodeWithTag("LocationLabel")
            typeIntoUrlBar("Page")
            onNodeWithTag("SuggestionList").assertDoesNotExist()
            onNodeWithText(getString(R.string.history)).assertDoesNotExist()
            waitForNodeWithText(getString(R.string.suggested_sites)).assertIsDisplayed()
        }
    }
}
