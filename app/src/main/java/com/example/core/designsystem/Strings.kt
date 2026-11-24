package com.example.core.designsystem

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedStatus
import com.example.domain.model.DeedType
import com.example.domain.model.DeedVisibility
import com.example.domain.model.LandClass
import com.example.domain.model.LandUnit
import com.example.domain.model.MatchReason
import com.example.domain.model.SortOption
import com.example.domain.model.SyncState
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserRole
import com.example.domain.validation.ValidationErrorCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalLocalizedContext = staticCompositionLocalOf<Context?> { null }

@Composable
fun ProvideLocalizedContext(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        val locale = Locale.forLanguageTag(language.code)
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(locale)
        }
        baseContext.createConfigurationContext(config)
    }
    CompositionLocalProvider(LocalLocalizedContext provides localizedContext) {
        content()
    }
}

@Composable
fun dolilStr(@StringRes resId: Int, vararg formatArgs: Any): String {
    val context = LocalLocalizedContext.current ?: LocalContext.current
    return if (formatArgs.isEmpty()) {
        context.getString(resId)
    } else {
        context.getString(resId, *formatArgs)
    }
}

object DolilStrings {
    fun appTitle(lang: AppLanguage) = if (lang == AppLanguage.BN) "দলিল অনুসন্ধান" else "Dolil Onusondhan"
    fun appSubtitle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "বাংলাদেশ জমি ও দলিল তথ্য ব্যবস্থাপনা" else "Bangladesh Land Deed Information System"

    // Navigation destinations
    fun navHome(lang: AppLanguage) = if (lang == AppLanguage.BN) "হোম" else "Home"
    fun navSearch(lang: AppLanguage) = if (lang == AppLanguage.BN) "খোঁজ" else "Search"
    fun navAddDeed(lang: AppLanguage) = if (lang == AppLanguage.BN) "নতুন দলিল" else "Add Deed"
    fun navRecords(lang: AppLanguage) = if (lang == AppLanguage.BN) "দলিল" else "Records"
    fun navProfile(lang: AppLanguage) = if (lang == AppLanguage.BN) "প্রোফাইল" else "Profile"

    // Locale-aware date & numeric helpers
    fun formatDate(millis: Long, lang: AppLanguage): String {
        val locale = if (lang == AppLanguage.BN) Locale.forLanguageTag("bn-BD") else Locale.ENGLISH
        return SimpleDateFormat("dd MMM yyyy", locale).format(Date(millis))
    }

    fun formatDateTime(millis: Long, lang: AppLanguage): String {
        val locale = if (lang == AppLanguage.BN) Locale.forLanguageTag("bn-BD") else Locale.ENGLISH
        return SimpleDateFormat("dd MMM yyyy, HH:mm", locale).format(Date(millis))
    }

    fun shotokUnit(lang: AppLanguage): String = if (lang == AppLanguage.BN) "শতাংশ" else "Shotok"

    fun deedNumberFormatted(deedNumber: String, year: Int, lang: AppLanguage): String {
        val fallback = if (lang == AppLanguage.BN) "খসড়া" else "Draft"
        val clean = deedNumber.ifBlank { fallback }
        return if (lang == AppLanguage.BN) "দলিল নং $clean ($year)" else "Deed #$clean ($year)"
    }

