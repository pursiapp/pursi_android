package app.pursi.datasource.fi

import app.pursi.datasource.core.ChartProvider
import app.pursi.datasource.core.JsonChartProvider
import app.pursi.datasource.core.JsonProviderLoader
import javax.inject.Inject

class FinnishChartProvider @Inject constructor(
    loader: JsonProviderLoader
) : ChartProvider by JsonChartProvider(
    loader.loadChartConfig("fi-traficom")!!
)
