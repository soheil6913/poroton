package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OKM Visualizer", appName)
  }

  @Test
  fun `test export csv generation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val scan = com.example.data.ScanRecord(
      name = "Test Geological Survey",
      gridWidth = 4,
      gridLength = 4,
      soilType = "خاک کشاورزی",
      scanPattern = "زیگزاگ",
      data = listOf(
        200f, 210f, 220f, 230f,
        240f, 650f, 260f, 270f,
        180f, 150f, 290f, 300f,
        310f, 320f, 330f, 340f
      ),
      notes = "Field test survey notes"
    )

    val csvFile = com.example.util.ReportExporter.exportCsv(context, scan, shareIntent = false)
    org.junit.Assert.assertTrue(csvFile.exists())
    org.junit.Assert.assertTrue(csvFile.length() > 0)

    val content = csvFile.readText(Charsets.UTF_8)
    org.junit.Assert.assertTrue(content.contains("OKM 3D GROUND SCAN RADAR"))
    org.junit.Assert.assertTrue(content.contains("Test Geological Survey"))
    org.junit.Assert.assertTrue(content.contains("Line_1"))
  }
}
