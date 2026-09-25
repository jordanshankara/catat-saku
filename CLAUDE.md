# CLAUDE.md — Catat Uang

Spesifikasi lengkap aplikasi Android pribadi untuk mencatat pengeluaran. Dokumen ini adalah **sumber kebenaran** proyek.

**Versi 2.** Perubahan dari versi 1: pemicu Penyesuaian memakai Kebutuhan standar (R-06) · hari ditutup 00:00, notif 22:00 hanya preview (R-21) · Target Cadangan selalu dihitung ulang (R-13) · gaji telat memakai Dana Talangan bayangan (R-04) · kantong Dana Darurat (6.7) · Transport per akhir pekan (R-35–R-38) · Lain-lain jadi pos STOK 150.000, Nabung 160.000 · sisa STOK dicairkan (R-39) · peringatan hutang besar (R-27) · Pakai Tabungan (Rencana) dari layar input (R-59) · tambah/arsip pos (R-95) · PIN 4 digit (8.14) · test diperbarui.

## 0. Cara Claude Code memakai dokumen ini

- Baca dokumen ini **utuh** sebelum menulis kode. Kerjakan **per fase** (bagian 14). Jangan mengerjakan fase berikutnya sebelum diminta.
- **Aturan bisnis (bagian 6) tidak boleh ditafsirkan ulang.** Kalau ada yang ambigu atau saling bertentangan, **berhenti dan tanya** — jangan menebak.
- Semua hitungan uang ada di modul **`core/engine`**: Kotlin murni, tanpa dependensi Android, dan **wajib lulus unit test di bagian 13 sebelum UI yang memakainya dibuat.**
- Jangan menambah fitur, library, izin, atau koneksi internet di luar yang tertulis di sini.
- Referensi visual ada di folder `design/` (3 mockup HTML). File itu **referensi warna, jarak, dan hierarki**, bukan kode untuk dijalankan. Kalau teks di mockup berbeda dengan dokumen ini, **dokumen ini yang benar**.
- Bahasa UI: **Bahasa Indonesia**, santai tapi jelas. Pengguna dipanggil **"Ko"** (bisa diubah di Pengaturan).

---

## 1. Tentang aplikasi

Aplikasi pencatat keuangan pribadi untuk **satu pengguna**, **100% offline**, tanpa server, tanpa akun, tanpa biaya.

Tujuan utama: pengguna **tidak malas mencatat**. Dari membuka app sampai transaksi tersimpan cukup **4 tap**: buka → pilih kategori → ketik angka → simpan. Saat mencatat, pengguna langsung **sadar** posisinya: jatah hari ini, sisa, hutang, dan peringatan.

Profil pengguna:
- Gaji bulanan diterima sekitar **tanggal 29/30**, untuk dipakai **bulan berikutnya**.
- Tinggal di mess dekat kantor. Berangkat kerja **tidak** mengeluarkan biaya.
- Setiap akhir pekan pulang ke rumah: **pergi Sabtu siang/sore, balik Minggu siang/sore** (1 trip = pergi + balik).

---

## 2. Prinsip produk (wajib dipatuhi di setiap layar)

1. **Alur harian tidak boleh bertambah tap.** Kerumitan hanya boleh ada di: alur Gajian (bulanan), Tutup Buku (bulanan), Pengaturan (sekali), dan pertanyaan yang **hanya muncul kalau ada masalah**.
2. **Default otomatis, konfirmasi hanya saat perlu.** Contoh: slot makan dipilih otomatis sesuai jam.
3. **Status tidak boleh hanya warna.** Setiap status wajib punya teks dan/atau ikon.
4. **Peringatan muncul sebelum menyimpan**, bukan sesudahnya (preview dampak di layar input).
5. **Tidak ada pemotongan diam-diam.** Uang tidak pernah benar-benar keluar dari Tabungan atau Dana Darurat tanpa aksi eksplisit pengguna (tahan tombol 3 detik). Satu-satunya pengecualian adalah tampilan **talangan** (R-04), yang hanya bayangan.
6. **Semua saldo adalah hasil hitung ulang** dari data transaksi (lihat 7.2), tidak pernah disimpan sebagai angka yang di-update manual.

---

## 3. Batasan teknis

| Aspek | Keputusan |
|---|---|
| Platform | Android native, **Kotlin + Jetpack Compose + Material 3** |
| minSdk / target | minSdk **26**; compileSdk & targetSdk = SDK stabil terbaru yang terinstal |
| JDK | 17 |
| Database | **Room (SQLite)** |
| Preferensi | DataStore (Preferences) |
| Serialisasi | kotlinx.serialization (JSON) |
| Tanggal | `java.time` (`LocalDate`, `YearMonth`, `LocalTime`), zona waktu perangkat |
| Uang | **`Long` dalam rupiah penuh.** Dilarang `Float`/`Double` untuk uang. |
| Arsitektur | Single-activity, MVVM, `StateFlow`. DI manual (satu `AppContainer`) — **tanpa Hilt/Koin**. |
| Navigasi | Navigation Compose |
| Notifikasi & jadwal | `AlarmManager` (inexact window, `setWindow`/`setAndAllowWhileIdle`) + `BroadcastReceiver`. Jadwal ulang saat `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`. **Tidak** memakai exact alarm. |
| Widget | Jetpack Glance |
| PDF | `android.graphics.pdf.PdfDocument` (bawaan Android) |
| Excel | Penulis **XLSX minimal** buatan sendiri (SpreadsheetML via `ZipOutputStream`). **Dilarang Apache POI.** |
| File | Storage Access Framework (`ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`) + `FileProvider` untuk share |
| PIN | Hash PBKDF2 + salt acak, disimpan di DataStore. Tidak pernah disimpan dalam bentuk asli. |
| Font | **Plus Jakarta Sans** (OFL), file TTF dibundel di `res/font` — tidak diunduh saat runtime |
| Ikon | Ikon garis gaya Lucide (lisensi ISC) dikonversi ke `ImageVector`. **Tanpa emoji di UI.** |
| Izin | Hanya `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`. **Tanpa izin INTERNET.** |
| Build rilis | APK rilis di-sign dengan keystore pribadi (lihat 14, Fase 0). `versionCode` naik setiap build yang di-install. |

Dilarang: koneksi jaringan, analytics, iklan, login, cloud sync, library berat yang tidak tercantum.

---

## 4. Glosarium

| Istilah | Arti |
|---|---|
| **Periode / Bulan** | Bulan kalender, tanggal 1 sampai akhir bulan (`YearMonth`). Semua cutoff = akhir bulan. |
| **Pos** | Kategori anggaran. Punya satu **jenis**: HARIAN, STOK, TETAP, atau TABUNGAN (lihat 5). |
| **Jatah harian** | Nominal per hari untuk pos HARIAN. |
| **Jatah akhir pekan** | Nominal per akhir pekan untuk Transport = budget Transport ÷ 4. |
| **Hutang (harian)** | Kelebihan pemakaian pos HARIAN, per pos. Hanya bisa dilunasi dari hemat hari-hari berikutnya pos yang sama, atau lewat Mode Darurat. |
| **Saku Sisa** | Kantong uang bebas bulan berjalan. Diisi dari sisa gaji setelah alokasi, hemat harian, sisa jatah akhir pekan, dan sisa pos STOK di akhir bulan. Membiayai kelebihan pos STOK dan trip tambahan. **Boleh minus.** |
| **Reservasi trip** | Bagian Saku Sisa yang dicadangkan untuk akhir pekan ke-5 (bulan dengan 5 hari Sabtu). |
| **Sisa bebas** | Saku Sisa − reservasi trip yang belum terpakai. |
| **Target Cadangan** | Kekurangan yang harus dikumpulkan pengguna (lewat hemat) agar Sisa bebas tidak minus di akhir bulan. Selalu dihitung ulang. |
| **Tabungan** | Kantong tabungan (virtual; pengguna yang memindahkan uang sungguhan). |
| **Dana Darurat** | Kantong cadangan terakhir, terpisah dari Tabungan. Punya target (default 1.000.000). |
| **Urutan penutup** | Urutan kantong yang dipakai untuk menutup kekurangan: **Saku Sisa → Tabungan → Dana Darurat**. |
| **Saldo Pending** | Gaji yang sudah diterima tetapi untuk bulan berikutnya. Belum boleh dipakai. |
| **Talangan** | Pengeluaran di bulan yang gajinya belum masuk. Ditampilkan sebagai potongan **bayangan** dari kantong-kantong sesuai urutan penutup, dan otomatis kembali saat gaji masuk. |
| **Tutup Buku** | Alur penutupan bulan (tanggal 1 untuk bulan sebelumnya). Setelahnya bulan itu read-only. |
| **Verdict** | Kesimpulan bulan: BERHASIL NABUNG / PAS-PASAN / BONCOS, dengan label khusus TANPA GAJI. |
| **Uang pegangan** | Semua uang pengguna di luar Tabungan dan Dana Darurat (dompet + rekening + e-wallet). |

