package fi.pursi.datasource.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureRendererRegistry @Inject constructor(
    val renderers: Set<FeatureRenderer>
) {
    fun getRenderer(featureType: String, providerId: String): FeatureRenderer? =
        renderers.find { it.canRender(featureType, providerId) }
}
