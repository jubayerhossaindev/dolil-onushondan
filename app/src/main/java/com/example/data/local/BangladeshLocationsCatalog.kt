package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.domain.model.AppLanguage
import com.example.domain.model.LocationDistrict
import com.example.domain.model.LocationMouza
import com.example.domain.model.LocationSurveyType
import com.example.domain.model.LocationUpazila
import com.example.domain.search.SearchNormalizer
import org.json.JSONObject
import java.text.Normalizer

data class SubRegistryOfficeOption(
    val officeId: String,
    val bnName: String,
    val enName: String,
    val districtCode: String,
    val upazilaCode: String
)

object BangladeshLocationsCatalog {

    private const val TAG = "BangladeshLocations"
    private const val NARAYANGANJ_ASSET = "narayanganj_locations.json"

    val offices: List<SubRegistryOfficeOption> = listOf(
        SubRegistryOfficeOption("office_narayanganj_sadar", "সাব-রেজিস্ট্রি অফিস, নারায়ণগঞ্জ সদর", "Narayanganj Sadar Sub-Registry Office", "NRG", "NRG_UPZ_58"),
        SubRegistryOfficeOption("office_fatulla", "সাব-রেজিস্ট্রি অফিস, ফতুল্লা", "Fatulla Sub-Registry Office", "NRG", "NRG_RC_10"),
        SubRegistryOfficeOption("office_siddhirganj", "সাব-রেজিস্ট্রি অফিস, সিদ্ধিরগঞ্জ", "Siddhirganj Sub-Registry Office", "NRG", "NRG_RC_99"),
        SubRegistryOfficeOption("office_rupganj", "সাব-রেজিস্ট্রি অফিস, রূপগঞ্জ", "Rupganj Sub-Registry Office", "NRG", "NRG_UPZ_68"),
        SubRegistryOfficeOption("office_araihazar", "সাব-রেজিস্ট্রি অফিস, আড়াইহাজার", "Araihazar Sub-Registry Office", "NRG", "NRG_UPZ_02"),
        SubRegistryOfficeOption("office_sonargaon", "সাব-রেজিস্ট্রি অফিস, সোনারগাঁ", "Sonargaon Sub-Registry Office", "NRG", "NRG_UPZ_04"),
        SubRegistryOfficeOption("office_bandar", "সাব-রেজিস্ট্রি অফিস, বন্দর", "Bandar Sub-Registry Office", "NRG", "NRG_UPZ_06")
    )

    val districts: List<LocationDistrict> = listOf(
        LocationDistrict("NRG", "নারায়ণগঞ্জ", "Narayanganj")
    )

    private val defaultUpazilas: List<LocationUpazila> = listOf(
        LocationUpazila("NRG_UPZ_02", "NRG", "আড়াইহাজার", "Araihazar", isRevenueCircle = false, bbsCode = "02"),
        LocationUpazila("NRG_UPZ_04", "NRG", "সোনারগাঁ", "Sonargaon", isRevenueCircle = false, bbsCode = "04"),
        LocationUpazila("NRG_UPZ_06", "NRG", "বন্দর", "Bandar", isRevenueCircle = false, bbsCode = "06"),
        LocationUpazila("NRG_UPZ_58", "NRG", "নারায়ণগঞ্জ সদর", "Narayanganj Sadar", isRevenueCircle = false, bbsCode = "58"),
        LocationUpazila("NRG_UPZ_68", "NRG", "রূপগঞ্জ", "Rupganj", isRevenueCircle = false, bbsCode = "68"),
        LocationUpazila("NRG_RC_10", "NRG", "ফতুল্লা রাজস্ব সার্কেল", "Fatulla Revenue Circle", isRevenueCircle = true, bbsCode = "10"),
        LocationUpazila("NRG_RC_69", "NRG", "পুর্বাচল রাজস্ব সার্কেল", "Purbachar Revenue Circle", isRevenueCircle = true, bbsCode = "69"),
        LocationUpazila("NRG_RC_70", "NRG", "কাঁচপুর রাজস্ব সার্কেল", "Kachpur Revenue Circle", isRevenueCircle = true, bbsCode = "70"),
        LocationUpazila("NRG_RC_99", "NRG", "সিদ্ধিরগঞ্জ রাজস্ব সার্কেল", "Shiddirganj Revenue Circle", isRevenueCircle = true, bbsCode = "99")
    )

    @Volatile
    var upazilas: List<LocationUpazila> = defaultUpazilas
        private set

    @Volatile
    var mouzas: List<LocationMouza> = emptyList()
        private set

    @Volatile
    var narayanganjSurveys: List<LocationSurveyType> = emptyList()
        private set

    @Volatile
    private var narayanganjSourceNoteBn: String =
        "তথ্যসূত্র: e-porcha.com (যাচাই অপেক্ষমাণ • dlrms.land.gov.bd হতে চূড়ান্ত যাচাইযোগ্য)"

    @Volatile
    private var narayanganjSourceNoteEn: String =
        "Source: e-porcha.com reference (Pending official dlrms.land.gov.bd verification)"

    @Volatile
    private var isNarayanganjLoaded: Boolean = false

    private val loadLock = Any()

    fun ensureLoaded(context: Context) {
        if (isNarayanganjLoaded) return
        synchronized(loadLock) {
            if (isNarayanganjLoaded) return
            try {
                val jsonText = context.applicationContext.assets
                    .open(NARAYANGANJ_ASSET)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                loadFromJson(jsonText)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load $NARAYANGANJ_ASSET from assets: ${e.message}")
            }
        }
    }