---

## 5. Konfigurasi default (seed saat instal pertama)

| Pos (`key`) | Nama tampil | Jenis | Nominal | Catatan |
|---|---|---|---|---|
| `makan` | Makan | HARIAN | **50.000 / hari** | Slot: Sarapan, Siang, Malam, Jajan |
| `buah` | Buah | HARIAN | **10.000 / hari** | Tanpa slot |
| `transport` | Transport OE | STOK (mode akhir pekan) | **480.000 / bulan** | Jatah **120.000 / akhir pekan** (480.000 ÷ 4). Hari pulang: Sabtu. |
| `protein` | Protein | STOK | **200.000 / bulan** | ±2× beli @ ±100.000 (sekali beli ±93.000, habis ±14 hari). Selisihnya sengaja jadi buffer. |
| `lain` | Lain-lain | STOK | **150.000 / bulan** | Sabun, pulsa, laundry, dll. |
| `iuran_mess` | Iuran Mess | TETAP | **120.000 / bulan** | Tanggal jatuh tempo diatur pengguna (default tgl 1) |
| `ai` | AI | TETAP | **390.000 / bulan** | Nominal bisa berubah (kurs). Jatuh tempo diatur pengguna (default tgl 1). |
| `nabung` | Nabung | TABUNGAN | **160.000 / bulan** | Disetor ke Tabungan saat split |

Pengaturan lain:

| Kunci | Default |
|---|---|
| Gaji template | 3.300.000 |
| Perkiraan tanggal gajian | 29 |
| Jam notifikasi harian | 22:00 |
| Ambang aman Sisa bebas | 50.000 |
| Target Dana Darurat | 1.000.000 |
| Kunci PIN setelah di-background | 5 menit |
| Nama panggilan | Ko |
| Tema | Terang |

Perubahan pos (nominal, tambah, arsip) di Pengaturan **berlaku mulai bulan berikutnya** (R-95).

**Kebutuhan standar** (30 hari, 4 akhir pekan) = 1.500.000 + 300.000 + 480.000 + 200.000 + 150.000 + 120.000 + 390.000 + 160.000 = **3.300.000**.

---

## 6. Aturan bisnis

Kode aturan (R-xx) dipakai di test dan komentar kode.

### 6.1 Periode & gaji

- **R-01** Periode = bulan kalender. Tidak ada periode "gaji ke gaji".
- **R-02** Input gaji menyimpan: nominal, tanggal terima, **bulan target**. Bulan target default: jika `tanggal terima ≥ 20` → bulan berikutnya; jika `< 20` → bulan berjalan (gaji telat). Ditampilkan dan bisa diubah sebelum konfirmasi ("Gaji untuk bulan Oktober?").
- **R-03 Gaji lebih cepat:** gaji untuk bulan yang belum dimulai = **Saldo Pending**. Tidak menambah Saku Sisa bulan berjalan dan tidak bisa dipakai. Split-nya dikonfirmasi saat input, tetapi alokasi baru **aktif tanggal 1** bulan target.
- **R-04 Gaji telat — Dana Talangan (bayangan):**
  - Jika bulan M berjalan tanpa gaji, jatah HARIAN dan budget STOK tetap aktif memakai alokasi template bulan M, tetapi **tidak ada Saku Sisa awal dari gaji perkiraan**.
  - `Talangan(M)` = total pengeluaran bulan M sejak tanggal 1 selama gaji M belum ada. Ini **nilai turunan**, bukan transaksi.
  - Tampilan: talangan dibebankan secara bayangan mengikuti **urutan penutup**: Saku Sisa bawaan bulan lalu (atau Saku Sisa akhir bulan lalu jika Tutup Buku belum selesai) → Tabungan → Dana Darurat. Porsi yang ditalangi ditampilkan dengan gaya berbeda (garis putus-putus + label "talangan").
  - Banner Beranda: "Gaji belum masuk · ditalangi Rp X (Sisa Rp A, Tabungan Rp B, Dana Darurat Rp C)" — komponen yang 0 disembunyikan.
  - Jika talangan > semua kantong: peringatan merah "Dana talangan habis, kurang Rp X". Pencatatan tetap boleh.
  - Saat gaji M diinput: talangan hilang otomatis (hasil hitung ulang). Tampilkan feedback "Talangan Rp X dikembalikan". Semua pengeluaran sejak tanggal 1 dihitung normal ke alokasi M.
  - Talangan **tidak pernah** dihitung sebagai ambil tabungan dan **tidak** mempengaruhi verdict.
  - Bulan tanpa gaji sampai Tutup Buku: lihat 6.10 langkah 1.
- **R-05 Gaji ganda:** jika bulan target sudah punya gaji, tanyakan "Ini gaji tambahan/rapel?" — jika ya, catat sebagai **Pemasukan** (bukan gaji kedua).
- **R-06 Penyesuaian wajib:** `KebutuhanStandar(M) = Σ HARIAN(jatah × 30) + Σ STOK(budget) + Σ TETAP(estimasi) + Nabung`, memakai nominal pos yang berlaku untuk bulan M (3.300.000 dengan default). Jika `gaji < KebutuhanStandar(M)`, layar **Penyesuaian** wajib muncul sebelum split.
  - Usulan otomatis: pos TETAP tidak disentuh → budget STOK & jatah HARIAN dipotong proporsional → Nabung dipotong terakhir. Semua angka bisa diedit; tombol lanjut aktif hanya jika KebutuhanStandar hasil penyesuaian ≤ gaji. Hasilnya disimpan sebagai alokasi bulan M.
  - Kekurangan karena **kalender** (hari > 30, Sabtu > 4) **tidak** memicu Penyesuaian — itu tugas Target Cadangan (6.2).

### 6.2 Alokasi & Target Cadangan

Notasi: `D(M)` = jumlah hari bulan M; `S(M)` = jumlah hari Sabtu di bulan M; `J` = jatah akhir pekan Transport.

- **R-10** `Kebutuhan(M) = Σ HARIAN(jatah × D(M)) + Σ STOK(budget) + Σ TETAP(estimasi) + Nabung`.
- **R-11** `TripTambahan(M) = max(0, S(M) − 4) × J`. Budget Transport **tetap** (tidak ikut dinaikkan).
- **R-12** `SakuSisaAwal(M) = gaji(M) − Kebutuhan(M) + bawaan(M)`, dengan `bawaan(M)` = sisa yang dipilih "bawa ke bulan depan" saat Tutup Buku M−1. **Boleh minus.**
- **R-13** `TargetCadangan(M) = max(0, TripTambahan(M) − SakuSisaAwal(M))`. `TripTambahan`, `SakuSisaAwal`, dan `TargetCadangan` adalah **nilai turunan** yang selalu dihitung ulang oleh `computeLedger` dari gaji, alokasi (hasil Penyesuaian), dan bawaan. **Tidak disimpan sebagai snapshot.**
- **R-14 Warning awal** (tidak ada pemotongan otomatis): jika TargetCadangan > 0, tampilkan di:
  1. Preview split: kartu kuning berisi rincian penyebab, contoh "31 hari: +60rb (Makan & Buah)" dan "5 kali Sabtu: +120rb". Jika Tutup Buku bulan lalu belum dilakukan, bawaan dianggap 0 dengan keterangan "Bisa berubah setelah Tutup Buku {bulan lalu}".
  2. Banner di Beranda pada tanggal 1 (nilai terbaru, sekali, bisa ditutup).
  3. Header Beranda sepanjang bulan: "Cadangan: terkumpul X / Y".
  4. Notifikasi H-5 akhir bulan (09:00) jika belum tercapai: "Kurang Rp X, hemat ±Rp Y/hari biar tabungan aman" (Y = X ÷ sisa hari, dibulatkan ke atas ke ribuan).
  5. Tutup Buku: Saku Sisa minus wajib ditutup (R-72).
