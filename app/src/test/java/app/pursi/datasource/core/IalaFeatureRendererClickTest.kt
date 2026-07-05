package app.pursi.datasource.core

import app.pursi.data.model.WfsFeature
import app.pursi.datasource.fi.FinnishPropertyMapper
import app.pursi.ui.viewmodel.SeamarkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [IalaFeatureRenderer.handleClick] — the central piece that turns a marine
 * WfsFeature (Finnish navigation_aid or other IALA type) into the SeamarkDetail the
 * popup UI consumes.
 *
 * The intent is to lock in behaviour parity with the pre-refactor
 * MapViewModel.buildTurvalaiteDetail, so future changes don't silently drop fields
 * users see in the popup (omistaja, väylä, MMSI, …).
 */
class IalaFeatureRendererClickTest {

    private val mapperRegistry = PropertyMapperRegistry(setOf(FinnishPropertyMapper()))
    private val renderer = IalaFeatureRenderer(mapperRegistry)

    @Test
    fun `navigation_aid is accepted by canRender`() {
        assertTrue(renderer.canRender("navigation_aid", "fi-vayla-traficom"))
    }

    @Test
    fun `unknown feature types are rejected`() {
        assertFalse(renderer.canRender("bogus", "any"))
        assertFalse(renderer.canRender("made_up_type", "fi-vayla-traficom"))
    }

    @Test
    fun `handleClick returns null when no mapper is registered for the provider`() {
        val feature = navigationAidFeature(
            source = "no-such-provider",
            props = "turvalaitetyyppifi=Viitta\nalityyppi=KELLUVA"
        )
        assertNull(renderer.handleClick(feature))
    }

    @Test
    fun `handleClick returns null for non-IALA feature types`() {
        val feature = navigationAidFeature(
            featureType = "made_up_type",
            source = "fi-vayla-traficom",
            props = "name=test"
        )
        assertNull(renderer.handleClick(feature))
    }

    @Test
    fun `handleClick builds full FI detail for a Finnish navigation_aid`() {
        val props = listOf(
            "nimifi=Harmaja",
            "nimisv=Harmaja",
            "turvalaitetyyppifi=Merimajakka",
            "alityyppi=KIINTEÄ",
            "navigointilajikoodi=1",
            "toimintatilakoodi=1",
            "valaistu=K",
            "loistojen_tiedot=Fl W 5s",
            "valosektorien_tiedot=White 360°",
            "omistaja=Liikennevirasto",
            "vaylan_nimi=Helsinki–Tallinna",
            "turvalaitenumero=1234",
            "rakennusvuodet=1900",
            "mmsi=992300001",
            "aisfi=Kyllä"
        ).joinToString("\n")
        val feature = navigationAidFeature(props = props)

        val detail = renderer.handleClick(feature)
        assertNotNull(detail)
        detail!!

        assertEquals(SeamarkSource.VV, detail.source)
        assertEquals("Harmaja", detail.name)
        assertEquals("Harmaja", detail.subtitle) // name_sv == name
        assertEquals("Majakka", detail.typeLabel) // human-readable, mapped from "Merimajakka"
        assertEquals("Käytössä", detail.statusLabel)
        assertEquals("Kiinteä", detail.structureLabel)
        assertTrue(detail.hasLight)
        assertEquals("Fl W 5s", detail.lightCharacteristic)
        assertEquals("White 360°", detail.sectorInfo)
        assertEquals("1234", detail.turvalaitenumero)
        assertTrue(detail.kaytossa)
        assertEquals("KIINTEÄ", detail.alityyppi)
        assertEquals("1900", detail.rakennusvuodet)
        assertEquals("Liikennevirasto", detail.omistaja)
        assertEquals("Helsinki–Tallinna", detail.vaylanNimi)
        assertEquals(992300001L, detail.mmsi)
        assertTrue(detail.aiskaytossa)
        assertTrue(
            "Omistaja should be in extraInfo",
            detail.extraInfo.any { it.contains("Omistaja:") }
        )
        assertTrue(
            "Väylä should be in extraInfo",
            detail.extraInfo.any { it.contains("Väylä:") }
        )
        assertTrue(
            "AIS MMSI should appear when AIS is on and MMSI present",
            detail.extraInfo.any { it.contains("AIS MMSI:") }
        )
        assertEquals(feature.latitude, detail.latitude, 0.0001)
        assertEquals(feature.longitude, detail.longitude, 0.0001)
    }

