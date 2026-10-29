package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun competitionInfo_correctlyConstructed() {
    val comp = com.example.model.CompetitionInfo(
      id = "COMP_2",
      competitionNumber = 2,
      title = "مسابقه شماره ۲ (جایزه پلی‌استیشن ۵)",
      status = "ACTIVE",
      startedAtMs = 1700000000000L,
      totalParticipants = 45,
      totalScore = 1200
    )

    assertEquals("COMP_2", comp.id)
    assertEquals(2, comp.competitionNumber)
    assertEquals("ACTIVE", comp.status)
    assertEquals(45, comp.totalParticipants)
    assertEquals(1200, comp.totalScore)
  }

  @Test
  fun userScore_resetsToZeroOnNewCompetitionWithoutLosingAccount() {
    val userBefore = com.example.model.User(
      id = "usr_123",
      firstName = "مجتبی",
      lastName = "چراغ‌زاده",
      phoneNumber = "09339509691",
      totalScore = 223,
      token = "jwt_token_abc"
    )

    // New competition starts -> server sends score 0
    val serverNewCompetitionScore = 0
    val userAfter = userBefore.copy(totalScore = serverNewCompetitionScore)

    assertEquals(0, userAfter.totalScore)
    assertEquals("usr_123", userAfter.id)
    assertEquals("09339509691", userAfter.phoneNumber)
    assertEquals("مجتبی چراغ‌زاده", userAfter.fullName)
  }

  @Test
  fun homeAnnouncement_activeAndCountdownCalculations() {
    val futureEnd = System.currentTimeMillis() + 86400000L * 2 // 2 days in future
    val announcement = com.example.model.HomeAnnouncement(
      id = "ann_101",
      title = "آغاز مرحله نهایی مسابقه ۲",
      content = "جوایز ویژه برای ۵ نفر اول جدول رده‌بندی",
      type = "COMPETITION_START",
      isActive = true,
      endsAtMs = futureEnd
    )

    assertEquals("ann_101", announcement.id)
    assertEquals(true, announcement.isActive)
    assertEquals("COMPETITION_START", announcement.type)
    assertNotNull(announcement.remainingMs)
    assertTrue("Should have remaining time", (announcement.remainingMs ?: 0L) > 0)
    assertFalse("Should not be ended", announcement.isEnded)
  }

  @Test
  fun homeAnnouncement_expiredCalculation() {
    val pastEnd = System.currentTimeMillis() - 5000L // 5 seconds ago
    val announcement = com.example.model.HomeAnnouncement(
      id = "ann_102",
      title = "مسابقه پایان یافت",
      content = "تبریک به برندگان",
      type = "WINNERS",
      isActive = true,
      endsAtMs = pastEnd
    )

    assertTrue("Should be marked as ended", announcement.isEnded)
    assertEquals(0L, announcement.remainingMs)
  }

  @Test
  fun supportTicket_categoryAndStatusMapping() {
    val ticket = com.example.model.SupportTicket(
      id = "TCK_123",
      trackingCode = "TRK-987654",
      subject = "مشکل در ثبت امتیاز تماشای ویدیو",
      category = "ADS",
      status = "ANSWERED",
      lastMessage = "پاسخ داده شد",
      lastSenderType = "ADMIN"
    )

    assertEquals("TCK_123", ticket.id)
    assertEquals("TRK-987654", ticket.trackingCode)
    assertEquals(com.example.model.SupportCategory.ADS, ticket.categoryEnum)
    assertEquals("تبلیغات", ticket.categoryEnum.titlePersian)
    assertEquals(com.example.model.SupportStatus.ANSWERED, ticket.statusEnum)
    assertEquals("پاسخ داده شد", ticket.statusEnum.titlePersian)
    assertFalse(ticket.isClosed)
  }

  @Test
  fun supportTicket_closedStateDetection() {
    val closedTicket = com.example.model.SupportTicket(
      id = "TCK_456",
      trackingCode = "TRK-112233",
      subject = "سوال عمومی",
      category = "OTHER",
      status = "CLOSED"
    )

    assertTrue("Ticket with CLOSED status must be marked as closed", closedTicket.isClosed)
    assertEquals(com.example.model.SupportStatus.CLOSED, closedTicket.statusEnum)
  }

  @Test
  fun supportMessage_adminDetection() {
    val userMsg = com.example.model.SupportMessage(
      id = "MSG_1",
      ticketId = "TCK_123",
      senderType = "USER",
      senderId = "usr_1",
      senderName = "مجتبی",
      messageText = "سلام پشتیبانی"
    )

    val adminMsg = com.example.model.SupportMessage(
      id = "MSG_2",
      ticketId = "TCK_123",
      senderType = "ADMIN",
      senderId = "admin",
      senderName = "پشتیبانی رقابت",
      messageText = "سلام کاربر عزیز، پیام شما بررسی شد."
    )

    assertFalse("User message must not be marked as admin", userMsg.isAdmin)
    assertTrue("Admin message must be marked as admin", adminMsg.isAdmin)
  }
}


