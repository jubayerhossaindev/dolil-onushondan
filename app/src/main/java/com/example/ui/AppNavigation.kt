package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.designsystem.DolilBottomNavigationBar
import com.example.core.designsystem.DolilExtendedAddFab
import com.example.core.designsystem.DolilGuestBanner
import com.example.core.designsystem.DolilNavigationRail
import com.example.core.designsystem.DolilOfflineBanner
import com.example.core.designsystem.DolilPrimaryButton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.DolilTheme
import com.example.core.designsystem.DolilTopBar
import com.example.core.designsystem.PrimaryDestination
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.isReducedMotionEnabled
import com.example.core.designsystem.tabularNumerals
import com.example.core.security.AuthManager
import com.example.core.security.RolePermissions
import com.example.data.local.DeviceIdentityStore
import com.example.data.local.DolilDatabase
import com.example.data.local.GuestSandboxSeeder
import com.example.data.local.LocalUserPreferences
import com.example.data.local.SessionPassphraseVault
import com.example.data.local.UserPreferencesDataStore
import com.example.data.repository.DolilSyncRepository
import com.example.data.sync.DriveSyncScheduler
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedConflict
import com.example.domain.model.UiState
import com.example.ui.screens.AccessSelectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeedDetailsScreen
import com.example.ui.screens.DeedEntryScreen
import com.example.ui.screens.LandMouzaDirectoryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PersonsDirectoryScreen
import com.example.ui.screens.ProductTourOverlay
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.RecordsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TourTargetId
import com.example.ui.screens.TourTargetsState
import com.example.ui.screens.UserGuideScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.example.ui.screens.tourTarget

object DolilRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val ADD_DEED = "add_deed?district={district}&upazila={upazila}&mouza={mouza}&jl={jl}"
    const val EDIT_DEED = "edit_deed/{deedId}"
    const val RECORDS = "records"
    const val DEED_DETAILS = "deed_details/{deedId}"
    const val PERSONS = "persons"
    const val LAND = "land"
    const val PROFILE = "profile"
    const val USER_GUIDE = "user_guide"

    fun addDeed(
        district: String = "",
        upazila: String = "",
        mouza: String = "",
        jl: String = ""
    ): String {
        val d = Uri.encode(district)
        val u = Uri.encode(upazila)
        val m = Uri.encode(mouza)
        val j = Uri.encode(jl)
        return "add_deed?district=$d&upazila=$u&mouza=$m&jl=$j"
    }

    fun deedDetails(deedId: String) = "deed_details/$deedId"
    fun editDeed(deedId: String) = "edit_deed/$deedId"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    remember(context) {
        com.example.data.local.BangladeshLocationsCatalog.ensureLoaded(context)
        true
    }
    val scope = rememberCoroutineScope()
    val prefsStore = remember(context) { UserPreferencesDataStore(context.applicationContext) }
    val localPrefsNullable by prefsStore.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = null
    )
    val localPrefs = localPrefsNullable ?: LocalUserPreferences()
    val currentUser by remember(context) { AuthManager.safeAuthStateFlow(context) }
        .collectAsStateWithLifecycle(initialValue = AuthManager.safeFirebaseAuth(context)?.currentUser)

    var showSplash by remember { mutableStateOf(true) }
    var forceShowOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        if (currentUser != null && localPrefs.isGuestModeActive) {
            prefsStore.setGuestModeActive(false)
        }
    }

    DolilTheme(
        themeMode = localPrefs.themeMode,
        useDynamicColor = localPrefs.useDynamicColor,
        language = localPrefs.language
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
        ) {
            when {
            showSplash || localPrefsNullable == null -> {
                SplashScreen(
                    language = localPrefs.language,
                    onSplashFinished = { showSplash = false }
                )
            }
            currentUser != null -> {
                val user = currentUser!!
                key(user.uid) {
                    DolilSessionShell(
                        uid = user.uid,
                        email = user.email.orEmpty(),
                        displayName = user.displayName.orEmpty(),
                        isEmailVerified = user.isEmailVerified,
                        providerIds = user.providerData.map { it.providerId },
                        isGuestMode = false,
                        initialPreferences = localPrefs,
                        prefsStore = prefsStore,
                        onExitGuestToSignIn = {}
                    )
                }
            }
            localPrefs.isGuestModeActive -> {
                key("guest_sandbox") {
                    DolilSessionShell(
                        uid = GuestSandboxSeeder.GUEST_USER_ID,
                        email = "demo@sandbox.local",
                        displayName = "Guest Explorer",
                        isEmailVerified = false,
                        providerIds = emptyList(),
                        isGuestMode = true,
                        initialPreferences = localPrefs,
                        prefsStore = prefsStore,
                        onExitGuestToSignIn = {
                            scope.launch {
                                prefsStore.setGuestModeActive(false)
                            }
                        }
                    )
                }
            }
            !localPrefs.hasCompletedOnboarding || forceShowOnboarding -> {
                OnboardingScreen(
                    language = localPrefs.language,
                    onLanguageChange = { lang ->
                        scope.launch { prefsStore.setLanguage(lang) }
                    },
                    onSkipOrFinishToAccess = {
                        forceShowOnboarding = false
                        scope.launch { prefsStore.setHasCompletedOnboarding(true) }
                    },
                    onLoginOrRegisterFromOnboarding = {
                        forceShowOnboarding = false
                        scope.launch { prefsStore.setHasCompletedOnboarding(true) }
                    },
                    onExploreAsGuest = {
                        forceShowOnboarding = false
                        scope.launch {
                            prefsStore.setHasCompletedOnboarding(true)
                            prefsStore.setGuestModeActive(true)
                        }
                    }
                )
            }
            else -> {
                AccessSelectionScreen(
                    language = localPrefs.language,
                    onLanguageChange = { lang ->
                        scope.launch { prefsStore.setLanguage(lang) }
                    },
                    onEnterGuestMode = {
                        scope.launch { prefsStore.setGuestModeActive(true) }
                    },
                    onViewOnboardingAgain = {
                        forceShowOnboarding = true
                    },
                    onAuthSuccess = {
                        scope.launch { prefsStore.setGuestModeActive(false) }
                    }
                )
            }
            }
        }
    }
}

