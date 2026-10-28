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
}
