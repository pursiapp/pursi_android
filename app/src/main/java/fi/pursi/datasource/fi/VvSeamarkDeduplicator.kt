package fi.pursi.datasource.fi

import fi.pursi.data.model.WfsFeature
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

object VvSeamarkDeduplicator {

    private const val TOLERANCE = 0.001

    data class VvKey(
        val latKey: Long,
        val lonKey: Long,
        val seamarkType: String
    )

    fun buildVvKeys(features: List<WfsFeature>): Set<VvKey> {
        return features.mapNotNull { feature ->
            val type = TurvalaiteIconMapper.extractProperty(feature.properties, "turvalaitetyyppifi") ?: return@mapNotNull null
            val alityyppi = TurvalaiteIconMapper.extractProperty(feature.properties, "alityyppi")
            val navigointilajikoodi = TurvalaiteIconMapper.extractProperty(feature.properties, "navigointilajikoodi")
            val seamarkType = TurvalaiteIconMapper.toSeamarkType(type, alityyppi, navigointilajikoodi)
            VvKey(
                latKey = TurvalaiteIconMapper.roundCoord(feature.latitude),
                lonKey = TurvalaiteIconMapper.roundCoord(feature.longitude),
                seamarkType = seamarkType
            )
        }.toSet()
    }

    fun deduplicateOsmSeamarks(
        osmFeatures: List<Feature>,
        vvKeys: Set<VvKey>
    ): List<Feature> {
        return osmFeatures.filterNot { osm ->
            val osmType = osm.getStringProperty("seamark:type") ?: return@filterNot false
            if (!TurvalaiteIconMapper.isIALAType(osmType)) return@filterNot false
            val geom = osm.geometry() as? Point ?: return@filterNot false
            val key = VvKey(
                latKey = TurvalaiteIconMapper.roundCoord(geom.latitude()),
                lonKey = TurvalaiteIconMapper.roundCoord(geom.longitude()),
                seamarkType = osmType
            )
            key in vvKeys
        }
    }
}
