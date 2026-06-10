package fi.pursi.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// AnalyticsManager is created via @Inject constructor — no @Provides needed
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule
