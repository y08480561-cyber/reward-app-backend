package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SupportCategory
import com.example.model.SupportMessage
import com.example.model.SupportStatus
import com.example.model.SupportTicket
import com.example.ui.components.FeedbackTone
import com.example.ui.components.LuxuryBadge
import com.example.ui.components.LuxuryBadgeTone
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryEmptyState
import com.example.ui.components.LuxuryFeedbackBanner
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryLoadingState
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxuryTextField
import com.example.ui.components.LuxuryTopBar
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SupportScreen(
    tickets: List<SupportTicket>,
    activeTicket: SupportTicket?,
    messages: List<SupportMessage>,
    isLoading: Boolean,
    isSending: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onRefresh: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onCloseTicketDetail: () -> Unit,
    onCreateTicket: (String, String, String) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onClearMessages: () -> Unit,
    onBackToHome: () -> Unit
) {
    BackHandler {
        if (activeTicket != null) {
            onCloseTicketDetail()
        } else {
            onBackToHome()
        }
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxuryTopBar(
                title = if (activeTicket != null) "گفت‌وگوی تیکت" else "مرکز پشتیبانی",
                subtitle = if (activeTicket != null) {
                    "کد پیگیری: ${activeTicket.trackingCode.toPersianDigits()}"
                } else {
                    "ارتباط مستقیم با تیم پشتیبانی"
                },
                onBack = {
                    if (activeTicket != null) {
                        onCloseTicketDetail()
                    } else {
                        onBackToHome()
                    }
                },
                backTestTag = "btn_support_back",
                trailingContent = {
                    IconButton(
                        onClick = {
                            if (activeTicket != null) {
                                onOpenTicket(activeTicket.id)
                            } else {
                                onRefresh()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_support_refresh")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "به‌روزرسانی",
                            tint = GoldLight
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activeTicket != null) {
                    TicketConversationView(
                        ticket = activeTicket,
                        messages = messages,
                        isLoading = isLoading,
                        isSending = isSending,
                        errorMessage = errorMessage,
                        onSendMessage = { text -> onSendMessage(activeTicket.id, text) },
                        onRetry = { onOpenTicket(activeTicket.id) }
                    )
                } else {
                    SupportTabsView(
                        tickets = tickets,
                        isLoading = isLoading,
                        isSending = isSending,
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onOpenTicket = onOpenTicket,
                        onCreateTicket = onCreateTicket,
                        onClearMessages = onClearMessages,
                        onRefresh = onRefresh
                    )
                }
            }
        }
    }
}

@Composable
fun SupportTabsView(
    tickets: List<SupportTicket>,
    isLoading: Boolean,
    isSending: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onOpenTicket: (String) -> Unit,
    onCreateTicket: (String, String, String) -> Unit,
    onClearMessages: () -> Unit,
    onRefresh: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CharcoalSurface,
            contentColor = WarmWhite,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GoldAccent
                )
            },
            divider = {
                LuxuryHairlineDivider()
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.heightIn(min = 50.dp),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (selectedTab == 0) GoldLight else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "درخواست‌های من (${tickets.size.toPersianDigits()})",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedTab == 0) GoldLight else TextSecondary
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.heightIn(min = 50.dp),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AddComment,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (selectedTab == 1) GoldLight else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ثبت درخواست جدید",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedTab == 1) GoldLight else TextSecondary
                        )
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                LuxuryFeedbackBanner(
                    message = errorMessage,
                    tone = FeedbackTone.ERROR,
                    onDismiss = onClearMessages
                )
            }
            if (!successMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                LuxuryFeedbackBanner(
                    message = successMessage,
                    tone = FeedbackTone.SUCCESS,
                    onDismiss = onClearMessages
                )
            }
        }

        if (selectedTab == 0) {
            TicketsListTab(
                tickets = tickets,
                isLoading = isLoading,
                onOpenTicket = onOpenTicket,
                onGoToNewTicket = { selectedTab = 1 },
                onRefresh = onRefresh
            )
        } else {
            NewTicketTab(
                isSending = isSending,
                onSubmit = { subject, category, message ->
                    onCreateTicket(subject, category, message)
                    selectedTab = 0
                }
            )
        }
    }
}

