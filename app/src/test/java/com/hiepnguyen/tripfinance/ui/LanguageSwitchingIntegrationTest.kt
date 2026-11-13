package com.hiepnguyen.tripfinance.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hiepnguyen.tripfinance.TripFinanceApp
import com.hiepnguyen.tripfinance.data.db.AppDatabase
import com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager
import com.hiepnguyen.tripfinance.data.security.InMemoryKeyProvider
import com.hiepnguyen.tripfinance.ui.theme.TripFinanceTheme
import com.hiepnguyen.tripfinance.ui.viewmodel.TripFinanceViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LanguageSwitchingIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: TripFinanceViewModel

    @Before
    fun setup() {
        DatabaseKeyManager.keyProvider = InMemoryKeyProvider()
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("trip_finance_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AppDatabase.resetDatabase(app)
        viewModel = TripFinanceViewModel(app)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppDatabase.resetDatabase(context)
        DatabaseKeyManager.resetKeyProvider()
    }

    @Test
    fun switchLanguageToEnglish_updatesCreateTripBackupAndUserGuideDialogs() {
        composeTestRule.setContent {
            TripFinanceTheme(darkTheme = false) {
                TripFinanceApp(viewModel = viewModel)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("toggle_language_top_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Starts in Vietnamese
        composeTestRule.onNodeWithText("🇻🇳 VI").assertIsDisplayed()

        // Toggle to English
        composeTestRule.onNodeWithTag("toggle_language_top_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("🇬🇧 EN").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 1. Open Create Trip Dialog via empty-state CTA button
        composeTestRule.onNode(hasText("Create New Business Trip") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Create New Trip / Group").assertExists()
        composeTestRule.onNodeWithText("VND (₫) - Vietnamese Dong").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Create Trip").assertExists()
        composeTestRule.onNodeWithText("Tạo Chuyến Đi / Đoàn Mới").assertDoesNotExist()
        composeTestRule.onNodeWithText("Việt Nam Đồng", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Mã đoàn", substring = true).assertDoesNotExist()

        // Create a trip so we also test when currentTrip != null
        composeTestRule.onNodeWithTag("create_trip_title_input").performScrollTo().performTextInput("Da Nang Trip")
        composeTestRule.onNodeWithTag("create_trip_admin_name_input").performScrollTo().performTextInput("Alice")
        composeTestRule.onNodeWithTag("submit_create_trip_button").performClick()
        composeTestRule.waitForIdle()

        // Wait for trip to appear on dashboard
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Da Nang Trip").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Re-open Create Trip Dialog from top menu while a trip is active
        composeTestRule.onNodeWithTag("switch_trip_top_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create new group...").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create New Trip / Group").assertExists()
        composeTestRule.onNodeWithText("Tạo Chuyến Đi / Đoàn Mới").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // 3. Open Backup & Recovery Dialog (and nested Create Backup Options dialog)
        composeTestRule.onNodeWithTag("backup_restore_top_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Backup & Recovery").assertExists()
        composeTestRule.onNodeWithText("Preserve data integrity across app updates").assertExists()
        composeTestRule.onNodeWithText("Create Backup").assertExists()
        composeTestRule.onNodeWithText("Export File").assertExists()
        composeTestRule.onNodeWithText("Import File").assertExists()
        composeTestRule.onNodeWithText("Sao Lưu & Phục Hồi").assertDoesNotExist()
        composeTestRule.onNodeWithText("Tạo sao lưu").assertDoesNotExist()

        composeTestRule.onNodeWithText("Create Backup").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create Backup Options").assertExists()
        composeTestRule.onNodeWithText("Password-protected encryption").assertExists()
        composeTestRule.onNodeWithText("Tùy Chọn Tạo Bản Sao Lưu").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()

        // 4. Open User Guide Screen (inside Dialog) and verify both UI and guide item content
        composeTestRule.onNodeWithTag("user_guide_top_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("User Guide & Help").assertExists()
        composeTestRule.onNodeWithText("Trip finance handbook").assertExists()
        composeTestRule.onNodeWithText("Welcome to TripFinance").assertExists()
        composeTestRule.onNodeWithText("Hướng Dẫn Sử Dụng").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cẩm nang quản lý tài chính đoàn").assertDoesNotExist()

        composeTestRule.onNodeWithTag("user_guide_list")
            .performScrollToNode(hasTestTag("guide_item_quick_start_flow"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1. 4-Step Group Finance Management Workflow").assertExists()
        composeTestRule.onNodeWithText("Step 1: Create a new trip on your device.").assertExists()
        composeTestRule.onNodeWithText("1. Quy trình 4 bước quản lý tài chính đoàn").assertDoesNotExist()
        composeTestRule.onNodeWithText("Bước 1:", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithTag("user_guide_list")
            .performScrollToNode(hasTestTag("guide_item_trip_create_join"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2. Create a Trip & Manage Member Roster").assertExists()
        composeTestRule.onNodeWithText("2. Tạo đoàn mới và Quản lý danh sách thành viên").assertDoesNotExist()

        // Also verify Privacy Policy dialog opened from User Guide is in English
        composeTestRule.onNodeWithTag("user_guide_list")
            .performScrollToNode(hasTestTag("privacy_policy_banner_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Read in App").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Privacy & Data Safety Policy").assertCountEquals(2)
        composeTestRule.onNodeWithText("1. Local-First Sandbox Architecture").assertExists()
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("user_guide_back_button").performClick()
        composeTestRule.waitForIdle()

        // 5. Open Export Report Dialog and verify Text report preview is English
        composeTestRule.onNodeWithTag("export_report_top_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Export Financial Report").assertExists()
        composeTestRule.onNodeWithTag("format_text_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("FINANCIAL SETTLEMENT & RECONCILIATION REPORT", substring = true).assertExists()
        composeTestRule.onNodeWithText("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()

        // 6. Toggle back to Vietnamese and verify modals return to Vietnamese
        composeTestRule.onNodeWithTag("toggle_language_top_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("🇻🇳 VI").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("backup_restore_top_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sao Lưu & Phục Hồi").assertExists()
        composeTestRule.onNodeWithText("Backup & Recovery").assertDoesNotExist()
        composeTestRule.onNodeWithText("Đóng").performClick()
        composeTestRule.waitForIdle()
    }
}
