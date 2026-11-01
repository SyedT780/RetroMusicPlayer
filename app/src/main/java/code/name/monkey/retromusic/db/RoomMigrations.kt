package code.name.monkey.retromusic.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE LyricsEntity")
        database.execSQL("DROP TABLE BlackListStoreEntity")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shuffle_runs` (
                `runId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `totalDomainN` INTEGER NOT NULL,
                `engineMode` TEXT NOT NULL,
                `playedTracksJson` TEXT NOT NULL,
                `slotMatches` INTEGER NOT NULL,
                `repeatedTransitions` INTEGER NOT NULL,
                `repetitivenessScore` REAL NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `played_tracks_history` (
                `trackId` TEXT NOT NULL PRIMARY KEY,
                `invariantId` INTEGER NOT NULL,
                `lastPlayedTimestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
