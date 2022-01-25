package com.neeva.app.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@RunWith(AndroidJUnit4::class)
class HistoryDatabaseMigrationTest {
    companion object {
        private const val TEST_DB_FILENAME = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HistoryDatabase::class.java,
        listOf(HistoryDatabase.MigrationFrom6To7()),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate6To7() {
        helper.createDatabase(TEST_DB_FILENAME, 6).apply {
            // Insert data using SQL queries.  Can't use DAO classes because they're built with the
            // latest schema.

            // Insert two sites into the database.
            // * Part of Site itself:   siteUID, siteURL, visitCount, lastVisitTimestamp
            // * @Embedded Metadata:    imageURL, title, description, entityType
            // * @Embedded Favicon:     faviconURL, encodedImage, width, and height
            execSQL(
                """
                INSERT INTO Site(
                    siteUID, siteURL, visitCount, lastVisitTimestamp,
                    imageURL, title, description, entityType,
                    faviconURL, encodedImage, width, height
                )
                VALUES(
                    1, 'https://www.neeva.com', 12345, 67890,
                    'unused image URL', 'Neeva homepage', 'unused description', 0,
                    'https://www.neeva.com/favicon.png', 'encoded image URI', 128, 128
                )
            """
            )
            execSQL(
                """
                INSERT INTO Site(
                    siteUID, siteURL, visitCount, lastVisitTimestamp,
                    imageURL, title, description, entityType,
                    faviconURL, encodedImage, width, height
                )
                VALUES(
                    2, 'https://www.reddit.com/r/android', 11111, 22222,
                    'unused image URL', 'Reddit: Android', 'unused description', 0,
                    'https://www.reddit.com/favicon.png', 'unused image URI', 128, 128
                )
            """
            )

            // Insert two domains into the database.
            // * Part of Domain itself: domainUID, domainName, providerName
            // * @Embedded Favicon:     faviconURL, encodedImage, width, and height
            execSQL(
                """
                INSERT INTO Domain(
                    domainUID, domainName, providerName,
                    faviconURL, encodedImage, width, height
                )
                VALUES(
                    1, 'neeva.com', 'neeva provider name',
                    'https://www.neeva.com/favicon.png', 'unused image URI', 128, 128
                )
            """
            )
            execSQL(
                """
                INSERT INTO Domain(
                    domainUID, domainName, providerName,
                    faviconURL, encodedImage, width, height
                )
                VALUES(
                    2, 'reddit.com', 'reddit provider name',
                    'https://www.reddit.com/favicon.png', 'unused image URI', 128, 128
                )
            """
            )

            // Allow the database to be reopened as version 7.
            close()
        }

        // Re-open the database with version 7 and do some cursory tests.  The migration from 6 to 7
        // just drops a few columns in the Site and Domain tables.
        val db = helper.runMigrationsAndValidate(TEST_DB_FILENAME, 7, true)
        db.query("SELECT * FROM Site").apply {
            expectThat(count).isEqualTo(2)

            this.moveToFirst()
            expectThat(this.getInt(0)).isEqualTo(1)
            expectThat(this.getString(1)).isEqualTo("https://www.neeva.com")
            expectThat(this.getString(3)).isEqualTo("https://www.neeva.com/favicon.png")

            this.moveToNext()
            expectThat(this.getInt(0)).isEqualTo(2)
            expectThat(this.getString(1)).isEqualTo("https://www.reddit.com/r/android")
            expectThat(this.getString(3)).isEqualTo("https://www.reddit.com/favicon.png")

            close()
        }

        db.query("SELECT * FROM Domain").apply {
            expectThat(count).isEqualTo(2)

            this.moveToFirst()
            expectThat(this.getInt(0)).isEqualTo(1)
            expectThat(this.getString(1)).isEqualTo("neeva.com")
            expectThat(this.getString(2)).isEqualTo("neeva provider name")
            expectThat(this.getString(3)).isEqualTo("https://www.neeva.com/favicon.png")

            this.moveToNext()
            expectThat(this.getInt(0)).isEqualTo(2)
            expectThat(this.getString(1)).isEqualTo("reddit.com")
            expectThat(this.getString(2)).isEqualTo("reddit provider name")
            expectThat(this.getString(3)).isEqualTo("https://www.reddit.com/favicon.png")

            close()
        }
    }
}