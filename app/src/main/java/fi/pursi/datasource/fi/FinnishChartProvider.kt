package fi.pursi.datasource.fi

import fi.pursi.datasource.core.ChartProvider
import fi.pursi.datasource.core.JsonChartProvider
import fi.pursi.datasource.core.JsonProviderLoader
import javax.inject.Inject

class FinnishChartProvider @Inject constructor(
    loader: JsonProviderLoader
) : ChartProvider by JsonChartProvider(
    loader.loadChartConfig("fi-traficom")!!
)