    internal fun loadFromJson(jsonText: String) {
        synchronized(loadLock) {
            val root = JSONObject(jsonText)
            root.optJSONObject("district")?.let { distObj ->
                distObj.optString("sourceNoteBn").takeIf { it.isNotBlank() }?.let {
                    narayanganjSourceNoteBn = it
                }
                distObj.optString("sourceNoteEn").takeIf { it.isNotBlank() }?.let {
                    narayanganjSourceNoteEn = it
                }
            }

            val loadedSubdivisions = buildList {
                val arr = root.optJSONArray("subdivisions")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            LocationUpazila(
                                code = obj.getString("code"),
                                districtCode = obj.optString("districtCode", "NRG"),
                                bnName = obj.getString("bnName"),
                                enName = obj.getString("enName"),
                                isRevenueCircle = obj.optBoolean("isRevenueCircle", false),
                                bbsCode = obj.optString("bbsCode", "")
                            )
                        )
                    }
                }
            }

            val loadedSurveys = buildList {
                val arr = root.optJSONArray("surveys")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            LocationSurveyType(
                                id = obj.getString("id"),
                                codeEn = obj.getString("codeEn"),
                                nameBn = obj.getString("nameBn"),
                                order = obj.optInt("order", 99)
                            )
                        )
                    }
                }
            }

            val loadedMouzas = buildList {
                val arr = root.optJSONArray("mouzas")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val tagsArray = obj.optJSONArray("surveyTags")
                        val tags = buildList {
                            if (tagsArray != null) {
                                for (t in 0 until tagsArray.length()) {
                                    add(tagsArray.getString(t))
                                }
                            }
                        }
                        add(
                            LocationMouza(
                                code = obj.getString("code"),
                                upazilaCode = obj.getString("upazilaCode"),
                                unionBn = obj.optString("unionBn", ""),
                                unionEn = obj.optString("unionEn", ""),
                                mouzaBn = obj.getString("mouzaBn"),
                                mouzaEn = obj.optString("mouzaEn"),
                                jlNumber = obj.optString("jlNumber", ""),
                                districtCode = obj.optString("districtCode", "NRG"),
                                surveyTags = tags,
                                verificationStatus = obj.optString("verificationStatus", "unverified")
                            )
                        )
                    }
                }
            }

            if (loadedSubdivisions.isNotEmpty() && loadedMouzas.isNotEmpty()) {
                upazilas = loadedSubdivisions
                mouzas = loadedMouzas
                narayanganjSurveys = loadedSurveys
                isNarayanganjLoaded = true
            }
        }
    }

    fun normalizeLocationKey(raw: String): String {
        val base = SearchNormalizer.normalizeText(raw)
        if (base.isBlank()) return ""
        val canonical = Normalizer.normalize(base, Normalizer.Form.NFC)
            .replace("\u09AF\u09BC", "\u09DF") // য + nukta -> য়
            .replace("\u09A1\u09BC", "\u09DC") // ড + nukta -> ড়
            .replace("\u09A2\u09BC", "\u09DD") // ঢ + nukta -> ঢ়
        return when (canonical) {
            "নারায়নগঞ্জ", "নারায়ণগঞ্জ", "নারায়ণগঞ্জ" -> "নারায়ণগঞ্জ"
            "আড়াইহাজার", "আড়াইহাজার" -> "আড়াইহাজার"
            "পূর্বাচল", "পুর্বাচল", "পূর্বাচল রাজস্ব সার্কেল", "পুর্বাচল রাজস্ব সার্কেল" -> canonical.replace("পুর্বাচল", "পূর্বাচল")
            else -> canonical
        }
    }

    fun findDistrict(districtNameOrCode: String): LocationDistrict? {
        val q = normalizeLocationKey(districtNameOrCode)
        if (q.isBlank()) return null
        return districts.firstOrNull { d ->
            normalizeLocationKey(d.code) == q ||
                normalizeLocationKey(d.bnName) == q ||
                normalizeLocationKey(d.enName) == q
        }
    }

    fun findUpazila(upazilaNameOrCode: String): LocationUpazila? {
        val q = normalizeLocationKey(upazilaNameOrCode)
        if (q.isBlank()) return null
        return upazilas.firstOrNull { u ->
            normalizeLocationKey(u.code) == q ||
                normalizeLocationKey(u.bnName) == q ||
                normalizeLocationKey(u.enName) == q
        }
    }

    fun upazilasForDistrict(districtNameOrCode: String): List<LocationUpazila> {
        val district = findDistrict(districtNameOrCode) ?: return upazilas
        return upazilas.filter { it.districtCode == district.code }
    }

    fun mouzasForUpazila(upazilaNameOrCode: String): List<LocationMouza> {
        val upazila = findUpazila(upazilaNameOrCode) ?: return mouzas
        return mouzas.filter { it.upazilaCode == upazila.code }
    }

    fun mouzasForDistrict(districtNameOrCode: String): List<LocationMouza> {
        val district = findDistrict(districtNameOrCode) ?: return mouzas
        val validUpazilaCodes = upazilas.filter { it.districtCode == district.code }
            .map { it.code }
            .toSet()
        return mouzas.filter { it.districtCode == district.code || it.upazilaCode in validUpazilaCodes }
    }

    fun narayanganjSubdivisions(): List<LocationUpazila> = upazilas

    fun narayanganjMouzas(): List<LocationMouza> = mouzas

    fun narayanganjSourceNote(language: AppLanguage): String =
        if (language == AppLanguage.BN) narayanganjSourceNoteBn else narayanganjSourceNoteEn
}