@Composable
private fun DolilSessionShell(
    uid: String,
    email: String,
    displayName: String,
    isEmailVerified: Boolean,
    providerIds: List<String>,
    isGuestMode: Boolean,
    initialPreferences: LocalUserPreferences,
    prefsStore: UserPreferencesDataStore,
    onExitGuestToSignIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: DolilViewModel = viewModel(
        key = if (isGuestMode) "guest_sandbox" else uid,
        factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val mainDb = DolilDatabase.getInstance(appContext)
                val guestDb = DolilDatabase.getGuestSandboxInstance(appContext)
                val activeDb = if (isGuestMode) guestDb else mainDb
                val identityStore = DeviceIdentityStore(appContext)
                val deviceId = identityStore.getOrCreateDeviceId()
                val syncRepo = DolilSyncRepository(
                    dao = activeDb.dolilDao(),
                    ownerUid = uid,
                    deviceId = deviceId,
                    enableCloudSync = !isGuestMode,
                    isDriveConnectedProvider = { !isGuestMode && identityStore.getDriveLink(uid).isConnected }
                )
                DolilViewModel(
                    syncRepo = syncRepo,
                    prefsStore = prefsStore,
                    currentUserId = uid,
                    authEmail = email,
                    authDisplayName = displayName,
                    isGuestMode = isGuestMode,
                    isEmailVerifiedInitial = isEmailVerified,
                    authProviderIds = providerIds,
                    appContext = appContext,
                    dao = activeDb.dolilDao(),
                    guestDao = guestDb.dolilDao(),
                    identityStore = identityStore
                )
            }
        }
    )

    DolilMainScaffold(
        viewModel = viewModel,
        initialPreferences = initialPreferences,
        onSignOut = {
            AuthManager.signOut(
                context = context.applicationContext,
                credentialManager = credentialManager,
                scope = scope,
                onBeforeSignOut = {
                    SessionPassphraseVault.clear(uid)
                    DriveSyncScheduler.cancelAllSync(context.applicationContext)
                }
            )
        },
        onExitGuestToSignIn = onExitGuestToSignIn
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DolilMainScaffold(
    viewModel: DolilViewModel,
    initialPreferences: LocalUserPreferences = LocalUserPreferences(),
    onSignOut: () -> Unit,
    onExitGuestToSignIn: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DolilRoutes.HOME

    val preferences by viewModel.preferences.collectAsStateWithLifecycle(initialValue = initialPreferences)
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val allDeedsState by viewModel.allDeedsState.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val searchFilter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val officeStats by viewModel.officeStats.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val validationErrors by viewModel.validationErrors.collectAsStateWithLifecycle()
    val duplicateCandidateId by viewModel.duplicateCandidateId.collectAsStateWithLifecycle()
    val activeConflict by viewModel.activeConflict.collectAsStateWithLifecycle()
    val lastArchivedDeed by viewModel.lastArchivedDeed.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val savingAsDraft by viewModel.savingAsDraft.collectAsStateWithLifecycle()

    val driveLinkState by viewModel.driveLinkState.collectAsStateWithLifecycle()
    val isDriveBusy by viewModel.isDriveBusy.collectAsStateWithLifecycle()
    val legacyDeedCount by viewModel.legacyDeedCount.collectAsStateWithLifecycle()
    val customGuestDeedCount by viewModel.customGuestDeedCount.collectAsStateWithLifecycle()
    val availableBackups by viewModel.availableBackups.collectAsStateWithLifecycle()
    val isBackupsLoading by viewModel.isBackupsLoading.collectAsStateWithLifecycle()
    val connectedDevices by viewModel.connectedDevices.collectAsStateWithLifecycle()
    val isDevicesLoading by viewModel.isDevicesLoading.collectAsStateWithLifecycle()
    val readinessReport by viewModel.readinessReport.collectAsStateWithLifecycle()
    val pendingDriveConsentIntent by viewModel.pendingDriveConsentIntent.collectAsStateWithLifecycle()
    val passphrasePromptBackupId by viewModel.passphrasePromptBackupId.collectAsStateWithLifecycle()
    val driveAccountMismatch by viewModel.driveAccountMismatch.collectAsStateWithLifecycle()

    val scaffoldContext = LocalContext.current
    val ioScope = rememberCoroutineScope()
    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDriveConsentResult(result.data)
    }

    LaunchedEffect(pendingDriveConsentIntent) {
        val intentSender = pendingDriveConsentIntent?.intentSender ?: return@LaunchedEffect
        runCatching {
            driveConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }.onFailure { err ->
            viewModel.clearPendingDriveConsentIntent()
            viewModel.reportStatusMessage(
                err.localizedMessage ?: if (preferences.language == AppLanguage.BN) {
                    "Google Drive অনুমোদন ডায়ালগ খোলা যায়নি।"
                } else {
                    "Could not open the Google Drive consent prompt."
                }
            )
        }
    }

    var pendingPortableJson by remember { mutableStateOf<String?>(null) }
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingPortableJson
        pendingPortableJson = null
        if (uri != null && json != null) {
            ioScope.launch {
                val writeResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val out = scaffoldContext.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Unable to open destination file")
                        out.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                            stream.flush()
                        }
                    }
                }
                writeResult.onSuccess {
                    viewModel.reportStatusMessage(
                        if (preferences.language == AppLanguage.BN) {
                            "পোর্টেবল JSON ব্যাকআপ সফলভাবে সংরক্ষিত হয়েছে।"
                        } else {
                            "Portable JSON backup exported successfully."
                        }
                    )
                }.onFailure { err ->
                    viewModel.reportStatusMessage(
                        err.localizedMessage ?: if (preferences.language == AppLanguage.BN) {
                            "JSON ফাইল সংরক্ষণ করা যায়নি।"
                        } else {
                            "Failed to export JSON backup."
                        }
                    )
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ioScope.launch {
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val maxBytes = 10 * 1024 * 1024
                        val input = scaffoldContext.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Unable to open selected file")
                        input.use { stream ->
                            val buffer = ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val read = stream.read(chunk)
                                if (read == -1) break
                                total += read
                                if (total > maxBytes) {
                                    throw IllegalArgumentException("Backup file exceeds maximum supported size (10 MB).")
                                }
                                buffer.write(chunk, 0, read)
                            }
                            buffer.toString(Charsets.UTF_8.name())
                        }
                    }
                }
                readResult.onSuccess { rawJson ->
                    viewModel.importPortableBackupJson(rawJson)
                }.onFailure { err ->
                    viewModel.reportStatusMessage(
                        err.localizedMessage ?: if (preferences.language == AppLanguage.BN) {
                            "নির্বাচিত ব্যাকআপ ফাইল পড়া যায়নি।"
                        } else {
                            "Could not read the selected backup file."
                        }
                    )
                }
            }
        }
    }

    val tourTargets = remember { TourTargetsState() }
    var isTourVisible by remember { mutableStateOf(false) }
    var tourStepIndex by remember { mutableIntStateOf(0) }
    var requestedRecordsTab by remember { mutableIntStateOf(0) }
    var recordsTabSelectionNonce by remember { mutableIntStateOf(0) }

    val isTourCompleted = if (viewModel.isGuestMode) {
        preferences.hasCompletedGuestTour
    } else {
        preferences.hasCompletedUserTour
    }

    LaunchedEffect(isTourCompleted, currentRoute) {
        if (!isTourCompleted && currentRoute == DolilRoutes.HOME && !isTourVisible) {
            tourStepIndex = 0
            isTourVisible = true
        }
    }

    val adminUsersFlow = remember(userProfile.role, viewModel.isGuestMode) {
        if (!viewModel.isGuestMode && RolePermissions.canManageRoles(userProfile.role)) {
            viewModel.observeAdminUsers()
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val adminUsers by adminUsersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage, lastArchivedDeed) {
        val msg = statusMessage
        val archived = lastArchivedDeed
        if (!msg.isNullOrBlank()) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (archived != null) DolilStrings.undoAction(preferences.language) else null,
                duration = if (archived != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && archived != null) {
                viewModel.undoLastArchive()
            } else if (archived != null) {
                viewModel.clearLastArchivedDeed()
            }
            viewModel.clearStatusMessage()
        }
    }

    fun navigateTopLevel(dest: PrimaryDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToAddDeed(
        district: String = "",
        upazila: String = "",
        mouza: String = "",
        jl: String = ""
    ) {
        navController.navigate(DolilRoutes.addDeed(district, upazila, mouza, jl)) {
            launchSingleTop = true
        }
    }

    fun finishTour() {
        isTourVisible = false
        viewModel.completeProductTour()
        if (currentRoute != DolilRoutes.HOME) {
            navigateTopLevel(PrimaryDestination.HOME)
        }
    }

    fun moveTourToStep(nextStep: Int) {
        val clamped = nextStep.coerceIn(0, 5)
        tourStepIndex = clamped
        when (clamped) {
            4 -> if (currentRoute != DolilRoutes.SEARCH) navigateTopLevel(PrimaryDestination.SEARCH)
            else -> if (currentRoute != DolilRoutes.HOME) navigateTopLevel(PrimaryDestination.HOME)
        }
    }

    val showPrimaryNav = currentRoute in PrimaryDestination.entries.map { it.route }
    val showAddFab = currentRoute == DolilRoutes.HOME || currentRoute == DolilRoutes.RECORDS

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    if (showAddFab) {
                        DolilExtendedAddFab(
                            language = preferences.language,
                            onClick = { navigateToAddDeed() },
                            modifier = Modifier
                                .testTag("add_deed_fab")
                                .tourTarget(tourTargets, TourTargetId.ADD_DEED_TAB)
                        )
                    }
                },
                bottomBar = {
                    if (showPrimaryNav && !useNavigationRail) {
                        DolilBottomNavigationBar(
                            currentRoute = currentRoute,
                            language = preferences.language,
                            onNavigate = ::navigateTopLevel,
                            onDestinationPositioned = { dest, rect ->
                                when (dest) {
                                    PrimaryDestination.RECORDS -> tourTargets.register(TourTargetId.RECORDS_TAB, rect)
                                    PrimaryDestination.PROFILE -> tourTargets.register(TourTargetId.PROFILE_TAB, rect)
                                    else -> Unit
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showPrimaryNav && useNavigationRail) {
                        DolilNavigationRail(
                            currentRoute = currentRoute,
                            language = preferences.language,
                            onNavigate = ::navigateTopLevel,
                            onDestinationPositioned = { dest, rect ->
                                when (dest) {
                                    PrimaryDestination.RECORDS -> tourTargets.register(TourTargetId.RECORDS_TAB, rect)
                                    PrimaryDestination.PROFILE -> tourTargets.register(TourTargetId.PROFILE_TAB, rect)
                                    else -> Unit
                                }
                            }
                        )
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                        DolilGuestBanner(
                            visible = viewModel.isGuestMode,
                            onCreateAccountClick = onExitGuestToSignIn
                        )

                        if (!viewModel.isGuestMode) {
                            DolilOfflineBanner(
                                visible = isOffline,
                                pendingCount = pendingSyncCount,
                                language = preferences.language
                            )
                        }

                        val context = LocalContext.current
                        val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
                        val topRoutes = remember {
                            setOf(
                                DolilRoutes.HOME,
                                DolilRoutes.SEARCH,
                                DolilRoutes.RECORDS,
                                DolilRoutes.PROFILE
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = DolilRoutes.HOME,
                            enterTransition = {
                                if (reducedMotion) {
                                    EnterTransition.None
                                } else if (targetState.destination.route in topRoutes && initialState.destination.route in topRoutes) {
                                    fadeIn(animationSpec = tween(200))
                                } else {
                                    slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                        animationSpec = tween(260)
                                    ) + fadeIn(animationSpec = tween(200))
                                }
                            },
                            exitTransition = {
                                if (reducedMotion) {
                                    ExitTransition.None
                                } else if (targetState.destination.route in topRoutes && initialState.destination.route in topRoutes) {
                                    fadeOut(animationSpec = tween(150))
                                } else {
                                    fadeOut(animationSpec = tween(180))
                                }
                            },
                            popEnterTransition = {
                                if (reducedMotion) {
                                    EnterTransition.None
                                } else if (targetState.destination.route in topRoutes && initialState.destination.route in topRoutes) {
                                    fadeIn(animationSpec = tween(200))
                                } else {
                                    fadeIn(animationSpec = tween(200))
                                }
                            },
                            popExitTransition = {
                                if (reducedMotion) {
                                    ExitTransition.None
                                } else if (targetState.destination.route in topRoutes && initialState.destination.route in topRoutes) {
                                    fadeOut(animationSpec = tween(150))
                                } else {
                                    slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                                        animationSpec = tween(240)
                                    ) + fadeOut(animationSpec = tween(180))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(DolilRoutes.HOME) {
                                DashboardScreen(
                                    language = preferences.language,
                                    userProfile = userProfile,
                                    deedsState = allDeedsState,
                                    officeStats = officeStats,
                                    onQuickSearchSubmit = { query ->
                                        viewModel.updateSearchQuery(query)
                                        navigateTopLevel(PrimaryDestination.SEARCH)
                                    },
                                    onNavigateAllRecords = { navigateTopLevel(PrimaryDestination.RECORDS) },
                                    onNavigatePersons = { navController.navigate(DolilRoutes.PERSONS) },
                                    onNavigateLand = { navController.navigate(DolilRoutes.LAND) },
                                    onNavigateProfile = { navigateTopLevel(PrimaryDestination.PROFILE) },
                                    onOpenDeedDetail = { deedId ->
                                        navController.navigate(DolilRoutes.deedDetails(deedId))
                                    },
                                    onNavigateNewDeed = { navigateToAddDeed() },
                                    isGuestMode = viewModel.isGuestMode,
                                    tourTargets = tourTargets,
                                    onNavigateUserGuide = { navController.navigate(DolilRoutes.USER_GUIDE) },
                                    onNavigateRecordsWithTab = { tabIdx ->
                                        requestedRecordsTab = tabIdx
                                        recordsTabSelectionNonce += 1
                                        navigateTopLevel(PrimaryDestination.RECORDS)
                                    }
                                )
                            }

                            composable(DolilRoutes.SEARCH) {
                                SearchScreen(
                                    language = preferences.language,
                                    filterState = searchFilter,
                                    results = searchResults,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onUpdateFilters = { type, year, district, mouza, sort ->
                                        viewModel.updateFilters(
                                            deedType = type,
                                            year = year,
                                            district = district,
                                            mouza = mouza,
                                            sortOption = sort
                                        )
                                    },
                                    onClearFilters = viewModel::clearFilters,
                                    onLoadMore = viewModel::loadNextPage,
                                    onOpenDeedDetail = { deedId ->
                                        navController.navigate(DolilRoutes.deedDetails(deedId))
                                    },
                                    isGuestMode = viewModel.isGuestMode,
                                    tourTargets = tourTargets,
                                    userProfile = userProfile,
                                    onEditDeed = { id -> navController.navigate(DolilRoutes.editDeed(id)) },
                                    onApproveDeed = viewModel::approveDeed,
                                    onSoftDeleteDeed = viewModel::softDeleteDeed,
                                    onRestoreDeed = viewModel::restoreDeed
                                )
                            }

                            composable(
                                route = DolilRoutes.ADD_DEED,
                                arguments = listOf(
                                    navArgument("district") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("upazila") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("mouza") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("jl") { type = NavType.StringType; defaultValue = "" }
                                )
                            ) { entry ->
                                val args = entry.arguments
                                DeedEntryScreen(
                                    language = preferences.language,
                                    userProfile = userProfile,
                                    initialDeed = null,
                                    prefillDistrict = args?.getString("district").orEmpty(),
                                    prefillUpazila = args?.getString("upazila").orEmpty(),
                                    prefillMouza = args?.getString("mouza").orEmpty(),
                                    prefillJl = args?.getString("jl").orEmpty(),
                                    validationErrors = validationErrors,
                                    duplicateCandidateId = duplicateCandidateId,
                                    isSaving = isSaving,
                                    savingAsDraft = savingAsDraft,
                                    onSaveDeed = { deed, asDraft, ignoreDup ->
                                        viewModel.saveDeed(deed, asDraft, ignoreDup) { saved ->
                                            navController.navigate(DolilRoutes.deedDetails(saved.id)) {
                                                popUpTo(DolilRoutes.HOME)
                                            }
                                        }
                                    },
                                    onViewDuplicateDeed = { dupId ->
                                        navController.navigate(DolilRoutes.deedDetails(dupId))
                                    },
                                    onClearValidationError = viewModel::clearValidationError,
                                    onClearValidation = viewModel::clearValidationErrors,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = DolilRoutes.EDIT_DEED,
                                arguments = listOf(navArgument("deedId") { type = NavType.StringType })
                            ) { entry ->
                                val deedId = entry.arguments?.getString("deedId").orEmpty()
                                val currentDeedsState = allDeedsState
                                val existing = (currentDeedsState as? UiState.Success)?.data
                                    ?.firstOrNull { it.id == deedId }
                                when {
                                    currentDeedsState is UiState.Loading -> {
                                        Scaffold(
                                            topBar = {
                                                DolilTopBar(
                                                    title = DolilStrings.editDeedTitle(preferences.language),
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                        ) { pad ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(pad),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }
                                    }
                                    existing == null -> {
                                        Scaffold(
                                            topBar = {
                                                DolilTopBar(
                                                    title = DolilStrings.editDeedTitle(preferences.language),
                                                    onBack = { navController.popBackStack() }
                                                )
                                            }
                                        ) { pad ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(pad)
                                                    .padding(DolilSpacing.Space4)
                                                    .testTag("edit_deed_not_found"),
                                                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3, Alignment.CenterVertically),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = if (preferences.language == AppLanguage.BN) {
                                                        "সম্পাদনার জন্য দলিলটি পাওয়া যায়নি।"
                                                    } else {
                                                        "This deed could not be found for editing."
                                                    },
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                DolilPrimaryButton(
                                                    text = if (preferences.language == AppLanguage.BN) "ফিরে যান" else "Go Back",
                                                    onClick = { navController.popBackStack() },
                                                    testTag = "edit_deed_not_found_back"
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        DeedEntryScreen(
                                            language = preferences.language,
                                            userProfile = userProfile,
                                            initialDeed = existing,
                                            validationErrors = validationErrors,
                                            duplicateCandidateId = duplicateCandidateId,
                                            isSaving = isSaving,
                                            savingAsDraft = savingAsDraft,
                                            onSaveDeed = { deed, asDraft, ignoreDup ->
                                                viewModel.saveDeed(deed, asDraft, ignoreDup) {
                                                    navController.popBackStack()
                                                }
                                            },
                                            onViewDuplicateDeed = { dupId ->
                                                navController.navigate(DolilRoutes.deedDetails(dupId))
                                            },
                                            onClearValidationError = viewModel::clearValidationError,
                                            onClearValidation = viewModel::clearValidationErrors,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }
                            }

                            composable(DolilRoutes.RECORDS) {
                                RecordsScreen(
                                    language = preferences.language,
                                    userProfile = userProfile,
                                    deedsState = allDeedsState,
                                    onOpenDeedDetail = { deedId ->
                                        navController.navigate(DolilRoutes.deedDetails(deedId))
                                    },
                                    onCreateNewDeed = { navigateToAddDeed() },
                                    isGuestMode = viewModel.isGuestMode,
                                    onEditDeed = { id -> navController.navigate(DolilRoutes.editDeed(id)) },
                                    onApproveDeed = viewModel::approveDeed,
                                    onSoftDeleteDeed = viewModel::softDeleteDeed,
                                    onRestoreDeed = viewModel::restoreDeed,
                                    initialTabIndex = requestedRecordsTab,
                                    tabSelectionNonce = recordsTabSelectionNonce
                                )
                            }

                            composable(
                                route = DolilRoutes.DEED_DETAILS,
                                arguments = listOf(navArgument("deedId") { type = NavType.StringType })
                            ) { entry ->
                                val deedId = entry.arguments?.getString("deedId").orEmpty()
                                val deed = (allDeedsState as? UiState.Success)?.data
                                    ?.firstOrNull { it.id == deedId }

                                DeedDetailsScreen(
                                    language = preferences.language,
                                    userProfile = userProfile,
                                    deed = deed,
                                    onEditDeed = { id ->
                                        navController.navigate(DolilRoutes.editDeed(id))
                                    },
                                    onApproveDeed = viewModel::approveDeed,
                                    onSoftDeleteDeed = viewModel::softDeleteDeed,
                                    onRestoreDeed = viewModel::restoreDeed,
                                    onBack = {
                                        navController.popBackStack()
                                    },
                                    isGuestMode = viewModel.isGuestMode
                                )
                            }

                            composable(DolilRoutes.PERSONS) {
                                PersonsDirectoryScreen(
                                    language = preferences.language,
                                    deedsState = allDeedsState,
                                    onOpenDeedDetail = { deedId ->
                                        navController.navigate(DolilRoutes.deedDetails(deedId))
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(DolilRoutes.LAND) {
                                LandMouzaDirectoryScreen(
                                    language = preferences.language,
                                    deedsState = allDeedsState,
                                    onOpenDeedDetail = { deedId ->
                                        navController.navigate(DolilRoutes.deedDetails(deedId))
                                    },
                                    onCreateDeedFromCatalog = { district, upazila, mouza, jl ->
                                        navigateToAddDeed(
                                            district = district,
                                            upazila = upazila,
                                            mouza = mouza,
                                            jl = jl
                                        )
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(DolilRoutes.PROFILE) {
                                ProfileSettingsScreen(
                                    language = preferences.language,
                                    preferences = preferences,
                                    userProfile = userProfile,
                                    pendingSyncCount = pendingSyncCount,
                                    adminUsers = adminUsers,
                                    onUpdateProfileNamePhone = viewModel::updateProfileNamePhone,
                                    onLanguageChange = viewModel::setLanguage,
                                    onThemeChange = viewModel::setThemeMode,
                                    onDynamicColorChange = viewModel::setUseDynamicColor,
                                    onSyncNow = viewModel::syncNow,
                                    onAssignUserRole = viewModel::adminAssignRole,
                                    onSignOut = onSignOut,
                                    isGuestMode = viewModel.isGuestMode,
                                    onReplayProductTour = {
                                        viewModel.replayProductTour()
                                        tourStepIndex = 0
                                        isTourVisible = true
                                        navigateTopLevel(PrimaryDestination.HOME)
                                    },
                                    onOpenUserGuide = { navController.navigate(DolilRoutes.USER_GUIDE) },
                                    onExitGuestToSignIn = onExitGuestToSignIn,
                                    driveLinkState = driveLinkState,
                                    isDriveBusy = isDriveBusy,
                                    legacyDeedCount = legacyDeedCount,
                                    customGuestDeedCount = customGuestDeedCount,
                                    availableBackups = availableBackups,
                                    isBackupsLoading = isBackupsLoading,
                                    connectedDevices = connectedDevices,
                                    isDevicesLoading = isDevicesLoading,
                                    readinessReport = readinessReport,
                                    onConnectDrive = { viewModel.connectGoogleDrive(forceSelectAccount = false) },
                                    onSwitchDriveAccount = { viewModel.connectGoogleDrive(forceSelectAccount = true) },
                                    onDisconnectDrive = viewModel::disconnectDriveLocally,
                                    onRevokeDriveAccess = viewModel::revokeGoogleDriveAccess,
                                    onToggleAutoSync = viewModel::toggleAutoSync,
                                    onEnableEncryption = viewModel::enableEncryption,
                                    onDisableEncryption = viewModel::disableEncryption,
                                    onLoadBackups = viewModel::loadAvailableBackups,
                                    onRestoreBackup = viewModel::restoreFromBackup,
                                    onLoadDevices = viewModel::loadConnectedDevices,
                                    onRetireDevice = viewModel::retireDevice,
                                    onExportPortableJson = {
                                        viewModel.buildPortableBackupJson { json ->
                                            pendingPortableJson = json
                                            exportJsonLauncher.launch("dolil-backup.json")
                                        }
                                    },
                                    onImportPortableJson = {
                                        importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
                                    },
                                    onImportLegacyDeeds = viewModel::importLegacyLocalDeeds,
                                    onImportCustomGuestDeeds = viewModel::importCustomGuestDeeds,
                                    onResendVerificationEmail = viewModel::resendVerificationEmail,
                                    onRefreshEmailVerification = viewModel::refreshEmailVerificationStatus,
                                    onDeleteAccount = viewModel::deleteAccount,
                                    onRefreshReadiness = viewModel::refreshCredentialReadiness
                                )
                            }

                            composable(DolilRoutes.USER_GUIDE) {
                                UserGuideScreen(
                                    onReplayTour = {
                                        viewModel.replayProductTour()
                                        tourStepIndex = 0
                                        isTourVisible = true
                                        navigateTopLevel(PrimaryDestination.HOME)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }

        ProductTourOverlay(
            visible = isTourVisible,
            currentStep = tourStepIndex,
            targets = tourTargets,
            isOfficeUser = RolePermissions.isOfficeUser(userProfile.role),
            isGuestMode = viewModel.isGuestMode,
            onNext = {
                if (tourStepIndex >= 5) {
                    finishTour()
                } else {
                    moveTourToStep(tourStepIndex + 1)
                }
            },
            onPrevious = {
                if (tourStepIndex > 0) {
                    moveTourToStep(tourStepIndex - 1)
                }
            },
            onSkip = ::finishTour
        )
    }

    activeConflict?.let { conflict ->
        ConflictResolutionDialog(
            language = preferences.language,
            conflict = conflict,
            onKeepLocal = { viewModel.resolveConflictKeepLocal(conflict) },
            onAcceptServer = { viewModel.resolveConflictAcceptServer(conflict) },
            onKeepBoth = { viewModel.resolveConflictKeepBoth(conflict) },
            onDismiss = viewModel::dismissConflict
        )
    }

    passphrasePromptBackupId?.let { _ ->
        var unlockInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissPassphrasePrompt,
            title = {
                Text(
                    if (preferences.language == AppLanguage.BN) {
                        "এনক্রিপ্টেড ব্যাকআপ আনলক করুন"
                    } else {
                        "Unlock Encrypted Backup"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    Text(
                        text = if (preferences.language == AppLanguage.BN) {
                            "এই ব্যাকআপ স্ন্যাপশট ডিক্রিপ্ট করতে আপনার এনক্রিপশন পাসফ্রেজ দিন।"
                        } else {
                            "Enter your backup passphrase to decrypt and sync this repository."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = unlockInput,
                        onValueChange = { unlockInput = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = {
                            Text(if (preferences.language == AppLanguage.BN) "পাসফ্রেজ" else "Passphrase")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.unlockPassphraseAndSync(unlockInput) }) {
                    Text(if (preferences.language == AppLanguage.BN) "আনলক ও সিঙ্ক" else "Unlock & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPassphrasePrompt) {
                    Text(DolilStrings.cancel(preferences.language))
                }
            }
        )
    }

    driveAccountMismatch?.let { mismatch ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDriveAccountMismatch,
            title = {
                Text(
                    if (preferences.language == AppLanguage.BN) {
                        "ভিন্ন Google Drive অ্যাকাউন্ট"
                    } else {
                        "Different Google Drive Account"
                    }
                )
            },
            text = {
                Text(
                    text = if (preferences.language == AppLanguage.BN) {
                        "এই অ্যাকাউন্টটি আগে '${mismatch.expectedEmail}'-এর সাথে যুক্ত ছিল, কিন্তু এখন '${mismatch.actualEmail}' নির্বাচিত হয়েছে। আলাদা রিপোজিটরি হিসেবে সুইচ করবেন?"
                    } else {
                        "This Firebase account was previously synced with '${mismatch.expectedEmail}', but '${mismatch.actualEmail}' was selected. Repositories are kept strictly separate. Switch linked Drive account?"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSwitchDriveAccount(mismatch) }) {
                    Text(if (preferences.language == AppLanguage.BN) "অ্যাকাউন্ট সুইচ করুন" else "Switch Drive Account")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDriveAccountMismatch) {
                    Text(DolilStrings.cancel(preferences.language))
                }
            }
        )
    }
}

@Composable
internal fun ConflictResolutionDialog(
    language: AppLanguage,
    conflict: DeedConflict,
    onKeepLocal: () -> Unit,
    onAcceptServer: () -> Unit,
    onKeepBoth: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    val unitLabel = DolilStrings.shotokUnit(language)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (language == AppLanguage.BN) "সংস্করণ দ্বন্দ্ব শনাক্ত হয়েছে" else "Sync Conflict Detected",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                Text(
                    text = if (language == AppLanguage.BN) {
                        "এই দলিলটি অন্য ডিভাইসে ইতিমধ্যেই হালনাগাদ হয়েছে। কোন সংস্করণটি রাখবেন তা নির্বাচন করুন:"
                    } else {
                        "This deed was updated concurrently on another device. Choose how to resolve:"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = semantic.infoContainer,
                    shape = RoundedCornerShape(DolilRadius.Input),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(DolilSpacing.Space3),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (language == AppLanguage.BN) {
                                "আপনার স্থানীয় সংস্করণ (v${conflict.localDeed.rowVersion})"
                            } else {
                                "Your Local Version (v${conflict.localDeed.rowVersion})"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.onInfoContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${DolilStrings.deedNumberFormatted(conflict.localDeed.deedNumber, conflict.localDeed.deedYear, language)} • ${conflict.localDeed.totalAreaDecimalText} $unitLabel",
                            style = MaterialTheme.typography.bodySmall.tabularNumerals(),
                            color = semantic.onInfoContainer
                        )
                    }
                }
                Surface(
                    color = semantic.warningContainer,
                    shape = RoundedCornerShape(DolilRadius.Input),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(DolilSpacing.Space2),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (language == AppLanguage.BN) {
                                "Google Drive সংস্করণ (v${conflict.serverDeed.rowVersion})"
                            } else {
                                "Drive / Remote Version (v${conflict.serverDeed.rowVersion})"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.onWarningContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${DolilStrings.deedNumberFormatted(conflict.serverDeed.deedNumber, conflict.serverDeed.deedYear, language)} • ${conflict.serverDeed.totalAreaDecimalText} $unitLabel",
                            style = MaterialTheme.typography.bodySmall.tabularNumerals(),
                            color = semantic.onWarningContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onKeepLocal,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("conflict_keep_local_button")
            ) {
                Text(if (language == AppLanguage.BN) "আমার পরিবর্তন রাখুন" else "Keep Local")
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    TextButton(
                        onClick = onAcceptServer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("conflict_accept_server_button")
                    ) {
                        Text(if (language == AppLanguage.BN) "Drive সংস্করণ নিন" else "Accept Drive Version")
                    }
                    TextButton(
                        onClick = onKeepBoth,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("conflict_keep_both_button")
                    ) {
                        Text(if (language == AppLanguage.BN) "উভয় রাখুন" else "Keep Both")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(DolilStrings.cancel(language))
                }
            }
        }
    )
}
