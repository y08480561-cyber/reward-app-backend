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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
                            text = if (activeTicket != null) "گفت‌وگوی تیکت پشتیبانی" else "مرکز پشتیبانی و ارتباط با مدیریت",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmWhite
                        )
                        if (activeTicket != null) {
                            Text(
                                text = "کد پیگیری: ${activeTicket.trackingCode}",
                                fontSize = 12.sp,
                                color = GoldLight,
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
                            tint = GoldAccent
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
                            tint = GoldAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LuxuryBlack
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LuxuryDarkGradient)
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
        // Luxury Tab Selection
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardSurface,
            contentColor = WarmWhite,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GoldAccent
                )
            },
            modifier = Modifier.border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, LuxuryBorder, Color.Transparent)
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            )
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ListAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == 0) GoldLight else SilverAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "درخواست‌های من (${tickets.size.toPersianDigits()})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedTab == 0) GoldLight else SilverAccent
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == 1) GoldLight else SilverAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "ثبت درخواست جدید",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedTab == 1) GoldLight else SilverAccent
                        )
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
                shape = RoundedCornerShape(14.dp),
                color = ErrorRed.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage, color = WarmWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = WarmWhite, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (!successMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = SuccessGreen.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = successMessage, color = WarmWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = WarmWhite, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Tab Content
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
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.12f))
                    .border(1.dp, LuxuryBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = GoldLight,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "هنوز درخواست پشتیبانی ثبت نکرده‌اید",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = WarmWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "در صورت بروز هرگونه سوال یا مسئله پیرامون امتیازها، جوایز یا عضویت، می‌توانید تیکت جدید ارسال نمایید.",
                fontSize = 12.sp,
                color = SilverAccent,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGoToNewTicket,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(14.dp), spotColor = GoldAccent)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = LuxuryBlack, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ثبت اولین تیکت پشتیبانی", color = LuxuryBlack, fontWeight = FontWeight.ExtraBold)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
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
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .border(1.dp, LuxuryBorder, RoundedCornerShape(18.dp))
            .testTag("ticket_card_${ticket.trackingCode}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        tint = GoldLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ticket.trackingCode,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = GoldLight
                    )
                }
                TicketStatusBadge(status = ticket.statusEnum)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subject
            Text(
                text = ticket.subject,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = WarmWhite,
                maxLines = 1
            )

            // Category
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "موضوع: ${ticket.categoryEnum.titlePersian}",
                    fontSize = 11.sp,
                    color = SilverAccent
                )
            }

            // Last message preview
            if (ticket.lastMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1AF5F2E9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (ticket.lastSenderType == "ADMIN") "پاسخ مدیریت: ${ticket.lastMessage}" else "پیام شما: ${ticket.lastMessage}",
                        fontSize = 11.sp,
                        color = if (ticket.lastSenderType == "ADMIN") GoldLight else WarmWhite.copy(alpha = 0.85f),
                        maxLines = 2,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Footer: Date and Chevron
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTicketDate(ticket.updatedAtMs),
                    fontSize = 10.sp,
                    color = SilverAccent
                )
                Text(
                    text = "مشاهده گفت‌وگو ←",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight
                )
            }
        }
    }
}

