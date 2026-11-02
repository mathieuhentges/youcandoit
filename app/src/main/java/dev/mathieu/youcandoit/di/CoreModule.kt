package dev.mathieu.youcandoit.di

import dev.mathieu.youcandoit.domain.model.IdGenerator
import dev.mathieu.youcandoit.domain.model.UuidIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /**
     * UTC, ticking in whole milliseconds: the domain only uses instants, and Room stores
     * epoch millis, so a value read back from the database equals the one that was saved.
     * The UI formats instants with the device's current zone.
     */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))

    @Provides
    @Singleton
    fun idGenerator(): IdGenerator = UuidIdGenerator()

    /** Not a singleton: read each time, so a time zone change on the device is picked up. */
    @Provides
    fun zone(): ZoneId = ZoneId.systemDefault()
}