- **R-15** `ReservasiSisa(M) = J × (jumlah akhir pekan ke-5 bulan M yang belum tertutup)` (bernilai 0 atau J). `SisaBebas = SakuSisa − ReservasiSisa`. Progres cadangan: `terkumpul = TargetCadangan − max(0, −SisaBebas)` (dibatasi 0..Target).
- **R-16 Status Sisa bebas:** `≥ ambang aman` → aman; `0 .. < ambang` → waspada; `< 0` → minus.

Contoh wajib (dipakai di test): **Oktober 2026** = 31 hari, 5 Sabtu (3, 10, 17, 24, 31). Kebutuhan = 1.550.000 + 310.000 + 480.000 + 200.000 + 150.000 + 120.000 + 390.000 + 160.000 = **3.360.000**. Dengan gaji 3.300.000: SakuSisaAwal = −60.000, TripTambahan = 120.000, **Target Cadangan = 180.000**, Penyesuaian tidak terpicu.

### 6.3 Pos HARIAN (Makan, Buah) & hutang

- **R-20** Setiap pos HARIAN punya jatah per hari dan **hutang sendiri-sendiri** (hutang Makan terpisah dari hutang Buah).
- **R-21** Hari D dianggap **tertutup** mulai pukul **00:00 hari D+1**. Hari ini selalu **sementara** (preview). Notifikasi 22:00 hanya menampilkan preview.
- **R-22** Saat hari D tertutup, untuk tiap pos HARIAN: `delta = jatah − terpakai(D)`.
  - Jika `delta ≥ 0`: `bayar = min(delta, hutang)`; `hutang −= bayar`; `SakuSisa += delta − bayar`. (**Hemat melunasi hutang dulu, sisanya ke Saku Sisa.**)
  - Jika `delta < 0`: `hutang += −delta`. Hutang **tidak** otomatis diambil dari kantong mana pun.
- **R-23 Mode Darurat** (tombol manual di kartu hutang): melunasi hutang sebesar yang dipilih pengguna mengikuti **urutan penutup**:
  1. Dari **Saku Sisa** lebih dulu (Saku Sisa tidak boleh jadi minus karena aksi ini).
  2. Kekurangannya dari **Tabungan**, lalu **Dana Darurat** → layar merah, peringatan keras, **tahan tombol 3 detik**. Tercatat sebagai ambil kantong alasan DARURAT (mempengaruhi verdict).
- **R-24 Pergantian bulan:** hutang dibawa ke bulan baru (default) dan tetap dilunasi oleh hemat hari berikutnya. Saat Tutup Buku, pengguna boleh memilih melunasi pakai Saku Sisa.
- **R-25** Hari sebelum pengguna mulai memakai app (sebelum tanggal mulai onboarding) tidak dihitung.
- **R-26 Slot Makan:** Sarapan / Siang / Malam / Jajan. Default otomatis dari jam input: `< 10:00` Sarapan, `10:00–14:59` Siang, `≥ 15:00` Malam. Jajan hanya dipilih manual. Jika tanggal yang dipilih "kemarin", default Malam. Slot hanya untuk rincian; perhitungan jatah memakai total harian.
- **R-27 Hutang besar:** jika hutang suatu pos > 3× jatah hariannya, tampilkan peringatan di tile, layar input, dan notifikasi 22:00, dengan saran **Mode Darurat**.

### 6.4 Pos STOK (Transport OE, Protein, Lain-lain)

- **R-30** Budget bulanan. Tidak ada jatah harian dan tidak ada hutang.
- **R-31** Pemakaian melebihi budget → kelebihannya **mengurangi Saku Sisa** saat itu juga (boleh sampai minus, dengan peringatan merah di layar input).
- **R-34 Status STOK:** `lebih` jika terpakai > budget; `waspada` jika terpakai ≥ 80% budget; selain itu `normal`. Transport memakai status per akhir pekan (R-37).

**Transport (mode akhir pekan)**

- **R-35 Jendela akhir pekan** = Jumat 00:00 – Minggu 23:59. Jendela dimiliki bulan tempat **Sabtu**-nya berada (termasuk jika Jumat/Minggu jatuh di bulan lain). Jendela tertutup pukul 00:00 hari Senin.
- **R-36** Akhir pekan ke-1 s/d ke-4 bulan itu masing-masing punya jatah `J` dari budget Transport. Akhir pekan ke-5 dibiayai **reservasi Saku Sisa** (R-15).
- **R-37 Saat jendela tertutup (Senin 00:00):**
  - Akhir pekan ke-1–4: `delta = J − terpakai(jendela)`. `delta ≥ 0` → Saku Sisa += delta (termasuk seluruh `J` jika tidak pulang). `delta < 0` → Saku Sisa −= |delta|.
  - Akhir pekan ke-5: pengeluarannya mengurangi Saku Sisa langsung; reservasi dilepas saat jendela tertutup.
  - Selama jendela masih terbuka, pengeluaran hanya mengurangi jatah akhir pekan itu (belum menyentuh Saku Sisa).
  - Status tile: di dalam jendela "Akhir pekan ini: Rp X / Rp J"; di luar jendela "Trip n dari S(M)".
- **R-38 Trip tambahan:** transaksi Transport di luar jendela akhir pekan langsung mengurangi Saku Sisa (tidak memakai budget Transport).
- **R-33** Pada hari Jumat, Sabtu, dan Minggu, tile Transport pindah ke posisi pertama di Beranda.
- **R-32** Tutup Buku bulan M baru bisa diselesaikan setelah jendela akhir pekan terakhir milik M tertutup. Jika belum, langkah 1 menampilkan "Menunggu akhir pekan selesai (Senin)".

**Protein & Lain-lain**

- **R-39** Sisa budget Protein dan Lain-lain dicairkan ke Saku Sisa pada **hari terakhir bulan** (saat hari itu tertutup), sebelum Tutup Buku. Protein sengaja tidak dicairkan per pembelian karena selisihnya adalah buffer untuk hari yang tidak tertutup.

### 6.5 Pos TETAP (Iuran Mess, AI)

- **R-40** Saat split, estimasi dicadangkan dengan status **BELUM BAYAR**. Tidak ada input harian.
- **R-41** Pada tanggal jatuh tempo (09:00) muncul notifikasi "AI jatuh tempo. Bayar berapa?" dengan nominal estimasi terisi. Pengguna konfirmasi atau ubah nominal → status LUNAS.
- **R-42** `selisih = estimasi − aktual` masuk ke Saku Sisa (positif menambah, negatif mengurangi).
- **R-43** Belum dibayar saat Tutup Buku → ditanya: "Sudah dibayar (nominal?)" atau "Tidak jadi" (estimasi kembali ke Saku Sisa).

### 6.6 Saku Sisa

- **R-51** Preview di layar input untuk transaksi yang mengurangi Saku Sisa: jika membuat SisaBebas < 0 → "Ini memakai cadangan akhir bulan" (kuning) + tombol cepat **Pakai Tabungan (Rencana)** (R-59). Jika membuat Saku Sisa < 0 → "Saku Sisa minus — akhir bulan akan memotong Tabungan" (merah). Pengguna tetap boleh menyimpan.
- **R-52** Saku Sisa minus **tidak** otomatis mengambil Tabungan selama bulan berjalan. Penyelesaiannya di Tutup Buku (R-72). Tersedia aksi manual "Tutup sekarang" (urutan penutup, tahan 3 detik).

### 6.7 Tabungan & Dana Darurat

- **R-55** Setoran Tabungan: Nabung rutin (saat split), sisa bulan lalu (Tutup Buku), pemasukan bertujuan Tabungan (mis. THR), setoran manual. Setoran Dana Darurat: Tutup Buku ("Isi Dana Darurat") dan setoran manual.
- **R-56** Mengambil dari Tabungan atau Dana Darurat wajib memilih alasan:
  - **DARURAT** — masuk hitungan BONCOS.
  - **RENCANA** — tidak dihitung boncos, tetap tampil di laporan.
  - **TANPA_GAJI** — hanya dibuat sistem saat Tutup Buku bulan tanpa gaji; tidak memicu BONCOS.
  Uang yang diambil masuk ke Saku Sisa. Aksi manual selalu memakai tahan tombol 3 detik.
