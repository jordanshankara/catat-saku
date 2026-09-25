# Panduan Eksekusi — Catat Uang

Panduan buat lu, Ko: dari laptop kosong sampai APK ter-install di HP. Semua gratis, kecuali langganan Claude Code yang udah lu punya.

---

## 1. Isi paket

| File | Fungsi |
|---|---|
| `CLAUDE.md` | Spec lengkap. Claude Code otomatis membaca file ini kalau ditaruh di root folder proyek. |
| `design/*.dc.html` | 3 mockup (Beranda, Input, Detail Makan) sebagai referensi visual buat Claude Code. |
| `PANDUAN-EKSEKUSI.md` | File ini. Buat lu, bukan buat Claude Code. |

---

## 2. Persiapan (sekali saja)

### Di laptop
1. **Install Android Studio** (gratis, dari developer.android.com). Cara paling gampang karena sudah termasuk JDK dan Android SDK. Buka sekali sampai proses download SDK selesai, lalu boleh ditutup — ngodingnya tetap lewat Claude Code.
2. **Install Git** kalau belum ada.
3. **Claude Code** — sudah lu punya. Panduan resmi: https://docs.claude.com/en/docs/claude-code/overview

### Di HP
Pilih salah satu cara install:
- **Kabel USB (paling cepat):** Pengaturan → Tentang ponsel → ketuk **Nomor versi/Build number 7×** → muncul Opsi Developer → aktifkan **USB debugging**. Claude Code bisa langsung install APK ke HP.
- **Tanpa kabel:** kirim file APK ke HP (Drive/WA ke diri sendiri) → tap file → izinkan **"Install dari sumber tidak dikenal"** untuk aplikasi yang lu pakai membuka file itu.

---

## 3. Siapkan folder proyek

```
catat-uang/
 ├─ CLAUDE.md
 └─ design/
     ├─ Main.dc.html
     ├─ Input.dc.html
     └─ DetailMakan.dc.html
```

Buka terminal di folder `catat-uang`, jalankan Claude Code, lalu pakai prompt di bawah **satu fase per sesi**.

---

## 4. Prompt per fase

Copy-paste apa adanya. Setelah tiap fase, **install ke HP dan cek checklist-nya** sebelum lanjut.

### Fase 0 — Fondasi
```
Baca CLAUDE.md sampai habis dan lihat file di folder design/. Kerjakan HANYA Fase 0.
Buat keystore rilis. Setelah itu jelaskan ke gue dengan bahasa sederhana: file apa dan password apa
yang harus gue simpan, dan di mana lokasinya. Pastikan keystore dan keystore.properties masuk .gitignore.
Build APK rilis, kasih tahu path-nya, dan install ke HP kalau terhubung. Jangan lanjut ke Fase 1.
```
**Cek:** app kosong terbuka di HP · font & warna sesuai mockup · **file keystore + password sudah lu simpan di 2 tempat** (Drive + email draft/password manager).

### Fase 1 — Mesin hitung
```
Kerjakan Fase 1 di CLAUDE.md. Tulis dulu test bab 13, baru implementasi core/engine sampai semua lulus.
Kalau ada aturan di bab 6 yang ambigu atau bertentangan, berhenti dan tanya gue. Laporkan hasil test
dalam tabel ID test → lulus/gagal.
```
**Cek:** semua test lulus, terutama T-01 (hutang), T-02 (Oktober 2026 = cadangan 170rb), T-07 (gaji telat).

### Fase 2 — Pencatatan harian
```
Kerjakan Fase 2 di CLAUDE.md. Ikuti mockup di folder design/ untuk warna, jarak, dan hierarki.
Pastikan dari Beranda sampai transaksi tersimpan cukup 4 tap. Build rilis dan install ke HP.
```
**Cek:** onboarding · catat Makan dalam 4 tap · slot otomatis sesuai jam · warning muncul **sebelum** simpan · Urungkan jalan · Riwayat bisa edit/hapus · Sabtu/Minggu tile Transport pindah ke depan.