@Composable
fun TicketsListTab(
    tickets: List<SupportTicket>,
    isLoading: Boolean,
    onOpenTicket: (String) -> Unit,
    onGoToNewTicket: () -> Unit,
    onRefresh: () -> Unit
) {
    if (isLoading && tickets.isEmpty()) {
        LuxuryLoadingState(message = "در حال دریافت فهرست تیکت‌ها…")
        return
    }

    if (tickets.isEmpty()) {
        LuxuryEmptyState(
            icon = Icons.Rounded.SupportAgent,
            title = "هنوز درخواستی ثبت نکرده‌اید",
            description = "در صورت داشتن پرسش درباره امتیازها، جوایز یا خریدها، می‌توانید تیکت پشتیبانی جدید ارسال کنید.",
            actionLabel = "ثبت اولین تیکت",
            onAction = onGoToNewTicket
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tickets, key = { it.id }) { ticket ->
            SupportTicketCard(ticket = ticket, onClick = { onOpenTicket(ticket.id) })
        }
    }
}

@Composable
fun SupportTicketCard(ticket: SupportTicket, onClick: () -> Unit) {
    LuxuryCard(
        modifier = Modifier.testTag("ticket_card_${ticket.trackingCode}"),
        onClick = onClick,
        borderColor = LuxuryBorder,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.ConfirmationNumber,
                    contentDescription = null,
                    tint = GoldLight,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = ticket.trackingCode.toPersianDigits(),
                    style = MaterialTheme.typography.labelLarge,
                    color = GoldLight
                )
            }
            TicketStatusBadge(status = ticket.statusEnum)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = ticket.subject,
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "دسته‌بندی: ${ticket.categoryEnum.titlePersian}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (ticket.lastMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianBlack.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (ticket.lastSenderType == "ADMIN") {
                        "پاسخ پشتیبانی: ${ticket.lastMessage}"
                    } else {
                        "پیام شما: ${ticket.lastMessage}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ticket.lastSenderType == "ADMIN") GoldLight else WarmWhite.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTicketDate(ticket.updatedAtMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "مشاهده گفت‌وگو",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldLight
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = GoldLight,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun TicketStatusBadge(status: SupportStatus) {
    val tone = when (status) {
        SupportStatus.NEW -> LuxuryBadgeTone.ERROR
        SupportStatus.IN_PROGRESS -> LuxuryBadgeTone.WARNING
        SupportStatus.ANSWERED -> LuxuryBadgeTone.SUCCESS
        SupportStatus.CLOSED -> LuxuryBadgeTone.NEUTRAL
    }
    LuxuryBadge(
        text = status.titlePersian,
        tone = tone
    )
}

@Composable
fun NewTicketTab(
    isSending: Boolean,
    onSubmit: (String, String, String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SupportCategory.SCORES) }
    var message by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LuxuryCard(
            borderColor = LuxuryBorder,
            contentPadding = PaddingValues(18.dp)
        ) {
            Text(
                text = "ثبت درخواست پشتیبانی جدید",
                style = MaterialTheme.typography.titleMedium,
                color = WarmWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "درخواست شما مستقیماً برای تیم پشتیبانی ارسال شده و پاسخ در همین بخش نمایش داده می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "موضوع درخواست",
                style = MaterialTheme.typography.labelLarge,
                color = GoldLight
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(SupportCategory.SCORES, SupportCategory.ADS, SupportCategory.PRIZES).forEach { cat ->
                    CategoryChip(
                        category = cat,
                        isSelected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(SupportCategory.MEMBERSHIP, SupportCategory.PURCHASE, SupportCategory.LOGIN, SupportCategory.OTHER).forEach { cat ->
                    CategoryChip(
                        category = cat,
                        isSelected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LuxuryTextField(
                value = subject,
                onValueChange = { subject = it },
                label = "عنوان درخواست",
                placeholder = "مثال: بررسی امتیاز مربع‌های امروز"
            )

            Spacer(modifier = Modifier.height(14.dp))

            LuxuryTextField(
                value = message,
                onValueChange = { message = it },
                label = "شرح کامل درخواست یا پرسش",
                placeholder = "جزئیات مورد نیاز را بنویسید…",
                singleLine = false,
                maxLines = 6,
                modifier = Modifier.heightIn(min = 128.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            LuxuryPrimaryButton(
                text = "ارسال درخواست",
                icon = Icons.AutoMirrored.Rounded.Send,
                isLoading = isSending,
                enabled = subject.isNotBlank() && message.isNotBlank(),
                onClick = {
                    if (subject.isNotBlank() && message.isNotBlank() && !isSending) {
                        onSubmit(subject.trim(), selectedCategory.name, message.trim())
                    }
                },
                modifier = Modifier.testTag("btn_submit_ticket")
            )
        }

        Spacer(modifier = Modifier.height(104.dp))
    }
}

@Composable
fun CategoryChip(
    category: SupportCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) GoldAccent.copy(alpha = 0.18f) else DarkGraySurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) GoldAccent else CharcoalBorder
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.titlePersian,
                color = if (isSelected) GoldLight else TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TicketConversationView(
    ticket: SupportTicket,
    messages: List<SupportMessage>,
    isLoading: Boolean,
    isSending: Boolean,
    errorMessage: String?,
    onSendMessage: (String) -> Unit,
    onRetry: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBarsBottomPx = WindowInsets.navigationBars.getBottom(density)
    val isImeVisible = imeBottomPx > 0
    val bottomNavHeightPx = with(density) { 92.dp.roundToPx() }
    val dynamicBottomPadding = with(density) {
        maxOf(imeBottomPx, navBarsBottomPx + bottomNavHeightPx).toDp()
    }

    LaunchedEffect(messages.size, isImeVisible) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ticket Summary Strip
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ticket.subject,
                            style = MaterialTheme.typography.titleSmall,
                            color = WarmWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TicketStatusBadge(status = ticket.statusEnum)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "دسته‌بندی: ${ticket.categoryEnum.titlePersian}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "ثبت: ${formatTicketDate(ticket.createdAtMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                LuxuryHairlineDivider()
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LuxuryFeedbackBanner(
                    message = errorMessage,
                    tone = FeedbackTone.ERROR,
                    onRetry = onRetry
                )
            }
        }

        if (isLoading && messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LuxuryLoadingState(message = "در حال دریافت پیام‌های تیکت…")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }
        }

        if (ticket.isClosed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottom = dynamicBottomPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "این تیکت بسته شده است. در صورت نیاز به راهنمایی بیشتر، می‌توانید درخواست جدیدی ثبت کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dynamicBottomPadding),
                color = CharcoalSurface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = BorderStroke(1.dp, LuxuryBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = {
                            Text(
                                text = "پاسخ خود را بنویسید…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_ticket_reply"),
                        shape = RoundedCornerShape(18.dp),
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = WarmWhite,
                            unfocusedTextColor = WarmWhite,
                            focusedContainerColor = ObsidianBlack,
                            unfocusedContainerColor = ObsidianBlack,
                            cursorColor = GoldAccent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (replyText.trim().isNotEmpty() && !isSending) {
                                    val text = replyText.trim()
                                    replyText = ""
                                    onSendMessage(text)
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val canSend = replyText.trim().isNotEmpty() && !isSending
                    IconButton(
                        onClick = {
                            if (replyText.trim().isNotEmpty() && !isSending) {
                                val text = replyText.trim()
                                replyText = ""
                                onSendMessage(text)
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) GoldGradientBrush else Brush.linearGradient(
                                    listOf(DarkGraySurface, DarkGraySurface)
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (canSend) GoldLight.copy(alpha = 0.6f) else CharcoalBorder,
                                shape = CircleShape
                            )
                            .testTag("btn_send_reply")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = GoldLight,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "ارسال پاسخ",
                                tint = if (canSend) ObsidianBlack else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: SupportMessage) {
    val isAdmin = message.isAdmin

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isAdmin) Alignment.Start else Alignment.End
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isAdmin) Arrangement.Start else Arrangement.End,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (isAdmin) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = GoldLight,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "پشتیبانی باشگاه",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = GoldLight
                )
            } else {
                Text(
                    text = "شما",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatMessageTime(message.createdAtMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isAdmin) 4.dp else 16.dp,
                bottomEnd = if (isAdmin) 16.dp else 4.dp
            ),
            color = if (isAdmin) CharcoalSurface else DarkGraySurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (isAdmin) LuxuryBorderStrong else CharcoalBorder
            )
        ) {
            Text(
                text = message.messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = WarmWhite,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

private fun formatTicketDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        sdf.format(Date(timestamp)).toPersianDigits()
    } catch (_: Exception) {
        ""
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.format(Date(timestamp)).toPersianDigits()
    } catch (_: Exception) {
        ""
    }
}
