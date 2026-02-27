package com.digia.digiaui.framework.widgets.timer

import com.digia.digiaexpr.callable.ExprInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Timer controller for countdown/countup operations with configurable intervals.
 *
 * This controller manages a timer that emits values at regular intervals, supporting both
 * countdown and countup modes. The timer can be started, paused, resumed, and reset.
 *
 * Usage:
 * ```kotlin
 * val timer = TimerController(
 *     initialValue = 60,
 *     updateInterval = 1000L, // 1 second
 *     isCountDown = true,
 *     duration = 60 // 60 ticks
 * )
 *
 * // Start the timer
 * timer.start()
 *
 * // Observe values in Composable
 * val currentValue by timer.stream.collectAsState()
 * Text("Time: $currentValue")
 *
 * // Control the timer
 * timer.pause()
 * timer.resume()
 * timer.reset()
 * ```
 *
 * @param initialValue The starting value of the timer
 * @param updateInterval The interval in milliseconds between timer updates
 * @param isCountDown Whether to count down (true) or count up (false)
 * @param duration The total number of ticks before the timer completes
 */
class TimerController(
    val initialValue: Int,
    val updateInterval: Long,
    val isCountDown: Boolean,
    val duration: Int
) : ExprInstance {

    private val _stream = MutableStateFlow(initialValue)
    private var _currentValue: Int = initialValue
    private var _isRunning = false
    private var _isPaused = false
    private var _timerJob: Job? = null
    private var _tickCount = 0

    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Stream of timer values that emits the current value at each interval.
     * Collect this in Composables to observe timer changes.
     */
    val stream: StateFlow<Int> = _stream.asStateFlow()

    /**
     * The current value of the timer.
     */
    val currentValue: Int
        get() = _currentValue

    /**
     * Whether the timer is currently running.
     */
    val isRunning: Boolean
        get() = _isRunning

    /**
     * Whether the timer is currently paused.
     */
    val isPaused: Boolean
        get() = _isPaused

    /**
     * Starts the timer. If the timer is already running, this does nothing.
     * The timer will emit values at the specified interval until the duration is reached.
     */
    fun start() {
        if (_isRunning && !_isPaused) return

        _isRunning = true
        _isPaused = false

        _timerJob = scope.launch {
            while (_isRunning && _tickCount <= duration) {
                // Wait for resume if paused
                while (_isPaused) {
                    delay(100)
                }

                // Calculate current value based on count direction
                _currentValue = if (isCountDown) {
                    initialValue - _tickCount
                } else {
                    initialValue + _tickCount
                }

                // Emit the value
                _stream.value = _currentValue

                // Increment tick count
                _tickCount++

                // Break if duration reached
                if (_tickCount > duration) {
                    _isRunning = false
                    break
                }

                // Wait for the next interval
                delay(updateInterval)
            }

            _isRunning = false
        }
    }

    /**
     * Resets the timer to its initial value and restarts it.
     * This cancels the current timer job and creates a new one.
     */
    fun reset() {
        _timerJob?.cancel()
        _currentValue = initialValue
        _tickCount = 0
        _isRunning = false
        _isPaused = false
        _stream.value = initialValue
        start()
    }

    /**
     * Pauses the timer. The timer will stop emitting values but retain its current state.
     * Call resume() to continue from where it was paused.
     */
    fun pause() {
        if (!_isRunning) return
        _isPaused = true
    }

    /**
     * Resumes a paused timer. If the timer is not paused, this does nothing.
     */
    fun resume() {
        if (!_isRunning || !_isPaused) return
        _isPaused = false
    }

    /**
     * Stops the timer completely. This cancels the timer job and resets the running state.
     * To restart, call start() again.
     */
    fun stop() {
        _timerJob?.cancel()
        _isRunning = false
        _isPaused = false
    }

    /**
     * Disposes of the timer controller, canceling any running timer job.
     * Call this when the controller is no longer needed to free resources.
     */
    fun dispose() {
        _timerJob?.cancel()
        _isRunning = false
        _isPaused = false
    }

    /**
     * Gets a field value for expression evaluation.
     * Supports accessing timer state in JSON expressions.
     *
     * Available fields:
     * - `currentValue` - The current timer value
     * - `isRunning` - Whether the timer is running
     * - `isPaused` - Whether the timer is paused
     * - `initialValue` - The starting value
     * - `duration` - The total duration in ticks
     */
    override fun getField(name: String): Any? {
        return when (name) {
            "currentValue" -> currentValue
            "isRunning" -> isRunning
            "isPaused" -> isPaused
            "initialValue" -> initialValue
            "duration" -> duration
            "isCountDown" -> isCountDown
            "updateInterval" -> updateInterval
            else -> null
        }
    }
}