    // Auth & Access
    fun signInTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "দলিল অনুসন্ধানে স্বাগতম!" else "Welcome to Dolil Onusondhan!"
    fun signInSubtitle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) {
            "আপনার Google Account দিয়ে প্রবেশ করুন এবং দলিলের তথ্য, খসড়া ও ক্লাউড সিঙ্ক সহজভাবে পরিচালনা করুন।"
        } else {
            "Sign in with Google to search authorized deeds, keep drafts safely, and sync across devices."
        }
    fun signInWithGoogle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "Google দিয়ে প্রবেশ করুন" else "Continue with Google"
    fun signOut(lang: AppLanguage) = if (lang == AppLanguage.BN) "লগ আউট" else "Sign Out"
    fun signOutConfirmTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "এখন কি বের হতে চান?" else "Ready to Sign Out?"
    fun signOutConfirmBody(lang: AppLanguage) =
        if (lang == AppLanguage.BN) {
            "আপনার খসড়াগুলো এই ডিভাইসেই নিরাপদে থাকবে। তবে লগ আউট করলে নতুন করে প্রবেশ না করা পর্যন্ত Cloud Sync বন্ধ থাকবে।"
        } else {
            "Your local drafts stay safe on this device, while cloud sync pauses until you sign back in. Continue?"
        }

    fun guestSyncNotice(lang: AppLanguage) =
        if (lang == AppLanguage.BN) {
            "নিজের দলিল সংরক্ষণ ও বিনামূল্যে ক্লাউড সিঙ্ক ব্যবহার করতে Google দিয়ে প্রবেশ করুন।"
        } else {
            "Sign in for free with your Google account to save and sync your own deeds across devices."
        }

    fun saveSuccessMessage(lang: AppLanguage, isDraft: Boolean, isUpdate: Boolean): String = when {
        isDraft && lang == AppLanguage.BN -> "খসড়া রাখা হয়েছে! যেকোনো সময় এটি সম্পূর্ণ করতে পারবেন।"
        isDraft -> "Saved! You can finish this draft anytime."
        isUpdate && lang == AppLanguage.BN -> "চমৎকার! পরিবর্তনগুলো সংরক্ষণ করা হয়েছে।"
        isUpdate -> "Great! Your changes have been saved."
        lang == AppLanguage.BN -> "হয়ে গেছে! আপনার তথ্য সফলভাবে সংরক্ষণ হয়েছে।"
        else -> "All done! Your information has been saved."
    }

    fun friendlyAuthErrorResId(rawError: String?): Int {
        val lower = rawError.orEmpty().lowercase()
        return when {
            "network" in lower || "timeout" in lower || "offline" in lower || "internet" in lower || "connect" in lower ->
                R.string.auth_error_network
            "cancel" in lower || "dismiss" in lower ->
                R.string.auth_error_cancelled
            else ->
                R.string.auth_error_generic
        }
    }

    // Dashboard
    fun searchPlaceholder(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "নাম, দলিল নম্বর, দাগ বা খতিয়ান লিখুন" else "Search by deed no, name, dag, or khatian"
    fun quickActions(lang: AppLanguage) = if (lang == AppLanguage.BN) "দ্রুত কাজ" else "Quick Actions"
    fun allDeeds(lang: AppLanguage) = if (lang == AppLanguage.BN) "সকল দলিল" else "All Deeds"
    fun personInfo(lang: AppLanguage) = if (lang == AppLanguage.BN) "ব্যক্তির তথ্য" else "Persons"
    fun landInfo(lang: AppLanguage) = if (lang == AppLanguage.BN) "জমির তথ্য" else "Land & Mouza"
    fun userGuide(lang: AppLanguage) = if (lang == AppLanguage.BN) "ব্যবহার নির্দেশিকা" else "User Guide"
    fun officeStats(lang: AppLanguage) = if (lang == AppLanguage.BN) "অফিস পরিসংখ্যান" else "Office Statistics"
    fun totalDeeds(lang: AppLanguage) = if (lang == AppLanguage.BN) "মোট দলিল" else "Total Deeds"
    fun todayEntries(lang: AppLanguage) = if (lang == AppLanguage.BN) "আজকের এন্ট্রি" else "Today's Entries"
    fun drafts(lang: AppLanguage) = if (lang == AppLanguage.BN) "খসড়া (Draft)" else "Drafts"
    fun pendingReview(lang: AppLanguage) = if (lang == AppLanguage.BN) "যাচাই অপেক্ষমাণ" else "Pending Review"
    fun recentRecords(lang: AppLanguage) = if (lang == AppLanguage.BN) "সাম্প্রতিক দলিল" else "Recent Records"
    fun undoAction(lang: AppLanguage) = if (lang == AppLanguage.BN) "পূর্বাবস্থায় ফেরান" else "Undo"
    fun optionalDetails(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "অতিরিক্ত তথ্য (ঐচ্ছিক)" else "Optional Details"

    // Deed Entry sections
    fun newDeedTitle(lang: AppLanguage) = if (lang == AppLanguage.BN) "নতুন দলিল যোগ" else "New Deed Entry"
    fun editDeedTitle(lang: AppLanguage) = if (lang == AppLanguage.BN) "দলিল সম্পাদনা" else "Edit Deed Record"
    fun secGeneral(lang: AppLanguage) = if (lang == AppLanguage.BN) "ক. সাধারণ তথ্য" else "A. General Information"
    fun secGrantors(lang: AppLanguage) = if (lang == AppLanguage.BN) "খ. দাতার তথ্য" else "B. Grantors"
    fun secGrantees(lang: AppLanguage) = if (lang == AppLanguage.BN) "গ. গ্রহীতার তথ্য" else "C. Grantees"
    fun secLocation(lang: AppLanguage) = if (lang == AppLanguage.BN) "ঘ. জমির অবস্থান" else "D. Location Information"
    fun secLand(lang: AppLanguage) = if (lang == AppLanguage.BN) "ঙ. তফসিল ও জমির তথ্য" else "E. Land & Parcel Details"

    fun deedTypeLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "দলিলের ধরন" else "Deed Type"
    fun deedNumberLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "দলিল নম্বর" else "Deed Number"
    fun deedYearLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "দলিলের সাল" else "Deed Year"
    fun regDateLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "রেজিস্ট্রির তারিখ" else "Registration Date"
    fun officeLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "সাব-রেজিস্ট্রি অফিস" else "Sub-Registry Office"
    fun districtLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "জেলা" else "District"
    fun upazilaLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "উপজেলা / থানা" else "Upazila / Thana"
    fun unionLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "ইউনিয়ন / ওয়ার্ড" else "Union / Ward"
    fun mouzaLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "মৌজা" else "Mouza"
    fun jlNumberLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "জে.এল. নম্বর" else "J.L. Number"

    fun fullNameLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "পূর্ণ নাম" else "Full Name"
    fun grantorNameLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "দাতার নাম" else "Grantor Name"
    fun granteeNameLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "গ্রহীতার নাম" else "Grantee Name"
    fun fatherNameLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "পিতার নাম" else "Father's Name"
    fun motherNameLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "মাতার নাম" else "Mother's Name"
    fun spouseNameLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "স্বামী/স্ত্রীর নাম" else "Spouse's Name"
    fun addressLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "ঠিকানা" else "Address"

    fun surveyTypeLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "জরিপের ধরন" else "Survey Type"
    fun khatianLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "খতিয়ান নম্বর" else "Khatian Number"
    fun dagLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "দাগ নম্বর" else "Dag Number"
    fun landClassLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "জমির শ্রেণি" else "Land Classification"
    fun landAreaLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "জমির পরিমাণ" else "Land Area"
    fun landUnitLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "একক" else "Land Unit"
    fun boundaryLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "চৌহদ্দি বিবরণ" else "Boundary Description"
    fun notesLabel(lang: AppLanguage) = if (lang == AppLanguage.BN) "মন্তব্য / নোট" else "Notes"

    fun addAnotherGrantor(lang: AppLanguage) = if (lang == AppLanguage.BN) "+ আরও একজন দাতা যোগ করুন" else "+ Add Another Grantor"
    fun addAnotherGrantee(lang: AppLanguage) = if (lang == AppLanguage.BN) "+ আরও একজন গ্রহীতা যোগ করুন" else "+ Add Another Grantee"
    fun addAnotherParcel(lang: AppLanguage) = if (lang == AppLanguage.BN) "+ আরেকটি দাগ যোগ করুন" else "+ Add Another Parcel"
    fun saveDraft(lang: AppLanguage) = if (lang == AppLanguage.BN) "খসড়া রাখুন" else "Save Draft"
    fun saveDeed(lang: AppLanguage) = if (lang == AppLanguage.BN) "দলিল সংরক্ষণ করুন" else "Save Deed"
    fun cancel(lang: AppLanguage) = if (lang == AppLanguage.BN) "বাতিল" else "Cancel"
    fun remove(lang: AppLanguage) = if (lang == AppLanguage.BN) "সরান" else "Remove"

    fun roleLabel(role: UserRole, lang: AppLanguage): String = when (role) {
        UserRole.PUBLIC_USER -> if (lang == AppLanguage.BN) "সাধারণ ব্যবহারকারী" else "Public User"
        UserRole.OFFICE_VIEWER -> if (lang == AppLanguage.BN) "অফিস দর্শক" else "Office Viewer"
        UserRole.OFFICE_DATA_ENTRY -> if (lang == AppLanguage.BN) "অফিস ডাটা এন্ট্রি" else "Office Data Entry"
        UserRole.OFFICE_SUPERVISOR -> if (lang == AppLanguage.BN) "অফিস সুপারভাইজার" else "Office Supervisor"
        UserRole.ORGANIZATION_ADMIN -> if (lang == AppLanguage.BN) "সংস্থা অ্যাডমিন" else "Organization Admin"
        UserRole.PLATFORM_ADMIN -> if (lang == AppLanguage.BN) "প্ল্যাটফর্ম অ্যাডমিন" else "Platform Admin"
    }

    fun deedTypeText(type: DeedType, lang: AppLanguage) = if (lang == AppLanguage.BN) type.bnLabel else type.enLabel
    fun statusText(status: DeedStatus, lang: AppLanguage) = if (lang == AppLanguage.BN) status.bnLabel else status.enLabel
    fun visibilityText(visibility: DeedVisibility, lang: AppLanguage) =
        if (lang == AppLanguage.BN) visibility.bnLabel else visibility.enLabel
    fun landClassText(lc: LandClass, lang: AppLanguage) = if (lang == AppLanguage.BN) lc.bnLabel else lc.enLabel
    fun landUnitText(unit: LandUnit, lang: AppLanguage) = if (lang == AppLanguage.BN) unit.bnLabel else unit.enLabel
    fun sortText(option: SortOption, lang: AppLanguage) = if (lang == AppLanguage.BN) option.bnLabel else option.enLabel
    fun syncText(state: SyncState, lang: AppLanguage) = if (lang == AppLanguage.BN) state.bnLabel else state.enLabel
    fun themeText(mode: ThemeMode, lang: AppLanguage) = if (lang == AppLanguage.BN) mode.bnLabel else mode.enLabel
    fun matchReasonText(reason: MatchReason, lang: AppLanguage) = if (lang == AppLanguage.BN) reason.bnLabel else reason.enLabel

    fun dynamicColorTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "Material You ডায়নামিক কালার" else "Material You Dynamic Color"

    fun dynamicColorSubtitle(lang: AppLanguage, supported: Boolean): String = when {
        !supported && lang == AppLanguage.BN -> "Android 12 বা তার পরবর্তী সংস্করণে উপলব্ধ"
        !supported -> "Available on Android 12 and newer"
        lang == AppLanguage.BN -> "ডিভাইস ওয়ালপেপারের রঙের সাথে থিম সমন্বয় করুন (বন্ধ থাকলে কাগজ ও কালি থিম থাকবে)"
        else -> "Adapt app surfaces to your wallpaper colors (Paper & Ink remains default when off)"
    }

    fun selectDeedPreviewTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) "বিস্তারিত দেখতে একটি দলিল নির্বাচন করুন" else "Select a deed to inspect"

    fun selectDeedPreviewSubtitle(lang: AppLanguage) =
        if (lang == AppLanguage.BN) {
            "বাম পাশের তালিকা থেকে যেকোনো দলিল বেছে নিলে এখানে পূর্ণাঙ্গ তফসিল ও অনুমোদনের তথ্য দেখা যাবে।"
        } else {
            "Choose any record from the list to inspect parties, parcels, and approval details side by side."
        }

    fun validationMessage(code: ValidationErrorCode, lang: AppLanguage): String = when (code) {
        ValidationErrorCode.REQUIRED_DEED_NUMBER ->
            if (lang == AppLanguage.BN) "দয়া করে দলিল নম্বরটি লিখুন।" else "Please enter the deed number."
        ValidationErrorCode.INVALID_DEED_YEAR ->
            if (lang == AppLanguage.BN) "সালটি আরেকবার দেখুন (১৯০০ থেকে বর্তমান+১ এর মধ্যে হবে)।" else "Please check the deed year (1900 to current+1)."
        ValidationErrorCode.INVALID_REGISTRATION_DATE ->
            if (lang == AppLanguage.BN) "রেজিস্ট্রির তারিখটি বেছে নিন।" else "Please pick the registration date."
        ValidationErrorCode.REQUIRED_GRANTOR_NAME ->
            if (lang == AppLanguage.BN) "কমপক্ষে একজন দাতার নাম যোগ করুন।" else "Please add at least one grantor name."
        ValidationErrorCode.REQUIRED_GRANTEE_NAME ->
            if (lang == AppLanguage.BN) "কমপক্ষে একজন গ্রহীতার নাম যোগ করুন।" else "Please add at least one grantee name."
        ValidationErrorCode.REQUIRED_DISTRICT ->
            if (lang == AppLanguage.BN) "জমিটি কোন জেলায়, তা বেছে নিন।" else "Please select the district."
        ValidationErrorCode.REQUIRED_UPAZILA ->
            if (lang == AppLanguage.BN) "উপজেলা বা থানা নির্বাচন করুন।" else "Please select the upazila."
        ValidationErrorCode.REQUIRED_MOUZA ->
            if (lang == AppLanguage.BN) "মৌজার নাম নির্বাচন করুন।" else "Please select the mouza."
        ValidationErrorCode.REQUIRED_JL_NUMBER ->
            if (lang == AppLanguage.BN) "জে.এল. নম্বরটি লিখুন।" else "Please enter the J.L. number."
        ValidationErrorCode.REQUIRED_PARCEL ->
            if (lang == AppLanguage.BN) "কমপক্ষে একটি জমির তফসিল যোগ করুন।" else "Please add at least one land parcel."
        ValidationErrorCode.REQUIRED_KHATIAN ->
            if (lang == AppLanguage.BN) "জমির খতিয়ান নম্বরটি লিখে দিন।" else "Please enter the khatian number for each parcel."
        ValidationErrorCode.REQUIRED_DAG ->
            if (lang == AppLanguage.BN) "জমির দাগ নম্বরটি লিখে দিন।" else "Please enter the dag number for each parcel."
        ValidationErrorCode.INVALID_LAND_AREA ->
            if (lang == AppLanguage.BN) "জমির পরিমাণ ০-এর বেশি হতে হবে।" else "Please enter a land area greater than 0."
        ValidationErrorCode.DUPLICATE_DEED_IN_OFFICE ->
            if (lang == AppLanguage.BN) "এই অফিস ও সালে একই দলিল নম্বর আগেই যোগ করা আছে!" else "A deed with this number already exists in this office and year."
    }
}
