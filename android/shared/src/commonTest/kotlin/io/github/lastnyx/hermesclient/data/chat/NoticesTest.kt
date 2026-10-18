package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    private fun show(json: String, type: String = "notification.show") = LinkEvent(type, "s1", JSONObject(json), null)

    private fun NoticeStore.addJson(json: String, now: Long = 0) = add(show(json), now)

    @Test
    fun rules() {
        val sticky = NoticeStore().addJson("""{"text":"a","kind":"sticky"}""")
        assertEquals(1, sticky.expire(1_000_000).notices.size)

        val ttl = NoticeStore().addJson("""{"text":"a","kind":"ttl","ttl_ms":3000}""", 100)
        assertEquals(1, ttl.expire(100 + 2999).notices.size)
        assertEquals(0, ttl.expire(100 + 3000).notices.size)

        val dflt = NoticeStore().addJson("""{"text":"a","kind":"ttl","ttl_ms":null}""")
        assertEquals(1, dflt.expire(4999).notices.size)
        assertEquals(0, dflt.expire(5000).notices.size)

        assertEquals(1, NoticeStore().addJson("""{"text":"a","kind":"agent"}""").expire(9_999_999).notices.size)
        assertEquals(1, NoticeStore().addJson("""{"text":"a","kind":"weird"}""").expire(9_999_999).notices.size)

        val keyed = NoticeStore().addJson("""{"text":"a","key":"k"}""").addJson("""{"text":"b"}""")
        val replaced = keyed.addJson("""{"text":"a2","key":"k"}""")
        assertEquals(listOf("a2", "b"), replaced.notices.map { it.text })
        assertEquals(true, replaced.notices[0].uid != keyed.notices[0].uid)

        assertEquals(2, NoticeStore().addJson("""{"text":"a"}""").addJson("""{"text":"a"}""").notices.size)

        var six = NoticeStore()
        for (i in 1..6) six = six.addJson("""{"text":"t$i"}""")
        assertEquals(listOf("t2", "t3", "t4", "t5", "t6"), six.notices.map { it.text })

        assertEquals(0, keyed.clear("k").notices.count { it.key == "k" })
        assertEquals(keyed, keyed.clear("nope"))

        assertEquals(listOf("b"), keyed.dismiss(keyed.notices[0].uid).notices.map { it.text })
        assertEquals(keyed, keyed.dismiss(999))

        assertEquals(NoticeStore(), NoticeStore().addJson("""{"text":"  "}"""))
        assertEquals(NoticeStore(), NoticeStore().addJson("""{"level":"info"}"""))
        assertEquals(NoticeLevel.Info, NoticeStore().addJson("""{"text":"a","level":"loud"}""").notices[0].level)
        assertEquals(NoticeLevel.Warn, NoticeStore().addJson("""{"text":"a","level":"warn"}""").notices[0].level)

        assertNull(NoticeStore().addJson("""{"text":"a","key":null,"id":null}""").notices[0].key)
        assertEquals("i1", NoticeStore().addJson("""{"text":"a","key":null,"id":"i1"}""").notices[0].key)

        assertEquals(NoticeStore(), NoticeStore().add(show("""{"text":"a"}""", "other"), 0))
        assertEquals(NoticeStore(), NoticeStore().add(LinkEvent("notification.show", "s1", null, null), 0))
    }
}
