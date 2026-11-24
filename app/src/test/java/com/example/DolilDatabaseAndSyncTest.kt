package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DolilDao
import com.example.data.local.DolilDatabase
import com.example.data.repository.DolilSyncRepository
import com.example.data.repository.SaveDeedOutcome
import com.example.domain.model.AuditAction
import com.example.domain.model.DeedConflict
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandParcel
import com.example.domain.model.PartyInfo
import com.example.domain.model.SyncState
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DolilDatabaseAndSyncTest {

    private lateinit var db: DolilDatabase
    private lateinit var dao: DolilDao
    private lateinit var syncRepo: DolilSyncRepository

    private val actor = UserProfile(
        userId = "user-sup-1",
        fullName = "Jubayer Hossain",
        email = "jubayer@example.com",
        role = UserRole.OFFICE_SUPERVISOR
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DolilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dolilDao()
        syncRepo = DolilSyncRepository(dao = dao, ownerUid = actor.userId)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `offline save writes deed audit log and pending sync queue atomically`() = runBlocking {
        val deed = DeedRecord(
            id = "deed-local-1",
            deedNumber = "১২৫৬/২০২৪",
            deedYear = 2024,
            districtName = "নারায়ণগঞ্জ",
            upazilaName = "ফতুল্লা রাজস্ব সার্কেল",
            mouzaName = "ফতুল্লা",
            jlNumber = "140",
            status = DeedStatus.DRAFT,
            visibility = DeedVisibility.PRIVATE_DRAFT,
            grantors = listOf(PartyInfo(fullName = "আব্দুল করিম")),
            grantees = listOf(PartyInfo(fullName = "সালমা বেগম")),
            parcels = listOf(LandParcel(khatianNumber = "712", dagNumber = "1845", landAreaDecimal = "12.5"))
        )

        val res = syncRepo.saveDeedLocallyAndTrySync(deed, actor, AuditAction.CREATE)
        assertTrue(res.isSuccess)
        assertTrue(res.getOrThrow() is SaveDeedOutcome.Saved)

        val localDeeds = syncRepo.observeLocalDeeds().first()
        assertEquals(1, localDeeds.size)
        assertEquals(SyncState.LOCAL_ONLY_DRAFT, localDeeds[0].syncState)

        val pendingCount = syncRepo.observePendingSyncCount().first()
        assertEquals(1, pendingCount)

        val logs = syncRepo.observeAuditLogs("deed-local-1").first()
        assertEquals(1, logs.size)
        assertEquals(AuditAction.CREATE, logs[0].action)

        // Indexed Room SQL search finds Bengali digits normalized to ASCII
        val searchHit = syncRepo.searchLocalIndexed("1256")
        assertEquals(1, searchHit.size)
        assertEquals("deed-local-1", searchHit[0].id)
    }

    @Test
    fun `softDeleteDeed excludes record from indexed search and restoreDeed brings it back`() = runBlocking {
        val deed = DeedRecord(
            id = "deed-life-1",
            deedNumber = "450/2024",
            deedYear = 2024,
            mouzaName = "চাষাড়া",
            status = DeedStatus.APPROVED,
            visibility = DeedVisibility.PUBLIC
        )
        syncRepo.saveDeedLocallyAndTrySync(deed, actor, AuditAction.APPROVE)

        syncRepo.softDeleteDeed(deed, actor)
        val afterDelete = syncRepo.observeLocalDeeds().first().first()
        assertEquals(DeedStatus.SOFT_DELETED, afterDelete.status)
        assertTrue(syncRepo.searchLocalIndexed("450").isEmpty())

        syncRepo.restoreDeed(afterDelete, actor, DeedStatus.APPROVED)
        val afterRestore = syncRepo.observeLocalDeeds().first().first()
        assertEquals(DeedStatus.APPROVED, afterRestore.status)
        assertEquals(1, syncRepo.searchLocalIndexed("450").size)
    }

    @Test
    fun `cacheRemoteDeeds does not overwrite pending local edits and acceptServer resolves conflict`() = runBlocking {
        val localPending = DeedRecord(
            id = "deed-conflict-1",
            deedNumber = "900/2024",
            mouzaName = "স্থানীয় খসড়া",
            rowVersion = 1L
        )
        syncRepo.saveDeedLocallyAndTrySync(localPending, actor, AuditAction.CREATE)

        val remoteStale = localPending.copy(mouzaName = "সার্ভার ভার্সন", rowVersion = 2L)
        syncRepo.cacheRemoteDeeds(listOf(remoteStale))

        // Must preserve local pending edit until conflict resolved
        val current = syncRepo.observeLocalDeeds().first().first()
        assertEquals("স্থানীয় খসড়া", current.mouzaName)

        // Accepting server updates local copy and clears pending sync entry
        syncRepo.resolveConflictAcceptServer(DeedConflict(localDeed = current, serverDeed = remoteStale))
        val resolved = syncRepo.observeLocalDeeds().first().first()
        assertEquals("সার্ভার ভার্সন", resolved.mouzaName)
        assertEquals(SyncState.SYNCED, resolved.syncState)
        assertEquals(0, syncRepo.observePendingSyncCount().first())
    }
}
