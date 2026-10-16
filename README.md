# Mehburity

Minecraft **26.2 / Fabric** modu. Mehburity, oyuncunun yanında süzülen neon cyan, gülen yüzlü bir
yapay zeka yardımcısı: Gemini ile konuşur, seni korur, maden kazar, ev yapar ve sinirlenince
18 blokluk bir canavara dönüşür.

> Durum: **Faz 1 (Mehburity, kutu ve karanlık) tamamlandı.** Gemini, savaş, madencilik, ev, öfke ve
> canavar formu sonraki fazlarda gelecek; tam README Faz 8'de.

## Özellikler (şu an)

- **Kutu:** Dünyaya ilk girişte önüne bir karton kutu bırakılır. Kutuya **sol tık** yapınca kutu sallanır,
  kapakları açılır (partikül + ses) ve Mehburity içinden dönerek yükselir. Kutuyu sadece sahibi açabilir.
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
| `/mehburity yenile` | OP | Ayar dosyasını yeniden yükler ve oyunculara gönderir |

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
Ses olayları: `box.shake`, `box.open`, `mehburity.emerge`.

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
  verity/   Verity özellikleri (VerityFeature arayüzü): kutu, karanlık
  network/  Sunucu ↔ istemci paketleri
  command/  /mehburity
  config/   Tek ayar dosyası
  ai/ build/ combat/ mining/ anger/   Sonraki fazlar
src/client/java/com/mehbur/mehburity/client/
  render/   Küre renderer'ı, kutu modeli
  verity/   Karanlığın istemci tarafı (lightmap mixin'i)
  config/ screen/
src/gametest/   Sunucu ve istemci oyun testleri
tools/          gen_textures.py, scan_secrets.py
```

Yeni bir Verity özelliği eklemek için `verity/VerityFeature`'ı uygula ve `VerityFeatures.ALL`'a ekle.

## Güvenlik

`git config core.hooksPath .githooks` ile her commit'ten önce `tools/scan_secrets.py` çalışır ve
API anahtarı gibi hassas bilgi içeren commit'leri engeller.

## Notlar

Mehburity, ThatMob'un Verity karakterinden esinlenmiştir. Verity JE modunun hiçbir modeli, dokusu veya
sesi kullanılmamıştır (o mod "All Rights Reserved"); tüm görseller bu projede sıfırdan üretilmiştir.
