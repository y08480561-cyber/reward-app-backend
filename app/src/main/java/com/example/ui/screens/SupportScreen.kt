package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SupportCategory
import com.example.model.SupportMessage
import com.example.model.SupportStatus
import com.example.model.SupportTicket
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    tickets: List<SupportTicket>,
    activeTicket: SupportTicket?,
    messages: List<SupportMessage>,
    isLoading: Boolean,
    isSending: Boolean,
    giftBannerConfig: com.example.model.RewardAppearanceConfig = com.example.model.RewardAppearanceConfig(),
    errorMessage: String?,
    successMessage: String?,
    onRefresh: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onCloseTicketDetail: () -> Unit,
    onCreateTicket: (String, String, String) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onSaveGiftBanner: (title: String, description: String, imageUrl: String, isActive: Boolean, termsText: String) -> Unit = { _, _, _, _, _ -> },
    onClearMessages: () -> Unit,
    onBackToHome: () -> Unit
) {
    // Handle back button: if in conversation view, close detail first; else go back to Home/Profile
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (activeTicket != null) "گفت‌وگوی تیکت" else "پشتیبانی و ارتباط با مدیریت",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        if (activeTicket != null) {
                            Text(
                                text = "کد پیگیری: ${activeTicket.trackingCode}",
                                fontSize = 12.sp,
                                color = GoldAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeTicket != null) {
                                onCloseTicketDetail()
                            } else {
                                onBackToHome()
                            }
                        },
                        modifier = Modifier.testTag("btn_support_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = NavyPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (activeTicket != null) {
                                onOpenTicket(activeTicket.id)
                            } else {
                                onRefresh()
                            }
                        },
                        modifier = Modifier.testTag("btn_support_refresh")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "به‌روزرسانی",
                            tint = NavyPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            if (activeTicket != null) {
                // Detail / Conversation Thread View
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
                // Tickets List + Create New Tab View
                SupportTabsView(
                    tickets = tickets,
                    isLoading = isLoading,
                    isSending = isSending,
                    giftBannerConfig = giftBannerConfig,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    onOpenTicket = onOpenTicket,
                    onCreateTicket = onCreateTicket,
                    onSaveGiftBanner = onSaveGiftBanner,
                    onClearMessages = onClearMessages,
                    onRefresh = onRefresh
                )
            }
        }
    }
}

@Composable
fun SupportTabsView(
    tickets: List<SupportTicket>,
    isLoading: Boolean,
    isSending: Boolean,
    giftBannerConfig: com.example.model.RewardAppearanceConfig,
    errorMessage: String?,
    successMessage: String?,
    onOpenTicket: (String) -> Unit,
    onCreateTicket: (String, String, String) -> Unit,
    onSaveGiftBanner: (title: String, description: String, imageUrl: String, isActive: Boolean, termsText: String) -> Unit,
    onClearMessages: () -> Unit,
    onRefresh: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Selection
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = NavyPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GoldAccent
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تیکت‌ها (${tickets.size.toPersianDigits()})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("درخواست جدید", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مدیریت بنر هدیه", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        // Error & Success Feedback
        if (!errorMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = ErrorRed.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (!successMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = SuccessGreen.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = successMessage, color = SuccessGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            0 -> {
                TicketsListTab(
                    tickets = tickets,
                    isLoading = isLoading,
                    onOpenTicket = onOpenTicket,
                    onGoToNewTicket = { selectedTab = 1 },
                    onRefresh = onRefresh
                )
            }
            1 -> {
                NewTicketTab(
                    isSending = isSending,
                    onSubmit = { subject, category, message ->
                        onCreateTicket(subject, category, message)
                        selectedTab = 0
                    }
                )
            }
            2 -> {
                GiftBannerAdminTab(
                    giftBannerConfig = giftBannerConfig,
                    isSaving = isLoading,
                    onSave = onSaveGiftBanner
                )
            }
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GoldAccent)
        }
        return
    }

    if (tickets.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "هنوز درخواست پشتیبانی ثبت نکرده‌اید",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = NavyPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "در صورت بروز هرگونه سوال یا مشکل پیرامون امتیازها، جوایز یا عضویت، می‌توانید تیکت جدید ارسال نمایید.",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGoToNewTicket,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ثبت اولین تیکت پشتیبانی", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tickets, key = { it.id }) { ticket ->
            SupportTicketCard(ticket = ticket, onClick = { onOpenTicket(ticket.id) })
        }
    }
}

