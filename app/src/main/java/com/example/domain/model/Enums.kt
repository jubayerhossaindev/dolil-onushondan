package com.example.domain.model

enum class UserRole(val firestoreValue: String) {
    PUBLIC_USER("PUBLIC_USER"),
    OFFICE_VIEWER("OFFICE_VIEWER"),
    OFFICE_DATA_ENTRY("OFFICE_DATA_ENTRY"),
    OFFICE_SUPERVISOR("OFFICE_SUPERVISOR"),
    ORGANIZATION_ADMIN("ORGANIZATION_ADMIN"),
    PLATFORM_ADMIN("PLATFORM_ADMIN");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: PUBLIC_USER
    }
}

enum class DeedType(val firestoreValue: String, val bnLabel: String, val enLabel: String) {
    SAF_KABALA("SAF_KABALA", "সাফ কবলা", "Saf Kabala (Sale Deed)"),
    DANPATRA("DANPATRA", "দানপত্র", "Danpatra (Gift Deed)"),
    HEBA("HEBA", "হেবা", "Heba (Declaration of Gift)"),
    BANTONNAMA("BANTONNAMA", "বণ্টননামা", "Bantannama (Partition Deed)"),
    BINIMOY("BINIMOY", "বিনিময়", "Binimoy (Exchange Deed)"),
    OTHER("OTHER", "অন্যান্য", "Other Deed");

    companion object {
        fun fromRaw(raw: String?): DeedType =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: SAF_KABALA
    }
}

enum class DeedStatus(val firestoreValue: String, val bnLabel: String, val enLabel: String) {
    DRAFT("DRAFT", "খসড়া", "Draft"),
    PENDING_REVIEW("PENDING_REVIEW", "যাচাইয়ের অপেক্ষায়", "Pending Review"),
    APPROVED("APPROVED", "অনুমোদিত", "Approved"),
    SOFT_DELETED("SOFT_DELETED", "আর্কাইভকৃত", "Archived");

    companion object {
        fun fromRaw(raw: String?): DeedStatus =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: DRAFT
    }
}

enum class DeedVisibility(val firestoreValue: String, val bnLabel: String, val enLabel: String) {
    PUBLIC("PUBLIC", "সর্বজনীন", "Public"),
    OFFICE_ONLY("OFFICE_ONLY", "শুধুমাত্র অফিস", "Office Only"),
    PRIVATE_DRAFT("PRIVATE_DRAFT", "ব্যক্তিগত খসড়া", "Private Draft");

    companion object {
        fun fromRaw(raw: String?): DeedVisibility =
            entries.firstOrNull { it.firestoreValue == raw || it.name == raw } ?: PRIVATE_DRAFT
    }
}

enum class SurveyType(val code: String, val bnLabel: String, val enLabel: String) {
    CS("CS", "সি.এস.", "CS"),
    SA("SA", "এস.এ.", "SA"),
    RS("RS", "আর.এস.", "RS"),
    BS("BS", "বি.এস.", "BS"),
    BRS("BRS", "বি.আর.এস.", "BRS");

    companion object {
        fun fromRaw(raw: String?): SurveyType =
            entries.firstOrNull { it.code == raw || it.name == raw } ?: RS
    }
}

enum class LandClass(val code: String, val bnLabel: String, val enLabel: String) {
    NAL("NAL", "নাল / আবাদি", "Nal (Agricultural)"),
    VITI("VITI", "ভিটি / বাস্তু", "Viti (Homestead)"),
    BASTU("BASTU", "বাস্তুভিটা", "Bastu (Residential)"),
    DOBA("DOBA", "ডোবা / পুকুর", "Doba / Pond"),
    CHALA("CHALA", "চালা", "Chala (Highland)"),
    COMMERCIAL("COMMERCIAL", "বাণিজ্যিক", "Commercial"),
    OTHER("OTHER", "অন্যান্য", "Other");

