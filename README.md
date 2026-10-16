# Mehburity

Minecraft **26.2 / Fabric** modu. Mehburity, oyuncunun yanında süzülen neon cyan, gülen yüzlü bir
yapay zeka yardımcısı: Gemini ile konuşur, seni korur, maden kazar, ev yapar ve sinirlenince
18 blokluk bir canavara dönüşür.

> Durum: **Faz 0 (altyapı) tamamlandı.** Özellikler sonraki fazlarda eklenecek; tam README Faz 8'de.

## Sürümler

| Bileşen | Sürüm |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Fabric Loom | 1.18-SNAPSHOT |
| Mod Menu (isteğe bağlı) | 20.0.3 |
| Java | 25 |

## Kurulum (geliştirme)

```bat
install.bat
```

Derler, sunucu oyun testlerini çalıştırır ve jar'ı MehburMC Launcher'daki **Mehburity Test**
instance'ına kurar. Eksikse Fabric API, Mod Menu ve placeholder-api indirilir (SHA-512 doğrulamalı).

Diğer komutlar:

| Komut | Ne yapar |
|---|---|
| `gradlew build` | Derler + sunucu oyun testleri |
| `gradlew runClientGameTest` | Gerçek istemcide uçtan uca test (ekran görüntüleri `build/run/clientGameTest/screenshots`) |
| `gradlew runClient` | Geliştirme istemcisini açar |

## Ayar dosyaları

| Dosya | İçerik |
|---|---|
| `config/mehburity.json` | Tüm sayısal değerler ve kelime listeleri (tek dosya) |
| `config/mehburity-client.json` | Sadece istemci: Gemini API anahtarı. **Asla paylaşma**, git'e girmez |

Bozuk bir `mehburity.json` silinmez; `mehburity.json.broken` olarak saklanır ve varsayılanlar kullanılır.

## Komutlar

| Komut | Yetki | Ne yapar |
|---|---|---|
| `/mehburity` | herkes | Sürüm ve durum |
| `/mehburity yenile` | OP | Ayar dosyasını yeniden yükler |

## Proje yapısı

```
src/main/java/com/mehbur/mehburity/
  entity/   ai/   build/   combat/   mining/   anger/   verity/   network/   command/   config/
src/client/java/com/mehbur/mehburity/client/
  config/   screen/   render/
src/gametest/   Sunucu ve istemci oyun testleri
tools/          gen_textures.py (yer tutucu dokular), scan_secrets.py (hassas bilgi taraması)
```

## Güvenlik

`git config core.hooksPath .githooks` ile her commit'ten önce `tools/scan_secrets.py` çalışır ve
API anahtarı gibi hassas bilgi içeren commit'leri engeller.
