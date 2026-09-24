package com.smartexpense.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.smartexpense.data.model.bank.InstalledAppInfo

object InstalledAppsHelper {

    /**
     * 기기에 설치된 앱 중 알림 파싱 후보를 반환합니다.
     *
     * - [PackageManager.getInstalledApplications] + 런처 앱 조회를 병합
     * - 순수 시스템 앱(Play 스토어·삼성 유틸 등)은 제외, 사용자 설치 앱·금융 앱만 포함
     * - 금융 앱을 목록 상단에 배치
     */
    fun getLaunchableApps(
        context: Context,
        excludedPackages: Set<String> = emptySet()
    ): List<InstalledAppInfo> {
        val pm = context.packageManager
        val merged = linkedMapOf<String, ApplicationInfo>()

        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { appInfo ->
            merged.putIfAbsent(appInfo.packageName, appInfo)
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val launcherFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }
        pm.queryIntentActivities(launcherIntent, launcherFlags).forEach { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo
            merged.putIfAbsent(appInfo.packageName, appInfo)
        }

        return merged.values
            .asSequence()
            .filter { appInfo -> appInfo.packageName != context.packageName }
            .filter { appInfo -> appInfo.packageName !in excludedPackages }
            .map { appInfo ->
                val appName = appInfo.loadLabel(pm).toString().trim()
                appInfo to appName
            }
            .filter { (_, appName) -> appName.isNotBlank() }
            .filter { (appInfo, appName) -> shouldInclude(appInfo, appName) }
            .map { (appInfo, appName) ->
                InstalledAppInfo(
                    appName = appName,
                    packageName = appInfo.packageName,
                    isFinanceLikely = FinanceAppMatcher.isLikelyFinanceApp(appName, appInfo.packageName)
                )
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.isFinanceLikely }
                    .thenBy { it.appName.lowercase() }
            )
            .toList()
    }

    private fun shouldInclude(appInfo: ApplicationInfo, appName: String): Boolean {
        val isPureSystem = isPureSystemApp(appInfo)
        val isFinance = FinanceAppMatcher.isLikelyFinanceApp(appName, appInfo.packageName)
        if (isFinance) return true
        return !isPureSystem
    }

    private fun isPureSystemApp(appInfo: ApplicationInfo): Boolean {
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return isSystem && !isUpdatedSystem
    }
}
