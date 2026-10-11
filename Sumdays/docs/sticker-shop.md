# 스티커 상점

상점의 `테마 / 아이템 / 스티커` 중 스티커 탭에서 6종을 포인트로 구매한다.
한 번 구매할 때 1개가 추가되며, 같은 상품을 여러 번 구매할 수 있다.
가격은 100~200P이며 `StickerCatalog`에서 이름·설명·가격·이미지를 관리한다.

- `StickerShopItem`: 여우 꾸미기 아이템과 분리된 상품 타입. ID 1001~1006은 구매 재고 식별자로 유지한다.
- `StickerPrefs`: `AccountStorage`를 통해 로그인한 계정 또는 게스트의 `sticker_inventory`에 수량을 저장한다. 기존 여우 아이템 재고에는 추가하지 않는다.
- 구매는 기존 상점의 `PointPrefs`와 `InventoryOwner` 검사를 사용한다. 잔액을 다시 읽고 부족하면 수량과 포인트를 변경하지 않는다.
- 화면 재생성 후에도 스티커 탭을 유지하며, 상점에 다시 들어오면 포인트와 보유 수량을 갱신한다.

이번 구현에는 일기 위 배치, 스티커 사용·차감, 친구 일기의 공개 여부 검사 및 서버 동기화가 없다.
추후 내 일기와 친구의 공개 일기에 붙이기를 연결할 수 있도록 상품 ID와 이미지를 분리해 두었다.

## 검증

`Sumdays` 디렉터리에서 실행한다.

```powershell
.\gradlew.bat -I gradle/fox-placement-tests.init.gradle :app:assembleDebug :app:testDebugUnitTest --console=plain
.\gradlew.bat -I gradle/sticker-shop-device-tests.init.gradle :app:connectedDebugAndroidTest --console=plain
```

기기 검증은 별도 패키지 `com.example.first.inventorycheck`와 테스트 계정 303을 사용한다.
구매 전·후 및 스크롤 후 화면은 Gradle의 `connected_android_test_additional_output`에 수집된다.
별도 출력 경로 없이 테스트를 직접 실행하면 테스트 앱의 캐시 디렉터리에 저장한다.
기기 검증 뒤 기본 앱 APK가 필요하면 init 스크립트 없이 `:app:assembleDebug`를 실행한다.
