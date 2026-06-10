package fi.pursi.datasource.fi

@Deprecated("Moved to fi.pursi.datasource.core.VesiLiikennemerkkiIconMapper")
object VesiLiikennemerkkiIconMapper {
    fun toIconName(vlmlajityyppi: Int): String =
        fi.pursi.datasource.core.VesiLiikennemerkkiIconMapper.toIconName(vlmlajityyppi)
}
