# BatterySentinel Architecture

BatterySentinel is designed to solve a fundamental Android problem: how to reliably monitor battery levels in the background without draining the battery itself.

## 1. Background Execution Strategy

Modern Android versions aggressively kill background processes to save battery (especially on some OEM devices like Samsung, Xiaomi, etc.). BatterySentinel uses a three-tier defensive architecture to ensure alerts fire reliably.

![Background Execution Strategy](docs/diagrams/architecture_background.png)

### Tier 1: Dynamic Broadcast Receiver (Instant)
When the app process is alive in memory, a dynamic `BroadcastReceiver` listens to `ACTION_BATTERY_CHANGED`. This intent is "sticky" and fired by the Android OS itself on every percentage change.
- **Pros:** Instant reaction, virtually zero battery cost (the OS sends it anyway).
- **Cons:** If the OS kills the app process to free up RAM, the receiver dies.

### Tier 2: WorkManager Safety Net (30 Min)
To counteract process death, a `PeriodicWorkRequest` runs every 30 minutes. It wakes up the app, reads the current battery state directly (by registering a null receiver for the sticky intent) and processes it against your alarms.
- **Pros:** Survives app restarts and aggressive process kills. Managed safely by Android's job scheduler.
- **Cons:** Only fires every 30 minutes. Vulnerable to Android's "Doze Mode" which deliberately delays background work on inactive devices (e.g. a tablet sitting on a table overnight).

### Tier 3: Adaptive Doze Detection & AlarmManager (Doze Override)
If a device enters deep Doze Mode, WorkManager runs can be delayed by hours. BatterySentinel's `BatteryWorker` contains **Adaptive Doze Detection**:
- The worker compares the time since its last run. If the gap exceeds 45 minutes (for a 30-min interval), it detects that Doze Mode is delaying it.
- To prevent false positives after a device reboot (where the device was simply turned off), it also checks the device uptime.
- If Doze is detected, it activates an `AlarmManager` with `setAndAllowWhileIdle()`. This specific API overrides Doze Mode constraints.
- The `AlarmReceiver` fires every 30 minutes, checks the battery, and reschedules itself.
- Once the worker starts running on schedule again (device woke up, Doze ended), it intelligently deactivates the AlarmManager to resume maximum power saving.

## 2. Multi-Device Synchronization

BatterySentinel can send battery warnings to your other devices (e.g., warning your phone that your tablet is at 20%).

![Multi-Device Synchronization](docs/diagrams/architecture_multidevice.png)

### Data Flow
1. **Local Trigger:** The local architecture (Tiers 1-3) detects a threshold and immediately fires a local notification.
2. **State Preservation:** The "triggered" state is saved to `DataStore` synchronously. This ensures that if the process is killed during the subsequent network call, the alarm won't falsely trigger again later.
3. **Cloud Function:** A Firebase Cloud Function (`notifyOtherDevices`) is called. It uses Google Sign-In for strict user authentication.
4. **Push Notification:** The Cloud Function securely routes a high-priority FCM (Firebase Cloud Messaging) data message to all other registered devices owned by the same user.
5. **Remote Display:** The `FcmService` on receiving devices wakes up, formats the message, and displays a local notification indicating which device triggered the alarm.

### Privacy & Infrastructure
- Only the device name, current battery level, and custom message are transmitted.
- Authentication is handled entirely by Google (no custom passwords required).
- **Precompiled Version:** If you use the precompiled APK provided in the official releases, all Firebase Cloud Functions and Firestore databases are hosted exclusively on servers located within the **European Union (EU)** to ensure strict data privacy standards.

## 3. Diagnostic Logging System

BatterySentinel features an integrated, persistent text-based logging system (`events.log`) to provide transparency into background behavior without adding significant power consumption.
- **Append-Only Operations:** The `EventLogger` writes logs to local app storage using an IO coroutine dispatcher, preventing main-thread blocking.
- **Auto-Maintenance:** The logger continuously limits the log to 1000 entries (discarding older entries via a robust FIFO approach) to ensure storage and memory usage remains completely negligible over time.
- **Corruption Resilience:** If the file system experiences write locks or unexpected damage preventing normal parsing, the logger detects this and will proactively sanitize the offending line or recreate the file to guarantee stability.
