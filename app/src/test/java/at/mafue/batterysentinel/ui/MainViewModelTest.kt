package at.mafue.batterysentinel.ui

import android.app.Application
import at.mafue.batterysentinel.data.BatteryAlarm
import at.mafue.batterysentinel.data.BatteryPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockAlarmsFlow: MutableStateFlow<List<BatteryAlarm>>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockApplication = mockk(relaxed = true)
        
        // Use MockK's constructor mocking to intercept the creation of BatteryPreferences
        // right inside the MainViewModel's member initialization.
        mockkConstructor(BatteryPreferences::class)
        
        // Mock the internal Flow that MainViewModel heavily relies on
        mockAlarmsFlow = MutableStateFlow(emptyList())
        every { anyConstructed<BatteryPreferences>().alarmsFlow } returns mockAlarmsFlow
        
        // Mock the initial setup call and save calls so they don't crash
        coEvery { anyConstructed<BatteryPreferences>().initializeDefaultsIfNeeded() } returns Unit
        coEvery { anyConstructed<BatteryPreferences>().saveAlarms(any()) } answers {
            // Emulate saving by pushing the new list to our mock flow
            val newList = firstArg<List<BatteryAlarm>>()
            mockAlarmsFlow.value = newList
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test adding a new alarm updates the flow`() = runTest {
        // Arrange
        val viewModel = MainViewModel(mockApplication)
        
        // StateFlows with WhileSubscribed need an active collector in tests
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.alarmsFlow.collect {}
        }
        
        testScheduler.advanceUntilIdle() // Process the init block coroutines
        
        // Act
        viewModel.addAlarm(15, "Battery very low!")
        testScheduler.advanceUntilIdle() // Process the saveAlarms coroutine
        
        // Assert
        val currentAlarms = viewModel.alarmsFlow.value
        assertEquals(1, currentAlarms.size)
        assertEquals(15, currentAlarms[0].thresholdPercent)
        assertEquals("Battery very low!", currentAlarms[0].message)
        
        collectJob.cancel()
    }

    @Test
    fun `test removing an alarm`() = runTest {
        // Arrange
        val testAlarm = BatteryAlarm("ID_123", 25, "Test msg", true)
        mockAlarmsFlow.value = listOf(testAlarm)
        
        val viewModel = MainViewModel(mockApplication)
        
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.alarmsFlow.collect {}
        }
        
        testScheduler.advanceUntilIdle() 
        
        // Ensure starting state is correct
        assertEquals(1, viewModel.alarmsFlow.value.size)

        // Act
        viewModel.removeAlarm("ID_123")
        testScheduler.advanceUntilIdle()
        
        // Assert
        assertEquals(0, viewModel.alarmsFlow.value.size)
        
        collectJob.cancel()
    }
}
