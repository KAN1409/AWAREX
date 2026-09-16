package com.kareem.awarex.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceRelevancePolicyTest {
    @Test
    fun whatsappCommitmentIsKept() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Ahmed: I'll send the revised quotation tomorrow",
                "notification:com.whatsapp"
            )
        )
    }

    @Test
    fun ordinaryWhatsappMessageStillSurvivesThePassiveGate() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Ahmed: The revised quotation is attached",
                "notification:com.whatsapp"
            )
        )
    }

    @Test
    fun messengerDirectMessageCanStillBeEvidence() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Ahmed: I changed the meeting to 3 PM",
                "notification:com.facebook.orca"
            )
        )
    }

    @Test
    fun facebookDigestFromScreenshotIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Facebook: Waiting for you: 17 messages, 3 new notifications and 2 close friend updates",
                "notification:com.facebook.katana"
            )
        )
    }

    @Test
    fun snapchatStoryActivityFromScreenshotIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Hager Elsheikh: added 3 Snaps to their Story",
                "notification:com.snapchat.android"
            )
        )
    }

    @Test
    fun messengerChannelInviteFromScreenshotIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "El Tawaam: Invited you to their channel: التوأم",
                "notification:com.facebook.orca"
            )
        )
    }

    @Test
    fun birthdayDigestFromScreenshotIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Happy birthday to... Marian Naseif and Alena Kareem Abdel Nasser have birthdays today.",
                "notification:com.facebook.katana"
            )
        )
    }

    @Test
    fun sentYouASnapFromScreenshotIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "D kutb: sent you a Snap",
                "notification:com.snapchat.android"
            )
        )
    }

    @Test
    fun gmailNewsletterWithoutDurableSignalIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Sequence by Cosmos: Who approved that subway ad? Thinking underwear, an AI grandfather, and the comeback of the hand-painted sign.",
                "notification:com.google.android.gm"
            )
        )
    }

    @Test
    fun gmailWorkSignalIsKept() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Ahmed: Revised quotation attached - approval required tomorrow",
                "notification:com.google.android.gm"
            )
        )
    }

    @Test
    fun samsungWeatherFromScreenshotIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Today: النرجس المستثمرين الجنوبيه | Abundant sunshine. Highs 34 to 36C and lows 24 to 26C.",
                "notification:com.sec.android.daemonapp"
            )
        )
    }

    @Test
    fun snapchatRunningStatusIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Running...",
                "notification:com.snapchat.android"
            )
        )
    }

    @Test
    fun chatgptResponseReadyIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Response ready: Tap to return to ChatGPT to see your response",
                "notification:com.openai.chatgpt"
            )
        )
    }

    @Test
    fun chromeDownloadProgressIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "AWAREX_0.2.3_SIGNED.apk: 22.44 MB / ?",
                "notification:com.android.chrome"
            )
        )
    }

    @Test
    fun screenshotSavedNotificationIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Screenshot saved: Tap here to see your screenshot.",
                "notification:com.samsung.android.app.smartcapture"
            )
        )
    }

    @Test
    fun androidSystemNotificationSummaryIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "2 more notifications",
                "notification:com.android.systemui"
            )
        )
    }

    @Test
    fun unknownAppNeedsDurableSignalBeforeItBecomesEvidence() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Check out what is new today",
                "notification:com.example.app"
            )
        )
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Delivery date changed to tomorrow",
                "notification:com.example.app"
            )
        )
    }

    @Test
    fun arabicDurableSignalFromUnknownAppIsKept() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "أحمد: هبعت عرض السعر بكرة",
                "notification:com.example.app"
            )
        )
    }

    @Test
    fun manualEvidenceIsNeverFilteredAsNotificationNoise() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Happy birthday reminder for my son",
                "manual"
            )
        )
    }
}
