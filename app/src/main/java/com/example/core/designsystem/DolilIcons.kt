package com.example.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoEncryption
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.example.R

/**
 * Centralized Material Symbols iconography registry for Dolil Onusondhan.
 * Combines custom-bundled Material Symbols Rounded/Outlined vector drawables
 * with harmonized Rounded/Outlined navigation and action glyphs.
 */
object DolilIcons {
    // Navigation (Active Filled vs Inactive Outlined)
    val HomeSelected = Icons.Rounded.Home
    val HomeUnselected = Icons.Outlined.Home
    val SearchSelected = Icons.Rounded.Search
    val SearchUnselected = Icons.Outlined.Search
    val RecordsSelected = Icons.Rounded.Description
    val RecordsUnselected = Icons.Outlined.Description
    val ProfileSelected = Icons.Rounded.Person
    val ProfileUnselected = Icons.Outlined.Person

    // Directional (AutoMirrored)
    val ArrowBack = Icons.AutoMirrored.Rounded.ArrowBack
    val ArrowForward = Icons.AutoMirrored.Rounded.ArrowForward
    val Sort = Icons.AutoMirrored.Rounded.Sort
    val Help = Icons.AutoMirrored.Rounded.HelpOutline
    val GuideBook = Icons.AutoMirrored.Rounded.MenuBook
    val SignIn = Icons.AutoMirrored.Rounded.Login
    val SignOut = Icons.AutoMirrored.Rounded.Logout

    // Core Actions & Status
    val Add = Icons.Rounded.Add
    val AddCircle = Icons.Rounded.AddCircleOutline
    val Save = Icons.Rounded.Save
    val Edit = Icons.Rounded.Edit
    val Draft = Icons.Rounded.EditNote
    val Check = Icons.Rounded.Check
    val Approve = Icons.Rounded.CheckCircle
    val Pending = Icons.Rounded.HourglassTop
    val PendingQueue = Icons.Rounded.PendingActions
    val Archive = Icons.Rounded.Archive
    val Restore = Icons.Rounded.RestoreFromTrash
    val Delete = Icons.Rounded.DeleteOutline
    val DeleteForever = Icons.Rounded.DeleteForever
    val Copy = Icons.Rounded.ContentCopy
    val Download = Icons.Rounded.Download
    val UploadFile = Icons.Rounded.UploadFile
    val Print = Icons.Rounded.Print
    val Clear = Icons.Rounded.Clear
    val Close = Icons.Rounded.Close
    val Replay = Icons.Rounded.Replay
    val Reset = Icons.Rounded.RestartAlt
    val PlayCircle = Icons.Rounded.PlayCircleOutline
    val ExpandMore = Icons.Rounded.ExpandMore
    val ExpandLess = Icons.Rounded.ExpandLess
    val ChevronRight = Icons.Rounded.ChevronRight
    val Visibility = Icons.Rounded.Visibility
    val VisibilityOff = Icons.Rounded.VisibilityOff

    // Domain & Analytics
    val RegistrySeal = Icons.Rounded.AccountBalance
    val Verified = Icons.Rounded.VerifiedUser
    val Security = Icons.Rounded.Security
    val Lock = Icons.Rounded.Lock
    val Key = Icons.Rounded.Key
    val Email = Icons.Rounded.Email
    val Badge = Icons.Rounded.Badge
    val Code = Icons.Rounded.Code
    val Devices = Icons.Rounded.Devices
    val Backup = Icons.Rounded.Backup
    val Sync = Icons.Rounded.Sync
    val Encrypted = Icons.Rounded.EnhancedEncryption
    val Unencrypted = Icons.Rounded.NoEncryption
    val Explore = Icons.Rounded.Explore
    val Bolt = Icons.Rounded.Bolt
    val Stats = Icons.Rounded.BarChart
    val Folder = Icons.Rounded.Folder
    val FolderCopy = Icons.Rounded.FolderCopy
    val Today = Icons.Rounded.Today
    val History = Icons.Rounded.History
    val Person = Icons.Rounded.Person
    val People = Icons.Rounded.People
    val Group = Icons.Rounded.Group
    val Location = Icons.Rounded.LocationOn
    val Land = Icons.Rounded.Landscape
    val Calculate = Icons.Rounded.Calculate
    val Calendar = Icons.Rounded.CalendarToday
    val Filter = Icons.Rounded.FilterList
    val SearchOff = Icons.Rounded.SearchOff
    val Document = Icons.Rounded.Description
    val Language = Icons.Rounded.Language
    val Translate = Icons.Rounded.Translate
    val Palette = Icons.Rounded.Palette
    val LightMode = Icons.Rounded.LightMode
    val DarkMode = Icons.Rounded.DarkMode
    val BrightnessAuto = Icons.Rounded.BrightnessAuto
    val AdminPanel = Icons.Rounded.AdminPanelSettings
    val PrivacyTip = Icons.Rounded.PrivacyTip

    // Sync & Feedback
    val CloudDone = Icons.Rounded.CloudDone
    val CloudSync = Icons.Rounded.CloudSync
    val CloudUpload = Icons.Rounded.CloudUpload
    val CloudOff = Icons.Rounded.CloudOff
    val SyncProblem = Icons.Rounded.SyncProblem
    val Error = Icons.Rounded.ErrorOutline
    val Warning = Icons.Rounded.WarningAmber
    val Info = Icons.Rounded.Info

    // Custom Material Symbols Vector Painters
    @Composable
    fun deedSealPainter(): Painter = painterResource(id = R.drawable.ic_ms_deed_seal)

    @Composable
    fun mouzaMapPainter(): Painter = painterResource(id = R.drawable.ic_ms_mouza_map)

    @Composable
    fun khatianLedgerPainter(): Painter = painterResource(id = R.drawable.ic_ms_khatian_ledger)

    @Composable
    fun partyTransferPainter(): Painter = painterResource(id = R.drawable.ic_ms_party_transfer)

    @Composable
    fun stepCheckPainter(): Painter = painterResource(id = R.drawable.ic_ms_step_check)
}
