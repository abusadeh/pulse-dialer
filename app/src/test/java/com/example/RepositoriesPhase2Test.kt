package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FavoriteItem
import com.example.data.repository.BlockedNumbersRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.FavoritesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoriesPhase2Test {

    @Test
    fun testBlockedNumbersDeduplicationAndEquivalence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BlockedNumbersRepository(context)

        // Clear prefs before test
        context.getSharedPreferences("blocked_numbers_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repo.unblockNumber("0594091090")

        repo.blockNumber("0594091090", "Ahmad")
        assertTrue(repo.isNumberBlocked("0594091090"))
        assertTrue(repo.isNumberBlocked("+970594091090"))
        assertTrue(repo.isNumberBlocked("00972594091090"))
        assertTrue(repo.isNumberBlocked("594091090"))

        // Add equivalent number in international format
        repo.blockNumber("+970594091090", "Ahmad International")
        
        // Should update entry without creating duplicates
        val blockedList = repo.blockedNumbersFlow.value
        assertEquals(1, blockedList.size)
        assertEquals("+970594091090", blockedList[0].number)
    }

    @Test
    fun testBlockedNumbersUnblockEquivalenceAndPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("blocked_numbers_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val repo1 = BlockedNumbersRepository(context)
        repo1.blockNumber("0594091090", "Palestine Mobile")
        repo1.blockNumber("+12125551234", "US Number")

        assertEquals(2, repo1.blockedNumbersFlow.value.size)
        assertTrue(repo1.isNumberBlocked("0594091090"))
        assertTrue(repo1.isNumberBlocked("+12125551234"))

        // Unblock Palestinian number using an equivalent format
        repo1.unblockNumber("+972594091090")

        assertFalse(repo1.isNumberBlocked("0594091090"))
        assertFalse(repo1.isNumberBlocked("+970594091090"))
        assertTrue(repo1.isNumberBlocked("+12125551234"))
        assertEquals(1, repo1.blockedNumbersFlow.value.size)

        // Verify persistence after repository recreation
        val repo2 = BlockedNumbersRepository(context)
        assertTrue(repo2.isNumberBlocked("+12125551234"))
        assertFalse(repo2.isNumberBlocked("0594091090"))
    }

    @Test
    fun testBlockedNumbersLegacyPrePhase2Entries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("blocked_numbers_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Insert legacy entry directly in SharedPreferences
        val legacySet = setOf("0594091090|Legacy Contact|1600000000000")
        prefs.edit().putStringSet("blocked_numbers_set", legacySet).commit()

        val repo = BlockedNumbersRepository(context)
        assertTrue(repo.isNumberBlocked("+970594091090"))
        assertTrue(repo.isNumberBlocked("00972594091090"))

        repo.unblockNumber("+970594091090")
        assertFalse(repo.isNumberBlocked("0594091090"))
    }

    @Test
    fun testFavoritesDeduplicationEquivalenceAndRemoval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pulse_favorites_pref", Context.MODE_PRIVATE).edit().clear().commit()

        val contactsRepo = ContactsRepository(context)
        val favRepo = FavoritesRepository(context, contactsRepo)

        favRepo.addCustomFavorite("John Doe", "+970597778899", contactId = 200L)
        favRepo.addCustomFavorite("John Doe Local", "0597778899", contactId = 200L)

        favRepo.loadFavorites()
        val customFavs = favRepo.favoritesFlow.value.filter { !it.isDeviceStarred }
        assertEquals(1, customFavs.size)
        assertTrue(favRepo.isFavorite("00972597778899", contactId = 200L))

        // Remove using equivalent format item
        val itemToRemove = FavoriteItem(
            id = "custom_dummy",
            contactId = 200L,
            displayName = "John",
            phoneNumber = "00972597778899"
        )
        favRepo.removeFavorite(itemToRemove)

        favRepo.loadFavorites()
        val customFavsAfter = favRepo.favoritesFlow.value.filter { !it.isDeviceStarred }
        assertTrue(customFavsAfter.isEmpty())
        assertFalse(favRepo.isFavorite("0597778899", contactId = 200L))
    }

    @Test
    fun testFavoritesPersistenceAndRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pulse_favorites_pref", Context.MODE_PRIVATE).edit().clear().commit()

        val contactsRepo = ContactsRepository(context)
        val favRepo1 = FavoritesRepository(context, contactsRepo)

        favRepo1.addCustomFavorite("Ooredoo Contact", "0569123456")
        favRepo1.loadFavorites()
        assertTrue(favRepo1.isFavorite("+970569123456"))

        // Create new repository instance simulating app restart
        val favRepo2 = FavoritesRepository(context, contactsRepo)
        favRepo2.loadFavorites()

        val customFavs = favRepo2.favoritesFlow.value.filter { !it.isDeviceStarred }
        assertEquals(1, customFavs.size)
        assertTrue(favRepo2.isFavorite("+972569123456"))
    }

    @Test
    fun testUnrelatedInternationalNumbersInBlockedAndFavorites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("blocked_numbers_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val repo = BlockedNumbersRepository(context)
        repo.blockNumber("+12125551234", "NYC Office")

        assertTrue(repo.isNumberBlocked("+12125551234"))
        assertFalse(repo.isNumberBlocked("+12125559999"))
        assertFalse(repo.isNumberBlocked("0594091090"))
    }
}
