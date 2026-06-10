package fi.pursi.datasource.core

interface PropertyMapper {
    val providerId: String

    fun mapKey(providerKey: String): String?

    fun mapValue(key: String, providerValue: String): String?
}
