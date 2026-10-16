# Mehburity

Minecraft **26.2 / Fabric** modu. Mehburity, oyuncunun yanında süzülen neon cyan, gülen yüzlü bir
yapay zeka yardımcısı: Gemini ile konuşur, seni korur, maden kazar, ev yapar ve sinirlenince
18 blokluk bir canavara dönüşür.

> Durum: **Faz 4 (madencilik) tamamlandı.** Ev, öfke ve canavar formu sonraki fazlarda gelecek;
> tam README Faz 8'de.

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
| `/mehburity ver` | herkes | Kazdıklarını verir ("Mehburity, kazdığın madenleri ver") |
| `/mehburity canta` | herkes | Çantada ne olduğunu söyler ("Mehburity, ne kazdın?") |
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
| `src/main/resources/assets/mehburity/textures/entity/mehburity/box.png` | 64×64 | Kutu (gövde 0,0'da; ön/arka kapaklar 0,24'te; yan kapaklar 0,32'de) |
| `src/main/resources/assets/mehburity/icon.png` | 128×128 | Mod ikonu |

Yer tutucuları yeniden üretmek için: `python tools/gen_textures.py`

Sesler şimdilik vanilla seslere yönlendirilmiş (`assets/mehburity/sounds.json`). Kendi seslerin için
`.ogg` dosyalarını `src/main/resources/assets/mehburity/sounds/` klasörüne koy ve `sounds.json`'da
`{"name": "minecraft:...", "type": "event"}` yerine `"mehburity:dosya_adi"` yaz.
Ses olayları: `box.shake`, `box.open`, `mehburity.emerge`, `mehburity.speak` (anahtar yokken çalan "cıvıltı"),
`mehburity.beam.charge` (ışın şarjı), `mehburity.beam.fire` (atış).
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
  build/ anger/   Sonraki fazlar
src/client/java/com/mehbur/mehburity/client/
  render/   Küre renderer'ı (+ ışın), kutu modeli
  verity/   Karanlığın istemci tarafı (lightmap mixin'i)
  ai/       İstemcide Gemini çağrısı (ClientAi) ve ses (VoicePlayer)
  screen/   Ayarlar ekranı + Mod Menu entegrasyonu
  config/
src/gametest/   Sunucu ve istemci oyun testleri (FakeGemini: testler için yerel sahte Gemini sunucusu)
tools/          gen_textures.py, scan_secrets.py
```

Yeni bir Verity özelliği eklemek için `verity/VerityFeature`'ı uygula ve `VerityFeatures.ALL`'a ekle.

## Güvenlik

`git config core.hooksPath .githooks` ile her commit'ten önce `tools/scan_secrets.py` çalışır ve
API anahtarı gibi hassas bilgi içeren commit'leri engeller.

## Notlar

Mehburity, ThatMob'un Verity karakterinden esinlenmiştir. Verity JE modunun hiçbir modeli, dokusu veya
sesi kullanılmamıştır (o mod "All Rights Reserved"); tüm görseller bu projede sıfırdan üretilmiştir.