@Composable
fun TicketStatusBadge(status: SupportStatus) {
    val (bgColor, textColor) = when (status) {
        SupportStatus.NEW -> Pair(Color(0x28EF4444), Color(0xFFF87171))
        SupportStatus.IN_PROGRESS -> Pair(Color(0x28F59E0B), Color(0xFFFBBF24))
        SupportStatus.ANSWERED -> Pair(Color(0x2810B981), Color(0xFF34D399))
        SupportStatus.CLOSED -> Pair(Color(0x2864748B), Color(0xFF94A3B8))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = status.titlePersian,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "ثبت درخواست پشتیبانی جدید",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = WarmWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "پیام شما مستقیماً برای مدیر ارسال شده و پاسخ در همین بخش نمایش داده می‌شود.",
                    fontSize = 11.sp,
                    color = SilverAccent
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Category Selection
                Text(
                    text = "انتخاب موضوع درخواست:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = GoldLight
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

                Spacer(modifier = Modifier.height(18.dp))

                // Subject Input
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("عنوان درخواست") },
                    placeholder = { Text("مثال: عدم افزایش امتیاز پس از مشاهده ویدیو", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LuxuryBorder,
                        focusedLabelColor = GoldLight,
                        unfocusedLabelColor = SilverAccent
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Message Body Input
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("متن و شرح کامل مشکل یا سوال") },
                    placeholder = { Text("لطفاً توضیحات خود را با جزئیات بنویسید...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    maxLines = 6,
                    textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LuxuryBorder,
                        focusedLabelColor = GoldLight,
                        unfocusedLabelColor = SilverAccent
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (subject.isNotBlank() && message.isNotBlank() && !isSending) {
                            onSubmit(subject.trim(), selectedCategory.name, message.trim())
                        }
                    },
                    enabled = subject.isNotBlank() && message.isNotBlank() && !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = GoldAccent)
                        .testTag("btn_submit_ticket"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = LuxuryBlack, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال ثبت درخواست...", color = LuxuryBlack)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = LuxuryBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ارسال درخواست به مدیریت", fontWeight = FontWeight.ExtraBold, color = LuxuryBlack)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
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
        color = if (isSelected) Color(0x33D4AF37) else Color(0x1AF5F2E9),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) GoldAccent else LuxuryBorder.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = category.titlePersian,
            color = if (isSelected) GoldLight else SilverAccent,
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
    val density = LocalDensity.current

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBarsBottomPx = WindowInsets.navigationBars.getBottom(density)
    val isImeVisible = imeBottomPx > 0

    // Height of floating AppBottomNavigation + clearance so reply bar sits cleanly above it
    val bottomNavHeightPx = with(density) { 92.dp.roundToPx() }

    // When IME is open, pad by IME height. When closed, pad by navigation bars + bottom navigation height.
    val dynamicBottomPadding = with(density) {
        maxOf(imeBottomPx, navBarsBottomPx + bottomNavHeightPx).toDp()
    }

    // Auto scroll to bottom when messages update or keyboard opens
    LaunchedEffect(messages.size, isImeVisible) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Ticket Header Banner - Luxury Styling
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LuxuryBlack,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.horizontalGradient(listOf(Color.Transparent, LuxuryBorder, Color.Transparent))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ticket.subject,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmWhite,
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
                        color = SilverAccent
                    )
                    Text(
                        text = "ثبت: ${formatTicketDate(ticket.createdAtMs)}",
                        fontSize = 10.sp,
                        color = SilverAccent
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
                shape = RoundedCornerShape(10.dp),
                color = ErrorRed.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = errorMessage, color = WarmWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRetry, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "تلاش مجدد", tint = WarmWhite, modifier = Modifier.size(16.dp))
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    .padding(bottom = dynamicBottomPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = SilverAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "این تیکت توسط مدیریت بسته شده است. در صورت نیاز به راهنمایی بیشتر، لطفاً یک درخواست جدید ثبت کنید.",
                        fontSize = 11.sp,
                        color = SilverAccent
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dynamicBottomPadding),
                color = CardSurface,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
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
                        placeholder = { Text("پاسخ خود را بنویسید...", fontSize = 12.sp, color = SilverAccent) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_ticket_reply"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LuxuryBorder,
                            focusedTextColor = WarmWhite,
                            unfocusedTextColor = WarmWhite
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
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
                            .size(46.dp)
                            .background(
                                if (replyText.trim().isNotEmpty()) GoldGradientBrush else Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B))),
                                CircleShape
                            )
                            .shadow(if (replyText.trim().isNotEmpty()) 6.dp else 0.dp, CircleShape, spotColor = GoldAccent)
                            .testTag("btn_send_reply")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(color = LuxuryBlack, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "ارسال پاسخ",
                                tint = if (replyText.trim().isNotEmpty()) LuxuryBlack else SilverAccent,
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
                Icon(Icons.Default.Verified, contentDescription = null, tint = GoldLight, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "پشتیبانی مدیریت",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight
                )
            } else {
                Text(
                    text = "شما",
                    fontSize = 10.sp,
                    color = SilverAccent
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatMessageTime(message.createdAtMs),
                fontSize = 9.sp,
                color = SilverAccent.copy(alpha = 0.8f)
            )
        }

        // Bubble Body - Luxury Dark Styling
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isAdmin) 3.dp else 16.dp,
                bottomEnd = if (isAdmin) 16.dp else 3.dp
            ),
            color = if (isAdmin) Color(0xFF182030) else Color(0xFF222B3C),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isAdmin) LuxuryBorder else Color(0x33C0C0C0)
            ),
            shadowElevation = 2.dp
        ) {
            Text(
                text = message.messageText,
                color = WarmWhite,
                fontSize = 13.sp,
                modifier = Modifier.padding(14.dp),
                lineHeight = 20.sp
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
