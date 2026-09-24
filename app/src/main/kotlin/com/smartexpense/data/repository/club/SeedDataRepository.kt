package com.smartexpense.data.repository.club



import com.smartexpense.data.local.seed.DatabaseSeeder

import javax.inject.Inject

import javax.inject.Singleton

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext



@Singleton

class SeedDataRepository @Inject constructor(

    private val databaseSeeder: DatabaseSeeder

) {

    suspend fun resetToDefaultSeedData() = withContext(Dispatchers.IO) {
        databaseSeeder.resetToDefaultSeedData()
    }

    suspend fun markExternalImportComplete() = withContext(Dispatchers.IO) {
        databaseSeeder.markExternalImportComplete()
    }
}

