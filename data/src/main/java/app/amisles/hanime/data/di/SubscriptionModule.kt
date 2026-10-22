package app.amisles.hanime.data.di

import app.amisles.hanime.data.subscription.SubscriptionUpdateChecker
import app.amisles.hanime.domain.repository.ContentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubscriptionModule {

    @Provides
    @Singleton
    fun provideSubscriptionUpdateChecker(
        repository: ContentRepository
    ): SubscriptionUpdateChecker = SubscriptionUpdateChecker(repository)
}
