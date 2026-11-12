package dev.suks.midas.workspaces

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import dev.suks.midas.data.Workspace
import dev.suks.midas.data.WorkspaceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceRegistryTest {
    private lateinit var prefs: SharedPreferences

    @Before fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("workspaces-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun registry(ids: Iterator<String> = generateSequence(1) { it + 1 }.map { "ws$it" }.iterator()) =
        WorkspaceRegistry(prefs) { ids.next() }

    @Test fun `fresh install has only the default workspace, open`() {
        val r = registry()
        assertEquals(listOf(Workspace.DEFAULT_ID), r.workspaces.value.map { it.id })
        assertEquals(Workspace.DEFAULT_NAME, r.active.name)
        assertEquals(Workspace.DEFAULT_ID, r.activeId.value)
    }

    @Test fun `create adds a workspace with a cleaned name and an unused colour`() {
        val r = registry()
        val ws = r.create("   Room   mate  ")
        assertEquals("Room mate", ws.name)
        assertEquals("ws1", ws.id)
        assertNotEquals(r.get(Workspace.DEFAULT_ID)!!.color, ws.color)
        assertEquals(listOf("Personal", "Room mate"), r.workspaces.value.map { it.name })
        // Creating doesn't switch on its own.
        assertEquals(Workspace.DEFAULT_ID, r.activeId.value)
    }

    @Test fun `every workspace gets a distinct colour until the palette runs out`() {
        val r = registry()
        repeat(Workspace.palette.size - 1) { r.create("Friend $it") }
        assertEquals(Workspace.palette.size, r.workspaces.value.map { it.color }.toSet().size)
        r.create("One more") // palette exhausted: still works, reuses a colour
        assertEquals(Workspace.palette.size + 1, r.workspaces.value.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank names are rejected`() {
        registry().create("   ")
    }

    @Test fun `generated ids never collide with existing ones or the default id`() {
        val r = registry(listOf("dup", "dup", Workspace.DEFAULT_ID, "fresh").iterator())
        assertEquals("dup", r.create("A").id)
        assertEquals("fresh", r.create("B").id)
    }

    @Test fun `rename updates the name and ignores blanks and unknown ids`() {
        val r = registry()
        val ws = r.create("Roomie")
        assertTrue(r.rename(ws.id, "Aditya"))
        assertEquals("Aditya", r.get(ws.id)!!.name)
        assertFalse(r.rename(ws.id, "  "))
        assertEquals("Aditya", r.get(ws.id)!!.name)
        assertFalse(r.rename("missing", "X"))
        assertTrue(r.rename(Workspace.DEFAULT_ID, "Me"))
        assertEquals("Me", r.active.name)
    }

    @Test fun `select switches only to known workspaces`() {
        val r = registry()
        val ws = r.create("Trip")
        assertTrue(r.select(ws.id))
        assertEquals(ws, r.active)
        assertFalse(r.select("nope"))
        assertEquals(ws.id, r.activeId.value)
    }

    @Test fun `the default workspace can't be removed`() {
        val r = registry()
        assertFalse(r.remove(Workspace.DEFAULT_ID))
        assertEquals(1, r.workspaces.value.size)
    }

    @Test fun `removing the open workspace falls back to the default`() {
        val r = registry()
        val a = r.create("A")
        val b = r.create("B")
        r.select(a.id)
        assertTrue(r.remove(a.id))
        assertNull(r.get(a.id))
        assertEquals(Workspace.DEFAULT_ID, r.activeId.value)
        assertEquals(listOf(Workspace.DEFAULT_ID, b.id), r.workspaces.value.map { it.id })
        assertFalse(r.remove(a.id))
    }

    @Test fun `workspaces and the open one survive a restart`() {
        val r = registry()
        val ws = r.create("Roommate")
        r.rename(Workspace.DEFAULT_ID, "Me")
        r.select(ws.id)
        val again = registry()
        assertEquals(listOf("Me", "Roommate"), again.workspaces.value.map { it.name })
        assertEquals(ws.id, again.activeId.value)
    }

    @Test fun `corrupt or stale storage falls back safely`() {
        prefs.edit().putString("workspaces", "{not json").putString("active", "gone").commit()
        val r = registry()
        assertEquals(listOf(Workspace.DEFAULT_ID), r.workspaces.value.map { it.id })
        assertEquals(Workspace.DEFAULT_ID, r.activeId.value)
    }
}

class WorkspaceTest {
    @Test fun `default keeps the original file names so existing data carries over`() {
        assertEquals("midas.db", Workspace.databaseName(Workspace.DEFAULT_ID))
        assertEquals("settings", Workspace.settingsName(Workspace.DEFAULT_ID))
        assertEquals("midas-abc.db", Workspace.databaseName("abc"))
        assertEquals("settings-abc", Workspace.settingsName("abc"))
    }

    @Test fun `names are trimmed, collapsed and capped`() {
        assertEquals("Room mate", Workspace.cleanName("  Room \t mate "))
        assertNull(Workspace.cleanName("   "))
        assertEquals(Workspace.MAX_NAME_LENGTH, Workspace.cleanName("x".repeat(100))!!.length)
    }

    @Test fun `initial is the first letter, uppercased`() {
        assertEquals("R", Workspace("1", "roommate", 0).initial)
        assertEquals("?", Workspace("1", " ", 0).initial)
    }
}