- **R-57** Setelah split, checklist "Sudah transfer Rp X ke rekening tabungan?". Jika belum dicentang, pengingat esok hari 09:00 (maksimal 3 hari berturut-turut).
- **R-58 Urutan penutup** untuk semua kekurangan (Mode Darurat, Saku Sisa minus saat Tutup Buku, talangan, tanpa gaji): **Saku Sisa → Tabungan → Dana Darurat**. Dana Darurat hanya disentuh jika Tabungan sudah 0, dan layarnya menegaskan "Ini dana darurat terakhir lu".
- **R-59 Pakai Tabungan (Rencana)** dari layar input: mengambil dari **Tabungan saja** sebesar kekurangan yang membuat SisaBebas < 0 (tahan 3 detik), lalu menyimpan pengeluaran. Alasan RENCANA, tidak memicu BONCOS. Jika Tabungan tidak cukup, tampilkan pesan dan jangan sentuh Dana Darurat.

### 6.8 Pemasukan tambahan

| Jenis | Tujuan default | Pilihan tujuan |
|---|---|---|
| Pemberian | Saku Sisa | Saku Sisa / Tabungan / Dana Darurat / tambah budget pos STOK |
| Penghasilan sampingan | Saku Sisa | sama |
| THR / Bonus | **Tabungan** | sama |
| Pengembalian ke kategori (reimburse, refund) | pos asal | wajib pilih pos; **mengurangi "terpakai"** pos itu pada tanggal yang dipilih |
| Lainnya | Saku Sisa | sama |

Pemasukan tidak pernah membuka periode baru dan tidak mengubah jatah harian.

### 6.9 Tanggal, edit, dan anti-typo

- **R-60 Saldo selalu dihitung ulang** dari transaksi setiap ada perubahan (lihat 7.2). Transaksi tanggal mundur otomatis memperbarui hutang, Saku Sisa, talangan, dan status.
- **R-61** Input antara **00:00–04:59** → dialog wajib "Untuk hari ini atau kemarin?" (default: Kemarin). Di luar jam itu tanggal otomatis hari ini, dengan chip tanggal yang bisa diganti (date picker dibatasi ke bulan yang belum ditutup).
- **R-62** Setelah simpan: kartu feedback 3 detik + snackbar **Urungkan** 5 detik.
- **R-63** Transaksi di bulan yang belum ditutup bisa diedit/dihapus dari Riwayat.
- **R-64** Bulan yang sudah Tutup Buku = **read-only**. Koreksi dicatat di bulan berjalan sebagai transaksi **Koreksi** (pos + nominal ±, catatan wajib).
- **R-65 Anti-typo:** jika nominal > 3× median 20 transaksi terakhir pos itu (minimal 5 data), minta konfirmasi: "Yakin Rp 450.000? Biasanya ±Rp 45.000".
- **R-66** Tombol "Hari ini beres" hanya menandai hari itu selesai dicatat (menghentikan pengingat). Tidak mempengaruhi hitungan.

### 6.10 Tutup Buku

Dipicu tanggal 1 untuk bulan sebelumnya (notifikasi 07:00 + otomatis terbuka saat app dibuka pertama kali tanggal ≥ 1). Bisa ditunda, tetapi Beranda menampilkan pengingat sampai selesai. Langkah berurutan:

0. **"Ada catatan kemarin yang belum masuk?"** → pintasan ke input bertanggal hari terakhir bulan lalu. Jika jendela akhir pekan terakhir belum tertutup, tunggu (R-32).
1. **Status gaji.** Jika bulan itu tidak pernah punya gaji: tanya "Gajinya masuk tanggal berapa?" (lanjut ke alur Gajian) atau **"Bulan ini tanpa gaji"**. Bulan tanpa gaji: tidak ada alokasi Nabung/TETAP otomatis; `SakuSisaAkhir = bawaan + pemasukan − pengeluaran riil`; minusnya ditutup dengan urutan penutup memakai alasan **TANPA_GAJI**.
2. **Verdict** (R-80) dengan ringkasan angka.
3. **Cocokkan saldo** (bisa dilewati): "Uang pegangan lu sekarang (dompet + rekening + e-wallet, di luar tabungan & dana darurat) berapa?" → dibandingkan dengan uang pegangan hasil hitung (7.3). Selisih kurang → transaksi **Tidak tercatat** (mengurangi Saku Sisa bulan itu). Selisih lebih → transaksi **Selisih lebih** (menambah Saku Sisa). Verdict dihitung ulang dan ditampilkan lagi.
4. **Hutang harian tersisa** (per pos): **Bawa ke bulan baru** (default) / **Lunasi pakai Saku Sisa**.
5. **Pos TETAP belum dibayar** (R-43).
6. **Saku Sisa akhir:**
   - **R-72** Jika minus → **wajib** ditutup dengan urutan penutup (layar merah, tahan 3 detik). Tercatat ambil kantong alasan DARURAT.
   - Jika positif → pilihan: **Isi Dana Darurat** (tampil paling atas jika Dana Darurat < target) / **Semua ke Tabungan** / **Bawa ke bulan depan** / **Split** (atur nominal ke masing-masing tujuan).
7. **Backup keluar HP**: tombol "Kirim backup" (share sheet). Bisa dilewati.

Setelah selesai: bulan berstatus CLOSED, snapshot angka disimpan.

### 6.11 Verdict

Dihitung per bulan setelah langkah Tutup Buku 3–6.

- **R-80** Urutan penentuan:
  1. **TANPA GAJI** jika bulan itu ditandai tanpa gaji (label ini menggantikan verdict lain; tetap tampilkan angka).
  2. **BONCOS Rp X** jika total ambil Tabungan + Dana Darurat alasan DARURAT di bulan itu > 0 (termasuk penutupan Saku Sisa minus dan Mode Darurat). X = total tersebut.
  3. **PAS-PASAN** jika bukan boncos, dan (Saku Sisa akhir < ambang aman **atau** ada hutang harian yang dibawa).
  4. **BERHASIL NABUNG** selain itu. Tampilkan: Nabung rutin + sisa yang dipindah ke Tabungan/Dana Darurat.
- **R-81** Kartu verdict selalu menampilkan: pemasukan (gaji + tambahan), pengeluaran riil, tabungan masuk/keluar, **dana darurat terpakai** (baris terpisah jika > 0), hutang dibawa, hasil cocokkan saldo (jika dilakukan).

### 6.12 Pos baru & arsip

- **R-95** Pengguna bisa menambah pos (jenis STOK atau TETAP) dan mengarsipkan pos di Pengaturan. Berlaku mulai bulan berikutnya. Pos baru muncul di **preview split** Gajian berikutnya untuk ditinjau dan disesuaikan pengguna. Pos yang diarsipkan tetap tampil di laporan dan riwayat lama.

---

## 7. Arsitektur & model data

### 7.1 Struktur modul

```
app/
 ├─ core/engine/      ← Kotlin murni: semua aturan bab 6 + unit test (tanpa import android.*)
 ├─ data/             ← Room (entity, DAO), DataStore, repository, backup JSON
 ├─ feature/          ← layar Compose + ViewModel per fitur
 │   home/ input/ detail/ salary/ closing/ savings/ history/ report/ settings/ onboarding/ lock/
 ├─ notify/           ← AlarmManager, receiver, channel, pesan notifikasi
 ├─ export/           ← PDF & XLSX writer
 ├─ widget/           ← Glance widget
 └─ ui/theme/         ← warna, tipografi, bentuk, komponen bersama
```

### 7.2 Satu sumber kebenaran

