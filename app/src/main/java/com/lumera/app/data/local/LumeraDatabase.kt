package com.lumera.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.ThemeEntity
import com.lumera.app.data.model.SeriesNextUpEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.WatchlistEntity


@Database(
    entities = [
        AddonEntity::class,
        ProfileEntity::class,
        WatchHistoryEntity::class,
        CatalogConfigEntity::class,
        ThemeEntity::class,
        HubRowEntity::class,
        HubRowItemEntity::class,
        WatchlistEntity::class,
        SeriesNextUpEntity::class
    ],
    version = 44
)
abstract class LumeraDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
}