package app.pursi.datasource.fi

@Deprecated("Moved to app.pursi.datasource.core.VesiLiikennemerkkiIconMapper")
object VesiLiikennemerkkiIconMapper {
    fun toIconName(vlmlajityyppi: Int): String =
        app.pursi.datasource.core.VesiLiikennemerkkiIconMapper.toIconName(vlmlajityyppi)
}