- Data tersimpan hanya: **transaksi**, **alokasi per bulan**, **kewajiban pos tetap**, **penutupan bulan**, **pengaturan**, **tanda hari**.
- `core/engine` menyediakan fungsi murni:
  - `computeLedger(input: LedgerInput, today: LocalDate): LedgerState` — memutar ulang semua transaksi secara kronologis sejak tanggal mulai dan menghasilkan: Saku Sisa, Sisa bebas, reservasi, target & progres cadangan, hutang per pos HARIAN, status tiap pos (termasuk akhir pekan Transport), Tabungan, Dana Darurat, uang pegangan, status gaji (diterima/pending/belum masuk), talangan beserta pembebanan bayangannya, preview verdict.
  - `previewImpact(state, draft): Impact` — dampak transaksi yang sedang diketik (untuk warning sebelum simpan).
  - `planSplit(gaji, bulanTarget, alokasi, bawaan): SplitPlan` — rincian alokasi, kebutuhan Penyesuaian, target cadangan beserta alasannya.
  - `computeVerdict(...)`, `countSaturdays(ym)`, `weekendWindowOf(date)`, `defaultSlot(time)`, `isSuspiciousAmount(...)`, `coverShortfall(amount, pockets)`.
- Repository memanggil `computeLedger` setiap data berubah dan mengekspos hasilnya sebagai `StateFlow`. Data satu pengguna kecil (ribuan baris), jadi hitung ulang penuh itu aman. Boleh di-cache per bulan tertutup.

### 7.3 Jenis transaksi & efeknya

Semua `amount` positif (`Long`). Arah ditentukan jenis. Kolom `pot` bernilai `TABUNGAN` atau `DANA_DARURAT`.

| Jenis (`TxType`) | Contoh | Uang pegangan | Kantong (`pot`) | Efek anggaran |
|---|---|---|---|---|
| `SALARY` | Gaji | + | | Sumber alokasi bulan target (pending jika bulan belum mulai) |
| `INCOME` | Pemberian, sampingan, THR | + (jika tujuan bukan kantong) | + (jika tujuan kantong) | Tujuan Sisa → Saku Sisa +; tujuan pos STOK → budget pos + |
| `REFUND` | Pengembalian ke kategori | + | | "terpakai" pos asal − pada tanggalnya |
| `EXPENSE` | Pengeluaran pos HARIAN/STOK | − | | HARIAN → terpakai hari itu; STOK → terpakai bulan/jendela; Transport di luar jendela → Saku Sisa − |
| `FIXED_PAYMENT` | Bayar Iuran Mess / AI | − | | Pos tetap LUNAS; selisih estimasi → Saku Sisa (R-42) |
| `SAVING_DEPOSIT` | Nabung rutin, sisa → kantong, setor manual | − | + | Nabung rutin: bagian alokasi. Lainnya: Saku Sisa − |
| `SAVING_WITHDRAW` | Ambil kantong (`reason`: DARURAT/RENCANA/TANPA_GAJI) | + | − | Saku Sisa + |
| `DEBT_PAYOFF` | Mode Darurat dari Saku Sisa | | | Hutang pos − ; Saku Sisa − |
| `CARRY_OVER` | Bawa sisa ke bulan depan | | | Saku Sisa(M) − ; Saku Sisa(M+1) + |
| `UNRECORDED` | Selisih kurang saat cocokkan saldo | − | | Saku Sisa − |
| `SURPLUS_FOUND` | Selisih lebih saat cocokkan saldo | + | | Saku Sisa + |
| `CORRECTION` | Koreksi bulan tertutup (`signedAmount`) | ∓ | | Terpakai pos di bulan berjalan ± |

- Mode Darurat yang memakai kantong = `SAVING_WITHDRAW(DARURAT)` per kantong sesuai urutan penutup, lalu `DEBT_PAYOFF`, dalam satu transaksi database.
- Talangan (R-04) **bukan** transaksi; hanya hasil `computeLedger`.
- `uangPegangan = uangAwal(onboarding) + Σ efek "Uang pegangan"`. Pos TETAP yang belum dibayar dan Saldo Pending **termasuk** uang pegangan. Tabungan dan Dana Darurat **tidak** termasuk.

### 7.4 Entity Room (bentuk minimal, boleh disempurnakan tanpa mengubah makna)

- `category` — id, key, name, kind (`DAILY`, `STOCK`, `FIXED`, `SAVING`), weekendMode (khusus Transport), iconKey, colorKey, sortOrder, active, archivedFrom?, dailyAmount?, monthlyAmount?, dueDay?, hasSlots, effectiveFrom
- `month_plan` — yearMonth (PK), salaryTxId?, noSalary (bool), status (`OPEN`/`CLOSED`), createdAt. **Tanpa** field target/reservasi/sisa awal — semuanya turunan (R-13).
- `month_allocation` — yearMonth, categoryId, dailyAmount?, monthlyAmount (alokasi bulan itu, termasuk hasil Penyesuaian)
- `tx` — id, date, createdAt, updatedAt, type, categoryId?, amount, signedAmount?, slot?, note?, destination?, pot?, reason?, refYearMonth?
- `fixed_obligation` — yearMonth, categoryId, estimate, status (`UNPAID`/`PAID`/`CANCELLED`), paidTxId?
- `day_mark` — date (PK), doneMarked
- `month_closure` — yearMonth (PK), verdict, verdictAmount, snapshotJson, reconciledActual?, closedAt
- DataStore: gaji template, perkiraan tanggal gajian, jam notifikasi, ambang aman, target Dana Darurat, nama panggilan, tema, tanggal mulai, uang awal, tabungan awal, dana darurat awal, hash & salt PIN, batas waktu kunci, URI folder backup, checklist transfer, flag onboarding

---

## 8. Layar & alur

Mockup: `design/Main.dc.html` (Beranda), `design/Input.dc.html` (Input), `design/DetailMakan.dc.html` (Detail Harian). Layar lain mengikuti gaya yang sama.

### 8.1 Beranda (`design/Main.dc.html`)
- **Header:** tanggal lengkap ("Senin, 28 September") + "Halo, {nama}" + tombol notifikasi (daftar pengingat aktif).
- **Kartu hero (gradien biru):** Saku Sisa (angka besar) + label bulan. Dua chip: "Hutang makan" (total hutang harian; sembunyikan jika 0) dan "Tutup buku · n hari lagi".
  - Jika Target Cadangan > 0: tambah baris "Cadangan: terkumpul X / Y" + progress bar.
  - **Mode talangan** (gaji bulan ini belum masuk): angka besar diganti "Ditalangi Rp X" dengan rincian bayangan per kantong (R-04) dan tombol "Gaji sudah masuk".
  - Jika ada Saldo Pending: chip "Gaji {bulan} aman · aktif tgl 1".
  - Warna status Sisa bebas (R-16) wajib disertai teks.
- **Banner Gajian (emas):** tampil mulai tanggal 27 sampai gaji bulan depan diinput, **atau** saat bulan berjalan belum punya gaji. Teks kedua memuat Target Cadangan bulan depan jika > 0.
- **Baris "Catat pengeluaran"** + tombol **Pemasukan** (hijau muda).
- **Grid kategori** 2 kolom: Makan, Buah, Transport OE, Protein; Lain-lain lebar penuh. Jumat–Minggu: Transport di posisi pertama (R-33). Setiap tile: ikon berwarna, nama, satu baris status (teks), progress bar tipis, badge opsional (Hutang, Trip n/S, Stok, peringatan hutang besar).
- **Bottom nav:** Beranda · Riwayat · **[+] hijau tengah** · Laporan · Pengaturan. Tombol + membuka pemilih kategori (sheet berisi tile pos aktif) dari tab mana pun.
- Banner Tutup Buku (jika tertunda) muncul di atas grid.

### 8.2 Input pengeluaran (`design/Input.dc.html`) — bottom sheet
Urutan: handle → header (ikon pos, nama, sisa jatah/budget, chip tanggal) → slot (hanya Makan) → nominal besar → **kotak preview dampak** → chip nominal cepat → catatan opsional → numpad (1–9, 000, 0, hapus) → tombol "Simpan · Rp X".
- Chip nominal cepat: 4 nominal paling sering pos itu (60 hari terakhir). Fallback: Makan 10/15/20/25rb · Buah 5/10rb · Transport 60/120rb · Protein 93/100rb · Lain-lain 10/20/50/100rb.
- Preview dampak (contoh): HARIAN → "Lebih Rp 10.000 dari jatah — hutang makan jadi Rp 25.000"; Transport dalam jendela → "Akhir pekan ini jadi Rp X / Rp 120.000"; Transport di luar jendela → "Trip tambahan — diambil dari Saku Sisa"; STOK lewat budget → "Melebihi budget Rp X — diambil dari Saku Sisa". Ditambah R-51 dan tombol cepat R-59 bila berlaku, serta R-27.
- R-61 (tanya hari ini/kemarin), R-65 (anti-typo), R-62 (feedback + urungkan).
- Kartu feedback setelah simpan: "Makan +Rp 25.000 · Hari ini 60rb / 50rb · Hutang Rp 25.000" dengan warna + ikon status.

