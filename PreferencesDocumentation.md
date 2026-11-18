### 2026.11.17 version

# 1. Pref 종류 
1. 사용자 정보 : UserStatsPrefs, StreakPrefs, ProfileImagePrefs
2. 사용자 설정 : LabsPrefs, ReminderPrefs, DiaryPermissionPrefs, dark_mode, checkFirst
3. 재화·인벤토리 : PointPrefs, ItemPrefs, OwnedPrefs, StickerInventoryStore
4. 꾸미기·연금술 : FoxPrefs, ThemePrefs, AlchemySelectionManager
5. 미션·보상 : MissionEventPrefs, MissionStatePrefs
6. 임시 작업·재시도 : PendingAdRewardStore, PendingMergeStore, PendingShopPurchaseStore, AlchemyRepository, MergeMarbleRepository
7. 세션·동기화 : SessionManager, AccountStorage, LocalDataOwnerPrefs

# 2. 기본 rule 
- 로그아웃 시 : ServerPref, LocalPref 둘 다 deafault 값으로 초기화
- 로그인 시  : LocalPref to deafult, ServerPref - 서버에서 가져와 초기화 


# 3. 세부 pref (테마별로)
# 3-1. 사용자 정보 : UserStatsPrefs, StreakPrefs, ProfileImagePrefs

# 3-2. 사용자 설정 : LabsPrefs, ReminderPrefs, DiaryPermissionPrefs, dark_mode, checkFirst
- 사용자 설정 항목에 있는 pref
- data를 server에 저장할 필요 없음 