    companion object {
        fun fromRaw(raw: String?): LandClass =
            entries.firstOrNull { it.code == raw || it.name == raw } ?: NAL
    }
}

enum class LandUnit(
    val code: String,
    val bnLabel: String,
    val enLabel: String,
    val toShotokFactor: Double
) {
    DECIMAL("DECIMAL", "শতাংশ", "Decimal / Shotok", 1.0),
    KATHA("KATHA", "কাঠা", "Katha", 1.65),
    BIGHA("BIGHA", "বিঘা", "Bigha", 33.0),
    ACRE("ACRE", "একর", "Acre", 100.0),
    SQUARE_FEET("SQUARE_FEET", "বর্গফুট", "Sq. Ft", 1.0 / 435.6);

    companion object {
        fun fromRaw(raw: String?): LandUnit =
            entries.firstOrNull { it.code == raw || it.name == raw } ?: DECIMAL
    }
}

enum class AppLanguage(val code: String, val displayLabel: String) {
    BN("bn", "বাংলা"),
    EN("en", "English");

    companion object {
        fun fromRaw(raw: String?): AppLanguage =
            entries.firstOrNull { it.code == raw || it.name == raw } ?: BN
    }
}

enum class ThemeMode(val code: String, val bnLabel: String, val enLabel: String) {
    SYSTEM("system", "সিস্টেম", "System"),
    LIGHT("light", "লাইট", "Light"),
    DARK("dark", "ডার্ক", "Dark");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.code == raw || it.name == raw } ?: SYSTEM
    }
}

enum class SortOption(val bnLabel: String, val enLabel: String) {
    REGISTRATION_DATE_DESC("রেজিস্ট্রির তারিখ (নতুন থেকে পুরোনো)", "Registration Date (Newest)"),
    REGISTRATION_DATE_ASC("রেজিস্ট্রির তারিখ (পুরোনো থেকে নতুন)", "Registration Date (Oldest)"),
    DEED_YEAR_DESC("দলিলের সাল (নতুন)", "Deed Year (Newest)"),
    DEED_YEAR_ASC("দলিলের সাল (পুরোনো)", "Deed Year (Oldest)"),
    DEED_NUMBER_ASC("দলিল নম্বর (ক্রমানুসারে)", "Deed Number"),
    GRANTOR_NAME_ASC("দাতার নাম", "Grantor Name"),
    GRANTEE_NAME_ASC("গ্রহীতার নাম", "Grantee Name"),
    MOUZA_NAME_ASC("মৌজার নাম", "Mouza Name"),
    LAND_AREA_DESC("জমির পরিমাণ (বেশি থেকে কম)", "Land Area (Largest)"),
    UPDATED_AT_DESC("সর্বশেষ হালনাগাদ", "Recently Updated")
}

enum class AuditAction(val code: String, val bnLabel: String, val enLabel: String) {
    CREATE("CREATE", "নতুন তৈরি", "Created"),
    UPDATE("UPDATE", "সংশোধন", "Updated"),
    SUBMIT_DRAFT("SUBMIT_DRAFT", "খসড়া দাখিল", "Draft Submitted"),
    APPROVE("APPROVE", "অনুমোদন", "Approved"),
    SOFT_DELETE("SOFT_DELETE", "আর্কাইভ", "Archived"),
    RESTORE("RESTORE", "পুনরুদ্ধার", "Restored");

    companion object {
        fun fromRaw(raw: String?): AuditAction =
            entries.firstOrNull { it.code == raw || it.name == raw } ?: UPDATE
    }
}

enum class SyncState(val bnLabel: String, val enLabel: String) {
    SYNCED("সিঙ্ক সম্পন্ন", "Synced"),
    PENDING_UPLOAD("অফলাইনে সংরক্ষিত (অপেক্ষমাণ)", "Pending Sync"),
    CONFLICT("সংস্করণ দ্বন্দ্ব", "Version Conflict"),
    LOCAL_ONLY_DRAFT("স্থানীয় খসড়া", "Local Draft")
}