### 8.3 Detail pos HARIAN (`design/DetailMakan.dc.html`, tema gelap)
Gauge setengah lingkaran (terpakai vs jatah hari ini, angka besar = sisa jatah) → rincian per slot → kartu hutang (nominal, kalimat **"Tahan di Rp X sampai akhir hari — hutang lunas saat hari ditutup tengah malam."** jika sisa jatah hari ini ≥ hutang, tombol **Mode darurat**) → grafik batang 7 hari terakhir dengan garis putus-putus jatah dan label "+X" pada hari lebih → statistik bulan: hari hemat, hari lebih, total ke Saku Sisa → daftar transaksi hari ini.
Dibuka dengan tap lama pada tile, atau tap kartu feedback.

### 8.4 Detail pos STOK
Tema terang. Budget, terpakai, sisa, status, daftar transaksi bulan ini.
Khusus Transport: daftar akhir pekan bulan ini ("Sab 3 Okt · Rp 110.000 · +10.000 ke Sisa", "Sab 31 Okt · dari reservasi"), trip tambahan (di luar jendela), "Trip n dari S(M)".

### 8.5 Pemasukan (sheet)
Pilih jenis (6.8) → nominal (numpad sama) → tujuan (default sesuai jenis) → catatan → simpan.

### 8.6 Gajian (layar penuh, 4 langkah)
1. **Nominal** (terisi gaji terakhir) + tanggal terima (default hari ini, bisa mundur) + bulan target (R-02).
2. **Penyesuaian** — hanya jika R-06 terpicu.
3. **Preview split:** daftar pos dengan rumus terlihat ("Makan · 50.000 × 31 hari = 1.550.000"), pos baru ditandai "Baru" (R-95), total, **Saku Sisa awal**, kartu kuning Target Cadangan (R-14) jika ada. Tombol **Konfirmasi**.
4. **Checklist:** "Transfer Rp 160.000 ke rekening tabungan" (R-57). Selesai → kembali ke Beranda dengan feedback "Gaji Oktober tersimpan · aktif 1 Okt" (atau "Talangan Rp X dikembalikan" jika gaji telat).

### 8.7 Tutup Buku (layar penuh, stepper sesuai 6.10)
Setiap langkah satu layar dengan satu keputusan utama. Kartu verdict besar di langkah 2 dan di akhir.

### 8.8 Tabungan & Dana Darurat
Dua kartu: **Tabungan** (ungu) dan **Dana Darurat** (dengan progres terhadap target). Riwayat masuk/keluar per kantong. Tombol **Setor** dan **Ambil** per kantong (R-56, tahan 3 detik, layar merah untuk DARURAT, peringatan tambahan untuk Dana Darurat).

### 8.9 Riwayat
Pemilih bulan → daftar transaksi dikelompokkan per tanggal (header tanggal + total hari). Filter chip per pos. Tap → sheet edit (R-63). Bulan tertutup: badge "Terkunci", aksi edit diganti "Buat koreksi" (R-64).

### 8.10 Laporan
Tab **Mingguan** | **Bulanan** + tombol **Export** di kanan atas.
- **Mingguan (Senin–Minggu):** total per pos; grafik garis harian Makan & Buah vs jatah; hasil akhir pekan Transport; hari paling boros; perbandingan dengan minggu lalu (naik/turun %); hutang awal→akhir minggu; perubahan Saku Sisa.
- **Bulanan:** kartu verdict (untuk bulan berjalan tampil "sementara"); donut per pos + chip legenda; tabel budget vs realisasi; aliran Tabungan & Dana Darurat; hutang; statistik hari hemat/lebih; hasil cocokkan saldo.

### 8.11 Pengaturan
Nama panggilan · pos & nominal (berlaku bulan depan) · **tambah/arsip pos** (R-95) · tanggal jatuh tempo pos tetap · perkiraan tanggal gajian · jam notifikasi · ambang aman · target Dana Darurat · **ganti PIN & batas waktu kunci** · tema terang/gelap · folder auto-backup · Export / Backup / Pulihkan · panduan izin notifikasi & optimasi baterai · versi app.
- **Mode Uji Tanggal (tersembunyi):** ketuk "versi app" 7× → bisa memajukan "hari ini" untuk menguji Gajian, Tutup Buku, akhir pekan, dan notifikasi. Selama aktif, banner merah "MODE UJI" tampil di semua layar. Semua data yang dibuat saat mode uji diberi tanda dan **dihapus otomatis** saat mode dimatikan (data asli tidak tersentuh).

### 8.12 Onboarding
Layar pertama: dua tile **[Mulai Baru]** / **[Pulihkan dari Backup]**.
Mulai Baru:
1. Nama panggilan (default "Ko").
2. **Buat PIN 4 digit** (ketik dua kali).
3. Konfirmasi pos & nominal default (bagian 5), bisa diubah.
4. Saldo awal: "Uang pegangan lu sekarang berapa?", "Tabungan sekarang berapa?" (contoh 500.000), "Dana darurat sekarang berapa?" (contoh 1.000.000).
5. **Periode awal** (mulai di tengah bulan): jatah HARIAN dihitung dari hari ini sampai akhir bulan; Transport = jumlah akhir pekan tersisa (termasuk yang sedang berjalan, maks 4) × J; Protein & Lain-lain diprorata `budget × sisaHari ÷ D(M)` (dibulatkan ke ribuan); pos TETAP bulan ini dianggap sudah dibayar; `SakuSisaAwal = uangPegangan − (jatah harian sisa bulan + Transport + STOK prorata)`. Jika minus, tampilkan warning (R-14 berlaku).
6. Izin notifikasi → panduan mematikan optimasi baterai untuk app ini (bahasa sederhana, sebut Xiaomi/Oppo/Vivo/Realme).
7. Pilih folder auto-backup (boleh dilewati).

### 8.13 Komponen bersama
- **HoldToConfirmButton:** tahan 3 detik dengan cincin progres; lepas sebelum selesai = batal; getar singkat saat berhasil. Wajib untuk semua aksi yang mengurangi Tabungan atau Dana Darurat.
- **AmountText:** format `Rp 45.000`; versi ringkas `Rp 45rb`, `Rp 1,05 jt`. Angka tabular.
- **StatusPill:** warna + ikon + teks.
- **FeedbackCard**, **Numpad**, **CategoryTile**, **QuickAmountChips**, **DateChip**, **ShadowAmount** (angka bergaris putus-putus untuk porsi talangan).

### 8.14 Kunci PIN
- PIN 4 digit, wajib diset saat onboarding.
- Diminta saat app dibuka setelah di-background ≥ batas waktu (default 5 menit), dan untuk input dari widget/notifikasi saat terkunci.
- Numpad sama dengan layar input. 5× salah → jeda 30 detik (bertambah dua kali lipat setiap 5× salah berikutnya).
- Tautan "Lupa PIN?" menjelaskan: satu-satunya cara adalah instal ulang lalu **Pulihkan dari Backup**. File backup tidak terenkripsi, jadi simpan di tempat aman.
- Layar lain (Recent apps) memakai `FLAG_SECURE` saat terkunci agar isi tidak terlihat di pratinjau.

---

## 9. Notifikasi

Channel terpisah: Harian, Pengingat, Tutup Buku. Semua jadwal pakai waktu lokal dan dijadwal ulang saat boot/perubahan waktu. Semua notifikasi memakai **`VISIBILITY_PRIVATE`** dengan versi publik tanpa nominal (layar kunci hanya menampilkan "Catat Uang · ada info baru").

