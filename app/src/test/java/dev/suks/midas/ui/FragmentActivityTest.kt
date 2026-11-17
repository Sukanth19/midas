package dev.suks.midas.ui

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * MainActivity is a FragmentActivity (for the biometric prompt). Old fragment versions reject the
 * request codes that permission and file-picker launchers use, crashing on every launch. This
 * fails if a dependency drags the fragment library back to such a version.
 */
@RunWith(RobolectricTestRunner::class)
class FragmentActivityTest {
    @Test fun `fragment activity can launch permission and picker requests`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).create()
        val activity = controller.get()
        val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) {}
        controller.start().resume()
        permission.launch(Manifest.permission.CAMERA)
        picker.launch(arrayOf("*/*"))
    }
}
