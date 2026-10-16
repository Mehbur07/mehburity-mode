@echo off
rem Builds Mehburity (with all server game tests) and installs it into the MehburMC "Mehburity Test" instance.
rem Missing dependencies (Fabric API, Mod Menu + placeholder-api) are downloaded from Modrinth and verified by SHA-512.
setlocal EnableExtensions
chcp 65001 >nul

set "PROJECT=%~dp0"
set "MODS=%APPDATA%\MehburMC\game\mc\instances\mehburity-test-64ad68\mods"

title Mehburity - Build ^& Install
echo ============================================
echo   Mehburity - Derle ve Kur
echo ============================================
echo.

if not exist "%MODS%\" (
	echo [HATA] MehburMC "Mehburity Test" mods klasoru bulunamadi:
	echo        %MODS%
	echo        Instance silinmis ya da adi degismis olabilir.
	goto :fail
)

rem Fall back to an installed Temurin JDK 25 when JAVA_HOME is missing (e.g. stale environment).
if not defined JAVA_HOME (
	for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do set "JAVA_HOME=%%~fJ"
)
if not defined JAVA_HOME (
	echo [HATA] Java 25 bulunamadi. Eclipse Temurin JDK 25 kurulu olmali.
	goto :fail
)

echo [1/3] Mod derleniyor ve testler calisiyor (yaklasik 30 sn)...
set "BUILD_LOG=%PROJECT%build\install-build.log"
if not exist "%PROJECT%build\" mkdir "%PROJECT%build"
pushd "%PROJECT%"
call "%PROJECT%gradlew.bat" build --console=plain > "%BUILD_LOG%" 2>&1
set "BUILD_RESULT=%ERRORLEVEL%"
popd
if not "%BUILD_RESULT%"=="0" (
	echo [HATA] Derleme ya da testler basarisiz oldu, kurulum yapilmadi. Son satirlar:
	echo.
	powershell -NoProfile -Command "Get-Content -LiteralPath $env:BUILD_LOG -Tail 25"
	echo.
	echo Tam kayit: %BUILD_LOG%
	goto :fail
)
for /f "delims=" %%L in ('findstr /c:"required tests passed" "%BUILD_LOG%"') do echo       %%L

set "MOD_JAR="
for %%F in ("%PROJECT%build\libs\mehburity-*.jar") do (
	echo %%~nxF | findstr /i /c:"-sources" >nul || set "MOD_JAR=%%~fF"
)
if not defined MOD_JAR (
	echo [HATA] Derlenen jar bulunamadi.
	goto :fail
)

echo [2/3] Mod kuruluyor...
del /q "%MODS%\mehburity-*.jar" 2>nul
copy /y "%MOD_JAR%" "%MODS%\" >nul || (
	echo [HATA] Jar kopyalanamadi. Oyun dosyayi kilitliyor olabilir, oyunu kapatip tekrar dene.
	goto :fail
)
for %%F in ("%MOD_JAR%") do echo       %%~nxF kuruldu.

echo [3/3] Bagimliliklar kontrol ediliyor...
call :fetch "fabric-api-*.jar" "fabric-api-0.161.0+26.2.jar" ^
	"https://cdn.modrinth.com/data/P7dR8mSH/versions/ewUK83HI/fabric-api-0.161.0+26.2.jar" ^
	"2502fa5ade78e9a120b3747bc1a9a2167d6671743bea45bf115d3174c94121aeda87c549446d1a604a9c11fb828de17a551e3514ce42f3e93069dd14bdaee55b" || goto :fail
call :fetch "modmenu-*.jar" "modmenu-20.0.3.jar" ^
	"https://cdn.modrinth.com/data/mOgUt4GM/versions/iRo7AZXf/modmenu-20.0.3.jar" ^
	"c8224aa7a460d0c56ccb9978a112e4ad0d3eb17e2796e0c468a2392d220749f4f602838a11b3666145462c26078a879ab8a2fc177aa440f58e189c92a0088d6e" || goto :fail
call :fetch "placeholder-api-*.jar" "placeholder-api-3.1.0-beta.1+26.2.jar" ^
	"https://cdn.modrinth.com/data/eXts2L7r/versions/NDqH16LT/placeholder-api-3.1.0-beta.1+26.2.jar" ^
	"07b6fc802559d54ec6577f6e2788926ef391d755206c2bfb0528280977885cae4bc0c517d8a53eb6f34092015ebe6c41210627d255aa6504662daefa8fb76397" || goto :fail

echo.
echo Tamamlandi! MehburMC'de "Mehburity Test" instance'ini baslatabilirsin.
echo.
pause
exit /b 0

:fail
echo.
pause
exit /b 1

rem ------------------------------------------------------------------
rem :fetch <existing-pattern> <file-name> <url> <sha512>
rem Skips the download when a jar matching the pattern is already installed.
:fetch
if exist "%MODS%\%~1" (
	echo       %~2 zaten var.
	exit /b 0
)
echo       %~2 indiriliyor...
curl -fsSL -o "%MODS%\%~2.part" "%~3" || (
	echo [HATA] %~2 indirilemedi. Internet baglantini kontrol et.
	del /q "%MODS%\%~2.part" 2>nul
	exit /b 1
)
set "GOT_SHA="
for /f "skip=1 delims=" %%H in ('certutil -hashfile "%MODS%\%~2.part" SHA512') do if not defined GOT_SHA set "GOT_SHA=%%H"
set "GOT_SHA=%GOT_SHA: =%"
if /i not "%GOT_SHA%"=="%~4" (
	echo [HATA] %~2 dosyasinin dogrulamasi tutmadi, silindi.
	del /q "%MODS%\%~2.part" 2>nul
	exit /b 1
)
move /y "%MODS%\%~2.part" "%MODS%\%~2" >nul
echo       %~2 indirildi ve dogrulandi.
exit /b 0
