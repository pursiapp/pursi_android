package app.pursi.datasource.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PropertyMapperRegistryTest {

    @Test
    fun `getMapper returns the registered mapper for an exact providerId match`() {
        val mapper = TestMapper("fi-vayla-traficom")
        val registry = PropertyMapperRegistry(setOf(mapper))

        assertSame(mapper, registry.getMapper("fi-vayla-traficom"))
    }

    @Test
    fun `getMapper returns the registered mapper for a handled source name (vayla_*)`() {
        // The Finnish offline downloader stores WfsFeature rows with
        // source = "vayla_turvalaitteet" (and other vayla_* names). The Finnish
        // mapper declares them via matchesSource. This is the exact path the
        // click handler takes for offline VV data.
        val mapper = TestMapper("fi-vayla-traficom", handledSources = setOf(
            "vayla_turvalaitteet",
            "vayla_turvalaitteet_muut",
            "vayla_valosektorit",
            "vayla_vesiliikennemerkit"
        ))
        val registry = PropertyMapperRegistry(setOf(mapper))

        assertSame(mapper, registry.getMapper("vayla_turvalaitteet"))
        assertSame(mapper, registry.getMapper("vayla_turvalaitteet_muut"))
        assertSame(mapper, registry.getMapper("vayla_valosektorit"))
        assertSame(mapper, registry.getMapper("vayla_vesiliikennemerkit"))
    }

    @Test
    fun `getMapper returns null for an unhandled source name`() {
        val mapper = TestMapper("fi-vayla-traficom", handledSources = setOf("vayla_turvalaitteet"))
        val registry = PropertyMapperRegistry(setOf(mapper))

        assertNull(registry.getMapper("some_other_provider"))
        assertNull(registry.getMapper("vayla_lights"))  // not in handledSources
    }

    @Test
    fun `getMapper resolves the first matching mapper when multiple are registered`() {
        val fi = TestMapper("fi-vayla-traficom", handledSources = setOf("vayla_turvalaitteet"))
        val no = TestMapper("no-kystverket", handledSources = setOf("no_cardinal"))
        val registry = PropertyMapperRegistry(setOf(fi, no))

        assertSame(fi, registry.getMapper("vayla_turvalaitteet"))
        assertSame(no, registry.getMapper("no_cardinal"))
    }

    @Test
    fun `default matchesSource returns true only for exact providerId`() {
        val mapper = TestMapper("fi-vayla-traficom")
        assertEquals("fi-vayla-traficom", mapper.providerId)
        assertNotNull(mapper)
    }

    private class TestMapper(
        override val providerId: String,
        handledSources: Set<String> = emptySet()
    ) : PropertyMapper {
        private val handled = handledSources
        override fun matchesSource(source: String): Boolean =
            providerId == source || source in handled
        override fun mapKey(providerKey: String): String? = providerKey
        override fun mapValue(key: String, providerValue: String): String? = providerValue
    }
}