    @Test
    fun `handleClick works for offline source name vayla_turvalaitteet`() {
        // The offline VvDataDownloader stores WfsFeature rows with
        // source = "vayla_turvalaitteet". The Finnish mapper must accept that
        // source name via PropertyMapper.matchesSource so handleClick can build
        // a VV SeamarkDetail (regression test for the offline data path).
        val props = "turvalaitetyyppifi=Viitta\nalityyppi=KELLUVA\nnimifi=Testiviitta"
        val feature = navigationAidFeature(source = "vayla_turvalaitteet", props = props)

        val detail = renderer.handleClick(feature)
        assertNotNull("handleClick must succeed for offline source name", detail)
        assertEquals(SeamarkSource.VV, detail!!.source)
        assertEquals("Testiviitta", detail.name)
    }

    @Test
    fun `handleClick marks removed status for toimintatilakoodi=2`() {
        val props = listOf(
            "nimifi=Poistettu",
            "turvalaitetyyppifi=Viitta",
            "alityyppi=KELLUVA",
            "navigointilajikoodi=3",
            "toimintatilakoodi=2",
            "valaistu=E",
            "aisfi=Ei"
        ).joinToString("\n")
        val feature = navigationAidFeature(props = props)

        val detail = renderer.handleClick(feature)!!
        assertEquals("Poistettu", detail.statusLabel)
        assertFalse(detail.kaytossa)
        assertEquals("Kelluva", detail.structureLabel)
        assertFalse(detail.hasLight)
        assertFalse(detail.aiskaytossa)
    }

    @Test
    fun `handleClick omits AIS MMSI from extraInfo when MMSI is missing but AIS is on`() {
        val props = listOf(
            "turvalaitetyyppifi=Viitta",
            "alityyppi=KELLUVA",
            "navigointilajikoodi=1",
            "toimintatilakoodi=1",
            "valaistu=K",
            "aisfi=Kyllä"
            // no mmsi
        ).joinToString("\n")
        val feature = navigationAidFeature(props = props)

        val detail = renderer.handleClick(feature)!!
        assertTrue(detail.aiskaytossa)
        assertNull(detail.mmsi)
        assertFalse(
            "AIS MMSI line should not appear without an mmsi value",
            detail.extraInfo.any { it.contains("AIS MMSI:") }
        )
    }

    @Test
    fun `handleClick produces a Finnish-language description for lateral viitta`() {
        val props = listOf(
            "turvalaitetyyppifi=Viitta",
            "alityyppi=KELLUVA",
            "navigointilajikoodi=1"
        ).joinToString("\n")
        val feature = navigationAidFeature(props = props)

        val detail = renderer.handleClick(feature)!!
        assertNotNull(detail.description)
        assertTrue(
            "Description should mention Lateraalinen for nav code 1",
            detail.description!!.contains("Lateraalinen")
        )
    }

