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
import com.example.ui.components.toPersianDigits
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
    val myketBillingManager = com.example.billing.MyketBillingManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private var countdownJob: Job? = null
    private var otpTimerJob: Job? = null

    var isTickerEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                countdownJob?.cancel()
            } else if (countdownJob?.isActive != true) {
                startCycleCountdownTicker()
            }
        }

    init {
        val initialCachedLogin = backendRepository.parseCachedLoginNotification()
        if (initialCachedLogin != null) {
            _uiState.update { it.copy(loginNotification = initialCachedLogin) }
        }
        checkSavedSession()
        observeTapsellAdState()
        startCycleCountdownTicker()
        fetchLoginNotification()
        myketBillingManager.startConnection()
    }

    private fun fetchLoginNotification() {
        viewModelScope.launch {
            val res = backendRepository.fetchLoginNotificationConfig()
            if (res is Result.Success) {
                _uiState.update { it.copy(loginNotification = res.data) }
            }
        }
    }

    private fun checkSavedSession() {
        val cachedLogin = backendRepository.parseCachedLoginNotification()
        if (!prefs.isLoggedIn || prefs.userId.isEmpty() || prefs.firstName == "کاربر" || prefs.phoneNumber == "09120000000") {
            // First time entry: clear dummy session and present AuthScreen so user enters their real details
            prefs.clearSession()
            _uiState.update {
                it.copy(
                    currentUser = null,
                    currentScreen = AppScreen.AUTH,
                    loginNotification = cachedLogin ?: it.loginNotification
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
            token = prefs.authToken,
            notificationsEnabled = prefs.notificationsEnabled
        )
        val cachedAnn = backendRepository.parseCachedAnnouncement()
        val cachedBanners = backendRepository.parseCachedAdBanners()
        _uiState.update {
            it.copy(
                currentUser = user,
                activeAnnouncement = cachedAnn,
                promoAdBanners = cachedBanners,
                promoAdBannersClockOffsetMs = prefs.cachedAdBannersClockOffsetMs,
                currentScreen = AppScreen.HOME,
                isTestAdsEnabled = prefs.useTestAds
            )
        }
        com.example.CompetitionReminderManager.onUserAuthenticated(getApplication())
        viewModelScope.launch {
            val res = backendRepository.fetchLoginNotificationConfig()
            if (res is Result.Success) {
                _uiState.update { it.copy(loginNotification = res.data) }
            }
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
        if (screen == AppScreen.PRIZES) {
            loadRewardsData()
        } else if (screen == AppScreen.LEADERBOARD) {
            loadLeaderboard()
        } else if (screen == AppScreen.MEMBERSHIP) {
            loadMembershipData()
        } else if (screen == AppScreen.STORE) {
            loadStoreData()
        } else if (screen == AppScreen.SQUARES || screen == AppScreen.HOME) {
            refreshData()
        }
    }

    fun refreshData() {
        val user = _uiState.value.currentUser ?: return
        refreshAdBanners(force = true)
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

            // Fetch login notification config
            val loginNotifResult = backendRepository.fetchLoginNotificationConfig()
            val loginNotif = if (loginNotifResult is Result.Success) loginNotifResult.data else _uiState.value.loginNotification

            _uiState.update {
                it.copy(
                    dailyCycle = cycle,
                    squares = squares,
                    prizes = prizes,
                    activeCompetition = activeComp ?: it.activeCompetition,
                    activeAnnouncement = activeAnn,
                    loginNotification = loginNotif,
                    currentUser = updatedUser,
                    isLoading = false
                )
            }
            launch(Dispatchers.IO) {
                tapsellAdsManager.initializeSdk()
            }
            if (_uiState.value.leaderboard.isEmpty()) {
                launch {
                    val lbResult = backendRepository.fetchLeaderboard(user.id)
                    if (lbResult is Result.Success) {
                        _uiState.update { it.copy(leaderboard = lbResult.data) }
                    }
                }
            }
            checkAndShowFeedbackDialog()
        }
    }

    fun loadLeaderboard() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLeaderboardLoading = true,
                    leaderboardErrorMessage = null,
                    leaderboardErrorCode = null
                )
            }
            when (val result = backendRepository.fetchLeaderboard(user.id)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            leaderboard = result.data,
                            isLeaderboardLoading = false,
                            leaderboardErrorMessage = null,
                            leaderboardErrorCode = null
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLeaderboardLoading = false,
                            leaderboardErrorMessage = result.message,
                            leaderboardErrorCode = if (result.code != -1) result.code else null
                        )
                    }
                }
            }
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

    fun updateGiftBanner(
        title: String,
        description: String,
        imageUrl: String,
        isActive: Boolean,
        termsText: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSupportLoading = true, supportErrorMessage = null, supportSuccessMessage = null) }
            when (val res = backendRepository.updateRewardAppearance(title, description, imageUrl, isActive, termsText)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            rewardAppearance = res.data,
                            isSupportLoading = false,
                            supportSuccessMessage = "تغییرات بنر هدیه با موفقیت در سرور و دیتابیس ذخیره شد."
                        )
                    }
                    loadRewardsData()
                    onSuccess()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSupportLoading = false,
                            supportErrorMessage = res.message
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
        if (!isTickerEnabled) return
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive && isTickerEnabled) {
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

    fun instantUnlockSquare(squareNumber: Int) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = backendRepository.instantUnlockSquare(user.id, squareNumber)) {
                is Result.Success -> {
                    val json = result.data
                    val newScore = json.optInt("newScore", prefs.totalScore + 1)
                    val remainingCredits = json.optInt("remainingCredits", 0)
                    val updatedUser = user.copy(totalScore = newScore)

                    val squares = backendRepository.getSquares(user.id)
                    val cycle = backendRepository.ensureActiveCycle(user.id)
                    val entitlements = backendRepository.fetchUserEntitlements(user.id)

                    _uiState.update {
                        it.copy(
                            currentUser = updatedUser,
                            squares = squares,
                            dailyCycle = cycle,
                            userEntitlements = if (entitlements is Result.Success) entitlements.data else it.userEntitlements.copy(instantUnlockCredits = remainingCredits),
                            isLoading = false,
                            awardedPointsModal = json.optInt("awardedPoints", 1),
                            infoMessage = json.optString("message", "مربع با موفقیت باز شد.")
                        )
                    }
                    loadLeaderboard()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
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

    private fun isAllowedPersianNameChar(ch: Char): Boolean {
        return ch in '\u0621'..'\u064A' ||
            ch == '\u067E' || // پ
            ch == '\u0686' || // چ
            ch == '\u0698' || // ژ
            ch == '\u06A9' || // ک
            ch == '\u06AF' || // گ
            ch == '\u06CC' || // ی
            ch == '\u0622' || // آ
            ch == '\u0623' || // أ
            ch == '\u0624' || // ؤ
            ch == '\u0625' || // إ
            ch == '\u0626' || // ئ
            ch == '\u06C0' || // ۀ
            ch == '\u200C' || // نیم‌فاصله
            ch == ' '
    }

    private fun sanitizePersianName(input: String): Pair<String, Boolean> {
        val filtered = input.filter { isAllowedPersianNameChar(it) }
        return filtered to (filtered.length != input.length)
    }

    private fun isValidPersianName(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val hasPersianLetter = trimmed.any { it != ' ' && it != '\u200C' && isAllowedPersianNameChar(it) }
        return hasPersianLetter && trimmed.all { isAllowedPersianNameChar(it) }
    }

    private fun normalizePhoneDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '0'..'9' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun onFirstNameChange(value: String) {
        val (filtered, hadInvalidChars) = sanitizePersianName(value)
        _authUiState.update {
            it.copy(
                firstName = filtered,
                errorMessage = if (hadInvalidChars) "نام باید فقط با حروف فارسی وارد شود." else null
            )
        }
    }

    fun onLastNameChange(value: String) {
        val (filtered, hadInvalidChars) = sanitizePersianName(value)
        _authUiState.update {
            it.copy(
                lastName = filtered,
                errorMessage = if (hadInvalidChars) "نام خانوادگی باید فقط با حروف فارسی وارد شود." else null
            )
        }
    }

    fun onPhoneNumberChange(value: String) {
        val digitsOnly = normalizePhoneDigits(value).take(11)
        val phoneErr = if (digitsOnly.isNotEmpty() && !digitsOnly.startsWith("0")) {
            "شماره موبایل حتماً باید با عدد 0 شروع شود."
        } else null
        _authUiState.update { it.copy(phoneNumber = digitsOnly, errorMessage = phoneErr) }
    }

    fun onPasswordChange(value: String) = _authUiState.update { it.copy(password = value, errorMessage = null) }
    fun onAuthModeChange(mode: AuthMode) = _authUiState.update { it.copy(authMode = mode, errorMessage = null, infoMessage = null) }
    fun onOtpCodeChange(value: String) = _authUiState.update { it.copy(otpCode = value, errorMessage = null) }

    private fun validateAuthInputs(state: AuthUiState): String? {
        val first = state.firstName.trim()
        val last = state.lastName.trim()
        val phone = normalizePhoneDigits(state.phoneNumber.trim())

        if (first.isEmpty()) return "لطفاً نام خود را با حروف فارسی وارد کنید."
        if (!isValidPersianName(first)) return "نام باید فقط شامل حروف فارسی باشد."
        if (last.isEmpty()) return "لطفاً نام خانوادگی خود را با حروف فارسی وارد کنید."
        if (!isValidPersianName(last)) return "نام خانوادگی باید فقط شامل حروف فارسی باشد."
        if (phone.isEmpty()) return "لطفاً شماره موبایل ۱۱ رقمی خود را وارد کنید."
        if (!phone.startsWith("0")) return "شماره موبایل حتماً باید با عدد 0 شروع شود."
        if (phone.length != 11) return "شماره موبایل باید دقیقاً ۱۱ رقم باشد."
        if (!phone.matches(Regex("^09[0-9]{9}\$"))) return "شماره موبایل معتبر نیست؛ باید ۱۱ رقم بوده و با 09 آغاز شود."
        return null
    }

    fun loginOrRegister() {
        val state = _authUiState.value
        val validationError = validateAuthInputs(state)
        if (validationError != null) {
            _authUiState.update { it.copy(errorMessage = validationError) }
            return
        }
        val cleanFirstName = state.firstName.trim()
        val cleanLastName = state.lastName.trim()
        val cleanPhone = normalizePhoneDigits(state.phoneNumber.trim())
        val pin = state.password.trim()

        viewModelScope.launch {
            _authUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = backendRepository.loginDirectly(
                firstName = cleanFirstName,
                lastName = cleanLastName,
                phoneNumber = cleanPhone,
                password = pin
            )) {
                is Result.Success -> {
                    val user = result.data.copy(notificationsEnabled = prefs.notificationsEnabled)
                    _authUiState.update { AuthUiState() }
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            currentScreen = AppScreen.HOME,
                            infoMessage = "خوش آمدید، ${user.firstName} عزیز!"
                        )
                    }
                    com.example.CompetitionReminderManager.onUserAuthenticated(getApplication())
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
        val validationError = validateAuthInputs(state)
        if (validationError != null) {
            _authUiState.update { it.copy(errorMessage = validationError) }
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
                    val user = result.data.copy(notificationsEnabled = prefs.notificationsEnabled)
                    _authUiState.update { AuthUiState() }
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            currentScreen = AppScreen.HOME,
                            infoMessage = "خوش آمدید، ${user.firstName} عزیز!"
                        )
                    }
                    com.example.CompetitionReminderManager.onUserAuthenticated(getApplication())
                    refreshData()
                }
                is Result.Error -> {
                    _authUiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun logout() {
        val currentLoginNotif = _uiState.value.loginNotification
        val cachedLogin = backendRepository.parseCachedLoginNotification()
        com.example.CompetitionReminderManager.onUserLoggedOut(getApplication())
        prefs.clearSession()
        _uiState.update {
            MainUiState(
                currentScreen = AppScreen.AUTH,
                loginNotification = cachedLogin ?: currentLoginNotif
            )
        }
        fetchLoginNotification()
    }

    fun loadMembershipData() {
        myketBillingManager.startConnection()
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
        _uiState.update {
            it.copy(
                isMembershipLoading = false,
                errorMessage = "خرید ارتقای پنل فقط از طریق درگاه امن پرداخت درون‌برنامه‌ای مایکت امکان‌پذیر است."
            )
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
                    prefs.notificationsEnabled = enabled
                    if (enabled) {
                        com.example.CompetitionReminderManager.onUserAuthenticated(getApplication())
                    } else {
                        com.example.CompetitionReminderManager.onUserLoggedOut(getApplication())
                    }
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

    fun toggleTestAds(enabled: Boolean) {
        prefs.useTestAds = enabled
        _uiState.update { it.copy(isTestAdsEnabled = enabled) }
    }

    fun loadStoreData() {
        myketBillingManager.startConnection()
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isStoreLoading = true, storeErrorMessage = null) }
            
            // 1. Fetch Store Products from Backend
            val productsResult = backendRepository.fetchStoreProducts()
            
            // 2. Fetch User Entitlements
            val entitlementsResult = backendRepository.fetchUserEntitlements(user.id)
            
            if (productsResult is Result.Success) {
                val backendProducts = productsResult.data
                
                // 3. Enrich with real prices from Myket SDK if available
                val inventory = myketBillingManager.queryInventory()
                val finalProducts = if (inventory != null) {
                    backendProducts.map { product ->
                        val skuDetails = inventory.getSkuDetails(product.myketSku)
                        if (skuDetails != null) {
                            // Extract price as Long if possible (e.g. "50,000" -> 50000)
                            val priceStr = skuDetails.price ?: ""
                            val cleanPrice = priceStr.filter { it.isDigit() }.toLongOrNull() ?: product.priceToman
                            product.copy(
                                priceToman = cleanPrice,
                                title = (skuDetails.title ?: product.title).substringBefore("(").trim(),
                                description = (skuDetails.description ?: "").ifBlank { product.description }
                            )
                        } else {
                            product
                        }
                    }
                } else {
                    backendProducts
                }

                _uiState.update {
                    it.copy(
                        storeProducts = finalProducts,
                        userEntitlements = if (entitlementsResult is Result.Success) entitlementsResult.data else it.userEntitlements,
                        isStoreLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isStoreLoading = false,
                        storeErrorMessage = (productsResult as? Result.Error)?.message ?: "خطا در دریافت اطلاعات فروشگاه"
                    )
                }
            }
        }
    }

    fun restoreMyketPurchases() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isStoreLoading = true, storeErrorMessage = null) }
            val unconsumed = myketBillingManager.queryPurchases()
            if (unconsumed.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isStoreLoading = false,
                        infoMessage = "هیچ خرید معلقی برای بازیابی یافت نشد."
                    )
                }
                return@launch
            }
            var restoredCount = 0
            for (purchase in unconsumed) {
                processMyketPurchaseResult(purchase)
                restoredCount++
            }
            _uiState.update {
                it.copy(
                    isStoreLoading = false,
                    infoMessage = "$restoredCount خرید معلق با موفقیت بازیابی و اعمال شد."
                )
            }
        }
    }

    fun startMyketPurchaseFlow(activity: Activity?, productId: String) {
        val user = _uiState.value.currentUser ?: return
        if (activity == null) {
            _uiState.update { 
                it.copy(
                    storeErrorMessage = "خطا در دسترسی به محیط برنامه.",
                    errorMessage = "خطا در دسترسی به محیط برنامه."
                ) 
            }
            return
        }

        val product = _uiState.value.storeProducts.find { it.id == productId || it.myketSku == productId }
        val targetSku = product?.myketSku?.ifBlank { null } ?: when (productId) {
            "pkg_squares_50" -> "reqabat_unlock_50"
            "pkg_squares_100" -> "reqabat_unlock_100"
            "pkg_upgrade_silver", "silver_panel" -> "reqabat_upgrade_silver"
            "pkg_upgrade_gold", "gold_panel", "gold_upgrade" -> "reqabat_upgrade_gold"
            else -> productId
        }

        if (!myketBillingManager.isMyketInstalled()) {
            val msg = "برنامه مایکت روی دستگاه شما نصب نیست. لطفاً برای انجام خرید درون‌برنامه‌ای، ابتدا اپلیکیشن مایکت را نصب فرمایید."
            _uiState.update { 
                it.copy(
                    storeErrorMessage = msg,
                    errorMessage = msg,
                    isStoreLoading = false,
                    isMembershipLoading = false
                ) 
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isStoreLoading = true, 
                    isMembershipLoading = true, 
                    storeErrorMessage = null, 
                    errorMessage = null 
                ) 
            }

            val result = myketBillingManager.launchPurchaseFlow(
                activity = activity,
                sku = targetSku,
                isReadyInMyket = product?.isReadyInMyket ?: true,
                requestCode = com.example.billing.MyketBillingManager.REQUEST_CODE_MYKET_PURCHASE
            )

            _uiState.update { it.copy(isStoreLoading = false, isMembershipLoading = false) }

            when (result) {
                is com.example.billing.BillingResult.Success -> {
                    val purchaseData = result.data
                    if (purchaseData != null) {
                        processMyketPurchaseResult(purchaseData)
                    }
                }
                is com.example.billing.BillingResult.UnderPreparation -> {
                    _uiState.update { it.copy(storeErrorMessage = result.message, errorMessage = result.message) }
                }
                is com.example.billing.BillingResult.MyketNotInstalled -> {
                    _uiState.update { it.copy(storeErrorMessage = result.message, errorMessage = result.message) }
                }
                is com.example.billing.BillingResult.Error -> {
                    if (result.responseCode != 1) { // 1 is user cancel
                        _uiState.update { it.copy(storeErrorMessage = result.message, errorMessage = result.message) }
                    } else {
                        _uiState.update { it.copy(infoMessage = result.message) }
                    }
                }
            }
        }
    }

    fun purchasePanel(activity: Activity?, productId: String) {
        startMyketPurchaseFlow(activity, productId)
    }

    fun purchaseStoreProduct(activity: Activity?, productId: String) {
        startMyketPurchaseFlow(activity, productId)
    }

    fun processMyketPurchaseResult(purchaseData: com.example.billing.PurchaseData) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isStoreLoading = true, isMembershipLoading = true, storeErrorMessage = null, errorMessage = null) }

            val product = _uiState.value.storeProducts.find { 
                it.id == purchaseData.productId || it.myketSku == purchaseData.productId 
            }
            val canonicalId = product?.id ?: when (purchaseData.productId) {
                "reqabat_unlock_50" -> "pkg_squares_50"
                "reqabat_unlock_100" -> "pkg_squares_100"
                "reqabat_upgrade_silver" -> "pkg_upgrade_silver"
                "reqabat_upgrade_gold" -> "pkg_upgrade_gold"
                else -> purchaseData.productId
            }

            when (val result = backendRepository.verifyStorePurchase(
                userId = user.id, 
                productId = canonicalId, 
                purchaseToken = purchaseData.purchaseToken, 
                orderId = purchaseData.orderId,
                packageName = purchaseData.packageName,
                purchaseData = purchaseData.rawPurchaseData,
                signature = purchaseData.signature
            )) {
                is Result.Success -> {
                    // Consume purchase token if square unlock item so user can buy again next time
                    if (canonicalId.contains("50") || canonicalId.contains("100") || canonicalId.contains("squares")) {
                        myketBillingManager.consumePurchase(purchaseData.purchaseToken)
                    }

                    val title = product?.title ?: "محصول فروشگاه مایکت"
                    val benefitText = when {
                        product?.productType == "SQUARE_UNLOCK" || canonicalId.contains("squares") -> 
                            "+${(product?.unitsGranted ?: if (canonicalId.contains("50")) 50 else 100).toString().toPersianDigits()} اعتبار بازگشایی آنی"
                        canonicalId.contains("gold") -> "ارتقای سطح به طلایی (۶X)"
                        canonicalId.contains("silver") -> "ارتقای سطح به نقره‌ای (۳X)"
                        else -> "اعتبار و امتیاز عینا به حساب کاربر اضافه شد"
                    }
                    val price = product?.priceToman ?: 0L

                    _uiState.update {
                        it.copy(
                            isStoreLoading = false,
                            isMembershipLoading = false,
                            infoMessage = "خرید درون‌برنامه‌ای مایکت با موفقیت تایید و اعمال شد.",
                            purchaseSuccessModal = com.example.model.PurchaseSuccessModalData(
                                productTitle = title,
                                unitsOrMultiplierText = benefitText,
                                priceToman = price,
                                orderId = purchaseData.orderId
                            )
                        )
                    }
                    loadStoreData()
                    loadMembershipData()
                    refreshData()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isStoreLoading = false,
                            isMembershipLoading = false,
                            storeErrorMessage = result.message,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun dismissPurchaseSuccessModal() {
        _uiState.update { it.copy(purchaseSuccessModal = null) }
    }

    fun clearStoreMessages() {
        _uiState.update { it.copy(storeErrorMessage = null) }
    }

    fun markFeedbackShown() {
        prefs.isFeedbackShown = true
        _uiState.update { it.copy(showFeedbackDialog = false) }
    }

    fun submitInternalFeedback(rating: Int, comment: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingFeedback = true, errorMessage = null) }
            when (val result = backendRepository.submitFeedback(rating, comment)) {
                is Result.Success -> {
                    val newScore = result.data.optInt("newTotalScore", prefs.totalScore)
                    val user = _uiState.value.currentUser?.copy(totalScore = newScore)
                    prefs.totalScore = newScore
                    prefs.isFeedbackRewarded = true
                    prefs.isFeedbackShown = true
                    
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            isSubmittingFeedback = false,
                            showFeedbackDialog = false,
                            feedbackSuccessMessage = "بازخورد شما با موفقیت ثبت شد و ۲ امتیاز به حساب شما افزوده گردید.",
                            awardedPointsModal = 2
                        )
                    }
                    refreshData()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingFeedback = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun checkAndShowFeedbackDialog() {
        if (prefs.isLoggedIn && !prefs.isFeedbackShown) {
            _uiState.update { it.copy(showFeedbackDialog = true) }
        }
    }

    // ====================================================================
    // Promotional Clickable Ad Banners
    // ====================================================================
    private val trackedBannerImpressions = mutableSetOf<String>()
    private var lastPromoBannerClickTimeMs: Long = 0L
    private var lastPromoBannerClickedId: String? = null
    private var lastAdBannersRefreshMs: Long = 0L
    private var adBannersRefreshJob: kotlinx.coroutines.Job? = null

    fun refreshAdBanners(force: Boolean = false) {
        if (_uiState.value.currentUser == null) return
        val nowMs = System.currentTimeMillis()
        if (!force && (nowMs - lastAdBannersRefreshMs) < 1_500L) return
        if (adBannersRefreshJob?.isActive == true && !force) return

        adBannersRefreshJob?.cancel()
        adBannersRefreshJob = viewModelScope.launch {
            lastAdBannersRefreshMs = System.currentTimeMillis()
            val res = backendRepository.fetchActiveAdBanners()
            if (res is Result.Success) {
                _uiState.update {
                    it.copy(
                        promoAdBanners = res.data,
                        promoAdBannersClockOffsetMs = prefs.cachedAdBannersClockOffsetMs
                    )
                }
            }
        }
    }

    fun onPromoBannerVisible(bannerId: String, placement: String? = null) {
        if (bannerId.isBlank()) return
        val trackingKey = if (placement.isNullOrBlank()) bannerId else "$bannerId:$placement"
        if (!trackedBannerImpressions.add(trackingKey)) return
        val userId = _uiState.value.currentUser?.id ?: prefs.userId
        viewModelScope.launch {
            backendRepository.recordAdBannerImpression(bannerId, userId, placement)
        }
    }

    fun onPromoBannerClick(
        banner: com.example.model.PromoAdBanner,
        placement: String? = null,
        onOpenUrl: (safeUrl: String, linkOpenMode: String) -> Unit
    ) {
        val nowMs = System.currentTimeMillis()
        if (lastPromoBannerClickedId == banner.id && (nowMs - lastPromoBannerClickTimeMs) < 800L) {
            return
        }
        val safeUrl = com.example.model.PromoBannerUrlValidator.normalizeSafeHttpUrl(banner.targetUrl)
            ?: return
        val effectiveNowMs = nowMs + _uiState.value.promoAdBannersClockOffsetMs
        if (!banner.isCurrentlyDisplayable(effectiveNowMs, placement)) return

        lastPromoBannerClickedId = banner.id
        lastPromoBannerClickTimeMs = nowMs

        val userId = _uiState.value.currentUser?.id ?: prefs.userId
        val tapNonce = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            backendRepository.recordAdBannerClick(banner.id, userId, tapNonce, placement)
        }
        onOpenUrl(safeUrl, banner.linkOpenMode)
    }
}


