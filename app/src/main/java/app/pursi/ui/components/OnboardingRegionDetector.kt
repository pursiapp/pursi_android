package app.pursi.ui.components

import app.pursi.datasource.core.BoundingBox
import org.maplibre.android.geometry.LatLng

enum class OnboardingProfile { FINLAND, EUROPE }

private val TRAFICOM_BBOX = BoundingBox(
    minLat = 58.5, maxLat = 70.5,
    minLng = 19.0, maxLng = 32.0
)

fun detectOnboardingProfile(
    localeCountry: String?,
    lastKnownLatLng: LatLng?
): OnboardingProfile {
    if (lastKnownLatLng != null &&
        lastKnownLatLng.latitude >= TRAFICOM_BBOX.minLat &&
        lastKnownLatLng.latitude <= TRAFICOM_BBOX.maxLat &&
        lastKnownLatLng.longitude >= TRAFICOM_BBOX.minLng &&
        lastKnownLatLng.longitude <= TRAFICOM_BBOX.maxLng
    ) {
        return OnboardingProfile.FINLAND
    }
    if (localeCountry.equals("FI", ignoreCase = true)) {
        return OnboardingProfile.FINLAND
    }
    return OnboardingProfile.EUROPE
}