| Kapan | Isi | Aksi |
|---|---|---|
| **Setiap hari 22:00** (satu notifikasi) | Jika sudah ada catatan: preview status Makan & Buah hari ini (hemat/pas/lebih + nominal), hutang (+ peringatan R-27), Saku Sisa. Jika belum ada catatan: "Belum ada catatan hari ini." | Tombol **Makan**, **Transport**, **Lainnya** (membuka input) + **Hari ini beres** |
| Minggu 22:00 | Notifikasi harian di atas ditambah satu baris ringkasan minggu | Buka laporan mingguan |
| Tanggal jatuh tempo pos TETAP, 09:00 | "AI jatuh tempo. Bayar berapa?" | Buka konfirmasi pembayaran |
| Tanggal 29, 12:00 (jika gaji bulan depan belum diinput) | "Gaji sudah masuk?" | Buka Gajian |
| Tanggal 1, 07:00 | "{Bulan} selesai. Yuk tutup buku." | Buka Tutup Buku |
| H-5 akhir bulan, 09:00 (jika cadangan kurang) | R-14 butir 4 | Buka Beranda |
| Esok hari 09:00 (checklist belum dicentang, maks 3×) | "Udah transfer Rp 160.000 ke tabungan?" | Tandai selesai |

---

## 10. Laporan & export

Menu Export: rentang (**Minggu ini** / **Bulan [pilih]** / **Custom**) → format (**PDF** / **Excel**) → share sheet atau simpan (SAF).

- **PDF** (A4 portrait, `PdfDocument`): judul & rentang, kartu verdict (untuk bulan), ringkasan per pos, grafik batang sederhana, status hutang, cadangan, Tabungan & Dana Darurat, daftar transaksi (paginasi otomatis). Font dan warna sesuai tema terang.
- **Excel (.xlsx)**, sheet:
  1. `Ringkasan` — pemasukan, pengeluaran, Tabungan, Dana Darurat, verdict, cadangan
  2. `Transaksi` — tanggal, jenis, pos, slot, nominal, catatan
  3. `Per Kategori` — budget, terpakai, sisa, status
  4. `Harian Makan & Buah` — tanggal, jatah, terpakai, selisih, hutang akhir hari, ke Saku Sisa
  5. `Akhir Pekan Transport` — tanggal Sabtu, jatah, terpakai, ke/dari Saku Sisa
- Nama file: `catatuang-{rentang}-{yyyyMMdd}.pdf|xlsx`. Nominal ditulis sebagai angka (bukan teks) di Excel.

---

## 11. Backup & pulihkan

- **Format:** JSON `{ schemaVersion, appVersion, exportedAt, settings, categories, monthPlans, allocations, transactions, fixedObligations, dayMarks, closures }`. Hash PIN **tidak** ikut di-backup; setelah pulihkan, pengguna membuat PIN baru. Nama: `catatuang-backup-yyyyMMdd-HHmm.json`.
- **Auto-backup mingguan** (Minggu 23:00) ke folder pilihan pengguna (URI persisten SAF). Simpan 8 file terakhir, hapus yang lebih lama **di folder itu saja**.
- **Kirim backup** manual (share sheet) tersedia di Pengaturan dan di langkah terakhir Tutup Buku.
- **Pulihkan:** pilih file → validasi `schemaVersion` & struktur → preview ("8 bulan data · 1.240 transaksi · terakhir 23 Sep 2026") → konfirmasi → **ganti seluruh data** dalam satu transaksi database → buat PIN baru → langsung ke Beranda. Tanpa login.
- Migrasi skema Room wajib ditulis (tanpa `fallbackToDestructiveMigration`). Import backup versi lama harus didukung lewat migrasi JSON.
- `android:allowBackup="true"` tetap aktif sebagai lapisan tambahan, tapi tidak diandalkan.

---

## 12. Design system

Sumber: referensi desain pengguna (gaya dashboard keuangan biru) + mockup di `design/`.

### Warna — tema terang
| Token | Nilai | Pakai |
|---|---|---|
| `primary` | `#3563E9` | Tombol utama, progress normal |
| `primaryGradient` | `#3E6BF2 → #1E3A9E` (135°) | Kartu hero |
| `success` | `#17865A` (teks/isi), `#DDF5EA` (latar) | Hemat, tombol +, pemasukan |
| `warning` | `#F2A93B` (isi), `#8A5200` (teks), `#FFF1D6` (latar) | Waspada, cadangan |
| `danger` | `#D93A5A` / `#B3243F` (teks), `#FDE2E7` (latar) | Lebih, hutang, minus |
| `savings` | `#6A3FD0`, `#EFE7FF` | Tabungan |
| `emergency` | `#0E7490` (teks/isi), `#DDF3F8` (latar) | Dana Darurat |
| `gold` | `#F2C14E`, `#FFF4DB`, teks `#7A5200`, tombol `#8A5A00` | Banner Gajian |
| `background` | `#EEF0F8` | Latar layar |
| `surface` | `#FFFFFF` | Kartu |
| `textPrimary` / `textSecondary` | `#1E1E2D` / `#5F6478` | Teks |
| `divider` | `#D5D8E4` | Garis, border input |

### Warna — tema gelap (dipakai di layar Detail Harian, dan seluruh app jika tema gelap aktif)
`background #12152A` · `surface #1C2140` · `track #2C3360` · `text #F2F4FF` · `textSecondary #A3A9C7` · `primary #5B8CFF` · `success #3DD598` · `danger #FF6B81` / teks `#FF8FA3` · `dangerSurface #2B1C30`.

### Ikon pos (latar / ikon)
Makan `#FFE9D6 / #B85A00` · Buah `#DDF5EA / #17865A` · Transport `#E3E9FF / #3563E9` · Protein `#EFE7FF / #6A3FD0` · Lain-lain `#EEF0F4 / #5F6478`. Pos baru memilih dari palet ini.

### Tipografi (Plus Jakarta Sans)
Angka hero 34sp/800 · angka input 48sp/800 · judul layar 22sp/800 · judul kartu 15–16sp/700 · isi 13–14sp/500 · caption minimal 11–12sp/600. Semua angka uang memakai **tabular figures** (`fontFeatureSettings = "tnum"`).

### Bentuk & jarak
Radius: hero 24dp · kartu 20dp · sheet 28dp (atas) · tombol utama 16dp · chip/input 12dp. Padding layar 20dp. Grid 8dp. Bayangan halus (`y 4dp, blur 14dp, alpha 6%`). **Target sentuh minimal 44dp**; tombol numpad 50dp.

### Aksesibilitas
Kontras teks ≥ 4.5:1. Semua tombol ikon punya `contentDescription`. Status selalu teks + warna.

---

## 13. Test wajib (`core/engine`)

Semua lulus sebelum UI terkait dibuat. Nilai dalam rupiah, konfigurasi default bagian 5 kecuali disebut lain.

