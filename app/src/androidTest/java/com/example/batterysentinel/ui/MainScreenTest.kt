package com.example.batterysentinel.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import com.example.batterysentinel.MainActivity
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    // Launches MainActivity to test the full Compose tree with real ViewModels
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAddAlarmFlow() {
        // Find and click the Floating Action Button
        composeTestRule.onNodeWithContentDescription("Add Alarm").performClick()
        
        // Wait for the popup / Dialog to appear
        composeTestRule.onNodeWithText("New Alarm", ignoreCase = true).assertIsDisplayed()
        
        // Find the percentage text field. Using substring to match "Threshold" or "Percent" depending on exactly how it's named in strings.xml
        composeTestRule.onNodeWithText("Threshold", substring = true, ignoreCase = true)
            .performTextClearance()
            
        composeTestRule.onNodeWithText("Threshold", substring = true, ignoreCase = true)
            .performTextInput("18")
            
        // Enter a custom message
        composeTestRule.onNodeWithText("Message", substring = true, ignoreCase = true)
            .performTextInput("Custom Test Message")
            
        // Click Save
        composeTestRule.onNodeWithText("Save", ignoreCase = true).performClick()
        
        // Verify that the new tile has been created on the screen displaying 18%
        composeTestRule.onNodeWithText("18%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Custom Test Message").assertIsDisplayed()
    }
}
