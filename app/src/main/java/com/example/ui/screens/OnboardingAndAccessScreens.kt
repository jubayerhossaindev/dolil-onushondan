package com.example.ui.screens

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.designsystem.DolilComponentTokens
import com.example.core.designsystem.DolilErrorBanner
import com.example.core.designsystem.DolilIcons
import com.example.core.designsystem.DolilPrimaryButton
import com.example.core.designsystem.DolilRadius
import com.example.core.designsystem.DolilSecondaryButton
import com.example.core.designsystem.DolilSpacing
import com.example.core.designsystem.DolilStrings
import com.example.core.designsystem.LocalLocalizedContext
import com.example.core.designsystem.dolilColors
import com.example.core.designsystem.dolilStr
import com.example.core.designsystem.isReducedMotionEnabled
import com.example.core.designsystem.performSubtleTick
import com.example.core.security.AuthActionOutcome
import com.example.core.security.AuthManager
import com.example.core.security.AuthValidator
import com.example.domain.model.AppLanguage
import kotlinx.coroutines.launch

private data class OnboardingPageSpec(
    val imageRes: Int,
    val titleRes: Int,
    val descRes: Int
)

@Composable
fun OnboardingScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onSkipOrFinishToAccess: () -> Unit,
    onLoginOrRegisterFromOnboarding: () -> Unit,
    onExploreAsGuest: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPageSpec(
                imageRes = R.drawable.img_onboarding_welcome,
                titleRes = R.string.onboarding_1_title,
                descRes = R.string.onboarding_1_desc
            ),
            OnboardingPageSpec(
                imageRes = R.drawable.img_onboarding_entry,
                titleRes = R.string.onboarding_2_title,
                descRes = R.string.onboarding_2_desc
            ),
            OnboardingPageSpec(
                imageRes = R.drawable.img_onboarding_search,
                titleRes = R.string.onboarding_3_title,
                descRes = R.string.onboarding_3_desc
            ),
            OnboardingPageSpec(
                imageRes = R.drawable.img_onboarding_ready,
                titleRes = R.string.onboarding_4_title,
                descRes = R.string.onboarding_4_desc
            )
        )
    }

    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompactHeight = maxHeight < 640.dp
        val semantic = MaterialTheme.dolilColors

        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(horizontal = DolilSpacing.Space4, vertical = DolilSpacing.Space3),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Language switcher & Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = DolilIcons.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.displayLabel, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .testTag("onboarding_lang_${lang.code}")
                        )
                    }
                }

                TextButton(
                    onClick = onSkipOrFinishToAccess,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = dolilStr(R.string.action_skip),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val spec = pages[pageIndex]
                val title = dolilStr(spec.titleRes)
                val desc = dolilStr(spec.descRes)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = DolilSpacing.Space2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(DolilRadius.Pill)
                    ) {
                        Text(
                            text = dolilStr(R.string.onboarding_step_format, pageIndex + 1, pages.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(Modifier.height(DolilSpacing.Space3))

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tonalElevation = 2.dp,
                        border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 360.dp)
                            .heightIn(max = if (isCompactHeight) 200.dp else 270.dp)
                            .aspectRatio(4f / 3f, matchHeightConstraintsFirst = true)
                    ) {
                        Image(
                            painter = painterResource(id = spec.imageRes),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(DolilSpacing.Space4))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = DolilSpacing.Space2)
                            .semantics { heading() }
                    )

                    Spacer(Modifier.height(DolilSpacing.Space2))

                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = DolilSpacing.Space3)
                    )
                }
            }

            Spacer(Modifier.height(DolilSpacing.Space2))

            // Page indicator dots
            val stepDesc = dolilStr(R.string.onboarding_step_format, pagerState.currentPage + 1, pages.size)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = DolilSpacing.Space2)
                    .semantics(mergeDescendants = true) {
                        contentDescription = stepDesc
                    }
                    .testTag("onboarding_page_indicator")
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        animationSpec = if (reducedMotion) snap() else androidx.compose.animation.core.tween(220),
                        label = "dotWidth"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        animationSpec = if (reducedMotion) snap() else androidx.compose.animation.core.tween(220),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            Spacer(Modifier.height(DolilSpacing.Space2))

            // Bottom Navigation / Screen 4 CTAs
            if (pagerState.currentPage < pages.lastIndex) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        DolilSecondaryButton(
                            text = dolilStr(R.string.action_previous),
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            icon = DolilIcons.ArrowBack,
                            testTag = "onboarding_prev_button",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val nextLabel = if (pagerState.currentPage == 0) {
                        dolilStr(R.string.onboarding_1_cta)
                    } else {
                        dolilStr(R.string.action_next)
                    }
                    DolilPrimaryButton(
                        text = nextLabel,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        icon = DolilIcons.ArrowForward,
                        testTag = "onboarding_next_button",
                        modifier = Modifier.weight(1.3f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                ) {
                    DolilPrimaryButton(
                        text = dolilStr(R.string.onboarding_4_get_started),
                        onClick = onLoginOrRegisterFromOnboarding,
                        icon = DolilIcons.SignIn,
                        testTag = "onboarding_get_started_button",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("onboarding_prev_button")
                        ) {
                            Icon(DolilIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dolilStr(R.string.action_previous))
                        }

                        DolilSecondaryButton(
                            text = dolilStr(R.string.onboarding_4_guest),
                            onClick = onExploreAsGuest,
                            icon = DolilIcons.Explore,
                            testTag = "onboarding_guest_button",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccessSelectionScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onEnterGuestMode: () -> Unit,
    onViewOnboardingAgain: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val semantic = MaterialTheme.dolilColors

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var isResetLoading by remember { mutableStateOf(false) }

    val localizedContext = LocalLocalizedContext.current ?: context

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {
                Log.d("AccessSelection", "No cached Google credential; showing access options.")
            }
        )
    }

    val isAnyAuthBusy = isGoogleLoading || isEmailLoading || isResetLoading

    fun startGoogleSignIn() {
        if (isAnyAuthBusy) return
        errorMessage = null
        infoMessage = null
        isGoogleLoading = true
        AuthManager.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            language = language,
            onAuthSuccess = {
                isGoogleLoading = false
                onAuthSuccess()
            },
            onAuthError = { rawMsg ->
                isGoogleLoading = false
                errorMessage = rawMsg
            },
            onAuthCancelled = {
                isGoogleLoading = false
                errorMessage = localizedContext.getString(R.string.auth_error_cancelled)
            }
        )
    }

    fun submitEmailAuth() {
        if (isAnyAuthBusy) return
        errorMessage = null
        infoMessage = null
        if (isRegisterMode) {
            val validationErrors = AuthValidator.validateRegistration(
                fullName = fullName,
                email = email,
                password = password,
                confirmPassword = confirmPassword
            )
            if (validationErrors.isNotEmpty()) {
                errorMessage = AuthValidator.validationMessage(validationErrors.first(), language)
                return
            }
            isEmailLoading = true
            scope.launch {
                val res = AuthManager.registerWithEmailPassword(
                    context = context,
                    fullName = fullName.trim(),
                    email = email.trim(),
                    password = password,
                    language = language
                )
                isEmailLoading = false
                when (res) {
                    is AuthActionOutcome.Success -> onAuthSuccess()
                    is AuthActionOutcome.Error -> errorMessage = res.message
                    is AuthActionOutcome.Cancelled -> errorMessage = res.reason
                }
            }
        } else {
            val validationErrors = AuthValidator.validateSignIn(
                email = email,
                password = password
            )
            if (validationErrors.isNotEmpty()) {
                errorMessage = AuthValidator.validationMessage(validationErrors.first(), language)
                return
            }
            isEmailLoading = true
            scope.launch {
                val res = AuthManager.signInWithEmailPassword(
                    context = context,
                    email = email.trim(),
                    password = password,
                    language = language
                )
                isEmailLoading = false
                when (res) {
                    is AuthActionOutcome.Success -> onAuthSuccess()
                    is AuthActionOutcome.Error -> errorMessage = res.message
                    is AuthActionOutcome.Cancelled -> errorMessage = res.reason
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("access_selection_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DolilSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space4)
        ) {
            // Top bar: View Intro + Language Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onViewOnboardingAgain,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("access_view_intro_button")
                ) {
                    Icon(
                        imageVector = DolilIcons.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = dolilStr(R.string.access_view_intro),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = DolilIcons.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.displayLabel) },
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .testTag("access_lang_${lang.code}")
                        )
                    }
                }
            }

            // Friendly Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = DolilIcons.deedSealPainter(),
                            contentDescription = dolilStr(R.string.app_name),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = dolilStr(R.string.access_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = dolilStr(R.string.access_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            errorMessage?.let { msg ->
                DolilErrorBanner(
                    message = msg,
                    onDismiss = { errorMessage = null }
                )
            }

            infoMessage?.let { msg ->
                Surface(
                    color = semantic.successContainer,
                    shape = RoundedCornerShape(DolilRadius.Input),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("access_info_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(DolilSpacing.Space3),
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(DolilIcons.Approve, contentDescription = null, tint = semantic.success)
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.onSuccessContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Hero Card: Google Sign-In
            AccessChoiceCard(
                icon = DolilIcons.SignIn,
                title = dolilStr(R.string.access_google_title),
                description = dolilStr(R.string.access_google_desc),
                buttonLabel = dolilStr(R.string.access_google_btn),
                isPrimary = true,
                badgeText = dolilStr(R.string.access_google_badge),
                isSuccessBadge = true,
                isLoading = isGoogleLoading,
                enabled = !isAnyAuthBusy,
                buttonTestTag = "access_login_button",
                onClick = ::startGoogleSignIn
            )

            // Email & Password Card (Sign In + Register + Forgot Password)
            Surface(
                shape = RoundedCornerShape(DolilRadius.Card),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(DolilComponentTokens.CardBorderWidth, semantic.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("access_email_card")
            ) {
                Column(
                    modifier = Modifier.padding(DolilSpacing.Space4),
                    verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)
                    ) {
                        Icon(
                            imageVector = DolilIcons.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (language == AppLanguage.BN) "ইমেইল ও পাসওয়ার্ড দিয়ে প্রবেশ" else "Email & Password Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isRegisterMode,
                            onClick = {
                                isRegisterMode = false
                                errorMessage = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("access_mode_signin")
                        ) {
                            Text(if (language == AppLanguage.BN) "লগইন" else "Sign In")
                        }
                        SegmentedButton(
                            selected = isRegisterMode,
                            onClick = {
                                isRegisterMode = true
                                errorMessage = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("access_mode_register")
                        ) {
                            Text(if (language == AppLanguage.BN) "নতুন অ্যাকাউন্ট" else "Create Account")
                        }
                    }

                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text(if (language == AppLanguage.BN) "পূর্ণ নাম" else "Full Name") },
                            singleLine = true,
                            leadingIcon = { Icon(DolilIcons.Person, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("access_fullname_input")
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(if (language == AppLanguage.BN) "ইমেইল ঠিকানা" else "Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        leadingIcon = { Icon(DolilIcons.Email, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("access_email_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (language == AppLanguage.BN) "পাসওয়ার্ড" else "Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        leadingIcon = { Icon(DolilIcons.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("access_password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) DolilIcons.VisibilityOff else DolilIcons.Visibility,
                                    contentDescription = if (language == AppLanguage.BN) "পাসওয়ার্ড দেখান/লুকান" else "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("access_password_input")
                    )

                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(if (language == AppLanguage.BN) "পাসওয়ার্ড নিশ্চিত করুন" else "Confirm Password") },
                            singleLine = true,
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = { Icon(DolilIcons.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("access_confirm_password_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) DolilIcons.VisibilityOff else DolilIcons.Visibility,
                                        contentDescription = if (language == AppLanguage.BN) "পাসওয়ার্ড দেখান/লুকান" else "Toggle password visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("access_confirm_password_input")
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    resetEmail = email.trim()
                                    showForgotDialog = true
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("access_forgot_password_button")
                            ) {
                                Text(if (language == AppLanguage.BN) "পাসওয়ার্ড ভুলে গেছেন?" else "Forgot Password?")
                            }
                        }
                    }

                    Button(
                        onClick = ::submitEmailAuth,
                        enabled = !isAnyAuthBusy,
                        shape = RoundedCornerShape(DolilRadius.Button),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .height(50.dp)
                            .testTag("access_email_submit_button")
                    ) {
                        if (isEmailLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(DolilIcons.SignIn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRegisterMode) {
                                    if (language == AppLanguage.BN) "নতুন অ্যাকাউন্ট তৈরি করুন" else "Create Account"
                                } else {
                                    if (language == AppLanguage.BN) "ইমেইল দিয়ে লগইন করুন" else "Sign In with Email"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Secondary Card: Explore as Guest (Local Sandbox)
            AccessChoiceCard(
                icon = DolilIcons.Explore,
                title = dolilStr(R.string.access_guest_title),
                description = dolilStr(R.string.access_guest_desc),
                buttonLabel = dolilStr(R.string.access_guest_btn),
                isPrimary = false,
                badgeText = dolilStr(R.string.access_guest_badge),
                isSuccessBadge = false,
                isLoading = false,
                enabled = !isAnyAuthBusy,
                buttonTestTag = "access_guest_button",
                onClick = onEnterGuestMode
            )

            Spacer(Modifier.height(DolilSpacing.Space2))
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetLoading) showForgotDialog = false },
            title = {
                Text(if (language == AppLanguage.BN) "পাসওয়ার্ড রিসেট করুন" else "Reset Password")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space2)) {
                    Text(
                        text = if (language == AppLanguage.BN) {
                            "আপনার নিবন্ধিত ইমেইল লিখুন। সেখানে পাসওয়ার্ড পরিবর্তনের লিংক পাঠানো হবে।"
                        } else {
                            "Enter your registered email address and we will send a password reset link."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        singleLine = true,
                        label = { Text(if (language == AppLanguage.BN) "ইমেইল ঠিকানা" else "Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("access_reset_email_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isResetLoading,
                    onClick = {
                        val validationErr = AuthValidator.validateEmail(resetEmail)
                        if (validationErr != null) {
                            errorMessage = AuthValidator.validationMessage(validationErr, language)
                            showForgotDialog = false
                            return@TextButton
                        }
                        isResetLoading = true
                        scope.launch {
                            val res = AuthManager.sendPasswordResetEmail(
                                context = context,
                                email = resetEmail.trim(),
                                language = language
                            )
                            isResetLoading = false
                            showForgotDialog = false
                            res.onSuccess {
                                infoMessage = if (language == AppLanguage.BN) {
                                    "পাসওয়ার্ড রিসেট ইমেইল পাঠানো হয়েছে।"
                                } else {
                                    "Password reset email sent. Check your inbox."
                                }
                            }.onFailure { err ->
                                errorMessage = err.localizedMessage ?: "Failed to send reset email"
                            }
                        }
                    },
                    modifier = Modifier.testTag("access_reset_confirm_button")
                ) {
                    Text(if (language == AppLanguage.BN) "লিংক পাঠান" else "Send Reset Email")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isResetLoading,
                    onClick = { showForgotDialog = false }
                ) {
                    Text(dolilStr(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AccessChoiceCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    isPrimary: Boolean,
    badgeText: String?,
    isSuccessBadge: Boolean,
    isLoading: Boolean,
    enabled: Boolean = true,
    buttonTestTag: String,
    onClick: () -> Unit
) {
    val semantic = MaterialTheme.dolilColors
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(DolilRadius.Card),
        color = if (isPrimary) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            DolilComponentTokens.CardBorderWidth,
            if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else semantic.cardBorder
        ),
        tonalElevation = if (isPrimary) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(DolilSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DolilSpacing.Space3)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else semantic.surfaceSubtle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            color = if (isSuccessBadge) semantic.successContainer else semantic.warningContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSuccessBadge) {
                                    Icon(
                                        imageVector = DolilIcons.Approve,
                                        contentDescription = null,
                                        tint = semantic.success,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSuccessBadge) semantic.onSuccessContainer else semantic.onWarningContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isPrimary) {
                Button(
                    onClick = {
                        haptics.performSubtleTick()
                        onClick()
                    },
                    enabled = enabled && !isLoading,
                    shape = RoundedCornerShape(DolilRadius.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .height(50.dp)
                        .testTag(buttonTestTag)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        haptics.performSubtleTick()
                        onClick()
                    },
                    enabled = enabled && !isLoading,
                    shape = RoundedCornerShape(DolilRadius.Button),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .height(50.dp)
                        .testTag(buttonTestTag)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
