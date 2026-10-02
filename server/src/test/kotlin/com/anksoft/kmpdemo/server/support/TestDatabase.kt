package com.anksoft.kmpdemo.server.support

import com.anksoft.kmpdemo.server.db.connect
import com.anksoft.kmpdemo.server.db.createDataSource
import com.anksoft.kmpdemo.server.db.migrate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** Migrated, emptied database handle for repository tests; closed after [block]. */
fun <T> withTestDatabase(block: suspend (Database) -> T): T {
    PostgresTestDb.reset()
    val dataSource = createDataSource(testSettings())
    migrate(dataSource)
    PostgresTestDb.reset()
    val db = connect(dataSource)
    return try {
        kotlinx.coroutines.runBlocking { block(db) }
    } finally {
        TransactionManager.closeAndUnregister(db)
        dataSource.close()
    }
}