    @Test
    fun `handleClick builds a standard popup for a notice (vesiliikennemerkki)`() {
        // Sign type 6 = Nopeusrajoitus (speed limit)
        val props = listOf(
            "vlmlajityyppi=6",
            "rajoitusarvo=30",
            "lisakilventeksti1=City Marina"
        ).joinToString("\n")
        val feature = noticeFeature(props = props)

        val detail = renderer.handleClick(feature)
        assertNotNull(detail)
        detail!!

        assertEquals(SeamarkSource.VV, detail.source)
        assertEquals("Vesiliikennemerkki", detail.typeLabel)
        assertEquals("Nopeusrajoitus", detail.name)
        assertTrue(
            "Description should mention Nopeusrajoitus",
            detail.description!!.contains("Nopeusrajoitus")
        )
        assertTrue(
            "Description should mention 30 (the restriction value)",
            detail.description!!.contains("30")
        )
        assertTrue(
            "extraInfo should include the sign text",
            detail.extraInfo.any { it.contains("City Marina") }
        )
        assertTrue(
            "extraInfo should include the restriction value",
            detail.extraInfo.any { it.contains("30") }
        )
        assertEquals(feature.latitude, detail.latitude, 0.0001)
        assertEquals(feature.longitude, detail.longitude, 0.0001)
    }

    @Test
    fun `handleClick for notice works offline (vayla_vesiliikennemerkit source)`() {
        // The offline VvDataDownloader stores WfsFeature rows with
        // source = "vayla_vesiliikennemerkit". The Finnish mapper must accept that
        // source name via PropertyMapper.matchesSource.
        val props = "vlmlajityyppi=9\nrajoitusarvo=10"
        val feature = noticeFeature(source = "vayla_vesiliikennemerkit", props = props)

        val detail = renderer.handleClick(feature)
        assertNotNull("handleClick must succeed for offline source name", detail)
        assertEquals(SeamarkSource.VV, detail!!.source)
        assertEquals("Ajosuuntakielto", detail.name)
    }

    @Test
    fun `handleClick for notice falls back to a generic label for unknown sign type`() {
        // sign_type 999 is not in the table; should fall back to "Vesiliikennemerkki".
        val props = "vlmlajityyppi=999"
        val feature = noticeFeature(props = props)

        val detail = renderer.handleClick(feature)!!
        assertEquals("Vesiliikennemerkki", detail.name)
    }

    @Test
    fun `renderNotice sets the icon property from the mapper`() {
        // sign_type 6 → josm_Q126_generic_speed_limit
        val feature = noticeFeature(props = "vlmlajityyppi=6")
        val maplibreFeature = renderer.toMapLibreFeature(feature)
        assertNotNull(maplibreFeature)
        assertEquals(
            "josm_Q126_generic_speed_limit",
            maplibreFeature!!.getStringProperty("icon")
        )
    }

    @Test
    fun `renderNotice falls back to josm_Q126_generic_crossing for unknown sign type`() {
        val feature = noticeFeature(props = "vlmlajityyppi=9999")
        val maplibreFeature = renderer.toMapLibreFeature(feature)
        assertNotNull(maplibreFeature)
        assertEquals(
            "josm_Q126_generic_crossing",
            maplibreFeature!!.getStringProperty("icon")
        )
    }

    private fun navigationAidFeature(
        featureType: String = "navigation_aid",
        source: String = "fi-vayla-traficom",
        props: String = "turvalaitetyyppifi=Viitta"
    ) = WfsFeature(
        id = 42L,
        source = source,
        featureType = featureType,
        geometry = """{"type":"Point","coordinates":[24.0,60.0]}""",
        properties = props,
        latitude = 60.0,
        longitude = 24.0,
        minLat = 60.0,
        minLng = 24.0,
        maxLat = 60.0,
        maxLng = 24.0
    )

    private fun noticeFeature(
        source: String = "fi-vayla-traficom",
        props: String = "vlmlajityyppi=6"
    ) = WfsFeature(
        id = 99L,
        source = source,
        featureType = "notice",
        geometry = """{"type":"Point","coordinates":[24.0,60.0]}""",
        properties = props,
        latitude = 60.0,
        longitude = 24.0,
        minLat = 60.0,
        minLng = 24.0,
        maxLat = 60.0,
        maxLng = 24.0
    )
}
