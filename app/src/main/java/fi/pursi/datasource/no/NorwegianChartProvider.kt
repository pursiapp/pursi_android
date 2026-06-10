package fi.pursi.datasource.no

import fi.pursi.datasource.core.ChartProvider
import fi.pursi.datasource.core.JsonChartProvider
import fi.pursi.datasource.core.JsonProviderLoader
import javax.inject.Inject

class NorwegianChartProvider @Inject constructor(
    loader: JsonProviderLoader
) : ChartProvider by JsonChartProvider(
    loader.loadChartConfig("no-kartverket")!!
)