### Fase 3 — Gaji, pemasukan, tabungan
```
Kerjakan Fase 3 di CLAUDE.md. Build rilis dan install ke HP.
```
**Cek:** input gaji tgl 29 → jadi pending, aktif tgl 1 · preview split menampilkan rumus per pos · Oktober muncul kartu kuning cadangan 170rb · THR default ke Tabungan · ambil tabungan wajib tahan 3 detik.

### Fase 4 — Tutup Buku
```
Kerjakan Fase 4 di CLAUDE.md, termasuk Mode Uji Tanggal (bagian 8.11) supaya gue bisa menguji
Tutup Buku tanpa menunggu tanggal 1. Build rilis dan install ke HP.
```
**Cek:** semua langkah 6.10 · Saku Sisa minus wajib ditutup dari Tabungan · verdict benar · bulan lalu terkunci.

### Fase 5 — Notifikasi
```
Kerjakan Fase 5 di CLAUDE.md. Sertakan panduan di dalam app untuk mematikan optimasi baterai.
Build rilis dan install ke HP.
```
**Cek:** notif 22:00 muncul 2 hari berturut-turut · tombol di notif membuka input · notif tetap jalan setelah HP di-restart.

### Fase 6 — Laporan & export
```
Kerjakan Fase 6 di CLAUDE.md. Build rilis dan install ke HP.
```
**Cek:** laporan mingguan & bulanan · file PDF dan Excel terbuka normal di HP dan laptop · angka di Excel bisa dijumlah.

### Fase 7 — Backup & pulihkan
```
Kerjakan Fase 7 di CLAUDE.md. Build rilis dan install ke HP.
```
**Cek (wajib dilakukan sekali):** kirim backup ke Drive → uninstall app → install lagi → **Pulihkan dari Backup** → semua data kembali utuh.

### Fase 8 — Polish
```
Kerjakan Fase 8 di CLAUDE.md. Build rilis dan install ke HP.
```
**Cek:** widget di home screen · tema gelap · konfirmasi nominal janggal (mis. ketik 450.000 untuk Makan).

### Kalau ketemu bug
```
Di HP: [langkah yang lu lakukan]. Harusnya [yang diharapkan], tapi yang terjadi [yang terjadi].
Cari penyebabnya, tambahkan test yang mereproduksi bug ini, baru perbaiki. Build rilis dan install ke HP.
```

### Kalau mau ubah aturan
Ubah dulu bagian terkait di `CLAUDE.md`, baru minta Claude Code menyesuaikan kode + test-nya. Dengan begitu spec dan app tidak pernah beda.

---

## 5. Keystore — yang wajib lu jaga

Keystore itu **stempel resmi app**. Update hanya bisa di-install kalau APK barunya dicap stempel yang sama.

Yang disimpan (Claude Code akan memberi tahu lokasinya di Fase 0):
1. File keystore (`.jks`)
2. Password keystore & password key

Simpan di **2 tempat**: Google Drive + email draft atau password manager. Jangan di-commit ke git.

Kalaupun hilang: **Kirim backup** → uninstall → install APK baru → **Pulihkan**. Data aman selama backup ada.

---

## 6. Gajian 29/30 September

App kemungkinan belum selesai saat gajian minggu ini. Tidak masalah:
- Catat manual dulu (notes HP): tanggal & nominal gaji, dan pengeluaran besar.
- **Oktober 2026 kurang ±Rp 170rb** (31 hari + 5 kali Sabtu). Sisihkan dari sekarang.
- Setelah Fase 2 jadi, pakai onboarding **Mulai Baru**: app membuat periode awal dari tanggal lu mulai, dengan uang pegangan lu saat itu.

---

## 7. Tips kerja bareng Claude Code

- **Satu fase per sesi.** Jangan minta semua sekaligus.
- Minta dia **commit git** di akhir tiap fase, supaya gampang balik kalau ada yang rusak.
- Jangan skip Fase 1. Kalau mesin hitungnya salah, seluruh app ikut salah.
- Selalu install **build rilis** ke HP, jangan campur dengan build debug — tanda tangannya beda dan bisa memaksa uninstall.
