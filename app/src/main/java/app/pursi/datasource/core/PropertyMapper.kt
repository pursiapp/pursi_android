package app.pursi.datasource.core

interface PropertyMapper {
    val providerId: String

    /**
     * Returns true if this mapper knows how to translate properties from the given
     * WfsFeature.source name. The default matches the providerId, which works for
     * mappers whose data is stored under a single source name equal to the providerId.
     *
     * Mappers that handle data stored under a different naming convention (e.g. the
     * Finnish offline downloader stores under `vayla_*` while the provider is
     * `fi-vayla-traficom`) should override this to declare the source names they
     * accept.
     */
    fun matchesSource(source: String): Boolean = providerId == source

    fun mapKey(providerKey: String): String?

    fun mapValue(key: String, providerValue: String): String?
}
