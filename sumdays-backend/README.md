### 기존 DB를 유지하면서 서버 실행

`npm install` 후 `npm start`로 현재 코드의 서버를 실행합니다. 코드 갱신 후에는 실행 중인 Node 서버를 종료하고 다시 시작해야 새 API가 반영됩니다.

기존 DB에 상점·지갑·미션·여우구슬 테이블이 없다면 `npm run migrate:economy`로 추가할 테이블과 기본 데이터 목록을 먼저 확인합니다. 이 명령은 기본적으로 DB를 변경하지 않습니다. 대상 DB의 변경 승인을 받은 뒤 `npm run migrate:economy -- --apply --database=<설정된_DB_이름>`으로 한 번 적용합니다. 기존 데이터와 이미 설정된 상품 가격은 유지합니다. 서버 시작 시 이 마이그레이션을 자동 실행하지 않습니다.

로컬 MySQL을 사용할 때는 `.env.local.example`을 참고해 `.env.local`을 준비하고 `npm run start:local`을 실행합니다. `npm run db:init:local`은 로컬 DB를 삭제하고 다시 만드는 명령이므로 기존 데이터가 있는 DB의 오류 복구에 사용하지 않습니다.

스티커 구매 활성화 시 기존 상점 DB에는 `npm run migrate:sticker-products`로 확인한 뒤 `npm run migrate:sticker-products -- --apply --database=<설정된_DB_이름>`으로 6개 상품만 추가할 수 있습니다. 스티커는 `ITEM` 상품 ID 1001–1006을 사용하며, 구매와 일기 붙이기의 수량은 서버에서 관리합니다. 서버를 재시작하고 Android 앱도 갱신해야 합니다.

검증: `npm run test:stickers`, `npm run test:migrations`. 실제 DB 검증은 `node scripts/verify-sticker-shop.js --database=<DB_이름> --user=<기존_테스트_계정_ID>`로 실행하며, 테스트 거래는 하나의 트랜잭션 안에서 수행 후 모두 롤백합니다.

### 1. 서버 실행 방법

#### 1-0
gpt-4.1-nano 가 빠르고 싸서 좋네요.

#### 1-1
```$ npm install``` 을 실행하면 패키지 자동 설치됩니다   
```$ pip install -r requirements.txt``` 를 ai 폴더에서 실행하면 필요한 라이브러리가 설치됩니다.  

#### 1-2
.env.example 파일의 내용을 복사해서 .env 파일을 sumdays-backend폴더에 만들어주세요. Openai api key의 값을 바꿔서 사용해주시면 됩니다. (공지 문서 확인)
  
우선 openai api key는 Tutorial 5 실습용 을 사용하고, 안 되면 팀 api key로 바꾸어 사용하면 크레딧 아낄 수 있을 것 같네요.  

#### 1-3
```$ node server.js``` 로 중앙 nodejs 서버 실행합니다.(sumdays-backend 폴더에서, port 3000)  
```$ python app.py``` 로 python ai 서버 실행합니다.(sumdays-backend/ai폴더에서, port 5001)  

#### 1-4
postman을 사용해서 테스트. 주석을 확인하면 endpoint와 request 형식을 어떻게 해야할 지 확인할 수 있습니다. header에 content-Type application/json 추가해야 합니다.
  
핸드폰으로 테스트시 ```$ ipconfig```로 컴퓨터의 ip주소 확인 후 frontend에서 그 주소로 요청보내도록 수정해주세요. 핸드폰과 컴퓨터가 같은 와이파이(혹은 공유기 혹은 네트워크)이어야 합니다.
  
### 2. 테스트 db 환경(**일단 무시!!**)
```$ docker run --name my-mysql -e MYSQL_ROOT_PASSWORD=1234 -e MYSQL_DATABASE=diarydb -p 3306:3306 -d mysql:8``` 로 로컬에서 db 테스트  db 내용 확인은 아래와 같이 하면 됨.  
```docker exec -it my-mysql mysql -u root -p(1234 입력)SHOW DATABASES;USE diarydb;SHOW TABLES;```  

### 3. backend 구조
```
[Client]  
 ↓
[ Node.js 서버 ]  ←→  [ MySQL(DB) ]     
↓
[ Python 서버 (ai) ] 
```  
ai 관련 서버는 python의 flask를 사용해서 구현했습니다. Django대신 flask 사용한 이유는 제 생각에는 더 직관적이고, 메인 서버가 아니기 때문에 가벼운 기능만 있으면 되기 때문입니다.

#### 3-1. 파이썬 서버
프로젝트 구조는 api endpoint에 대응하게 만들었습니다. 
```
http://localhost:5001/{feature_group}/{feature_name}  
-> services/{feature_group}/ 폴더  
-> 폴더 내부 routes.py에서 feature_name에 대해 구분하여 함수 연결  
-> {feature}_service.py에서 실제 기능 코드
```  
구체적으로 어떻게 구현되었고, 앞으로 어떤 식으로 구현할 지는 analysis/diary 부분 구현해둔 코드 참고하면 좋을 것 같습니다. 
  
#### 3-2. nodejs 서버와 연결
```
코드의 흐름
http://localhost:3000/api/ai/{feature_group}/{feature_name}  

app.js 
-> routes/index.js 
-> routes/ai.js 
-> controllers/ai/{feature_group}Controller.js  
```
다음의 순서대로 코드 확인하시면 어떻게 작동하는지 알 수 있습니다. analyzeController.js  의 analyze 부분 확인하시면 두 서버를 어떻게 연결하는지 알 수 있습니다.

### 4. ai api(정리중)
https://docs.google.com/spreadsheets/d/1MJ_ZW7pfDGD6P6tcOB9NWApBRCmv3Z2C1eo9jW6cCjg/edit?usp=sharing

