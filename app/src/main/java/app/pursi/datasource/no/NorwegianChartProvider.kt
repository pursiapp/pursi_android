package app.pursi.datasource.no

import app.pursi.datasource.core.ChartProvider
import app.pursi.datasource.core.JsonChartProvider
import app.pursi.datasource.core.JsonProviderLoader
import javax.inject.Inject

class NorwegianChartProvider @Inject constructor(
    loader: JsonProviderLoader
) : ChartProvider by JsonChartProvider(
    loader.loadChartConfig("no-kartverket")!!
)