@Composable
fun SupportTicketCard(ticket: SupportTicket, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("ticket_card_${ticket.trackingCode}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Tracking Code + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ticket.trackingCode,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = NavyPrimary
                    )
                }
                TicketStatusBadge(status = ticket.statusEnum)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subject
            Text(
                text = ticket.subject,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary,
                maxLines = 1
            )

            // Category
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "موضوع: ${ticket.categoryEnum.titlePersian}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Last message preview
            if (ticket.lastMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (ticket.lastSenderType == "ADMIN") "پاسخ مدیریت: ${ticket.lastMessage}" else "پیام شما: ${ticket.lastMessage}",
                        fontSize = 11.sp,
                        color = if (ticket.lastSenderType == "ADMIN") Color(0xFF0284C7) else Color(0xFF475569),
                        maxLines = 2,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Footer: Date and Chevron
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTicketDate(ticket.updatedAtMs),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "مشاهده گفت‌وگو ←",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
            }
        }
    }
}

@Composable
fun TicketStatusBadge(status: SupportStatus) {
    val (bgColor, textColor) = when (status) {
        SupportStatus.NEW -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626))
        SupportStatus.IN_PROGRESS -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
        SupportStatus.ANSWERED -> Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
        SupportStatus.CLOSED -> Pair(Color(0xFFE2E8F0), Color(0xFF64748B))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Text(
            text = status.titlePersian,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
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
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ثبت درخواست پشتیبانی جدید",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NavyPrimary
                )
                Text(
                    text = "پیام شما مستقیماً برای مدیر ارسال شده و پاسخ در همین بخش نمایش داده می‌شود.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category Selection
                Text(
                    text = "انتخاب موضوع درخواست:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NavyPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val firstRow = listOf(SupportCategory.SCORES, SupportCategory.ADS, SupportCategory.PRIZES)
                    firstRow.forEach { cat ->
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
                    val secondRow = listOf(SupportCategory.MEMBERSHIP, SupportCategory.PURCHASE, SupportCategory.LOGIN, SupportCategory.OTHER)
                    secondRow.forEach { cat ->
                        CategoryChip(
                            category = cat,
                            isSelected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subject TextField
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("عنوان درخواست") },
                    placeholder = { Text("مثلاً: عدم ثبت امتیاز تماشای تبلیغ") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ticket_subject"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message Body TextField
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("متن کامل پیام") },
                    placeholder = { Text("لطفاً توضیحات کامل و شماره مربع یا مشکل خود را به دقت بنویسید...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ticket_message"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onSubmit(subject, selectedCategory.key, message)
                    },
                    enabled = !isSending && subject.trim().length >= 3 && message.trim().length >= 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_submit_ticket"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال ثبت درخواست...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ارسال درخواست به مدیریت", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) NavyPrimary else Color(0xFFF1F5F9),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) GoldAccent else Color(0xFFE2E8F0)
        )
    ) {
        Text(
            text = category.titlePersian,
            color = if (isSelected) GoldAccent else Color(0xFF475569),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            maxLines = 1
        )
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

    // Auto scroll to bottom when messages update
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Ticket Header Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NavyPrimary,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ticket.subject,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    TicketStatusBadge(status = ticket.statusEnum)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "موضوع: ${ticket.categoryEnum.titlePersian}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "ثبت: ${formatTicketDate(ticket.createdAtMs)}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Error Banner
        if (!errorMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = ErrorRed.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = errorMessage, color = ErrorRed, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "تلاش مجدد", tint = ErrorRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Message Bubbles List
        if (isLoading && messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldAccent)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }
        }

        // Reply Bar or Closed Notice
        if (ticket.isClosed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE2E8F0)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "این تیکت توسط مدیریت بسته شده است. در صورت نیاز به راهنمایی بیشتر، لطفاً یک درخواست جدید ثبت کنید.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("پاسخ خود را بنویسید...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_ticket_reply"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (replyText.trim().isNotEmpty() && !isSending) {
                                val text = replyText.trim()
                                replyText = ""
                                onSendMessage(text)
                            }
                        },
                        enabled = !isSending && replyText.trim().isNotEmpty(),
                        modifier = Modifier
                            .size(44.dp)
                            .background(NavyPrimary, CircleShape)
                            .testTag("btn_send_reply")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "ارسال پاسخ",
                                tint = GoldAccent,
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
        // Sender label + Timestamp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isAdmin) Arrangement.Start else Arrangement.End,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (isAdmin) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "پشتیبانی مدیریت",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldAccent
                )
            } else {
                Text(
                    text = "شما",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatMessageTime(message.createdAtMs),
                fontSize = 9.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Bubble Body
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isAdmin) 2.dp else 16.dp,
                bottomEnd = if (isAdmin) 16.dp else 2.dp
            ),
            color = if (isAdmin) NavyPrimary else Color(0xFFE2E8F0),
            shadowElevation = 1.dp
        ) {
            Text(
                text = message.messageText,
                color = if (isAdmin) Color.White else NavyPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

private fun formatTicketDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp)).toPersianDigits()
    } catch (e: Exception) {
        ""
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp)).toPersianDigits()
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun GiftBannerAdminTab(
    giftBannerConfig: com.example.model.RewardAppearanceConfig,
    isSaving: Boolean,
    onSave: (title: String, description: String, imageUrl: String, isActive: Boolean, termsText: String) -> Unit
) {
    var heroTitle by remember(giftBannerConfig) { mutableStateOf(giftBannerConfig.heroTitle) }
    var heroSubtitle by remember(giftBannerConfig) { mutableStateOf(giftBannerConfig.heroSubtitle) }
    var heroBannerUrl by remember(giftBannerConfig) { mutableStateOf(giftBannerConfig.heroBannerUrl) }
    var termsText by remember(giftBannerConfig) { mutableStateOf(giftBannerConfig.termsText) }
    var isActive by remember(giftBannerConfig) { mutableStateOf(giftBannerConfig.isActive) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مدیریت بنر هدیه (تنظیمات ویترین جوایز)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NavyPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "اطلاعات وارد شده در این بخش مستقیماً در صفحه ویترین جوایز اپلیکیشن به عنوان بنر معرفی هدیه نمایش داده می‌شود.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = heroTitle,
                    onValueChange = { heroTitle = it },
                    label = { Text("عنوان هدیه") },
                    placeholder = { Text("مثلاً: گوشی هوشمند سامسونگ Galaxy A55") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_banner_title"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle Input
                OutlinedTextField(
                    value = heroSubtitle,
                    onValueChange = { heroSubtitle = it },
                    label = { Text("توضیحات هدیه") },
                    placeholder = { Text("مثلاً: هدیه ویژه این دوره برای برندگان مسابقه ۱۰۰ مربع") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("input_banner_subtitle"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Banner Image URL Input
                OutlinedTextField(
                    value = heroBannerUrl,
                    onValueChange = { heroBannerUrl = it },
                    label = { Text("لینک تصویر هدیه (URL)") },
                    placeholder = { Text("https://example.com/image.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_banner_url"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Terms Input
                OutlinedTextField(
                    value = termsText,
                    onValueChange = { termsText = it },
                    label = { Text("متن راهنما یا شرایط تکمیلی") },
                    placeholder = { Text("این بنر صرفاً جهت اطلاع‌رسانی می‌باشد.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_banner_terms"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Active Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "وضعیت نمایش بنر هدیه",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Text(
                            text = if (isActive) "بنر هدیه در اپلیکیشن فعال و قابل مشاهده است" else "بنر هدیه در حالت غیرفعال/در حال بروزرسانی قرار دارد",
                            fontSize = 11.sp,
                            color = if (isActive) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        modifier = Modifier.testTag("switch_banner_active")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save Button
                Button(
                    onClick = {
                        onSave(heroTitle, heroSubtitle, heroBannerUrl, isActive, termsText)
                    },
                    enabled = !isSaving && heroTitle.trim().isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_save_gift_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال ذخیره‌سازی...")
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ذخیره تغییرات بنر هدیه", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
