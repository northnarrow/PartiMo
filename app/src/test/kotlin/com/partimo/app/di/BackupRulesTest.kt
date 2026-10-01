package com.partimo.app.di

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.annotation.XmlRes
import androidx.test.core.app.ApplicationProvider
import com.partimo.app.R
import com.partimo.data.local.preferences.userDataStoreFile
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Backup di Android: c'è, e contiene solo il file dei dati dell'utente (non la cache né i modelli scaricati). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRulesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Regole del file XML come "sezione:include|exclude:dominio:percorso". */
    private fun rules(@XmlRes xml: Int): List<String> {
        val parser = context.resources.getXml(xml)
        val rules = mutableListOf<String>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer", "full-backup-content" -> section = parser.name
                "include", "exclude" -> rules += listOf(section, parser.name, parser.getAttributeValue(null, "domain"), parser.getAttributeValue(null, "path")).joinToString(":")
            }
        }
        return rules
    }

    @Test
    fun `backup su Google e passaggio a un telefono nuovo contengono solo i dati dell'utente`() {
        val path = userDataStoreFile(context).relativeTo(context.filesDir).path
        assertEquals("datastore/partimo_user.preferences_pb", path)

        assertEquals(listOf("cloud-backup:include:file:$path", "device-transfer:include:file:$path"), rules(R.xml.data_extraction_rules))
        assertEquals(listOf("full-backup-content:include:file:$path"), rules(R.xml.backup_rules))
    }

    @Test
    fun `il manifest permette il backup`() {
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }
}
