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
    fun manualEvidenceIsNeverFilteredAsOperationalNoise() {
        assertTrue(
            EvidenceRelevancePolicy.shouldPersist(
                "Download complete for the tender package",
                "manual"
            )
        )
    }
}
