package dev.mathieu.youcandoit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mathieu.youcandoit.data.backup.BackupFiles
import dev.mathieu.youcandoit.data.backup.ContentResolverBackupFiles

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    abstract fun backupFiles(files: ContentResolverBackupFiles): BackupFiles
}
