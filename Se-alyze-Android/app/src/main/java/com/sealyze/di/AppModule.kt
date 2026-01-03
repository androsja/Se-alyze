package com.sealyze.di

import android.content.Context
import com.sealyze.data.repository.SignRepositoryImpl
import com.sealyze.data.source.MediaPipeDataSource
import com.sealyze.data.source.TfliteDataSource
import com.sealyze.domain.repository.SignRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMediaPipeDataSource(@ApplicationContext context: Context): MediaPipeDataSource {
        return MediaPipeDataSource(context)
    }

    @Provides
    @Singleton
    fun provideTfliteDataSource(@ApplicationContext context: Context): TfliteDataSource {
        return TfliteDataSource(context)
    }

    @Provides
    @Singleton
    fun provideSignRepository(
        mediaPipeDataSource: MediaPipeDataSource,
        tfliteDataSource: TfliteDataSource
    ): SignRepository {
        return SignRepositoryImpl(mediaPipeDataSource, tfliteDataSource)
    }
}