| ID | Skenario | Hasil yang diharapkan |
|---|---|---|
| T-01 | Hutang Makan: Senin pakai 65.000, Selasa 40.000, Rabu 35.000 (jatah 50.000, hutang awal 0) | Senin: hutang 15.000, Sisa +0 · Selasa: hutang 5.000, Sisa +0 · Rabu: hutang 0, Saku Sisa +10.000 |
| T-02 | **Oktober 2026**, gaji 3.300.000 | D=31, S=5, Kebutuhan 3.360.000, SakuSisaAwal −60.000, TripTambahan 120.000, **Target 180.000**, Penyesuaian **tidak** terpicu |
| T-03 | **November 2026**, gaji 3.300.000 | D=30, S=4, Kebutuhan 3.300.000, SakuSisaAwal 0, Target 0 |
| T-04 | **Februari 2027**, gaji 3.300.000 | D=28, S=4, Kebutuhan 3.180.000, SakuSisaAwal +120.000 |
| T-05 | Oktober 2026, gaji 3.280.000 | Penyesuaian terpicu (KebutuhanStandar 3.300.000); usulan tidak menyentuh pos TETAP; Nabung dipotong terakhir |
| T-06 | Gaji diterima 29 Sep 2026 | Bulan target Oktober; pending sampai 1 Okt; Saku Sisa September tidak berubah |
| T-07 | Split Oktober pada 29 Sep, Tutup Buku September belum | Target 180.000 + keterangan "bisa berubah"; setelah Tutup Buku September dengan bawa 100.000 → Target 80.000 |
| T-08 | Makan lebih 20.000 pada 31 Okt, 1 Nov hemat 15.000 | Hutang terbawa ke November; 1 Nov hutang jadi 5.000 |
| T-09 | Mode Darurat hutang 30.000; Saku Sisa 20.000; Tabungan 500.000 | Saku Sisa 0, Tabungan 490.000 (SAVING_WITHDRAW DARURAT 10.000), hutang 0; verdict BONCOS 10.000 |
| T-10 | Transport 5 akhir pekan di Oktober 2026 @120.000 | Akhir pekan 1–4: Saku Sisa ±0; akhir pekan 5: Saku Sisa −120.000, reservasi 0; SisaBebas tidak berubah akibat akhir pekan 5 |
| T-11 | Transport Sabtu 31 Okt + Minggu 1 Nov 2026 | Keduanya masuk akhir pekan ke-5 Oktober; Tutup Buku Oktober baru bisa selesai Senin 2 Nov |
| T-12 | Tutup Buku, Saku Sisa −40.000, Tabungan 500.000 | Wajib tutup: Tabungan 460.000 (DARURAT 40.000); verdict BONCOS 40.000 |
| T-13 | Saku Sisa akhir 30.000, tanpa hutang (ambang 50.000) | PAS-PASAN |
| T-14 | Saku Sisa akhir 120.000, tanpa hutang, tanpa ambil DARURAT | BERHASIL NABUNG; tampil 160.000 + sisa yang dipindah |
| T-15 | Ambil Tabungan alasan RENCANA 300.000 | Tidak memicu BONCOS |
| T-16 | Pos TETAP AI estimasi 390.000, dibayar 405.000 | Saku Sisa −15.000 |
| T-17 | Cocokkan saldo: hitung 250.000, aktual 185.000 | UNRECORDED 65.000; Saku Sisa −65.000; verdict dihitung ulang |
| T-18 | Transaksi kemarin ditambahkan setelah hari tertutup | Hutang & Saku Sisa dihitung ulang dengan benar (R-60) |
| T-19 | `defaultSlot`: 07:30, 12:00, 19:00; tanggal kemarin | Sarapan, Siang, Malam; Malam |
| T-20 | Input pukul 00:30 | Wajib tanya hari ini/kemarin (R-61) |
| T-21 | Anti-typo: median 45.000, input 450.000 | Perlu konfirmasi; input 90.000 → tidak |
| T-22 | Pengembalian 60.000 ke Protein | Terpakai Protein −60.000 |
| T-23 | THR 3.000.000 dengan tujuan default | Tabungan +3.000.000; Saku Sisa tetap |
| T-24 | Onboarding 16 Sep 2026 (D=30), uang pegangan 900.000 | Jatah harian 15 hari; Transport = akhir pekan tersisa × 120.000; Protein & Lain-lain prorata 15/30; SakuSisaAwal sesuai rumus 8.12 |
| T-25 | Round-trip backup: ekspor lalu impor | Seluruh `LedgerState` identik |
| T-26 | Bawaan September 60.000, Tabungan 500.000, Dana Darurat 1.000.000; gaji Oktober belum masuk; pengeluaran 1–2 Okt 90.000 | Tampilan talangan 90.000: Sisa bawaan 0 (60.000 ditalangi), Tabungan 470.000 (30.000 ditalangi); **tanpa** SAVING_WITHDRAW. Gaji 3.300.000 diinput 3 Okt → Tabungan 500.000; Saku Sisa Oktober termasuk bawaan 60.000; seluruh saldo identik dengan kondisi gaji masuk tepat waktu |
| T-27 | Talangan melebihi Sisa + Tabungan + Dana Darurat | Status peringatan "Dana talangan habis"; pencatatan tetap bisa |
| T-28 | Tutup Buku bulan tanpa gaji, pengeluaran riil 800.000, bawaan 100.000, Tabungan 500.000, Dana Darurat 1.000.000 | Kekurangan 700.000 ditutup: Tabungan 0 (TANPA_GAJI 500.000), Dana Darurat 800.000 (TANPA_GAJI 200.000); verdict TANPA GAJI, bukan BONCOS |
| T-29 | Protein terpakai 186.000 (budget 200.000) sampai akhir bulan | Saku Sisa +14.000 saat hari terakhir tertutup |
| T-30 | Hutang Makan 160.000 (jatah 50.000) | Status peringatan hutang besar (R-27) |
| T-31 | Transport hari Jumat | Masuk akhir pekan yang sama dengan Sabtu berikutnya |
| T-32 | Pakai Tabungan (Rencana) dari layar input | SAVING_WITHDRAW(RENCANA); tidak memicu BONCOS; Dana Darurat tidak tersentuh |
| T-33 | Akhir pekan tanpa transaksi Transport (akhir pekan ke-1–4) | Senin 00:00: Saku Sisa +120.000 |
| T-34 | Akhir pekan dengan Transport 105.000 | Senin 00:00: Saku Sisa +15.000 |
| T-35 | Transport hari Rabu 50.000 | Trip tambahan: Saku Sisa −50.000; budget Transport tidak berubah |
| T-36 | Kekurangan 700.000 ditutup (DARURAT), Saku Sisa 100.000, Tabungan 500.000, Dana Darurat 1.000.000 | Saku Sisa 0, Tabungan 0, Dana Darurat 900.000; BONCOS 600.000 |
| T-37 | Lain-lain terpakai 170.000 (budget 150.000) | Saku Sisa −20.000 saat transaksi yang melewati budget |
| T-38 | Tutup Buku, Saku Sisa +200.000, Dana Darurat 900.000 (target 1.000.000) | Opsi "Isi Dana Darurat" tampil paling atas |

Tambahkan test lain bila menemukan kasus pinggiran. Test UI minimal: navigasi 4 tap dari Beranda sampai tersimpan; kunci PIN (5× salah → jeda 30 detik).

---

## 14. Roadmap per fase

Setiap fase: kode dikomit ke git, test lulus, APK rilis ter-build dan ter-install di HP pengguna.

| Fase | Isi | Selesai jika |
|---|---|---|
| **0. Fondasi** | Proyek Gradle, tema & font, navigasi kosong, **keystore rilis** (`keystore.properties` di luar git), build `assembleRelease` | APK rilis ter-install; berkas keystore + password sudah disimpan pengguna |
| **1. Mesin** | `core/engine` + seluruh test bab 13, Room + DataStore + repository | Semua test lulus |
| **2. Harian** | Onboarding (Mulai Baru + PIN), kunci PIN, Beranda, Input, Detail Harian, Detail Stok (termasuk akhir pekan Transport), Riwayat (edit/hapus/urungkan) | Bisa dipakai mencatat sehari-hari |
| **3. Uang masuk** | Gajian (4 langkah), talangan, Pemasukan, Tabungan & Dana Darurat, Pos TETAP, warning cadangan, Pakai Tabungan (Rencana) | Siklus gaji bisa dijalankan |
| **4. Tutup Buku** | Stepper 6.10 (termasuk tanpa gaji), cocokkan saldo, verdict, bulan read-only, Koreksi, Mode Uji Tanggal (8.11) | Satu bulan bisa ditutup |
| **5. Notifikasi** | Semua jadwal bab 9, aksi dari notifikasi, reschedule boot, visibilitas privat | Notif 22:00 muncul tepat di HP pengguna |
| **6. Laporan** | Mingguan, Bulanan, Export PDF & XLSX | File terbuka normal di HP & laptop |
| **7. Keamanan data** | Backup JSON, auto-backup, Pulihkan (termasuk dari onboarding), migrasi | Instal ulang → pulihkan → data utuh |
| **8. Polish** | Widget Glance, tema gelap, tambah/arsip pos (R-95), anti-typo, uji aksesibilitas | — |

---

## 15. Di luar cakupan

**Tidak dibuat:** cloud sync, login/akun, multi-pengguna, multi-mata uang, integrasi bank/e-wallet, sinkron Google Calendar, iklan, analytics, biometrik.

**Backlog (prioritas rendah, setelah Fase 8):** Pinjaman — `LOAN_OUT` (minjemin: uang pegangan −, tidak dihitung pengeluaran) dan `LOAN_RETURN` (uang pegangan +), dengan nama peminjam dan daftar piutang terbuka.
