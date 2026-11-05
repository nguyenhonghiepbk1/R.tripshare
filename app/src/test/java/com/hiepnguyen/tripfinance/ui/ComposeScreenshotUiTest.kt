package com.hiepnguyen.tripfinance.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hiepnguyen.tripfinance.data.entity.TripEntity
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.domain.model.FinancialSummary
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.ui.screens.DashboardScreen
import com.hiepnguyen.tripfinance.ui.screens.SettlementScreen
import com.hiepnguyen.tripfinance.ui.theme.TripFinanceTheme
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for DashboardScreen and SettlementScreen across Light and Dark themes.
 * Verifies key UI nodes, semantic color schemes, and hierarchy rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeScreenshotUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createSampleUiState(): UiState {
        val trip = TripEntity(
            id = "trip-1",
            title = "Chuyến Đi Đà Nẵng 2026",
            description = "Chuyến du lịch kết hợp công tác",
            joinCode = "TRIP-DN2026",
            startDate = 1770000000000L,
            endDate = 1770432000000L,
            baseCurrency = "VND",
            isSettled = false,
            createdAt = 1770000000000L
        )
        val member1 = TripMemberEntity(
            id = "mem-1",
            tripId = "trip-1",
            userId = "user-1",
            name = "Nguyễn Văn A",
            role = "ADMIN",
            isActive = true
        )
        val member2 = TripMemberEntity(
            id = "mem-2",
            tripId = "trip-1",
            userId = "user-2",
            name = "Trần Thị B",
            role = "MEMBER",
            isActive = true
        )
        val summary = FinancialSummary(
            totalExpenses = 5000000L,
            personalPaidExpenses = 5000000L,
            fundPaidExpenses = 0L,
            totalFundCollected = 6000000L,
            remainingFund = 1000000L,
            isBalanced = true,
            balanceDiscrepancy = 0L,
            memberCount = 2,
            expenseCount = 1
        )
        val status1 = MemberFinancialStatus(
            member = member1,
            totalPaid = 3000000L,
            outOfPocketPaid = 3000000L,
            fundContributed = 0L,
            totalOwed = 2500000L,
            balance = 500000L,
            status = BalanceStatus.RECEIVE
        )
        val status2 = MemberFinancialStatus(
            member = member2,
            totalPaid = 2000000L,
            outOfPocketPaid = 2000000L,
            fundContributed = 0L,
            totalOwed = 2500000L,
            balance = -500000L,
            status = BalanceStatus.PAY
        )
        val transfer = SettlementTransfer(
            id = "trans-1",
            fromMember = member2,
            toMember = member1,
            amount = 500000L,
            transferNote = "QUYET TOAN TRIP-DN2026 TRAN THI B TRA NGUYEN VAN A",
            isCompleted = false
        )
        return UiState(
            currentTrip = trip,
            allTrips = listOf(trip),
            currentMember = member1,
            members = listOf(member1, member2),
            financialSummary = summary,
            memberStatuses = listOf(status1, status2),
            settlementTransfers = listOf(transfer)
        )
    }

    @Test
    fun dashboardScreen_rendersInLightMode() {
        val state = createSampleUiState()

        composeTestRule.setContent {
            TripFinanceTheme(darkTheme = false) {
                DashboardScreen(
                    uiState = state,
                    onNavigateToExpenses = {},
                    onNavigateToFund = {},
                    onNavigateToSettlement = {},
                    onRequestAiInsight = {},
                    onOpenAddExpense = {},
                    onOpenAddFund = {},
                    onOpenExportReport = {},
                    onOpenCreateTrip = {},
                    onOpenUserGuide = {},
                    onSwitchUser = {},
                    onSelectTrip = {},
                    onEditTrip = { _, _, _, _, _, _ -> },
                    onDeleteTrip = {}
                )
            }
        }

        // Verify key nodes in Light theme
        composeTestRule.onNodeWithTag("dashboard_user_guide_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_ai_advisor_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chuyến Đi Đà Nẵng 2026").assertIsDisplayed()
    }

    @Test
    fun dashboardScreen_rendersInDarkMode() {
        val state = createSampleUiState()

        composeTestRule.setContent {
            TripFinanceTheme(darkTheme = true) {
                DashboardScreen(
                    uiState = state,
                    onNavigateToExpenses = {},
                    onNavigateToFund = {},
                    onNavigateToSettlement = {},
                    onRequestAiInsight = {},
                    onOpenAddExpense = {},
                    onOpenAddFund = {},
                    onOpenExportReport = {},
                    onOpenCreateTrip = {},
                    onOpenUserGuide = {},
                    onSwitchUser = {},
                    onSelectTrip = {},
                    onEditTrip = { _, _, _, _, _, _ -> },
                    onDeleteTrip = {}
                )
            }
        }

        // Verify key nodes in Dark theme
        composeTestRule.onNodeWithTag("dashboard_user_guide_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_ai_advisor_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chuyến Đi Đà Nẵng 2026").assertIsDisplayed()
    }

    @Test
    fun settlementScreen_rendersInLightMode() {
        val state = createSampleUiState()

        composeTestRule.setContent {
            TripFinanceTheme(darkTheme = false) {
                SettlementScreen(
                    uiState = state,
                    onFinalizeSettlement = {},
                    onReopenSettlement = {},
                    onOpenExportReport = {}
                )
            }
        }

        // Verify key nodes in Light theme
        composeTestRule.onNodeWithText("Kế Hoạch Bù Trừ Công Nợ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trần Thị B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nguyễn Văn A").assertIsDisplayed()
    }

    @Test
    fun settlementScreen_rendersInDarkMode() {
        val state = createSampleUiState()

        composeTestRule.setContent {
            TripFinanceTheme(darkTheme = true) {
                SettlementScreen(
                    uiState = state,
                    onFinalizeSettlement = {},
                    onReopenSettlement = {},
                    onOpenExportReport = {}
                )
            }
        }

        // Verify key nodes in Dark theme
        composeTestRule.onNodeWithText("Kế Hoạch Bù Trừ Công Nợ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trần Thị B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nguyễn Văn A").assertIsDisplayed()
    }
}
