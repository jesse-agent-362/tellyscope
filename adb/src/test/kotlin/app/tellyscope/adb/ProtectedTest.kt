// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtectedTest {
    @Test fun coreRuleWithSpareProviders() {
        assertEquals(ProtectedReason.SYSTEM, Protected.reason("com.google.android.permissioncontroller"))
        assertEquals(ProtectedReason.SYSTEM, Protected.reason("com.android.providers.tv"))
        assertEquals(ProtectedReason.SYSTEM, Protected.reason("com.google.android.overlay.modules.ext.services"))
        assertNull(Protected.reason("com.android.providers.calendar"))
    }

    /** Package names from an Android TV 11 XGIMI projector. */
    @Test fun xgimiPackages() {
        assertEquals(ProtectedReason.TV_INPUTS_REMOTE, Protected.reason("com.xgimi.tvinput"))
        assertEquals(ProtectedReason.TV_INPUTS_REMOTE, Protected.reason("com.xgimi.bluetoothservice"))
        assertEquals(ProtectedReason.TV_INPUTS_REMOTE, Protected.reason("com.google.android.tv.remote.service"))
        assertEquals(ProtectedReason.KEYBOARD, Protected.reason("com.google.android.inputmethod.latin"))
        assertEquals(ProtectedReason.SETUP_WIZARD, Protected.reason("com.google.android.tungsten.setupwraith"))
        assertEquals(ProtectedReason.SETUP_WIZARD, Protected.reason("com.xgimi.overseas.xgimiatvbootwizard"))
        assertEquals(ProtectedReason.SYSTEM, Protected.reason("com.android.networkstack.tethering.inprocess"))
        assertEquals(ProtectedReason.SETTINGS, Protected.reason("com.android.tv.settings"))
        // Debloat targets stay allowed.
        assertNull(Protected.reason("com.google.android.katniss"))
        assertNull(Protected.reason("com.google.android.tvrecommendations"))
        assertNull(Protected.reason("com.amazon.amazonvideo.livingroom"))
    }
}
