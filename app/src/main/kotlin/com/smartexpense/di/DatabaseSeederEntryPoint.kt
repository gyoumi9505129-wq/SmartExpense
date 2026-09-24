package com.smartexpense.di

import com.smartexpense.data.repository.club.ClubSettingsLegacyMigrator
import com.smartexpense.data.local.seed.DatabaseSeeder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseSeederEntryPoint {
    fun databaseSeeder(): DatabaseSeeder
    fun clubSettingsLegacyMigrator(): ClubSettingsLegacyMigrator
}
