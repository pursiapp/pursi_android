package app.pursi.datasource.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyMapperRegistry @Inject constructor(
    private val mappers: Set<PropertyMapper>
) {
    fun getMapper(source: String): PropertyMapper? =
        mappers.find { it.matchesSource(source) }
}
