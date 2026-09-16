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
    fun chromeDownloadProgressIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "AWAREX_0.2.3_SIGNED.apk: 22.44 MB / ?",
                "notification:com.android.chrome"
            )
        )
    }

    @Test
    fun chromeDownloadStatusIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "AWAREX_0.2.3_SIGNED.apk: Waiting for network...",
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
    fun snapchatUpdatingMessagesStatusIsRejected() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "Updating messages...",
                "notification:com.snapchat.android"
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
    fun weatherFeedIsRejectedFromEvidenceMemory() {
        assertFalse(
            EvidenceRelevancePolicy.shouldPersist(
                "23° in New Cairo City: Clear · See full forecast.",
                "notification:com.google.android.googlequicksearchbox"
            )
        )
    }

    @Test
    fun ordinaryWhatsappMessageStillSurvivesTheNoiseGate() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Ahmed: The revised quotation is attached",
                "notification:com.whatsapp"
            )
        )
    }

    @Test
    fun manualEvidenceIsNeverFilteredAsOperationalNoise() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Download complete for the tender package",
                "manual"
            )
        )
    }
}