### 5. frontend 관련
#### 5-1. backend 구현현황
메모를 각각 합치기, 메모 한 번에 합치기, 완성된 일기 분석(이모지, 요약키워드, 피드백 등)을 구현했습니다. 사실상 frontend에서는 /api/ai/merge만 호출하면 됩니다.
```
Request json (memos에는 원본 메모 리스트를 보낸다.(중간상태메모XX))
{
    "memos": [
        {"id": 1, "content": "아침으로 빵을 먹었다.", "order": 1},
        {"id": 3, "content": "저녁을 가족과 먹었다.", "order": 2},
        {"id": 2, "content": "점심은 친구와 맛있게 먹었다.", "order": 3}
    ],
    "end_flag": true
}
```
1. if endflag = True
```
Response json
{
    "success": true,
    "result": {
        "ai_comment": "일상의 소소한 행복을 느끼며 가족과 친구와의 시간을 즐겼다.",
        "analysis": {
            "emotion_score": 0.6,
            "keywords": [
                "아침 빵",
                "가족과 저녁",
                "친구와 점심",
                "일상 기록"
            ]
        },
        "diary": "오늘의 하루를 기록한다. 아침으로 빵을 먹었다. 저녁은 가족과 함께 보냈다. 점심은 친구와 맛있게 먹었다.",
        "entry_date": null,
        "icon": "📖",
        "user_id": null
    }
}
```
2. endflag = false
```
{
    "success": true,
    "merged_content": {
        "merged_content": "오늘은 차분하게 흘러간 하루였다. 아침으로 빵을 먹으며 하루를 시작했고, 저녁은 가족과 함께 보내며 따뜻한 시간을 보냈다. 점심은 친구와 맛있게 먹었다. 이렇게 작은 순간들이 모여 오늘을 완성했다."
    }
}
```
#### 5-2. frontend에서 할 일
메모 합치는 부분에서 문제가 되는 부분은 
1. 메모가 만들어진 시간 순서
2. 사용자가 일기에서 입력되길 윈하는 순서(사용자가 원하는 기준에 따른 순서)
3. 사용자가 합치는 순서

이 세 가지가 다를 수 있다는 점입니다.  

저는 순서에 대한 문제를 프론트엔드에서 다루는 것이 좋다고 생각해서 백엔드에서는 **사용자가 입력되길 원하는 순서**를 기준으로 합쳐지도록 구현했습니다. 프론트엔드에서 다루는게 좋다고 생각한 이유는 db에 중간 상태의 메모(원본도 일기도 아닌 메모)를 위해 필드를 추가하거나 테이블을 새로 만드는 것이 비효율적으로 보였기 때문입니다.  
  
이 문제에 대한 구현 방향은 아래와 같이 크게 두 가지의 방법이 있을 것 같습니다. 우선 제가 이해한 저희 앱의 구현방식은 방법1이어서 방법1에 맞게 백엔드는 구현했습니다.(sum 버튼 눌러서 합치기 시작하는 방식이니까..)

**방법 1** : 일기를 합치는 행위가 일어나기 전에 순서를 사용자가 정해둔다. 합치는 순서는 생성되는 일기의 흐름에 영향을 미치지 않는다. (135메모와 246메모가 합쳐지면 123456이 된다)  

+합치는 순서가 자유롭다.  
+일기 중간에 새로운 사건이 삽입되는 재미?  
-합치기 전에 순서를 확정해야 한다.  

**방법 2** : 일기를 합치는 순서를 일기의 순서로 한다. (135메모와 246메모가 합쳐지면 135246이 된다)  

+실제로 일기를 쓰는 과정과 비슷한 흐름으로 합쳐나가게 됨.  
+추가적으로 ui_order를 따로 둘 필요 없다.  
+합치기 전에 순서를 미리 설정하지 않아도 된다.(일일이 합칠 경우)  
-메모가 중간에 삽입되어 만들어질 수 없다.  
-db에서 order를 다루기 까다롭다.(위젯3-5간의 전환이 자유로워야하므로 필수적)
  
방법 1로 어떻게 구현해야할지 대충 생각해봤을 때 아래와 같은 느낌으로 구현하면 될 것 같았습니다.
```
[base_memo] <---(drag)--- [dragged_memo]

===(result)===> [merged_memo]
```
```
# 합쳐진 중간상태 메모는 따로 표시
merged_memo.is_merged = True
merged_memo.order = Null

# ui_order은 양수인 메모들을 순서대로 보여준다
merged_memo.ui_order = base_memo.ui_order 
base_memo.ui_order = -base_memo.ui_order
dragged_memo.ui_order = -dragged_memo.ui_order

# 원본 메모들의 id를 리스트로 저장해둔다
merged_memo.merged_memo_id = base_memo.merged_memo_id + dragged_memo.merged_memo_id
```


### 6. ai 관련 TODO list
#### 6-1. 기본 기능  
- [x] 텍스트 메모 합치기
- [x] 일기에서 이모지, 감정 점수, 피드백, 요약 분석
- [ ] merge api: 일기 db 저장하도록 하기.
- [x] 일주일 일기로 주간 요약/분석
- [x] 한 달 일기로 월간 요약/분석
- [ ] prompt 다듬기

#### 6-2. db 확장 후 할 일
- [x] 사진-> text (ocr) memo 
- [ ] 일기 입력 관련이므로 날짜 추출까지.
- [x] 음성-> text (stt)
- [ ] 사진에서 데이터 얻기(e.g. 강변에서 산책하는 사진이다)
- [ ] 추측 강도에 따른 merge로 확장
- [ ] 일기 스타일 추출
- [ ] 일기 스타일에 따른 일기 생성으로 확장
- [ ] 사진, 음성 메모에 대한 일기 생성으로 확장
