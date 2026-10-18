# Mehburity

<img src="src/main/resources/assets/mehburity/icon.png" alt="Mehburity" width="96" align="right">

**Minecraft 26.2 / Fabric** için bir yapay zeka yoldaşı. Mehburity, yanında yerde yuvarlanan neon cyan, gülen yüzlü bir top.
Gemini ile seninle konuşur, sesli cevap verir, seni ışınıyla korur, maden kazar ve ev yapar. Kızdırılırsa 18 blokluk bir
iblise dönüşüp suçluyu avlar. ThatMob'un **Verity**'sinden esinlenmiştir: bir kutudan çıkar, dünya zifiri karanlıktır,
elinde bir el feneri vardır ve karanlıkta… bazen yalnız değilsindir.

> **Durum:** Tüm fazlar (0–9) tamamlandı. Son eklenen: bas-konuş ile sesli konuşma.

## İçindekiler

1. [Hızlı başlangıç](#hızlı-başlangıç)
2. [Mehburity ile konuşmak](#mehburity-ile-konuşmak)
   - [Sesli konuşma (bas-konuş)](#sesli-konuşma-bas-konuş)
3. [Sohbet emirleri](#sohbet-emirleri)
4. [Özellikler](#özellikler)
   - [Yoldaş: takip, ölümsüzlük](#yoldaş-takip-ölümsüzlük)
   - [Savaş](#savaş)
   - [Madencilik](#madencilik)
   - [Ev yapma](#ev-yapma)
   - [Öfke](#öfke)
   - [Canavar formu](#canavar-formu)
   - [Verity modülü](#verity-modülü): kutu, karanlık, el feneri, korku olayları, yüz ifadeleri
5. [Komutlar](#komutlar)
6. [Gemini kurulumu](#gemini-kurulumu)
7. [Sunucu sahipleri için](#sunucu-sahipleri-için)
8. [Ayarlar (`config/mehburity.json`)](#ayarlar)
9. [Sorun giderme](#sorun-giderme)
10. [Doku ve sesleri değiştirmek](#doku-ve-sesleri-değiştirmek)
11. [Geliştirme](#geliştirme)
12. [Sürümler ve notlar](#sürümler-ve-notlar)

---

## Hızlı başlangıç

**Gerekenler:** Minecraft 26.2, Fabric Loader 0.19.5+, Fabric API, Java 25. Ayarlar ekranı için isteğe bağlı olarak
Mod Menu.

1. `mehburity-<sürüm>.jar` dosyasını `.minecraft/mods` klasörüne at (Fabric API de orada olmalı).
2. Dünyaya gir. Önünde bir **karton kutu** durur. **Sağ tıkla**: kutu sallanır, kapakları açılır ve Mehburity içinden
   dönerek yükselir: *"Hello! I'm Mehburity. Your personal helper friend. Ask me anything. I know everything."*
3. Mehburity sana bir **el feneri** verir. Sağ tıkla açarsın, çünkü geceler ve mağaralar zifiri karanlıktır.
4. Sohbete **`Mehburity, merhaba!`** yaz. Gemini anahtarı olmadan da basit cevaplar verir. Gerçek sohbet ve ses için
   [Gemini kurulumu](#gemini-kurulumu)'na bak (ücretsiz, 2 dakika).
5. İstersen ayarlardan bir **Konuşma tuşu** ata. Tuşu basılı tutup sesli konuşursun; bkz.
   [Sesli konuşma](#sesli-konuşma-bas-konuş).

Kutuyu kaybettiysen `/mehburity cagir` yeni bir kutu verir (hiç açmadıysan) ya da Mehburity'yi yanına getirir.

## Mehburity ile konuşmak

- Sohbete **"Mehburity, …"**, "mehbur …" ya da "@mehburity …" diye başlayan bir mesaj yaz. Mehburity Gemini ile cevap
  verir. Yakındaki oyuncular cevabı sohbette görür.
- **Ne bildiği:** konumun, boyut ve biyom, canın ve açlığın, gündüz/gece ve hava, yakındaki düşmanlar, çantasındakiler,
  inşaat durumu, öfkesi ve son olaylar (savaştıkları, kazdıkları, gördüğün iblis…). Konuşmayı hatırlar (son 10 mesaj).
- **Kendiliğinden yorum yapar:** saldırıya uğrayınca ya da ölünce (en fazla 90 saniyede bir).
- **Sesli okur** (Gemini TTS, varsayılan ses *Puck*): söylediği her cümleyi. Sesi yalnızca sahibi duyar. Sık söylenen
  kısa cümleler önbelleğe alınır.
- **Uzaklık hissi:** ses Mehburity'nin olduğu yerden gelir. 3 bloğa kadar tam ses, uzaklaştıkça kısılır ve 32 blokta
  duyulmaz olur (yazı yine sohbete düşer). Konuşurken uzaklaşırsan ya da yaklaşırsan ses anında değişir. Cebindeyken
  tam sesle konuşur. Anahtar yokken çalan kısa ses de aynı şekilde uzaktan gelir.
- **Gemini yoksa da çalışır:** anahtar, internet ya da kota yoksa veya cevap 30 sn'de gelmezse yerleşik Türkçe/İngilizce
  cevaplarla devam eder ve bağlantı sorununu söyler. Ses yerine kısa bir "cıvıltı" çalar.

### Sesli konuşma (bas-konuş)

Yazmak yerine Mehburity'ye sesli de konuşabilirsin.

1. **Tuşu ata.** Varsayılan olarak atanmamıştır. **Modlar → Mehburity → Yapılandır** (ya da `/mehburity ayarlar`) →
   **Konuşma tuşu** düğmesine bas, sonra istediğin tuşa ya da fare tuşuna bas. Esc tuşu kaldırır. Aynı tuş
   **Seçenekler → Kontroller → Tuş Atamaları → Mehburity** altında da görünür.
2. **🎙 Mikrofonu dene** düğmesi 2 saniye kaydeder ve ses seviyesini gösterir. Mikrofon bulunamazsa ya da çok sessizse
   söyler.
3. Oyunda **tuşu basılı tut ve konuş**. Ekranın altında *"🎙 Dinliyorum… (bırakınca gönderilir)"* yazar. Tuşu
   bırakınca kayıt Gemini'ye gider, yazıya çevrilir ve sohbete **"Mehburity, …"** olarak yazılır.

Sohbete yazılmış gibi işlendiği için her şey aynen çalışır: sorular, "maden kaz", "ev yap", "özür dilerim"… Adını
kendin söylersen ("Mehburity, ev yap") tekrar eklenmez. Kayıt başlayınca Mehburity o an konuşuyorsa susar, mikrofon
onu duymasın diye.

- Kayıt en fazla 15 saniye sürer, ayarlardan 3–30 saniye arası seçilebilir. Çok kısa basışlar (0,3 sn altı) yok sayılır.
- Gemini anahtarı gerekir; yazıya çevirme ayarlardaki sohbet modeliyle yapılır ve kotadan düşer.
- Ses kaydı yalnızca senin bilgisayarından Google'a gider; sunucuya ses gitmez, sadece çıkan metin sohbete yazılır.
- Gemini konuşmayı anlayamazsa *"Anlayamadım. Bir daha söyler misin?"* der, sohbete hiçbir şey yazılmaz.

## Sohbet emirleri

Emirler Gemini'ye gitmeden anlaşılır, büyük/küçük harf ve Türkçe karakter fark etmez. Hepsi "Mehburity, …" ile başlar.

| Söyle | Ne olur | Komut karşılığı |
|---|---|---|
| maden kaz / go mining | Etraftaki açıkta görünen cevherleri kazar | `/mehburity kaz` |
| bunu kaz / mine this block | Baktığın bloğu kazar | `/mehburity kaz bunu` |
| bu ağacı kes / bu ağacı kaz / odun topla / chop this tree | Baktığın (yoksa en yakın) ağacı baştan sona keser | `/mehburity kaz agac` |
| kazmayı bırak / stop mining | Kazmayı bırakır | `/mehburity kaz dur` |
| kazdığın madenleri ver / give me what you mined | Çantasındakileri verir | `/mehburity ver` |
| ne kazdın? | Çantada ne olduğunu söyler | `/mehburity canta` |
| ev yap / build me a house | Önüne ahşap bir ev kurar | `/mehburity ev` |
| taş ev yap, tuğla ev yap, ahşap ev yap | Seçilen stilde ev | `/mehburity ev tas` … |
| elimdeki bloktan ev yap | Duvarları elindeki bloktan | `/mehburity ev elimdeki` |
| iki katlı, havuzlu, kırmızı çatılı beyaz bir ev yap | **Tarif ettiğin evi** tasarlayıp yapar | — |
| inşaatı durdur / stop building | Yarıda bırakır | `/mehburity ev dur` |
| evi geri al / remove the house | Evi kaldırır, araziyi eski haline getirir | `/mehburity ev geri` |
| özür dilerim, affet, pardon, sorry | Öfkesini azaltır (−25) | – |
| aptal, salak, I hate you… | Kızdırır (+15) | – |

## Özellikler

### Yoldaş: takip, ölümsüzlük

- Yerde yuvarlanarak seni takip eder (bir bloğu yuvarlanıp çıkar, suda yüzer); durunca yüzü dik konuma oturur ve baktığı
  yöne döner. 20 bloktan uzak kalırsan ya da başka boyuta geçersen
  yanına ışınlanır. `/mehburity otur` ile bulunduğu yerde bekler, `/mehburity takip` ile yeniden peşine düşer.
- **Konuşurken ağzı oynar:** her cümlede (sesli okurken de) ağzı açılıp kapanır ve boştaysa sana döner. Yakındaki
  herkes konuştuğunu görür; sinirliyken ve öfkeliyken de o yüzün ağzı oynar.
- **Ölümsüzdür:** hasar almaz, `/kill` ile ölmez, boşluğa düşerse sana döner. Ama vurulduğunu fark eder (bkz. [Öfke](#öfke)).
- **Oyuncu başına bir Mehburity.** Sahiplik dünya kaydında tutulur. Oyundan çıkınca seninle "gider", girince yanında
  belirir. "Otur" modundaysa bıraktığın yerde bekler.
- **Eline alabilirsin:** Mehburity'ye **sağ tıkla**, envanterine girer ("Vuhuu!"). Elindeyken **sağ tıklarsan**
  baktığın yere (havaya tıklarsan yanına) çıkar ve takip etmeye devam eder.
  - Envanterdeyken de sohbette cevap verir; öfkesi aynen saklanır.
  - Sadece sahibi alabilir ve çıkarabilir. Başkasının eline geçse bile onu yalnızca sahibi serbest bırakabilir.
  - Öfke nöbetindeyken, canavar formundayken ya da kutudan yeni çıkarken eline alınamaz ("Şu an bana dokunma!").
  - Pasta tutarken sağ tıklamak onu almaz, öfkesini yatıştırır.
  - Eşyayı kaybedersen `/mehburity cagir` onu geri getirir; eski eşya ise kullanılınca boşa gider.

### Savaş

Mehburity neon cyan bir **ışınla** savaşır: kısa bir şarj (ince ışın, ses), sonra parlak bir atış. Hedef seçerken
önceliği şöyledir:

1. sana **vuran** her şey,
2. senin **vurduğun** canavar,
3. seni **avlayan** düşmanlar (sana saldırmadan önce vurur).

Menzil dışındaki hedefe yaklaşır, duvar arkasındakine ateş etmez, senden 20 bloktan uzaklaşan hedefi bırakır. Otur
modunda **nöbet tutar**: menzilindeki her düşmana ateş eder. Seni, evcil hayvanlarını, köylüleri ve başka Mehburity'leri
**asla** vurmaz. Oyunculara ancak sunucu izin verirse (`combat.fightPlayers`) saldırır. Öldürdükleri senin öldürmen
sayılır (XP ve loot düşer). `/mehburity savas kapat` ile kendi Mehburity'ni savaştan çekebilirsin.

### Madencilik

"Mehburity, maden kaz" dediğinde etrafındaki (12 blok) **açıkta görünen** cevherleri bulur, yanlarına yuvarlanır ve alet
gerekmeden kendisi kırar. Damar bitene kadar devam eder. Taşa gömülü cevherleri görmez, x-ray yapmaz. Kazdıkları
**Mehburity'nin çantasına** gider (27 yığın). Çanta sana bağlıdır; oyundan çıkınca ya da ölünce kaybolmaz.
"Kazdığın madenleri ver" deyince her şey envanterine geçer, sığmayanlar ayağına düşer. Kırdığı bloklar elmas kazmayla
kırılmış gibi eşya düşürür (XP dahil). Spawn korumasına, macera moduna ve koruma modlarına uyar. Saldırı olursa önce
savaşır, sonra kazmaya devam eder.

**Ağaç kesme:** "Mehburity, bu ağacı kes" (ya da "kaz", "odun topla") dediğinde baktığın ağacı (kütüğüne ya da
yaprağına bakman yeter; bakmıyorsan en yakın ağacı) gövdesinden yukarı doğru, bütün kütükleriyle keser ve kütükleri
çantasına koyar. Yapraklar normal ağaçlardaki gibi kendiliğinden döküler. Etrafında doğal yaprak olmayan kütükleri
(ör. kütükten yapılmış evleri) ağaç saymaz ve kesmez. "Ağacı kesmeyi bırak" durdurur, "kestiğin odunları ver" verir.

### Ev yapma

"Mehburity, ev yap" dediğinde önüne, kapısı sana bakan küçük bir ev kurar. Yaratıcı modda olduğu gibi **malzeme
harcamaz**. Ev 7×7'lik bir gövde, köşe direkleri, camlar, kapı ve basamaklı çatıdan oluşur; gerekirse altına temel atar.
İçinde yatak, sandık, çalışma masası, fırın ve meşaleler vardır. Blokları tek tek, sesleriyle yerleştirir.

- **Stiller:** ahşap (varsayılan), taş, tuğla, ya da **elindeki blok**. Elindeki blok kırılmaz, operatör bloğu ya da
  tam olmayan bir blok (meşale, basamak…) olamaz.
- **"Evi geri al"** son evi kaldırır ve araziyi eski haline getirir (sunucu kapanana kadar).
- Kırılmaz bloklara, eşya tutan bloklara ve korunan alanlara dokunmaz. Önü doluysa sağa, arkaya ya da sola kurar.

**Tarif ettiğin her ev:** Evi kelimelerle anlat, Mehburity ona göre tasarlayıp yapsın. Örnekler:
"Mehburity, iki katlı, havuzlu, bahçeli, kırmızı çatılı beyaz bir ev yap", "kuleli ve bacalı büyük bir ortaçağ taş evi
yap", "modern bir villa yap", "9x13 boyutunda 3 katlı, 4 yataklı, sivri çatılı ladin ev yap", "build me a japanese house
with a garden".

- **Anlaşılanlar:** boyut (küçük / büyük / kocaman ya da `9x13`), kat sayısı (1–4, katlar arası merdiven), tavan
  yüksekliği, çatı (sivri/üçgen, düz, piramit), malzeme (ahşap, meşe, huş, ladin, koyu meşe, akasya, kiraz, orman,
  mangrov, bambu, taş, kırık taş, tuğla, kumtaşı, kuvars/mermer, beton, cam, derin taş, karataş, nether, prizmarin,
  kerpiç, buz, kar, altın, elmas, zümrüt, obsidyen, ametist, bakır), renkler ("beyaz ev", "kırmızı çatılı"), tarzlar
  (modern, ortaçağ/kale, Japon, kış, villa, malikane, kulübe) ve ekler: **havuz, bahçe** (çit, kapı, yol, çiçekler,
  fenerler), **balkon, veranda, baca** (tüten), **kule**, kütüphane, mutfak, büyük pencereler, yatak sayısı.
- **Gemini anahtarın varsa** tarif Gemini'ye de sorulur; o da aynı alanları seçer ("deniz kenarında romantik bir
  ev" gibi serbest tarifler de anlaşılır). Gemini'nin seçtiği bloklar denetlenir: düşen (kum, çakıl), operatör, kırılmaz
  bloklar ve kırmızı taş isteyen demir kapı kabul edilmez. Anahtar yoksa ya da 25 saniyede cevap gelmezse kelimelerden
  çıkan tasarım yapılır.
- Sadece "ev yap" (ya da "taş ev yap") demek eskisi gibi klasik 7×7 evi hemen yapar. Tarifli evler de "evi geri al" ile
  kaldırılır; en fazla 21×21, 4 kat. Yüksek çatılara ve kulelere aşağıdan, yerde yuvarlanırken blok koyar.

### Öfke

Mehburity'nin 0–100 arası bir **öfke göstergesi** var.

| Ne kızdırır | Öfke |
|---|---|
| Bir oyuncunun ona vurması (sahibi dahil) | +25 |
| Bir canavarın vurması (oklar atanın sayılır) | +5 |
| Sohbette hakaret | +15 |
| Sahibini öldürmek | +100 (anında nöbet) |

**33**'te "Hey, yapma şunu!" der, yüzü sinirli bir ifadeye döner. **66**'da "Beni gerçekten kızdırıyorsun…" der; yüzü
kızgınlaşır, üstünde kızgın partiküller çıkar ve ara ara kırmızı yanıp söner. **100**'de **öfke nöbetine** girer:
kükrer, işini bırakır, [canavar formuna](#canavar-formu) geçer ve **yalnızca suçluyu** kovalar.

**İntikam:** Onu nöbete sokan bir **oyuncuysa** hemen saldırmaz. Tek kelime etmeden duman içinde **kaybolur** ve
**3 Minecraft günü** ortalıkta görünmez: çağrılamaz (`/mehburity cagir` "hiçbir yerde yok… geri dönecek" der), sohbete
cevap vermez. **4. gün**, günün rastgele bir anında, suçlunun **arkasında birden belirir**: ekran kararır, gök gürler
ve kalın, boğuk bir sesle **"İntikamımı almaya geldim."** der, sonra canavara dönüşüp onu kovalar. Suçlu (ya da
sahibi) o sırada oyunda değilse, ikisi de oyundayken gelir. Uyuyarak ya da `/time add` ile geçen günler de sayılır;
dünya kaydında tutulur, sunucu kapanınca unutulmaz. Yaratıklara (ör. sahibini öldüren zombi) hemen saldırır.
`anger.revengeDays` ile gün sayısı değiştirilebilir (0: eskisi gibi hemen saldırır).

Nöbet dört şekilde biter: suçlu ölünce, pasta veya özür göstergeyi 40'ın altına indirince, suçlu kaçınca (oyundan çıkar,
başka boyuta geçer) ya da 90 saniye dolunca. Sonra Mehburity sahibinin yanına döner.

**Barışma:** Bir oyuncuya kızdıysa (ya da ona nöbet geçirdiyse), her sakinleştiğinde ondan özür diler ve ona bir
**pasta** verir (envanteri doluysa ayağına düşer). Pasta ya da özürle sakinleştirirsen hemen, kendi kendine soğursa
biraz sonra. Sunucu isterse `anger.makeUpCakeCooldownSeconds` ile pastayı seyrekleştirebilir.

**Sakinleştirmek için:**

- **Pasta ile sağ tık:** −60 (bir pasta harcanır).
- **"Mehburity, özür dilerim"** (affet, pardon, sorry): −25, oyuncu başına 30 saniyede bir sayılır. Kendi Mehburity'si
  olmayan biri de söyleyebilir; ona en yakın Mehburity (öncelikle ona kızgın olan) duyar.
- **Zamanla:** kimse dokunmazsa dakikada −10.

Öfke dünya kaydında saklanır, oyundan çıkıp girmek sıfırlamaz. Gemini öfkesini bilir ve ona göre konuşur.

### Canavar formu

Öfke nöbetinde Mehburity **18 blok boyunda, iskelet gibi sıska, neon cyan bir iblise** dönüşür (Verity JE'nin
canavarından esinlenmiştir; model ve dokular bu mod için sıfırdan yapılmıştır). Önce sırıtan küre olarak yere iner,
titrer ve dumanlar içinde 2 saniyede büyür. Bu iki saniye, suçlunun kaçmak için kazandığı süredir.

İblisin görünüşü:

- **Gövde:** ince, uca doğru incelen uzuvlar ve eklemleri; derin bir kamburluk, kaburgaları görünen dar bir göğüs,
  ince bir bel ve sırtından çıkan omur kabartıları.
- **Kollar ve bacaklar:** dizlerin altına inen çok uzun kollar, ellerinde dörder uzun, kemiksi parmak; uzun parmaklı
  ayaklar.
- **Kafa:** yumurta biçimli cyan bir kafa, **içi boş kapkara gözler** ve **çarpık, dişli, geniş bir sırıtış**. Başını
  yana yatık tutar, arada bir aniden seğirir; konuşurken ağzı açılır.
- **Renk:** neon cyan, parmak uçları koyu turkuaz. Karanlıkta parlar.

- **Yürüyerek** kovalar: saniyede 6,5 blok, yani koşan bir oyuncudan hızlıdır. Blokların **içinden geçer**, hiçbir
  şeyi kırmaz. Ayakları yüzeydedir; suçlu yerin altına saklanırsa onun hizasına iner.
- 5 blok yakına gelince **pençesiyle** vurur: 24 hasar (zırh azaltır) ve savurma. Kuleye çıkmak kurtarmaz.
- Ölüm mesajı: *"… Mehburity tarafından parçalandı"*.
- Nöbet bitince dumanlar içinde küçülüp küreye döner.
- `monster.enabled: false` yapılırsa öfke nöbeti kırmızı bir küre olarak kalır; daha hızlı yuvarlanır ve daha güçlü bir
  ışınla vurur.

### Verity modülü

Verity'den uyarlanan, her biri ayardan kapatılabilen özellikler (`verity.*`):

**Kutu.** Her oyuncu ilk girişinde bir kutu alır. Kutu **sağ tıkla** açılır, yalnızca sahibi açabilir. Kutuya vurmak
(sol tık) bir şey yapmaz, sadece nasıl açılacağını hatırlatır.

**Verity karanlığı (her zaman açık).** Parlaklık ayarı işe yaramaz, ay ışığı ve ortam ışığı söner; gece ve mağaralar
ışık kaynağı olmadan zifiri karanlıktır. Meşaleler normal çalışır. Gücü `verity.darkness.strength` ile ayarlanır.

**El feneri** (`mehburity:flashlight`)

- **Sağ tıkla** açılır ve kapanır. Ana elde ya da sol elde tutarken **baktığın yeri aydınlatır**: ışık 24 blok uzağa
  kadar düşer, uzun ışınların ortası da aydınlanır. Işık bakışınla birlikte hareket eder; elinden bırakınca, kapatınca
  ya da oyundan çıkınca hiçbir iz kalmaz.
- **Pil:** dayanıklılık çubuğu pildir; tam pil 20 dakika yeter. Pil bitince fener kendiliğinden söner. **Örste
  kızıltaşla** doldurulur (her kızıltaş %25). Yaratıcı modda pil bitmez.
- **Tarif:** üstte cam panel, ortada demir külçe, altta kızıltaş (tek sütun).
- Mehburity kutudan çıkınca bir tane hediye eder. Yaratıcı modda *Araçlar* sekmesinde bulunur.

**Korku olayları.** Mehburity'si olan her oyuncunun başına, **karanlıktayken**, ortalama 6–15 dakikada bir şunlardan
biri gelir:

| Olay | Ne olur |
|---|---|
| **Sesler** | Arkandan yaklaşan ayak sesleri, karanlıkta bir gürleme ya da kırılan bir dal. Bu sesleri yalnızca sen duyarsın. |
| **Fısıltı** | Mehburity korkar (yüzü değişir, sana döner) ve tedirgin bir şey söyler: *"Bunu duydun mu?"*, *"Bir şey bizi izliyor."*… |
| **İzleyen** (nadir) | 36–52 blok uzakta, ufukta 18 blokluk bir iblis durup seni izler. Kafası koyu, yüzünde Mehburity'nin beyaz, parlayan gülümsemesi var. Ona doğrudan bakınca ya da yaklaşınca kaybolur. Mehburity korkar: *"…Sen de gördün, değil mi?"* |

Bu olaylar Gemini bağlamına da eklenir, yani ona ne gördüğünü sorabilirsin. OP'ler `/mehburity olay ses|fisilti|izleyen`
ile istedikleri olayı hemen başlatabilir.

**Yüz ifadeleri.** Mehburity'nin yüzü duruma göre değişir: **mutlu** (normal), **sinirli** (düz ağız, öfke 33+),
**kızgın** (öfke 66+), **öfke nöbeti** (kırmızı) ve **korkmuş** (kocaman gözler, korku olaylarında).

## Komutlar

Alt komutlar ASCII yazılır (komut ayrıştırıcısı "ç, ğ" gibi harfleri kabul etmez).

| Komut | Yetki | Ne yapar |
|---|---|---|
| `/mehburity` | herkes | Sürüm ve durum |
| `/mehburity cagir` | herkes | Mehburity'yi yanına getirir; hiç kutu açmadıysan yeni kutu verir |
| `/mehburity otur` / `takip` | herkes | Olduğu yerde bekler / seni takip eder |
| `/mehburity savas` | herkes | Savaş durumunu söyler |
| `/mehburity savas ac` / `kapat` | herkes | Mehburity seni korusun / savaşmasın |
| `/mehburity kaz` / `kaz bunu` / `kaz agac` / `kaz dur` | herkes | Cevher kazar / baktığın bloğu kazar / ağacı keser / durur |
| `/mehburity ver` / `canta` | herkes | Kazdıklarını verir / çantayı söyler |
| `/mehburity ev [ahsap\|tas\|tugla\|elimdeki]` | herkes | Ev yapar |
| `/mehburity ev dur` / `ev geri` | herkes | İnşaatı durdurur / son evi kaldırır |
| `/mehburity ofke` | herkes | Ne kadar kızgın olduğunu söyler |
| `/mehburity ayarlar` | herkes | Ayarlar ekranını açar (Gemini anahtarı, ses) |
| `/mehburity sakin` | OP | Yüklü tüm Mehburity'leri sakinleştirir, öfke nöbetlerini bitirir, bekleyen intikamları iptal eder |
| `/mehburity intikam` | OP | Kaybolmuş Mehburity'leri intikam için hemen geri getirir (denemek için) |
| `/mehburity olay ses\|fisilti\|izleyen` | OP | Bir korku olayını hemen başlatır |
| `/mehburity yenile` | OP | `config/mehburity.json`'u yeniden yükler ve oyunculara gönderir |

## Gemini kurulumu

1. https://aistudio.google.com/apikey adresinden ücretsiz bir Gemini API anahtarı al.
2. Oyunda **Modlar → Mehburity → Yapılandır** (Mod Menu) ya da sohbete `/mehburity ayarlar` yaz.
3. Anahtarı yapıştır, **Bağlantıyı test et**'e bas, **Bitti** ile kaydet.

Ayarlar ekranında şunlar da var:

- model: `gemini-3.5-flash-lite` hızlı ve ucuz, `gemini-3.8-flash` daha akıllı,
- sesli konuşma açık/kapalı, 30 ses arasından seçim, TTS modeli ve ses yüksekliği,
- **▶ Dinle**: seçili sesi dener,
- bas-konuş için **Konuşma tuşu**, **🎙 Mikrofonu dene** ve **En uzun kayıt**.

**Anahtar nerede durur?** Yalnızca kendi bilgisayarında, `config/mehburity-client.json` içinde; sunucuya hiç gönderilmez.
Sunucu soruyu senin istemcine yollar, istemci Gemini'ye sorar ve cevabı geri gönderir. Bu sayede sunucularda da çalışır
ve herkes kendi anahtarını kullanır. Konuşma ve ses Google'ın API kotasından düşer; ücretsiz katmanın günlük sınırları
vardır.

## Sunucu sahipleri için

- Mod **hem sunucuda hem istemcide** kurulu olmalı (yeni varlıklar, eşya ve ağ paketleri var).
- Sunucunun Gemini anahtarına ihtiyacı yoktur; her oyuncu kendi anahtarını kullanır.
- Ayarlar `config/mehburity.json` içindedir. Değiştirdikten sonra `/mehburity yenile` yeterli, yeniden başlatmak gerekmez.
- Oyunculara karşı davranışı ayarlardan sınırlayabilirsin: `combat.fightPlayers` (varsayılan kapalı), öfke nöbetinin
  oyunculara (`anger.rageAtPlayers`) ya da sahibine (`anger.rageAtOwner`) yönelmesi, canavar formu (`monster.enabled`).
- Korku olaylarını `verity.events.enabled: false` ile, karanlığı `verity.darkness.enabled: false` ile kapatabilirsin.
- El feneri ışığı için görünmez `minecraft:light` blokları kullanılır; bunlar fener kapanınca ya da sunucu düzgün
  kapanınca kaldırılır. Bkz. [Sorun giderme](#sorun-giderme).

## Ayarlar

Tüm değerler `config/mehburity.json` içindedir. Dosya ilk açılışta oluşturulur. Aralık dışı değerler otomatik düzeltilir.
Bozuk bir dosya silinmez: `mehburity.json.broken` adıyla saklanır ve varsayılanlar kullanılır.

<details>
<summary><b>companion</b>: takip</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `companion.followStartDistance` | 4.0 | Bu mesafeden uzaksan peşinden yuvarlanır |
| `companion.followStopDistance` | 2.5 | Bu kadar yaklaşınca durur |
| `companion.teleportDistance` | 20.0 | Bundan uzaksan yanına ışınlanır |
| `companion.followSpeed` | 2.0 | Takip hızı çarpanı |
</details>

<details>
<summary><b>combat</b>: savaş</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `combat.enabled` | true | Sunucu genelinde savaş açık mı |
| `combat.damage` | 6.0 | Bir ışın atışının hasarı (zombinin 20 canı var) |
| `combat.beamRange` | 12.0 | Işın menzili; hedef daha uzaksa yaklaşır |
| `combat.chargeTicks` / `cooldownTicks` | 12 / 16 | Şarj süresi / iki atış arası (tick, 20 = 1 sn) |
| `combat.defendOwner` | true | Sana vurana saldırır |
| `combat.helpOwnerAttack` | true | Senin vurduğuna saldırır |
| `combat.guardAgainstHostiles` | true | Seni hedef alan düşmanlara saldırır |
| `combat.attackAllHostiles` | false | Yakındaki **tüm** düşmanlara saldırır |
| `combat.guardRadius` | 16.0 | Bu mesafedeki düşmanları fark eder |
| `combat.chaseRadius` | 20.0 | Senden bu kadar uzaklaşan hedefi bırakır |
| `combat.fightPlayers` | false | Oyunculara da saldırsın mı |
| `combat.ownerGetsDrops` | true | Öldürdükleri senin öldürmen sayılır (XP ve loot) |
</details>

<details>
<summary><b>mining</b>: madencilik</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `mining.enabled` | true | Sunucu genelinde madencilik açık mı |
| `mining.targets` | ["#c:ores"] | Kazılacak bloklar: `#etiket` ya da blok kimliği |
| `mining.searchRadius` | 12 | Arama yarıçapı (blok) |
| `mining.exposedOnly` | true | Yalnızca açık bir yüzü olan cevherler |
| `mining.reach` | 4.0 | Bloğu bu uzaklıktan kırar |
| `mining.ticksPerHardness` | 8.0 | Sertlik başına kırma süresi (tick) |
| `mining.minBreakTicks` / `maxBreakTicks` | 6 / 100 | Kırma süresinin alt/üst sınırı (tick) |
| `mining.tool` | "minecraft:diamond_pickaxe" | Düşen eşyalar bu aletle kazılmış gibi hesaplanır |
| `mining.bagSlots` | 27 | Çanta boyutu |
| `mining.maxBlocksPerTrip` | 64 | Bir seferde en fazla kaç blok |
| `mining.pointRange` | 24.0 | "Bunu kaz" için en uzak mesafe |
</details>

<details>
<summary><b>build</b>: ev yapma</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `build.enabled` | true | Sunucu genelinde ev yapma açık mı |
| `build.defaultStyle` | "ahsap" | `ahsap`, `tas`, `tugla` |
| `build.allowHeldBlock` | true | "Elimdeki bloktan ev yap"a izin |
| `build.blocksPerSecond` | 15.0 | İnşaat hızı |
| `build.maxFoundationDepth` | 6 | Zemin altındaki boşlukları bu derinliğe kadar doldurur |
| `build.furnish` | true | Yatak, sandık, çalışma masası, fırın |
| `build.reach` | 8.0 | Blokları bu uzaklıktan yerleştirir |
</details>

<details>
<summary><b>anger</b>: öfke</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `anger.enabled` | true | Sunucu genelinde öfke açık mı |
| `anger.playerHitAnger` / `mobHitAnger` | 25 / 5 | Oyuncu / canavar vurunca eklenen öfke |
| `anger.insultAnger` | 15 | Hakaret başına öfke |
| `anger.ownerKilledAnger` | 100 | Sahibi öldürülünce katile öfke |
| `anger.decayPerMinute` | 10 | Dakikada soğuma |
| `anger.rageThreshold` | 100 | Öfke nöbetinin başladığı seviye |
| `anger.calmBelow` | 40 | Pasta/özür göstergeyi buna indirince nöbet biter |
| `anger.cakeCalm` / `apologyCalm` | 60 / 25 | Pastanın / özrün düşürdüğü öfke |
| `anger.apologyCooldownSeconds` | 30 | Aynı oyuncunun özrü bu sürede bir sayılır |
| `anger.revengeDays` | 3 | Bir oyuncuya öfkelenince bu kadar gün kaybolur, sonraki gün intikama gelir (0: hemen saldırır) |
| `anger.makeUpCake` | true | Sakinleşince kızdığı oyuncudan özür diler ve ona pasta verir |
| `anger.makeUpCakeCooldownSeconds` | 0 | Oyuncu başına bu sürede en fazla bir barışma pastası (0: her seferinde) |
| `anger.rageDamage` / `rageCooldownTicks` / `rageSpeed` | 10 / 10 / 3.0 | Canavar formu kapalıyken nöbetteki ışın ve hız |
| `anger.maxRageSeconds` | 90 | Bu süreden sonra kovalamayı bırakır |
| `anger.rageAtPlayers` / `rageAtOwner` | true / true | Oyunculara / sahibine öfke nöbeti |
| `anger.insults` | ["aptal", "salak", …] | Hakaret sayılan kelimeler ("aptalsın" da sayılır) |
</details>

<details>
<summary><b>monster</b>: canavar formu</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `monster.enabled` | true | Öfke nöbetinde canavar formu |
| `monster.height` | 18.0 | İblisin boyu (4–32 blok) |
| `monster.transformTicks` | 40 | Dönüşüm süresi (tick) |
| `monster.blocksPerSecond` | 6.5 | Yürüme hızı |
| `monster.reach` | 5.0 | Pençe menzili |
| `monster.damage` | 24.0 | Pençe darbesinin hasarı |
| `monster.attackCooldownTicks` | 30 | İki darbe arası (tick) |
| `monster.knockback` | 1.2 | Savurma gücü |
</details>

<details>
<summary><b>ai</b>: konuşma</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `ai.enabled` | true | Sohbete cevap versin mi |
| `ai.chatPrefixes` | ["mehburity", "mehbur"] | Bu kelimelerle başlayan mesajlar Mehburity'ye sorulmuş sayılır |
| `ai.hearingRadius` | 32.0 | Cevabı sohbette görebilecek oyuncuların uzaklığı |
| `ai.requestTimeoutSeconds` | 30 | Bu sürede cevap gelmezse yerleşik cevap |
| `ai.cooldownSeconds` | 2.0 | Aynı oyuncunun iki sorusu arası |
| `ai.maxMessageLength` / `maxReplyLength` | 256 / 400 | Soru / cevap uzunluk sınırı |
| `ai.commentOnEvents` | true | Saldırı/ölüm gibi olaylarda kendiliğinden yorum |
| `ai.eventCommentCooldownSeconds` | 90 | İki olay yorumu arası |
| `ai.greeting` | "Hello! I'm Mehburity. …" | Kutudan çıkınca söylediği cümle |
| `ai.extraPersona` | "" | Kişiliğe ek not (ör. "Korsan gibi konuş.") |
</details>

<details>
<summary><b>verity</b>: Verity modülü</summary>

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `verity.box.enabled` | true | İlk girişte kutu |
| `verity.box.openTicks` | 40 | Kutu açılma animasyonu (tick) |
| `verity.darkness.enabled` | true | Verity karanlığı |
| `verity.darkness.strength` | 0.85 | 0 = normal ışık, 1 = ışıksız yerler tamamen siyah |
| `verity.flashlight.enabled` | true | El feneri ışık versin mi |
| `verity.flashlight.giveWithBox` | true | Mehburity kutudan çıkınca el feneri hediye etsin |
| `verity.flashlight.range` | 24 | Işının menzili (4–64 blok) |
| `verity.flashlight.lightLevel` | 15 | Işığın düştüğü yerin ışık seviyesi |
| `verity.flashlight.batteryMinutes` | 20.0 | Tam pilin süresi (0 = hiç bitmez) |
| `verity.events.enabled` | true | Korku olayları |
| `verity.events.minMinutes` / `maxMinutes` | 6 / 15 | İki olay arasındaki süre (rastgele, dakika) |
| `verity.events.onlyInDarkness` | true | Yalnızca karanlıktayken |
| `verity.events.sounds` / `whispers` / `watcher` | true | Hangi olaylar olabilir |
</details>

İstemcinin kendi ayarları `config/mehburity-client.json` içindedir ve ayarlar ekranından değiştirilir: Gemini anahtarı,
model, ses ve `pushToTalkMaxSeconds` (en uzun kayıt, 3–30 sn). Konuşma tuşu Minecraft'ın `options.txt` dosyasında
`key_key.mehburity.push_to_talk` olarak saklanır. **`mehburity-client.json` dosyasını kimseyle paylaşma.**

## Sorun giderme

| Sorun | Çözüm |
|---|---|
| Mehburity hep aynı basit cevapları veriyor | Gemini anahtarı girilmemiş ya da geçersiz: `/mehburity ayarlar` → **Bağlantıyı test et** |
| Ses gelmiyor | Ayarlarda "Sesli konuşma" açık mı? Ses yüksekliği? Ücretsiz kota dolmuş olabilir |
| Konuşma tuşu bir şey yapmıyor | Tuş atanmamış olabilir (varsayılan yok): ayarlar → **Konuşma tuşu** |
| "Mikrofon bulunamadı" | Windows → Ayarlar → Gizlilik → **Mikrofon**: masaüstü uygulamalarının (Java) erişimine izin ver; varsayılan kayıt aygıtını kontrol et. **🎙 Mikrofonu dene** ile sına |
| Söylediklerimi yanlış anlıyor | Net ve yakından konuş, adını başta söyle ("Mehburity, …"); oyun dilin konuştuğun dille aynı olsun |
| Mehburity kayboldu | `/mehburity cagir` |
| Mehburity bana kızgın | Pasta ile sağ tıkla ya da "Mehburity, özür dilerim" yaz; OP isen `/mehburity sakin` |
| Çok karanlık | El feneri ya da meşale kullan. Sunucu `verity.darkness.strength` değerini düşürebilir |
| El feneri yanmıyor | Pil bitmiş olabilir (dayanıklılık çubuğu): örste kızıltaşla doldur |
| Korku olayları istemiyorum | Sunucuda `verity.events.enabled: false`, sonra `/mehburity yenile` |
| Sunucu çöktükten sonra bir yerde açıklanamayan ışık var | El fenerinden kalmış görünmez ışık bloğu: `/fill <x1 y1 z1> <x2 y2 z2> air replace minecraft:light` |

## Doku ve sesleri değiştirmek

Bütün görseller bu projede sıfırdan üretilmiş yer tutuculardır. Kendi çizimlerini **aynı yol ve boyutla** koyman
yeterli (`src/main/resources/assets/mehburity/` altında):

| Dosya | Boyut | Ne |
|---|---|---|
| `textures/entity/mehburity/mehburity.png` | 256×128 | Mehburity küresi (eşdikdörtgen: ortası = yüz, üst kenar = tepe) |
| `textures/entity/mehburity/mehburity_annoyed.png` / `_mad.png` / `_scared.png` | 256×128 | Sinirli / kızgın / korkmuş yüz (aynı düzen); kızgın yüz canavarın kafası |
| `textures/entity/mehburity/mehburity_angry.png` | 256×128 | Canavar formu kapalıyken öfke nöbetindeki kırmızı küre |
| `textures/entity/mehburity/mehburity_monster.png` | 64×64 | Canavarın derisi: üst yarı gövde (her uzvun çevresine sarılır), alt yarı boynuz/pençe/diken |
| `textures/entity/mehburity/watcher.png` / `watcher_skin.png` | 256×128 / 64×64 | İzleyen iblisin kafası / derisi (canavarla aynı düzen) |
| `textures/item/mehburity.png` | 16×16 | Envanterdeki Mehburity |
| `textures/entity/mehburity/box.png` | 64×64 | Kutu (gövde 0,0; ön/arka kapaklar 0,24; yan kapaklar 0,32) |
| `textures/item/flashlight.png` / `flashlight_on.png` | 16×16 | El feneri (kapalı / açık) |
| `icon.png` | 128×128 | Mod ikonu: Mehburity'nin kafası |

Yer tutucuları yeniden üretmek için `python tools/gen_textures.py`. Logonun büyük bir kopyası için
`python tools/gen_textures.py --logo "Mehburity Logo.png" 512`.

**Sesler** şimdilik vanilla seslere yönlendirilmiştir (`assets/mehburity/sounds.json`). Kendi seslerin için `.ogg`
dosyalarını `assets/mehburity/sounds/` klasörüne koy ve `sounds.json`'da `"minecraft:…"` yerine `"mehburity:dosya_adi"`
yaz. Ses olayları şunlar:

- **Kutu ve konuşma:** `box.shake`, `box.open`, `mehburity.emerge`, `mehburity.speak` (anahtar yokken çalan cıvıltı).
- **Savaş ve öfke:** `mehburity.beam.charge`, `mehburity.beam.fire`, `mehburity.rage`.
- **Canavar formu:** `mehburity.monster.transform`, `mehburity.monster.step`, `mehburity.monster.swipe`,
  `mehburity.monster.shrink`.
- **Korku olayları:** `verity.footstep`, `verity.ambience`, `verity.snap`, `verity.watcher_vanish`.

Mehburity'nin konuşma sesi bir dosya değildir, Gemini TTS ile üretilir.

## Geliştirme

| Komut | Ne yapar |
|---|---|
| `gradlew build` | Derler ve sunucu oyun testlerini çalıştırır |
| `gradlew runClientGameTest` | Gerçek istemcide uçtan uca test; ekran görüntüleri `build/run/clientGameTest/screenshots` |
| `gradlew runClient` | Geliştirme istemcisini açar |
| `install.bat` | Derler, test eder, jar'ı MehburMC Launcher'daki **Mehburity Test** instance'ına kurar; eksik bağımlılıkları SHA-512 doğrulamalı indirir. Masaüstündeki **Mehburity Kur** kısayolu bunu çalıştırır. |

```
src/main/java/com/mehbur/mehburity/
  entity/   Mehburity, kutu, sahiplik kaydı (CompanionRegistry), CompanionManager, sesler, konuşma satırları
  ai/       GeminiClient (Interactions API), AiRouter, kişilik, oyun bağlamı ve olaylar, yerleşik cevaplar
  combat/   Hedef kuralları (CombatTargets), hedef seçimi, ışın saldırısı, hasar tipleri
  mining/   Kurallar, kazma hedefi (MineGoal), çanta (MiningBag), sohbet emirleri
  build/    Plan (HousePlan), stiller, inşaat hedefi (BuildGoal), geri alma (Building), sohbet emirleri
  anger/    Öfke göstergesi ve nöbet (Anger), suçluyu hedefleme, canavar formu (MonsterGoal), özür/hakaret (AngerChat)
  verity/   Verity modülü (VerityFeature): kutu, karanlık, el feneri (FlashlightItem/Feature),
            korku olayları (EventsFeature) ve izleyen iblis (WatcherEntity)
  network/  Sunucu ↔ istemci paketleri
  command/  /mehburity
  config/   Tek ayar dosyası
src/client/java/com/mehbur/mehburity/client/
  render/   Küre (+ ışın, yüz ifadeleri), canavar modeli, izleyen, kutu
  verity/   Karanlığın istemci tarafı (lightmap mixin'i)
  ai/       İstemcide Gemini çağrısı (ClientAi), ses (VoicePlayer), bas-konuş (VoiceInput, MicRecorder)
  screen/   Ayarlar ekranı + Mod Menu entegrasyonu
src/gametest/   Sunucu ve istemci oyun testleri (FakeGemini: testler için yerel sahte Gemini sunucusu)
tools/          gen_textures.py (doku ve logo üretici), scan_secrets.py
```

**Yeni bir Verity özelliği eklemek için:** `verity/VerityFeature`'ı uygula, `VerityFeatures.ALL`'a ekle, açma/kapama
ayarını `MehburityConfig.Verity` altına koy (çalışırken okunduğu için `/mehburity yenile` ile değişir).

**Güvenlik:** `git config core.hooksPath .githooks` ile her commit'ten önce `tools/scan_secrets.py` çalışır ve API
anahtarı gibi hassas bilgi içeren commit'leri engeller.

## Sürümler ve notlar

| Bileşen | Sürüm |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Fabric Loom | 1.18-SNAPSHOT |
| Mod Menu (isteğe bağlı) | 20.0.3 |
| Java | 25 |

Mehburity, ThatMob'un **Verity** karakterinden esinlenmiştir. Verity JE modunun hiçbir modeli, dokusu ya da sesi
kullanılmamıştır (o mod "All Rights Reserved"); tüm görseller bu projede sıfırdan üretilmiştir. Lisans: All Rights
Reserved © Mehbur07.
