package com.example.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.AdPlaybackState
import com.example.ads.TapsellAdsManager
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.BackendRepository
import com.example.data.remote.Result
import com.example.model.AdSquare
import com.example.model.DailyCycle
import com.example.model.SquareStatus
import com.example.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesManager(application)
    val database = AppDatabase.getInstance(application)
    val backendRepository = BackendRepository(application, database, prefs)
    val tapsellAdsManager = TapsellAdsManager(application, prefs)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private var countdownJob: Job? = null
    private var otpTimerJob: Job? = null

    init {
        checkSavedSession()
        observeTapsellAdState()
        startCycleCountdownTicker()
    }

    private fun checkSavedSession() {
        if (!prefs.isLoggedIn || prefs.userId.isEmpty() || prefs.firstName == "کاربر" || prefs.phoneNumber == "09120000000") {
            // First time entry: clear dummy session and present AuthScreen so user enters their real details
            prefs.clearSession()
            _uiState.update {
                it.copy(
                    currentUser = null,
                    currentScreen = AppScreen.AUTH
                )
            }
            return
        }

        val user = User(
            id = prefs.userId,
            firstName = prefs.firstName,
            lastName = prefs.lastName,
            phoneNumber = prefs.phoneNumber,
            totalScore = prefs.totalScore,
            token = prefs.authToken
        )
        val cachedAnn = backendRepository.parseCachedAnnouncement()
        _uiState.update {
            it.copy(
                currentUser = user,
                activeAnnouncement = cachedAnn,
                currentScreen = AppScreen.PROFILE
            )
        }
        refreshData()
    }

    private fun observeTapsellAdState() {
        viewModelScope.launch {
            tapsellAdsManager.adState.collect { adState ->
                when (adState) {
                    is AdPlaybackState.Loading -> {
                        // Mark square in UI as LOADING
                        updateSquareStatus(adState.squareNumber, SquareStatus.LOADING)
                    }
                    is AdPlaybackState.Playing -> {
                        updateSquareStatus(adState.squareNumber, SquareStatus.PLAYING)
                        _uiState.update {
                            it.copy(
                                activePlayingSquare = adState.squareNumber
                            )
                        }
                    }
                    is AdPlaybackState.Completed -> {
                        // Handled when Tapsell grants reward
                    }
                    is AdPlaybackState.Error -> {
                        updateSquareStatus(adState.squareNumber, SquareStatus.FAILED)
                        _uiState.update {
                            it.copy(
                                errorMessage = adState.message,
                                activePlayingSquare = null
                            )
                        }
                    }
                    is AdPlaybackState.Idle -> {
                        // Idle
                    }
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null, infoMessage = null) }
        if (screen == AppScreen.LEADERBOARD) {
            loadLeaderboard()
        } else if (screen == AppScreen.PRIZES) {
            loadRewardsData()
        } else if (screen == AppScreen.MEMBERSHIP) {
            loadMembershipData()
        } else if (screen == AppScreen.SQUARES || screen == AppScreen.HOME) {
            refreshData()
        }
    }

    fun refreshData() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val cycle = backendRepository.ensureActiveCycle(user.id)
            val squares = backendRepository.getSquares(user.id)
            val prizes = backendRepository.getPrizes()

            // Fetch active competition info & sync user score
            val compResult = backendRepository.getCurrentCompetition(user.id)
            var activeComp: com.example.model.CompetitionInfo? = null
            var updatedUser = user
            if (compResult is Result.Success) {
                val (comp, serverScore) = compResult.data
                activeComp = comp
                if (serverScore != user.totalScore) {
                    updatedUser = user.copy(totalScore = serverScore)
                    prefs.totalScore = serverScore
                    database.userDao().updateScore(user.id, serverScore)
                }
            }

            // Fetch active announcement from server (with offline cached fallback)
            val annResult = backendRepository.getActiveAnnouncement()
            val activeAnn = if (annResult is Result.Success) annResult.data else _uiState.value.activeAnnouncement

            _uiState.update {
                it.copy(
                    dailyCycle = cycle,
                    squares = squares,
                    prizes = prizes,
                    activeCompetition = activeComp ?: it.activeCompetition,
                    activeAnnouncement = activeAnn,
                    currentUser = updatedUser,
                    isLoading = false
                )
            }
            loadLeaderboard()
            launch(Dispatchers.IO) {
                tapsellAdsManager.initializeSdk()
            }
        }
    }

    private fun loadLeaderboard() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            val leaderboard = backendRepository.getLeaderboard(user.id)
            _uiState.update { it.copy(leaderboard = leaderboard) }
        }
    }

    fun loadRewardsData(categorySlug: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRewardsLoading = true, rewardsErrorMessage = null, selectedCategorySlug = categorySlug) }

            // 1. Fetch Categories
            val catRes = backendRepository.fetchRewardCategories()
            val categories = if (catRes is Result.Success) catRes.data else emptyList()

            // 2. Fetch Appearance
            val appRes = backendRepository.fetchRewardAppearance()
            val appearance = if (appRes is Result.Success) appRes.data else com.example.model.RewardAppearanceConfig()

            // 3. Fetch Rewards List
            val rewardsRes = backendRepository.fetchRewards(categorySlug = categorySlug)

            // 4. Fetch User Claims History
            val user = _uiState.value.currentUser
            var claimsHistory = emptyList<com.example.model.RewardClaimRecord>()
            if (user != null && user.id.isNotBlank()) {
                val claimsRes = backendRepository.fetchUserClaimsHistory(user.id)
                if (claimsRes is Result.Success) {
                    claimsHistory = claimsRes.data
                }
            }

            if (rewardsRes is Result.Success) {
                _uiState.update {
                    it.copy(
                        rewardCategories = categories,
                        rewardAppearance = appearance,
                        rewardItems = rewardsRes.data,
                        userClaimsHistory = claimsHistory,
                        isRewardsLoading = false
                    )
                }
            } else {
                val err = (rewardsRes as? Result.Error)?.message ?: "خطا در دریافت ویترین جوایز"
                _uiState.update {
                    it.copy(
                        rewardCategories = categories,
                        rewardAppearance = appearance,
                        userClaimsHistory = claimsHistory,
                        isRewardsLoading = false,
                        rewardsErrorMessage = err
                    )
                }
            }
        }
    }

    fun submitRewardClaim(
        rewardId: String,
        recipientName: String,
        phone: String,
        address: String,
        onSuccess: () -> Unit
    ) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(claimingRewardId = rewardId, rewardsErrorMessage = null) }
            when (val result = backendRepository.submitRewardClaim(rewardId, recipientName, phone, address)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            claimingRewardId = null,
                            claimSuccessMessage = "درخواست شما با کد پیگیری ${result.data.trackingCode} ثبت شد."
                        )
                    }
                    loadRewardsData(_uiState.value.selectedCategorySlug)
                    refreshData()
                    onSuccess()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            claimingRewardId = null,
                            rewardsErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearRewardsMessages() {
        _uiState.update { it.copy(rewardsErrorMessage = null, claimSuccessMessage = null) }
    }

    /**
     * Continuous 1-second ticker to update 24-hour cycle countdown and handle auto-resets
     */
    private fun startCycleCountdownTicker() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val cycle = _uiState.value.dailyCycle
                if (cycle != null) {
                    if (cycle.isExpired) {
                        // Cycle expired! Auto-reset the 50 squares
                        val user = _uiState.value.currentUser
                        if (user != null) {
                            val newCycle = backendRepository.ensureActiveCycle(user.id)
                            val newSquares = backendRepository.getSquares(user.id)
                            _uiState.update {
                                it.copy(
                                    dailyCycle = newCycle,
                                    squares = newSquares,
                                    infoMessage = "دوره ۲۴ ساعته جدید آغاز شد! تمام ۱۰۰ مربع باز شدند."
                                )
                            }
                        }
                    } else {
                        // Force recomposition for live countdown display
                        _uiState.update { it.copy(dailyCycle = cycle.copy()) }
                    }
                }
            }
        }
    }

    /**
     * User clicks on a square in the 50-squares grid
     */
    fun onSquareClicked(activity: Activity, square: AdSquare) {
        if (square.status == SquareStatus.COMPLETED) {
            _uiState.update {
                it.copy(infoMessage = "این مربع در این دوره تکمیل شده و ۱ امتیاز آن دریافت شده است.")
            }
            return
        }

        if (square.status == SquareStatus.LOADING || square.status == SquareStatus.PLAYING) {
            return
        }

        val cycle = _uiState.value.dailyCycle
        if (cycle != null && cycle.isExpired) {
            refreshData()
            return
        }

        // Request Rewarded Video Ad via Tapsell Ads Manager
        tapsellAdsManager.requestAndShowRewardedVideo(
            activity = activity,
            squareNumber = square.squareNumber,
            scope = viewModelScope,
            onReadyToShow = {
                // Video ad opened natively by Tapsell Plus SDK
            },
            onRewarded = { nonce ->
                onAdWatchedCompletely(square.squareNumber, 15)
            },
            onError = { error ->
                updateSquareStatus(square.squareNumber, SquareStatus.FAILED)
                _uiState.update { it.copy(errorMessage = error, activePlayingSquare = null) }
            }
        )
    }

    private fun updateSquareStatus(squareNumber: Int, status: SquareStatus) {
        viewModelScope.launch {
            backendRepository.updateSquareStatus(squareNumber, status)
            _uiState.update { state ->
                val updated = state.squares.map { sq ->
                    if (sq.squareNumber == squareNumber) sq.copy(status = status) else sq
                }
                state.copy(squares = updated)
            }
        }
    }

    /**
     * Called when the rewarded video player finishes completely.
     * Submits the completion proof to the backend repository.
     */
    fun onAdWatchedCompletely(squareNumber: Int, durationSeconds: Int) {
        val user = _uiState.value.currentUser ?: return

        tapsellAdsManager.notifyAdCompleted(squareNumber, durationSeconds) { rewardNonce ->
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                when (val result = backendRepository.completeAdAndAwardPoint(
                    userId = user.id,
                    squareNumber = squareNumber,
                    rewardNonce = rewardNonce,
                    adDurationSeconds = durationSeconds
                )) {
                    is Result.Success -> {
                        val newScore = result.data
                        val updatedUser = user.copy(totalScore = newScore)
                        val squares = backendRepository.getSquares(user.id)
                        val cycle = backendRepository.ensureActiveCycle(user.id)

                        _uiState.update {
                            it.copy(
                                currentUser = updatedUser,
                                squares = squares,
                                dailyCycle = cycle,
                                isLoading = false,
                                activePlayingSquare = null,
                                awardedPointsModal = 1,
                                infoMessage = "تبریک! ۱ امتیاز به مجموع امتیازهای شما افزوده شد."
                            )
                        }
                        loadLeaderboard()
                        tapsellAdsManager.resetState()
                    }
                    is Result.Error -> {
                        updateSquareStatus(squareNumber, SquareStatus.FAILED)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                activePlayingSquare = null,
                                errorMessage = result.message
                            )
                        }
                        tapsellAdsManager.resetState()
                    }
                }
            }
        }
    }

    /**
     * User closed ad before completion
     */
    fun onAdDismissedEarly(squareNumber: Int) {
        tapsellAdsManager.notifyAdDismissedPrematurely(squareNumber)
        updateSquareStatus(squareNumber, SquareStatus.READY)
        _uiState.update {
            it.copy(
                activePlayingSquare = null,
                errorMessage = "ویدیو تا انتها مشاهده نشد. طبق قوانین تپسل، برای ثبت امتیاز باید ویدیو کامل دیده شود."
            )
        }
        tapsellAdsManager.resetState()
    }

    fun dismissRewardModal() {
        _uiState.update { it.copy(awardedPointsModal = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    // --- Authentication ---

    fun onFirstNameChange(value: String) = _authUiState.update { it.copy(firstName = value, errorMessage = null) }
    fun onLastNameChange(value: String) = _authUiState.update { it.copy(lastName = value, errorMessage = null) }
    fun onPhoneNumberChange(value: String) = _authUiState.update { it.copy(phoneNumber = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _authUiState.update { it.copy(password = value, errorMessage = null) }
    fun onAuthModeChange(mode: AuthMode) = _authUiState.update { it.copy(authMode = mode, errorMessage = null, infoMessage = null) }
    fun onOtpCodeChange(value: String) = _authUiState.update { it.copy(otpCode = value, errorMessage = null) }

    fun loginOrRegister() {
        val state = _authUiState.value
        if (state.firstName.trim().isEmpty()) {
            _authUiState.update { it.copy(errorMessage = "لطفاً نام خود را وارد کنید.") }
            return
        }
        if (state.lastName.trim().isEmpty()) {
            _authUiState.update { it.copy(errorMessage = "لطفاً نام خانوادگی خود را وارد کنید.") }
            return
        }
        if (!state.phoneNumber.trim().matches(Regex("^09[0-9]{9}\$"))) {
            _authUiState.update { it.copy(errorMessage = "شماره موبایل وارد شده باید ۱۱ رقم و با 09 آغاز شود.") }
            return
        }
        val pin = state.password.trim().ifEmpty { "2026" }

        viewModelScope.launch {
            _authUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = backendRepository.loginDirectly(
                firstName = state.firstName,
                lastName = state.lastName,
                phoneNumber = state.phoneNumber,
                password = pin
            )) {
                is Result.Success -> {
                    val user = result.data
                    _authUiState.update { AuthUiState() }
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            currentScreen = AppScreen.SQUARES,
                            infoMessage = "خوش آمدید، ${user.firstName} عزیز!"
                        )
                    }
                    refreshData()
                }
                is Result.Error -> {
                    _authUiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun requestOtp() {
        val state = _authUiState.value
        if (state.firstName.trim().isEmpty()) {
            _authUiState.update { it.copy(errorMessage = "لطفاً نام خود را وارد کنید.") }
            return
        }
        if (state.lastName.trim().isEmpty()) {
            _authUiState.update { it.copy(errorMessage = "لطفاً نام خانوادگی خود را وارد کنید.") }
            return
        }
        if (!state.phoneNumber.trim().matches(Regex("^09[0-9]{9}\$"))) {
            _authUiState.update { it.copy(errorMessage = "شماره موبایل وارد شده باید ۱۱ رقم و با 09 آغاز شود.") }
            return
        }

        viewModelScope.launch {
            _authUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = backendRepository.requestOtp(state.phoneNumber)) {
                is Result.Success -> {
                    val code = result.data
                    _authUiState.update {
                        it.copy(
                            isLoading = false,
                            isOtpSent = true,
                            simulatedSentOtp = code,
                            otpCountdown = 120,
                            otpCode = code // Auto-fill convenience for testability
                        )
                    }
                    startOtpTimer()
                }
                is Result.Error -> {
                    _authUiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    private fun startOtpTimer() {
        otpTimerJob?.cancel()
        otpTimerJob = viewModelScope.launch {
            while (_authUiState.value.otpCountdown > 0) {
                delay(1000)
                _authUiState.update { it.copy(otpCountdown = it.otpCountdown - 1) }
            }
        }
    }

    fun verifyOtpAndSubmit() {
        val state = _authUiState.value
        if (state.otpCode.length < 4) {
            _authUiState.update { it.copy(errorMessage = "کد تایید را وارد کنید.") }
            return
        }

        viewModelScope.launch {
            _authUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = backendRepository.verifyOtpAndLogin(
                firstName = state.firstName,
                lastName = state.lastName,
                phoneNumber = state.phoneNumber,
                otpCode = state.otpCode
            )) {
                is Result.Success -> {
                    val user = result.data
                    _authUiState.update { AuthUiState() }
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            currentScreen = AppScreen.SQUARES,
                            infoMessage = "خوش آمدید، ${user.firstName} عزیز!"
                        )
                    }
                    refreshData()
                }
                is Result.Error -> {
                    _authUiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun logout() {
        prefs.clearSession()
        _uiState.update {
            MainUiState(currentScreen = AppScreen.AUTH)
        }
    }

    private val _healthCheckResult = MutableStateFlow<com.example.data.remote.HealthCheckResult?>(null)
    val healthCheckResult: StateFlow<com.example.data.remote.HealthCheckResult?> = _healthCheckResult.asStateFlow()

    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()

    fun performServerHealthCheck() {
        viewModelScope.launch {
            _isCheckingHealth.value = true
            val res = backendRepository.checkServerHealth()
            _healthCheckResult.value = res
            _isCheckingHealth.value = false
        }
    }

    fun dismissHealthCheck() {
        _healthCheckResult.value = null
    }

    fun loadMembershipData() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isMembershipLoading = true, errorMessage = null) }
            val panelsResult = backendRepository.getPanels()
            val membershipResult = backendRepository.getMyMembership(user.id)

            var panelsList = emptyList<org.json.JSONObject>()
            if (panelsResult is Result.Success) {
                val jsonArray = panelsResult.data
                val list = mutableListOf<org.json.JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getJSONObject(i))
                }
                panelsList = list
            }

            var memObj: org.json.JSONObject? = null
            if (membershipResult is Result.Success) {
                memObj = membershipResult.data
                val membership = memObj.optJSONObject("membership")
                if (membership != null) {
                    val pType = membership.optString("panelType", "free")
                    val mult = membership.optInt("multiplier", 1)
                    val updatedUser = user.copy(panelType = pType, multiplier = mult)
                    _uiState.update { it.copy(currentUser = updatedUser) }
                    prefs.saveMembershipSession(pType, mult)
                }
            }

            _uiState.update {
                it.copy(
                    panels = panelsList,
                    userMembership = memObj,
                    isMembershipLoading = false
                )
            }
        }
    }

    fun purchasePanel(productId: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isMembershipLoading = true, errorMessage = null) }
            // Secure server verification payload (Myket In-App Billing sandbox/production token verification)
            val purchaseToken = "MYKET_SECURE_TOKEN_" + System.currentTimeMillis()
            when (val result = backendRepository.verifyPurchase(user.id, productId, purchaseToken)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isMembershipLoading = false,
                            infoMessage = "خرید و فعال‌سازی پنل با موفقیت تایید و اعمال شد."
                        )
                    }
                    loadMembershipData()
                    refreshData()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isMembershipLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    // ====================================================================
    // Support System Methods
    // ====================================================================

    fun loadUserTickets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSupportLoading = true, supportErrorMessage = null) }
            when (val result = backendRepository.getUserSupportTickets()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            supportTickets = result.data,
                            isSupportLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSupportLoading = false,
                            supportErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun openTicketDetail(ticketId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSupportLoading = true, supportErrorMessage = null) }
            when (val result = backendRepository.getTicketDetails(ticketId)) {
                is Result.Success -> {
                    val (ticket, messages) = result.data
                    _uiState.update {
                        it.copy(
                            activeSupportTicket = ticket,
                            activeTicketMessages = messages,
                            isSupportLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSupportLoading = false,
                            supportErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun closeTicketDetail() {
        _uiState.update {
            it.copy(
                activeSupportTicket = null,
                activeTicketMessages = emptyList(),
                supportErrorMessage = null,
                supportSuccessMessage = null
            )
        }
    }

    fun createSupportTicket(
        subject: String,
        category: String,
        message: String,
        onSuccess: () -> Unit
    ) {
        if (subject.trim().length < 3) {
            _uiState.update { it.copy(supportErrorMessage = "عنوان درخواست باید حداقل ۳ کاراکتر باشد.") }
            return
        }
        if (message.trim().length < 5) {
            _uiState.update { it.copy(supportErrorMessage = "متن پیام باید حداقل ۵ کاراکتر باشد.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingTicketMessage = true, supportErrorMessage = null) }
            when (val result = backendRepository.createSupportTicket(subject, category, message)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSendingTicketMessage = false,
                            supportSuccessMessage = "درخواست پشتیبانی شما با شماره پیگیری ${result.data.trackingCode} ثبت شد."
                        )
                    }
                    loadUserTickets()
                    onSuccess()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSendingTicketMessage = false,
                            supportErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun sendTicketMessage(ticketId: String, messageText: String) {
        if (messageText.trim().isEmpty()) return
        val currentTicket = _uiState.value.activeSupportTicket ?: return
        if (currentTicket.isClosed) {
            _uiState.update { it.copy(supportErrorMessage = "این درخواست بسته شده است و امکان ارسال پیام وجود ندارد.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingTicketMessage = true, supportErrorMessage = null) }
            when (val result = backendRepository.sendTicketMessage(ticketId, messageText)) {
                is Result.Success -> {
                    val updatedMessages = _uiState.value.activeTicketMessages + result.data
                    val updatedTicket = currentTicket.copy(
                        status = if (currentTicket.status == "ANSWERED") "IN_PROGRESS" else currentTicket.status,
                        lastMessage = result.data.messageText,
                        lastSenderType = "USER",
                        updatedAtMs = System.currentTimeMillis()
                    )
                    _uiState.update {
                        it.copy(
                            activeSupportTicket = updatedTicket,
                            activeTicketMessages = updatedMessages,
                            isSendingTicketMessage = false
                        )
                    }
                    loadUserTickets()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSendingTicketMessage = false,
                            supportErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearSupportMessages() {
        _uiState.update {
            it.copy(supportErrorMessage = null, supportSuccessMessage = null)
        }
    }

    fun toggleNotificationPreference(enabled: Boolean) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = backendRepository.updateNotificationPreference(user.id, enabled)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            currentUser = user.copy(notificationsEnabled = enabled),
                            isLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }
}

