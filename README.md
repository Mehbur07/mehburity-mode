# Mehburity

Minecraft **26.2 / Fabric** modu. Mehburity, oyuncunun yanında süzülen neon cyan, gülen yüzlü bir
yapay zeka yardımcısı: Gemini ile konuşur, seni korur, maden kazar, ev yapar ve sinirlenince
18 blokluk bir canavara dönüşür.

> Durum: **Faz 7 (canavar formu) tamamlandı.** Tam README Faz 8'de, sesli konuşma (bas-konuş) Faz 9'da.

## Özellikler (şu an)

- **Kutu:** Dünyaya ilk girişte önüne bir karton kutu bırakılır. Kutuya **sol tık** yapınca kutu sallanır,
  kapakları açılır (partikül + ses) ve Mehburity içinden dönerek yükselir. Kutuyu sadece sahibi açabilir.
  Çıkınca Verity gibi *"Hello! I'm Mehburity. Your personal helper friend. Ask me anything. I know everything."*
  der (yazılı ve sesli).
- **Konuşma (Gemini):** Sohbete **"Mehburity, ..."** (veya "mehbur ...", "@mehburity ...") diye yaz; Mehburity
  Gemini ile cevap verir. Konumunu, canını, saati, yakındaki düşmanları ve son olayları bilir; konuşmayı hatırlar
  (son 10 mesaj). Saldırıya uğrayınca veya ölünce kendiliğinden yorum yapar (90 sn'de bir en fazla).
- **Ses:** Mehburity söylediklerini sesli okur (Gemini TTS). Varsayılan ses **Puck** ("upbeat"), Verity'nin
  enerjik/neşeli sesine en yakın hazır ses; ayarlardan 30 sesten biri seçilebilir. Sık söylenen kısa cümleler
  diske önbelleklenir (`config/mehburity/voice-cache`). Anahtar yoksa küçük bir "cıvıltı" sesi çalar.
- **Gemini yoksa çökmez:** Anahtar yoksa, internet yoksa, kota dolduysa veya 30 sn'de cevap gelmezse Mehburity
  basit yerleşik cevaplarla (Türkçe/İngilizce) devam eder ve sohbette bağlantı durumunu bildirir.
- **Savaş (seni korur):** Mehburity neon cyan bir **ışınla** savaşır: kısa bir şarjdan sonra (ince ışın + ses)
  hedefe parlak bir atış yapar. Öncelik sırası:
  1. Sana **vuran** her şey,
  2. Senin **vurduğun** canavar (yardım eder),
  3. Seni **avlayan** düşmanlar (sana saldırmadan önce vurur).

  Hedef menzil dışındaysa yaklaşır, duvar arkasındakine ateş etmez; senden 20 bloktan uzaklaşan hedefi bırakır.
  **Otur** modunda yerinden ayrılmadan **nöbet tutar**: menzilindeki her düşman canavara ateş eder.
  Seni, evcil hayvanlarını, köylüleri ve başka Mehburity'leri **asla** vurmaz; oyunculara ancak sunucu
  izin verirse (`combat.fightPlayers`) saldırır. Öldürdüğü canavarlar senin öldürmüşsün gibi **XP ve loot**
  düşürür. Öldürdükleri Gemini bağlamına eklenir ("az önce o zombiyi hallettim" diyebilir).
  `/mehburity savas kapat` ile kendi Mehburity'ni savaştan çekebilirsin (dünya kaydında hatırlanır).
- **Madencilik:** Sohbete **"Mehburity, maden kaz"** yaz: Mehburity etrafındaki (12 blok) **açıkta görünen**
  cevherleri bulur, yanına uçar ve alet gerekmeden **kendi kırar** (çatlama animasyonu + ses). Damar bitene kadar
  devam eder. Taşın içine gömülü cevherleri görmez (x-ray değil). Kazdıkları **Mehburity'nin çantasına** gider
  (27 slot). Çanta sana bağlı kaydedilir; oyundan çıkınca ya da ölünce kaybolmaz.
  - **"Mehburity, kazdığın madenleri ver"** → çantadaki her şey envanterine (sığmayan ayağına düşer).
  - **"Mehburity, bunu kaz"** → baktığın bloğu kazar (kırılmaz bloklar ve sandık gibi eşya tutan bloklar hariç).
  - **"Mehburity, ne kazdın?"** → çantayı söyler. **"Mehburity, kazmayı bırak"** → durur.
  - İngilizce de anlar: "go mining", "mine this block", "give me what you mined", "stop mining".

  Elmas kazma ile kazılmış gibi düşürür (her cevher eşyasını verir, XP küreleri çıkar). Spawn korumasına,
  macera moduna ve koruma modlarına uyar. Saldırı olursa kazmayı bırakıp savaşır, sonra devam eder.
  Gemini çantada ne olduğunu bilir.
- **Ev yapma (yaratıcı mod gibi, malzeme harcamaz):** Sohbete **"Mehburity, ev yap"** yaz: Mehburity önüne,
  kapısı sana bakan küçük bir ev kurar. 7×7 gövde, köşe direkleri, kapı, camlar, basamaklı piramit çatı, gerekiyorsa
  zemin altına temel; içinde yatak, sandık, çalışma masası, fırın ve meşaleler (Verity karanlığında önemli).
  Mehburity inşaat alanında süzülüp blokları tek tek yerleştirir.
  - Stiller: **"taş ev yap"**, **"tuğla ev yap"**, **"ahşap ev yap"** (varsayılan), ve
    **"elimdeki bloktan ev yap"**: duvarlar elindeki bloktan olur. Her tam blok olur; kırılmaz bloklar (ana kaya,
    bariyer...), operatör blokları (komut bloğu...) ve tam olmayan bloklar (meşale, basamak...) olmaz.
  - **"Mehburity, evi geri al"** → evi kaldırır, oradaki her şeyi eski haline getirir (son ev, sunucu kapanana kadar).
  - **"Mehburity, inşaatı durdur"** → yarıda bırakır (yine geri alınabilir).
  - İngilizce: "build me a house", "build a stone house", "remove the house", "stop building".

  Kırılmaz bloklara, eşya tutan bloklara (sandık, fırın...) ve korunan alanlara asla dokunmaz; önünde böyle bir şey
  varsa sağına, arkasına ya da soluna kurar, hiçbir yöne sığmazsa söyler. Kazma ile aynı anda yapılmaz; saldırı
  olursa önce savaşır. İnşaat sürerken Mehburity sahasından ayrılmaz.
- **Öfke:** Mehburity'nin 0–100 arası bir **öfke göstergesi** var. Onu sinirlendiren şeyler:
  - Bir oyuncunun ona vurması (sahibi dahil): **+25**, bir canavarın vurması: **+5** (ok, atanın sayılır),
  - Sohbette ona hakaret ("Mehburity, aptalsın", "salak", "I hate you"...): **+15**,
  - **Sahibini öldürmek: +100**, yani katil anında öfkenin hedefi olur.

  Hasar almaz, sadece fark eder. **33**'te "Hey, yapma şunu!", **66**'da "Beni gerçekten kızdırıyorsun..." der ve
  üstünden kızgın partiküller çıkar, küre ara ara kırmızı yanıp söner. **100**'de **öfke nöbetine** girer:
  kükrer, kazmayı/inşaatı bırakır ve **canavar formuna** geçer (aşağıda), sonra **sadece suçluyu** kovalar
  (24 bloktan uzaklaşırsa yanına ışınlanır). Başka kimseye dokunmaz. Nöbet şöyle biter:
  - Suçlu **ölürse** → sakinleşir (öfke 0),
  - **Pasta** veya **özür** göstergeyi 40'ın altına indirirse,
  - Suçlu kaçarsa (oyundan çıkar, başka boyuta geçer) ya da **90 sn** dolarsa.

  Her durumda sonra sahibinin yanına ışınlanır. Sakinleştirmek için:
  - Mehburity'ye **pasta ile sağ tık**: **−60** (bir pasta harcanır, yaratıcı modda harcanmaz),
  - **"Mehburity, özür dilerim"** / "affet", "pardon", "sorry": **−25** (oyuncu başına 30 sn'de bir sayılır).
    Sahibi olmayan biri (genelde suçlu) de yazabilir; ona en yakın, öncelikle ona kızgın olan Mehburity duyar.
  - Kendi kendine soğur: kimse dokunmazsa dakikada **−10** (her sinirlendirmeden sonra 5 sn bekler).

  Öfke dünya kaydında saklanır: oyundan çıkıp girmek sıfırlamaz. Gemini öfkesini bilir ve ona göre konuşur.
  Sunucu isterse oyunculara (`anger.rageAtPlayers`) ya da sahibine (`anger.rageAtOwner`) öfkelenmesini kapatabilir;
  o zaman o kişiye en fazla küser, nöbete girmez.
- **Canavar formu:** Öfke nöbetinde Mehburity **18 blok boyunda, sıska bir iblise** dönüşür. Kükrer, titrer, kırmızı
  küre olarak yere iner ve dumanlar içinde 2 saniyede büyür (bu, suçlunun kaçmak için kazandığı süredir). Uzun bacaklar,
  dizlerine kadar inen kollar, her elde üç pençe ve iki boynuz. Kafası Mehburity'nin kırmızı, kızgın yüzlü küresidir
  ve suçluya döner. Gövdesi koyu, kırmızı parlayan damarlıdır. Verity karanlığında da görünür.
  - **Yürüyerek** kovalar (saniyede 6,5 blok, koşan oyuncudan hızlı), ağır ayak sesleriyle. **Blokların içinden
    geçer**: mağarada sıkışmaz, hiçbir bloğu kırmaz. Ayakları yüzeydedir; suçlu yerin altına saklanırsa onun hizasına iner.
  - 5 blok yakına gelince kolunu kaldırıp **pençesiyle** vurur (24 hasar, zırh azaltır), suçluyu savurur. Kule yapmak
    kurtarmaz: ayağından başına kadar her yere uzanır.
  - Ölüm mesajı: "… Mehburity tarafından parçalandı".
  - Nöbet bitince dumanla tekrar küreye döner, sahibinin yanına ışınlanır (sahibi yoksa yüzeye çıkar).
  - `monster.enabled: false` ile kapatılırsa öfke nöbeti kırmızı bir küre olarak kalır: daha hızlı uçar ve kırmızı,
    daha güçlü bir ışınla (10 hasar) vurur.
- **Mehburity:** Havada süzülen, pürüzsüz neon cyan küre; yüzü baktığı yöne döner. Seni takip eder,
  uzakta kalırsa (veya başka boyuttaysan) yanına ışınlanır.
- **Ölümsüz:** Hiçbir hasar almaz, `/kill` ile ölmez; boşluğa düşerse sahibinin yanına döner.
- **Oyuncu başına tek Mehburity:** Sunucu, kimin hangi Mehburity'ye sahip olduğunu dünya kaydında tutar.
  Kaybolan bir kopya (çökme, yeniden çağırma) yüklendiği anda kendini siler.
- **Seninle gelir:** Oyundan çıkınca Mehburity de seninle "gider", girince yanında belirir.
  "Otur" modundaysa bıraktığın yerde bekler.
- **Verity karanlığı (her zaman açık):** Parlaklık ayarı işe yaramaz, ortam ışığı ve ay ışığı söner;
  gece ve mağaralar ışık kaynağı olmadan zifiri karanlıktır. Meşaleler normal çalışır. Mehburity neon
  olduğu için karanlıkta parlar. Ayar sunucudan oyunculara gönderilir.

## Komutlar

Alt komutlar ASCII yazılır (Minecraft'ın komut ayrıştırıcısı "ç, ğ" kabul etmez).

| Komut | Yetki | Ne yapar |
|---|---|---|
| `/mehburity` | herkes | Sürüm ve durum |
| `/mehburity cagir` | herkes | Mehburity'yi yanına getirir; hiç kutu açmadıysan yeni kutu verir |
| `/mehburity otur` | herkes | Olduğu yerde bekler |
| `/mehburity takip` | herkes | Seni takip eder |
| `/mehburity savas` | herkes | Savaş durumunu söyler |
| `/mehburity savas ac` / `kapat` | herkes | Mehburity seni korusun / savaşmasın |
| `/mehburity kaz` | herkes | Etraftaki cevherleri kazar ("Mehburity, maden kaz") |
| `/mehburity kaz bunu` | herkes | Baktığın bloğu kazar ("Mehburity, bunu kaz") |
| `/mehburity kaz dur` | herkes | Kazmayı bırakır ("Mehburity, kazmayı bırak") |
| `/mehburity ev` | herkes | Ev yapar ("Mehburity, ev yap"); stil: `/mehburity ev ahsap\|tas\|tugla\|elimdeki` |
| `/mehburity ev dur` | herkes | İnşaatı durdurur ("Mehburity, inşaatı durdur") |
| `/mehburity ev geri` | herkes | Son evi kaldırır, araziyi eski haline getirir ("Mehburity, evi geri al") |
| `/mehburity ver` | herkes | Kazdıklarını verir ("Mehburity, kazdığın madenleri ver") |
| `/mehburity canta` | herkes | Çantada ne olduğunu söyler ("Mehburity, ne kazdın?") |
| `/mehburity ofke` | herkes | Mehburity'nin ne kadar kızgın olduğunu söyler |
| `/mehburity sakin` | OP | Yüklü tüm Mehburity'leri sakinleştirir, öfke nöbetlerini bitirir |
| `/mehburity ayarlar` | herkes | Ayarlar ekranını açar (Gemini anahtarı, ses) |
| `/mehburity yenile` | OP | Ayar dosyasını yeniden yükler ve oyunculara gönderir |

## Gemini kurulumu

1. https://aistudio.google.com/apikey adresinden ücretsiz bir Gemini API anahtarı al.
2. Oyunda **Modlar → Mehburity → Yapılandır** (Mod Menu) veya sohbete `/mehburity ayarlar` yaz.
3. Anahtarı yapıştır, **Bağlantıyı test et**'e bas, **Bitti** ile kaydet.

Ayarlar ekranında: model (`gemini-3.5-flash-lite` hızlı/ucuz, `gemini-3.8-flash` daha akıllı), sesli konuşma
açık/kapalı, ses seçimi, TTS modeli, ses yüksekliği ve **▶ Dinle** (seçili sesi dener).

**Anahtar nerede durur?** Sadece kendi bilgisayarında, `config/mehburity-client.json` içinde. Sunucuya hiç
gönderilmez: sunucu soruyu senin istemcine yollar, istemci Gemini'ye sorar, cevabı geri yollar. Bu yüzden
sunucularda da çalışır; herkes kendi anahtarını kullanır. Yakındaki oyuncular cevabı sohbette görür, sesi sadece
Mehburity'nin sahibi duyar.

Gemini ile konuşma ve ses Google'ın API kotasından düşer (ücretsiz katmanın günlük sınırları vardır).

## Ayar dosyaları

| Dosya | İçerik |
|---|---|
| `config/mehburity.json` | Tüm sayısal değerler ve kelime listeleri (tek dosya) |
| `config/mehburity-client.json` | Sadece istemci: Gemini API anahtarı. **Asla paylaşma**, git'e girmez |

`config/mehburity.json` alanları:

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `companion.followStartDistance` | 4.0 | Bu mesafeden uzaksan peşinden uçar |
| `companion.followStopDistance` | 2.5 | Bu kadar yaklaşınca durur |
| `companion.teleportDistance` | 20.0 | Bundan uzaksan yanına ışınlanır |
| `companion.followSpeed` | 2.0 | Takip hızı çarpanı |
| `combat.enabled` | true | Sunucu genelinde savaş açık mı |
| `combat.damage` | 6.0 | Bir ışın atışının hasarı (zombinin 20 canı var) |
| `combat.beamRange` | 12.0 | Işın menzili; hedef daha uzaksa yaklaşır |
| `combat.chargeTicks` | 12 | Işının şarj süresi (tick) |
| `combat.cooldownTicks` | 16 | İki atış arası bekleme (tick) |
| `combat.defendOwner` | true | Sana vurana saldırır |
| `combat.helpOwnerAttack` | true | Senin vurduğuna saldırır |
| `combat.guardAgainstHostiles` | true | Seni hedef alan düşmanlara saldırır |
| `combat.attackAllHostiles` | false | Yakındaki **tüm** düşmanlara saldırır (seni fark etmemiş olsalar bile) |
| `combat.guardRadius` | 16.0 | Bu mesafedeki düşmanları fark eder |
| `combat.chaseRadius` | 20.0 | Senden bu kadar uzaklaşan hedefi bırakır (en fazla `teleportDistance`) |
| `combat.fightPlayers` | false | Sana saldıran (ya da senin saldırdığın) oyunculara da saldırsın mı |
| `combat.ownerGetsDrops` | true | Öldürdükleri senin öldürmen sayılır (XP ve loot) |
| `mining.enabled` | true | Sunucu genelinde madencilik açık mı |
| `mining.targets` | ["#c:ores"] | Kazılacak bloklar: `#etiket` ya da blok kimliği (ör. `"minecraft:amethyst_block"`) |
| `mining.searchRadius` | 12 | Senin etrafında bu kadar blok uzağa bakar (otur modunda kendi etrafına) |
| `mining.exposedOnly` | true | Sadece açık bir yüzü olan cevherler (gömülüleri görmez) |
| `mining.reach` | 4.0 | Bloğu bu uzaklıktan kırar |
| `mining.ticksPerHardness` | 8.0 | Blok sertliği başına kırma süresi (demir cevheri ≈ 1,2 sn) |
| `mining.minBreakTicks` / `maxBreakTicks` | 6 / 100 | Kırma süresinin alt/üst sınırı (tick) |
| `mining.tool` | "minecraft:diamond_pickaxe" | Düşen eşyalar bu aletle kazılmış gibi hesaplanır |
| `mining.bagSlots` | 27 | Çanta boyutu (yığın) |
| `mining.maxBlocksPerTrip` | 64 | Bir seferde en fazla kaç blok kazar |
| `mining.pointRange` | 24.0 | "Bunu kaz" için baktığın bloğun en uzak mesafesi |
| `build.enabled` | true | Sunucu genelinde ev yapma açık mı |
| `build.defaultStyle` | "ahsap" | Stil söylenmezse: `ahsap`, `tas`, `tugla` |
| `build.allowHeldBlock` | true | "Elimdeki bloktan ev yap"a izin |
| `build.blocksPerSecond` | 15.0 | İnşaat hızı |
| `build.maxFoundationDepth` | 6 | Zemin altındaki boşlukları bu derinliğe kadar doldurur |
| `build.furnish` | true | Yatak, sandık, çalışma masası, fırın (meşaleler her zaman konur) |
| `build.reach` | 8.0 | Blokları bu uzaklıktan yerleştirir |
| `anger.enabled` | true | Sunucu genelinde öfke açık mı |
| `anger.playerHitAnger` / `mobHitAnger` | 25 / 5 | Bir oyuncu / canavar vurunca eklenen öfke |
| `anger.insultAnger` | 15 | Hakaret başına öfke |
| `anger.ownerKilledAnger` | 100 | Sahibi öldürülünce katile öfke (100 = anında nöbet) |
| `anger.decayPerMinute` | 10 | Dakikada soğuma |
| `anger.rageThreshold` | 100 | Bu seviyede öfke nöbeti başlar |
| `anger.calmBelow` | 40 | Pasta/özür göstergeyi buna indirince nöbet biter |
| `anger.cakeCalm` / `apologyCalm` | 60 / 25 | Pastanın / özrün düşürdüğü öfke |
| `anger.apologyCooldownSeconds` | 30 | Aynı oyuncunun özrü bu sürede bir sayılır |
| `anger.rageDamage` | 10.0 | Nöbetteki ışının hasarı (canavar formu kapalıyken) |
| `anger.rageCooldownTicks` | 10 | Nöbette iki atış arası bekleme (tick, canavar formu kapalıyken) |
| `anger.rageSpeed` | 3.0 | Suçluyu kovalarken uçuş hız çarpanı (canavar formu kapalıyken) |
| `anger.maxRageSeconds` | 90 | Bu süreden sonra kovalamayı bırakır |
| `anger.rageAtPlayers` | true | Oyunculara öfke nöbeti (kapalıyken sadece canavarlar suçlu olabilir) |
| `anger.rageAtOwner` | true | Sahibine de öfke nöbeti (Verity gibi) |
| `anger.insults` | ["aptal", "salak", ...] | Hakaret sayılan kelimeler (büyük/küçük harf ve Türkçe karakter fark etmez; "aptalsın" da sayılır) |
| `monster.enabled` | true | Öfke nöbetinde canavar formu |
| `monster.height` | 18.0 | İblisin boyu (4–32 blok) |
| `monster.transformTicks` | 40 | Dönüşüm süresi (tick, 20 = 1 sn) |
| `monster.blocksPerSecond` | 6.5 | Yürüme hızı (koşan oyuncu ≈ 5,6) |
| `monster.reach` | 5.0 | Pençe menzili (blok) |
| `monster.damage` | 24.0 | Bir pençe darbesinin hasarı |
| `monster.attackCooldownTicks` | 30 | İki pençe darbesi arası bekleme (tick) |
| `monster.knockback` | 1.2 | Darbenin savurma gücü |
| `ai.enabled` | true | Mehburity sohbete cevap versin mi |
| `ai.chatPrefixes` | ["mehburity", "mehbur"] | Mesaj bunlardan biriyle başlarsa Mehburity'ye sorulmuş sayılır |
| `ai.hearingRadius` | 32.0 | Cevabı sohbette görebilecek oyuncuların uzaklığı (blok) |
| `ai.requestTimeoutSeconds` | 30 | Bu süre içinde cevap gelmezse yerleşik cevap verilir |
| `ai.cooldownSeconds` | 2.0 | Aynı oyuncunun iki sorusu arasındaki en kısa süre |
| `ai.maxMessageLength` / `ai.maxReplyLength` | 256 / 400 | Soru/cevap uzunluk sınırı (karakter) |
| `ai.commentOnEvents` | true | Saldırı/ölüm gibi olaylarda kendiliğinden yorum |
| `ai.eventCommentCooldownSeconds` | 90 | İki olay yorumu arasındaki en kısa süre |
| `ai.greeting` | "Hello! I'm Mehburity. ..." | Kutudan çıkınca söylediği cümle |
| `ai.extraPersona` | "" | Kişiliğe ek not (ör. "Korsan gibi konuş.") |
| `verity.box.enabled` | true | İlk girişte kutu verilsin mi |
| `verity.box.openTicks` | 40 | Kutu açılma animasyonu (tick, 20 = 1 sn) |
| `verity.darkness.enabled` | true | Verity karanlığı |
| `verity.darkness.strength` | 0.85 | 0 = normal ışık, 1 = ışıksız yerler tamamen siyah |

Bozuk bir `mehburity.json` silinmez; `mehburity.json.broken` olarak saklanır ve varsayılanlar kullanılır.
Aralık dışı değerler otomatik düzeltilir.

## Model, doku ve sesler (yer tutucular)

Kendi çizimlerini **aynı yol ve boyutla** koyman yeterli:

| Dosya | Boyut | Ne |
|---|---|---|
| `src/main/resources/assets/mehburity/textures/entity/mehburity/mehburity.png` | 128×64 | Mehburity küresi (eşdikdörtgen: ortası = yüz, üst kenar = tepe) |
| `src/main/resources/assets/mehburity/textures/entity/mehburity/mehburity_angry.png` | 128×64 | Öfke nöbetindeki kırmızı küre ve kızgın yüz (aynı düzen); canavarın kafası da bu |
| `src/main/resources/assets/mehburity/textures/entity/mehburity/mehburity_monster.png` | 64×64 | Canavarın derisi (her kutu yüzüne bir kez gerilir) |
| `src/main/resources/assets/mehburity/textures/entity/mehburity/box.png` | 64×64 | Kutu (gövde 0,0'da; ön/arka kapaklar 0,24'te; yan kapaklar 0,32'de) |
| `src/main/resources/assets/mehburity/icon.png` | 128×128 | Mod ikonu: Mehburity'nin kafası (gölgeli neon küre, şeffaf arka plan) |

Yer tutucuları yeniden üretmek için: `python tools/gen_textures.py`. Logonun büyük bir kopyası için:
`python tools/gen_textures.py --logo "Mehburity Logo.png" 512`

Sesler şimdilik vanilla seslere yönlendirilmiş (`assets/mehburity/sounds.json`). Kendi seslerin için
`.ogg` dosyalarını `src/main/resources/assets/mehburity/sounds/` klasörüne koy ve `sounds.json`'da
`{"name": "minecraft:...", "type": "event"}` yerine `"mehburity:dosya_adi"` yaz.
Ses olayları: `box.shake`, `box.open`, `mehburity.emerge`, `mehburity.speak` (anahtar yokken çalan "cıvıltı"),
`mehburity.beam.charge` (ışın şarjı), `mehburity.beam.fire` (atış), `mehburity.rage` (öfke nöbeti kükremesi),
`mehburity.monster.transform` / `step` / `swipe` / `shrink` (canavara dönüşme, ayak sesi, pençe, küreye dönme).
Mehburity'nin konuşma sesi dosya değil, Gemini TTS ile üretilir.

## Kurulum (geliştirme)

```bat
install.bat
```

Derler, sunucu oyun testlerini çalıştırır ve jar'ı MehburMC Launcher'daki **Mehburity Test**
instance'ına kurar. Eksikse Fabric API, Mod Menu ve placeholder-api indirilir (SHA-512 doğrulamalı).
Masaüstündeki **Mehburity Kur** kısayolu bunu çalıştırır.

| Komut | Ne yapar |
|---|---|
| `gradlew build` | Derler + sunucu oyun testleri |
| `gradlew runClientGameTest` | Gerçek istemcide uçtan uca test (ekran görüntüleri `build/run/clientGameTest/screenshots`) |
| `gradlew runClient` | Geliştirme istemcisini açar |

## Sürümler

| Bileşen | Sürüm |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Fabric Loom | 1.18-SNAPSHOT |
| Mod Menu (isteğe bağlı) | 20.0.3 |
| Java | 25 |

## Proje yapısı

```
src/main/java/com/mehbur/mehburity/
  entity/   Mehburity, kutu, sahiplik kaydı (CompanionRegistry), CompanionManager
  ai/       GeminiClient (Interactions API), AiRouter, kişilik, oyun bağlamı ve olaylar, yerleşik cevaplar
  verity/   Verity özellikleri (VerityFeature arayüzü): kutu, karanlık
  network/  Sunucu ↔ istemci paketleri
  command/  /mehburity
  config/   Tek ayar dosyası
  combat/   Savaş: hedef kuralları (CombatTargets), hedef seçimi, ışın saldırısı, hasar tipi
  mining/   Madencilik: kurallar (MiningRules), kazma hedefi (MineGoal), çanta (MiningBag), sohbet emirleri (MiningChat)
  build/    Ev: plan (HousePlan), stiller (HouseStyle), inşaat hedefi (BuildGoal), geri alma (Building), sohbet emirleri (BuildChat)
  anger/    Öfke: gösterge ve nöbet (Anger), suçluyu hedefleme (RageTargetGoal), canavar formunda kovalama ve pençe
            (MonsterGoal), özür/hakaret (AngerChat)
src/client/java/com/mehbur/mehburity/client/
  render/   Küre renderer'ı (+ ışın), kutu modeli
  verity/   Karanlığın istemci tarafı (lightmap mixin'i)
  ai/       İstemcide Gemini çağrısı (ClientAi) ve ses (VoicePlayer)
  screen/   Ayarlar ekranı + Mod Menu entegrasyonu
  config/
src/gametest/   Sunucu ve istemci oyun testleri (FakeGemini: testler için yerel sahte Gemini sunucusu;
                ev testi kendi test ortamında, mehburity-test:solo, tek başına çalışır)
tools/          gen_textures.py, scan_secrets.py
```

Yeni bir Verity özelliği eklemek için `verity/VerityFeature`'ı uygula ve `VerityFeatures.ALL`'a ekle.

## Güvenlik

`git config core.hooksPath .githooks` ile her commit'ten önce `tools/scan_secrets.py` çalışır ve
API anahtarı gibi hassas bilgi içeren commit'leri engeller.

## Notlar

Mehburity, ThatMob'un Verity karakterinden esinlenmiştir. Verity JE modunun hiçbir modeli, dokusu veya
sesi kullanılmamıştır (o mod "All Rights Reserved"); tüm görseller bu projede sıfırdan üretilmiştir.
