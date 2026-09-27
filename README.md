# App Manager (Shizuku-based)

Force-stop & freeze app pakai Shizuku, tanpa root. Build otomatis lewat GitHub Actions
supaya gak perlu download Gradle/Android SDK di Termux.

## 1. Cara bikin GitHub Personal Access Token (buat "password" push)

GitHub udah gak terima password akun biasa buat `git push` lewat HTTPS. Gantinya
pakai **Personal Access Token (PAT)**:

1. Buka https://github.com/settings/tokens di browser HP/laptop kamu (bukan di Termux)
2. Klik **Generate new token** → pilih **Generate new token (classic)**
3. Kasih nama bebas, misal `termux-push`
4. Expiration: pilih sesuai kebutuhan (misal 90 hari, atau No expiration kalau males bikin ulang)
5. Centang scope **repo** (itu aja cukup)
6. Klik **Generate token** di paling bawah
7. **Copy token-nya sekarang juga** (bentuknya `ghp_xxxxxxxxxxxxxxxxxxxx`) — token ini
   cuma ditampilin sekali, kalau ke-refresh halamannya ilang dan harus bikin baru

## 2. Setup repo di GitHub

1. Buka https://github.com/new
2. Kasih nama repo, misal `app-manager`
3. Jangan centang "Add README" (biar gak bentrok sama punya kita)
4. Create repository

## 3. Push dari Termux

```bash
pkg update && pkg install git -y

cd app-manager
git init
git branch -M main
git add .
git commit -m "init project"

git remote add origin https://github.com/USERNAME/app-manager.git
git push -u origin main
```

Pas diminta:
- **Username**: username GitHub kamu (bukan email)
- **Password**: paste token `ghp_...` yang tadi di-copy (BUKAN password akun GitHub biasa)

Kalau males input token tiap push, simpan sekali pakai credential helper:

```bash
git config --global credential.helper store
```

Push pertama kali tetap diminta token, tapi setelah itu tersimpan di
`~/.git-credentials` (di penyimpanan internal Termux, aman selama HP gak di-root
orang lain).

Alternatif lebih simpel: langsung selipin token di URL remote (tapi token jadi
kelihatan di riwayat command / `.git/config`, jadi hati-hati kalau share screen):

```bash
git remote set-url origin https://USERNAME:ghp_xxxx@github.com/USERNAME/app-manager.git
```

## 4. Ambil hasil APK

1. Buka repo di GitHub → tab **Actions**
2. Klik run terbaru (otomatis jalan tiap kali `git push` ke branch `main`)
3. Tunggu sampai selesai (ijo = sukses)
4. Scroll ke bawah, di bagian **Artifacts** ada `app-manager-debug.zip` →
   download & extract, isinya `app-debug.apk`

## 5. Cara pakai di HP

1. Install & buka Shizuku (dari Play Store / F-Droid / GitHub rikkaapps/Shizuku)
2. Aktifkan Shizuku lewat wireless debugging (Android 11+) atau ADB dari PC
3. Install `app-debug.apk` (aktifkan "install from unknown sources" kalau diminta)
4. Buka App Manager → grant izin Shizuku yang muncul
5. Daftar app muncul, tombol ❄ = freeze, ▶ = unfreeze, ⏹ = force stop

## Catatan teknis

- `pm disable-user` (dipakai buat freeze) **tidak menghapus data app**, beda
  sama `pm clear`. Jadi pas di-unfreeze, session/login tetap ada, gak perlu
  login ulang.
- Workflow build pakai `gradle` langsung (bukan `./gradlew`) jadi gak perlu
  commit file wrapper (`gradle-wrapper.jar`) yang bikin ukuran push lebih besar.
