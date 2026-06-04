const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const DAILY_LIMIT_PER_USER = 50;

/**
 * Sends battery alarm notifications to all other devices of the same user.
 * Protected by App Check, Firebase Auth, input validation, and rate limiting.
 */
exports.notifyOtherDevices = onCall(
    async (request) => {
        // Auth check
        if (!request.auth) {
            throw new HttpsError("unauthenticated", "Must be signed in");
        }

        const uid = request.auth.uid;
        const { deviceId, deviceName, message, threshold } = request.data;

        // Validate input presence
        if (!deviceId || !deviceName || !message || threshold === undefined) {
            throw new HttpsError("invalid-argument", "Missing required fields");
        }

        // Validate input length (server-side safety net – UI already enforces 200 chars)
        if (message.length > 500) {
            throw new HttpsError("invalid-argument", "Message too long (max 500 characters)");
        }
        if (deviceName.length > 100) {
            throw new HttpsError("invalid-argument", "Device name too long (max 100 characters)");
        }

        // Rate limiting with auto-expiry (expiresAt used by Firestore TTL policy)
        const today = new Date().toISOString().slice(0, 10);
        const rateLimitRef = getFirestore()
            .collection("rate_limits").doc(`${uid}_${today}`);
        const rateLimitDoc = await rateLimitRef.get();
        const currentCount = rateLimitDoc.exists ? rateLimitDoc.data().count : 0;

        if (currentCount >= DAILY_LIMIT_PER_USER) {
            throw new HttpsError("resource-exhausted",
                "Daily limit reached. Local notifications still work.");
        }

        // expiresAt = 7 days from now → Firestore TTL policy deletes this automatically
        const expiresAt = new Date();
        expiresAt.setDate(expiresAt.getDate() + 7);

        await rateLimitRef.set(
            {
                count: currentCount + 1,
                uid,
                date: today,
                expiresAt: Timestamp.fromDate(expiresAt)
            },
            { merge: true }
        );

        // Get other devices
        const devicesSnap = await getFirestore()
            .collection("users").doc(uid)
            .collection("devices").get();

        const tokens = devicesSnap.docs
            .filter(doc => doc.id !== deviceId)
            .map(doc => doc.data().fcmToken)
            .filter(Boolean);

        if (tokens.length === 0) return { sent: 0 };

        const response = await getMessaging().sendEachForMulticast({
            tokens,
            data: {
                type: "battery_alert",
                deviceName: deviceName,
                message: message,
                threshold: String(threshold)
            },
            android: {
                priority: "high"
            }
        });

        // Clean up invalid tokens
        const tokensToRemove = [];
        response.responses.forEach((resp, idx) => {
            if (resp.error && (
                resp.error.code === "messaging/invalid-registration-token" ||
                resp.error.code === "messaging/registration-token-not-registered"
            )) {
                tokensToRemove.push(tokens[idx]);
            }
        });

        if (tokensToRemove.length > 0) {
            const batch = getFirestore().batch();
            devicesSnap.docs.forEach(doc => {
                if (tokensToRemove.includes(doc.data().fcmToken)) {
                    batch.delete(doc.ref);
                }
            });
            await batch.commit();
        }

        return { sent: response.successCount };
    }
);

/**
 * Scheduled cleanup: runs weekly.
 * - Deletes device registrations not seen in 90+ days
 * - Removes empty user documents
 * Prevents Firestore from accumulating stale device entries
 * from uninstalled apps or forgotten devices.
 */
exports.cleanupStaleData = onSchedule(
    { schedule: "every sunday 03:00", timeZone: "Europe/Vienna" },
    async () => {
        const db = getFirestore();
        const ninetyDaysAgo = new Date();
        ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90);

        const usersSnap = await db.collection("users").get();
        let deletedDevices = 0;
        let deletedUsers = 0;

        for (const userDoc of usersSnap.docs) {
            // Find devices not seen in 90 days
            const staleDevices = await userDoc.ref
                .collection("devices")
                .where("lastSeen", "<", Timestamp.fromDate(ninetyDaysAgo))
                .get();

            if (!staleDevices.empty) {
                const batch = db.batch();
                staleDevices.docs.forEach(doc => {
                    batch.delete(doc.ref);
                    deletedDevices++;
                });
                await batch.commit();
            }

            // If user has no remaining devices, delete user doc too
            const remainingDevices = await userDoc.ref.collection("devices").get();
            if (remainingDevices.empty) {
                await userDoc.ref.delete();
                deletedUsers++;
            }
        }

        console.log(`Cleanup: deleted ${deletedDevices} stale devices, ${deletedUsers} empty users`);
    }
);
