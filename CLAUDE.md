# CLAUDE.md — Catat Uang

Spesifikasi lengkap aplikasi Android pribadi untuk mencatat pengeluaran. Dokumen ini adalah **sumber kebenaran** proyek.

## 0. Cara Claude Code memakai dokumen ini

- Baca dokumen ini **utuh** sebelum menulis kode. Kerjakan **per fase** (bagian 14). Jangan mengerjakan fase berikutnya sebelum diminta.
- **Aturan bisnis (bagian 6) tidak boleh ditafsirkan ulang.** Kalau ada yang ambigu atau saling bertentangan, **berhenti dan tanya** — jangan menebak.
- Semua hitungan uang ada di modul **`core/engine`**: Kotlin murni, tanpa dependensi Android, dan **wajib lulus unit test di bagian 13 sebelum UI yang memakainya dibuat.**
- Jangan menambah fitur, library, izin, atau koneksi internet di luar yang tertulis di sini.
- Referensi visual ada di folder `design/` (3 mockup HTML). File itu **referensi warna, jarak, dan hierarki**, bukan kode untuk dijalankan.
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
5. **Tidak ada pemotongan diam-diam.** Uang tidak pernah berpindah dari Tabungan tanpa aksi eksplisit pengguna (tahan tombol 3 detik).
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
| Font | **Plus Jakarta Sans** (OFL), file TTF dibundel di `res/font` — tidak diunduh saat runtime |
| Ikon | Ikon garis gaya Lucide (lisensi ISC) dikonversi ke `ImageVector`. **Tanpa emoji di UI.** |
| Izin | Hanya `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. **Tanpa izin INTERNET.** |
| Build rilis | APK rilis di-sign dengan keystore pribadi (lihat 14, Fase 0). `versionCode` naik setiap build yang di-install. |

Dilarang: koneksi jaringan, analytics, iklan, login, cloud sync, library berat yang tidak tercantum.

---

## 4. Glosarium

| Istilah | Arti |
|---|---|
| **Periode / Bulan** | Bulan kalender, tanggal 1 sampai akhir bulan (`YearMonth`). Semua cutoff = akhir bulan. |
| **Pos** | Kategori anggaran. Punya satu **jenis** (lihat 5). |
| **Jatah harian** | Nominal per hari untuk pos jenis HARIAN. |
| **Hutang (harian)** | Kelebihan pemakaian pos HARIAN, per pos. Hanya bisa dilunasi dari hemat hari-hari berikutnya pos yang sama (atau Mode Darurat). |
| **Saku Sisa** | Kantong uang bebas bulan berjalan. Diisi dari sisa gaji setelah alokasi dan dari hemat harian. Membiayai Lain-lain dan kelebihan pos STOK. **Boleh minus.** |
| **Reservasi trip** | Bagian Saku Sisa yang dicadangkan untuk trip ke-5 (bulan dengan 5 hari Sabtu). |
| **Sisa bebas** | Saku Sisa − reservasi trip yang belum terpakai. |
| **Target Cadangan** | Kekurangan yang harus dikumpulkan pengguna (lewat hemat) agar Sisa bebas tidak minus di akhir bulan. |
| **Tabungan** | Kantong tabungan (virtual; pengguna yang memindahkan uang sungguhan). |
| **Saldo Pending** | Gaji yang sudah diterima tetapi untuk bulan berikutnya. Belum boleh dipakai. |
| **Talangan** | Pengeluaran di bulan yang gajinya belum masuk. |
| **Tutup Buku** | Alur penutupan bulan (tanggal 1 untuk bulan sebelumnya). Setelahnya bulan itu read-only. |
| **Verdict** | Kesimpulan bulan: BERHASIL NABUNG / PAS-PASAN / BONCOS. |
| **Uang pegangan** | Semua uang pengguna di luar Tabungan (dompet + rekening + e-wallet). |

---

## 5. Konfigurasi default (seed saat instal pertama)

| Pos (`key`) | Nama tampil | Jenis | Nominal | Catatan |
|---|---|---|---|---|
| `makan` | Makan | HARIAN | **50.000 / hari** | Slot: Sarapan, Siang, Malam, Jajan |
| `buah` | Buah | HARIAN | **10.000 / hari** | Tanpa slot |
| `transport` | Transport OE | STOK | **480.000 / bulan** | Trip pulang mingguan. Estimasi **120.000 / trip**. Hari pulang: **Sabtu**. |
| `protein` | Protein | STOK | **200.000 / bulan** | ±2× beli @ ±100.000 (sekali beli ±93.000, habis ±14 hari) |
| `iuran_mess` | Iuran Mess | TETAP | **120.000 / bulan** | Tanggal jatuh tempo: diatur pengguna (default tgl 1) |
| `ai` | AI | TETAP | **390.000 / bulan** | Nominal bisa berubah (kurs). Jatuh tempo diatur pengguna (default tgl 1). |
| `nabung` | Nabung | TABUNGAN | **300.000 / bulan** | Disetor ke Tabungan saat split |
| `lain` | Lain-lain | SAKU | tanpa budget | Selalu dibayar dari Saku Sisa |

Pengaturan lain:

| Kunci | Default |
|---|---|
| Gaji template | 3.300.000 |
| Perkiraan tanggal gajian | 29 |
| Jam notifikasi harian | 22:00 |
| Ambang aman Sisa bebas | 50.000 |
| Trip standar per bulan | 4 |
| Nama panggilan | Ko |
| Tema | Terang |

Perubahan nominal pos di Pengaturan **berlaku mulai bulan berikutnya** (alokasi disimpan per bulan sebagai snapshot).

Total template bulan standar (30 hari, 4 Sabtu) = 1.500.000 + 300.000 + 480.000 + 200.000 + 120.000 + 390.000 + 300.000 = **3.290.000**.

---

## 6. Aturan bisnis

Kode aturan (R-xx) dipakai di test dan komentar kode.

### 6.1 Periode & gaji

- **R-01** Periode = bulan kalender. Tidak ada periode "gaji ke gaji".
- **R-02** Input gaji menyimpan: nominal, tanggal terima, **bulan target**. Bulan target default: jika `tanggal terima ≥ 20` → bulan berikutnya; jika `< 20` → bulan berjalan (gaji telat). Ditampilkan dan bisa diubah sebelum konfirmasi ("Gaji untuk bulan Oktober?").
- **R-03 Gaji lebih cepat:** gaji untuk bulan yang belum dimulai = **Saldo Pending**. Tidak menambah Saku Sisa bulan berjalan dan tidak bisa dipakai. Split-nya dikonfirmasi saat input, tetapi alokasi baru **aktif tanggal 1** bulan target.
- **R-04 Gaji telat:** bulan target berjalan sejak tanggal 1 memakai **gaji template sebagai estimasi** (ditandai "estimasi"). Header menampilkan "Gaji belum masuk · ditalangi Rp X" (X = total pengeluaran bulan itu sejauh ini). Saat gaji diinput, alokasi dihitung ulang dengan nominal asli; talangan otomatis selesai karena semua saldo dihitung ulang (R-60).
- **R-05 Gaji ganda:** jika bulan target sudah punya gaji, tanyakan "Ini gaji tambahan/rapel?" — jika ya, catat sebagai **Pemasukan** (bukan gaji kedua).
- **R-06 Penyesuaian wajib:** jika `gaji < Kebutuhan standar` (template 30 hari & 4 trip, 3.290.000 dengan default), layar **Penyesuaian** wajib muncul sebelum split. Usulan otomatis: Pos TETAP tidak disentuh → pos STOK & jatah HARIAN dipotong proporsional → Nabung dipotong terakhir. Semua angka bisa diedit; tombol lanjut aktif hanya jika total ≤ gaji. Hasilnya disimpan sebagai alokasi bulan itu.

### 6.2 Alokasi & Target Cadangan

Notasi: `D(M)` = jumlah hari bulan M; `S(M)` = jumlah hari Sabtu di bulan M.

- **R-10** `Kebutuhan(M) = Σ HARIAN(jatah × D(M)) + Σ STOK(budget) + Σ TETAP(estimasi) + Nabung`.
- **R-11** `TripTambahan(M) = max(0, S(M) − 4) × estimasiPerTrip`. Budget Transport **tetap 480.000** (tidak ikut dinaikkan).
- **R-12** `SakuSisaAwal(M) = gaji(M) − Kebutuhan(M) + bawaan(M)`, dengan `bawaan(M)` = sisa yang dipilih "bawa ke bulan depan" saat Tutup Buku M−1. **Boleh minus.**
- **R-13** `TargetCadangan(M) = max(0, TripTambahan(M) − SakuSisaAwal(M))`. Dihitung saat split, disimpan.
- **R-14 Warning awal** (tidak ada pemotongan otomatis): jika TargetCadangan > 0, tampilkan di:
  1. Preview split (kartu kuning berisi rincian penyebab, contoh: "31 hari: +60rb (Makan & Buah)", "5 kali Sabtu: +120rb").
  2. Banner di Beranda pada tanggal 1 (sekali, bisa ditutup).
  3. Header Beranda sepanjang bulan: "Cadangan: terkumpul X / Y".
  4. Notifikasi H-5 akhir bulan (09:00) jika belum tercapai: "Kurang Rp X, hemat ±Rp Y/hari biar tabungan aman" (Y = X ÷ sisa hari, dibulatkan ke atas ke ribuan).
  5. Tutup Buku: sisa minus wajib ditutup dari Tabungan (R-72).
- **R-15** `ReservasiSisa(M) = max(0, TripTambahan(M) − max(0, terpakaiTransport − budgetTransport))`. `SisaBebas = SakuSisa − ReservasiSisa`. Progres cadangan: `terkumpul = TargetCadangan − max(0, −SisaBebas)` (dibatasi 0..Target).
- **R-16 Status Sisa bebas:** `≥ ambang aman` → aman; `0 .. < ambang` → waspada; `< 0` → minus.

Contoh wajib (dipakai di test): **Oktober 2026** = 31 hari, 5 Sabtu (3, 10, 17, 24, 31). Kebutuhan = 1.550.000 + 310.000 + 480.000 + 200.000 + 120.000 + 390.000 + 300.000 = **3.350.000**. SakuSisaAwal = −50.000. TripTambahan = 120.000. **Target Cadangan = 170.000.**

### 6.3 Pos HARIAN (Makan, Buah) & hutang

- **R-20** Setiap pos HARIAN punya jatah per hari dan **hutang sendiri-sendiri** (hutang Makan terpisah dari hutang Buah).
- **R-21** Hari D dianggap **tertutup** mulai pukul 00:00 hari D+1. Hari ini selalu **sementara** (preview).
- **R-22** Saat hari D tertutup, untuk tiap pos HARIAN: `delta = jatah − terpakai(D)`.
  - Jika `delta ≥ 0`: `bayar = min(delta, hutang)`; `hutang −= bayar`; `SakuSisa += delta − bayar`. (**Hemat melunasi hutang dulu, sisanya ke Saku Sisa.**)
  - Jika `delta < 0`: `hutang += −delta`. Hutang **tidak** otomatis diambil dari saku mana pun.
- **R-23 Mode Darurat** (tombol manual di kartu hutang): melunasi hutang sebesar yang dipilih pengguna:
  1. Dari **Saku Sisa** lebih dulu (Saku Sisa tidak boleh jadi minus karena aksi ini).
  2. Jika Saku Sisa kurang: sisanya dari **Tabungan** → layar merah, peringatan keras, **tahan tombol 3 detik**. Tercatat sebagai ambil tabungan alasan DARURAT (mempengaruhi verdict).
- **R-24 Pergantian bulan:** hutang dibawa ke bulan baru (default) dan tetap dilunasi oleh hemat hari berikutnya. Saat Tutup Buku, pengguna boleh memilih melunasi pakai Saku Sisa.
- **R-25** Hari sebelum pengguna mulai memakai app (sebelum tanggal mulai onboarding) tidak dihitung.
- **R-26 Slot Makan:** Sarapan / Siang / Malam / Jajan. Default otomatis dari jam input: `< 10:00` Sarapan, `10:00–14:59` Siang, `≥ 15:00` Malam. Jajan hanya dipilih manual. Jika tanggal yang dipilih "kemarin", default Malam. Slot hanya untuk rincian; perhitungan jatah memakai total harian.

### 6.4 Pos STOK (Transport OE, Protein)

- **R-30** Budget bulanan. Tidak ada jatah harian dan tidak ada hutang.
- **R-31** Pemakaian melebihi budget → kelebihannya **mengurangi Saku Sisa** (boleh sampai minus, dengan peringatan merah di layar input).
- **R-32 Transport:** nomor trip = jumlah akhir pekan (Sabtu) yang sudah punya transaksi Transport di bulan itu, ditampilkan "Trip n dari S(M)". Transaksi hari Minggu masuk ke akhir pekan Sabtu sebelumnya. Trip yang Sabtunya tanggal akhir bulan dan Minggunya tanggal 1 dihitung ke bulan **Sabtu**-nya (transaksi Minggu tanggal 1 tetap masuk bulan si Sabtu).
- **R-33** Pada hari Sabtu & Minggu, tile Transport pindah ke posisi pertama di Beranda.
- **R-34 Status STOK:** `lebih` jika terpakai > budget; `waspada` jika terpakai ≥ 80% budget, atau (khusus Transport) `sisaBudget < sisaTrip × estimasiPerTrip`; selain itu `normal`.

### 6.5 Pos TETAP (Iuran Mess, AI)

- **R-40** Saat split, estimasi dicadangkan dengan status **BELUM BAYAR**. Tidak ada input harian.
- **R-41** Pada tanggal jatuh tempo (09:00) muncul notifikasi "AI jatuh tempo. Bayar berapa?" dengan nominal estimasi terisi. Pengguna konfirmasi atau ubah nominal → status LUNAS.
- **R-42** `selisih = estimasi − aktual` masuk ke Saku Sisa (positif menambah, negatif mengurangi).
- **R-43** Belum dibayar saat Tutup Buku → ditanya: "Sudah dibayar (nominal?)" atau "Tidak jadi" (estimasi kembali ke Saku Sisa).

### 6.6 Saku Sisa & Lain-lain

- **R-50** Lain-lain tidak punya budget. Setiap pengeluaran Lain-lain mengurangi Saku Sisa.
- **R-51** Preview di layar input: jika transaksi membuat SisaBebas < 0 → "Ini memakai cadangan akhir bulan" (kuning). Jika membuat Saku Sisa < 0 → "Saku Sisa minus — akhir bulan akan memotong Tabungan" (merah). Pengguna tetap boleh menyimpan.
- **R-52** Saku Sisa minus **tidak** otomatis mengambil Tabungan selama bulan berjalan. Penyelesaiannya di Tutup Buku (R-72). Tersedia aksi manual "Tutup pakai Tabungan sekarang" (tahan 3 detik).

### 6.7 Tabungan

- **R-55** Setoran: Nabung rutin (saat split), sisa bulan lalu (Tutup Buku), pemasukan bertujuan Tabungan (mis. THR), setoran manual.
- **R-56** Ambil tabungan wajib memilih alasan: **DARURAT** (masuk hitungan BONCOS) atau **RENCANA** (tidak dihitung boncos, tetap tampil di laporan). Uang yang diambil masuk ke Saku Sisa. Aksi ini selalu memakai tahan tombol 3 detik.
- **R-57** Setelah split, checklist "Sudah transfer Rp X ke rekening tabungan?". Jika belum dicentang, pengingat esok hari 09:00 (maksimal 3 hari berturut-turut).

### 6.8 Pemasukan tambahan

| Jenis | Tujuan default | Pilihan tujuan |
|---|---|---|
| Pemberian | Saku Sisa | Saku Sisa / Tabungan / tambah budget pos STOK |
| Penghasilan sampingan | Saku Sisa | sama |
| THR / Bonus | **Tabungan** | sama |
| Pengembalian ke kategori (reimburse, refund) | pos asal | wajib pilih pos; **mengurangi "terpakai"** pos itu pada tanggal yang dipilih |
| Lainnya | Saku Sisa | sama |

Pemasukan tidak pernah membuka periode baru dan tidak mengubah jatah harian.

### 6.9 Tanggal, edit, dan anti-typo

- **R-60 Saldo selalu dihitung ulang** dari transaksi setiap ada perubahan (lihat 7.2). Transaksi tanggal mundur (kemarin, dst.) otomatis memperbarui hutang, Saku Sisa, dan status.
- **R-61** Input antara **00:00–04:59** → dialog wajib "Untuk hari ini atau kemarin?" (default: Kemarin). Di luar jam itu tanggal otomatis hari ini, dengan chip tanggal yang bisa diganti (date picker dibatasi ke bulan yang belum ditutup).
- **R-62** Setelah simpan: kartu feedback 3 detik + snackbar **Urungkan** 5 detik.
- **R-63** Transaksi di bulan yang belum ditutup bisa diedit/dihapus dari Riwayat.
- **R-64** Bulan yang sudah Tutup Buku = **read-only**. Koreksi dicatat di bulan berjalan sebagai transaksi **Koreksi** (pos + nominal ±, catatan wajib).
- **R-65 Anti-typo:** jika nominal > 3× median 20 transaksi terakhir pos itu (minimal 5 data), minta konfirmasi: "Yakin Rp 450.000? Biasanya ±Rp 45.000".
- **R-66** Tombol "Hari ini beres" hanya menandai hari itu selesai dicatat (menghentikan pengingat). Tidak mempengaruhi hitungan.

### 6.10 Tutup Buku

Dipicu tanggal 1 untuk bulan sebelumnya (notifikasi 07:00 + otomatis terbuka saat app dibuka pertama kali tanggal ≥ 1). Bisa ditunda, tetapi Beranda menampilkan pengingat sampai selesai. Langkah berurutan:

0. **"Ada catatan kemarin yang belum masuk?"** → pintasan ke input bertanggal hari terakhir bulan lalu.
1. **Verdict** (R-80) dengan ringkasan angka.
2. **Cocokkan saldo** (bisa dilewati): "Uang pegangan lu sekarang (dompet + rekening + e-wallet, di luar tabungan) berapa?" → dibandingkan dengan uang pegangan hasil hitung (7.3). Selisih kurang → transaksi **Tidak tercatat** (mengurangi Saku Sisa bulan itu). Selisih lebih → transaksi **Selisih lebih** (menambah Saku Sisa). Verdict dihitung ulang dan ditampilkan lagi.
3. **Hutang harian tersisa** (per pos): **Bawa ke bulan baru** (default) / **Lunasi pakai Saku Sisa**.
4. **Pos TETAP belum dibayar** (R-43).
5. **Saku Sisa akhir:**
   - **R-72** Jika minus → **wajib** ditutup dari Tabungan (layar merah, tahan 3 detik). Tercatat ambil tabungan alasan DARURAT.
   - Jika positif → pilih **Semua ke Tabungan** / **Bawa ke bulan depan** / **Split** (atur nominal ke Tabungan dan sisanya dibawa).
   - Jika gaji bulan baru **belum masuk**, pilihan "ke Tabungan" dinonaktifkan dengan keterangan: "Tahan dulu — uang ini lagi nalangin bulan baru." Langkah ini bisa diselesaikan belakangan.
6. **Backup keluar HP**: tombol "Kirim backup" (share sheet). Bisa dilewati.

Setelah selesai: bulan berstatus CLOSED, snapshot angka disimpan.

### 6.11 Verdict

Dihitung per bulan setelah langkah Tutup Buku 2–5.

- **R-80** Urutan penentuan:
  1. **BONCOS Rp X** jika total ambil tabungan alasan DARURAT di bulan itu > 0 (termasuk penutupan Saku Sisa minus dan Mode Darurat). X = total tersebut.
  2. **PAS-PASAN** jika bukan boncos, dan (Saku Sisa akhir < ambang aman **atau** ada hutang harian yang dibawa).
  3. **BERHASIL NABUNG** selain itu. Tampilkan: Nabung rutin + sisa yang dipindah ke Tabungan.
- **R-81** Kartu verdict selalu menampilkan: pemasukan (gaji + tambahan), pengeluaran riil, tabungan masuk/keluar, hutang dibawa, hasil cocokkan saldo (jika dilakukan).

---

## 7. Arsitektur & model data

### 7.1 Struktur modul

```
app/
 ├─ core/engine/      ← Kotlin murni: semua aturan bab 6 + unit test (tanpa import android.*)
 ├─ data/             ← Room (entity, DAO), DataStore, repository, backup JSON
 ├─ feature/          ← layar Compose + ViewModel per fitur
 │   home/ input/ detail/ salary/ closing/ savings/ history/ report/ settings/ onboarding/
 ├─ notify/           ← AlarmManager, receiver, channel, pesan notifikasi
 ├─ export/           ← PDF & XLSX writer
 ├─ widget/           ← Glance widget
 └─ ui/theme/         ← warna, tipografi, bentuk, komponen bersama
```

### 7.2 Satu sumber kebenaran

- Data tersimpan hanya: **transaksi**, **alokasi per bulan**, **kewajiban pos tetap**, **penutupan bulan**, **pengaturan**, **tanda hari**.
- `core/engine` menyediakan fungsi murni:
  - `computeLedger(input: LedgerInput, today: LocalDate): LedgerState` — memutar ulang semua transaksi secara kronologis sejak tanggal mulai dan menghasilkan: Saku Sisa, Sisa bebas, reservasi, target & progres cadangan, hutang per pos HARIAN, status tiap pos, tabungan, uang pegangan, status gaji (diterima/estimasi/pending), talangan, preview verdict.
  - `previewImpact(state, draft): Impact` — dampak transaksi yang sedang diketik (untuk warning sebelum simpan).
  - `planSplit(gaji, bulanTarget, config, bawaan): SplitPlan` — rincian alokasi, penyesuaian yang diperlukan, target cadangan beserta alasannya.
  - `computeVerdict(...)`, `countSaturdays(ym)`, `tripIndexOf(date)`, `defaultSlot(time)`, `isSuspiciousAmount(...)`.
- Repository memanggil `computeLedger` setiap data berubah dan mengekspos hasilnya sebagai `StateFlow`. Data satu pengguna kecil (ribuan baris), jadi hitung ulang penuh itu aman. Boleh di-cache per bulan tertutup.

### 7.3 Jenis transaksi & efeknya

Semua `amount` positif (`Long`). Arah ditentukan jenis.

| Jenis (`TxType`) | Contoh | Uang pegangan | Tabungan | Efek anggaran |
|---|---|---|---|---|
| `SALARY` | Gaji | + | | Sumber alokasi bulan target (pending jika bulan belum mulai) |
| `INCOME` | Pemberian, sampingan, THR | + (jika tujuan bukan Tabungan) | + (jika tujuan Tabungan) | Tujuan Sisa → Saku Sisa +; tujuan pos STOK → budget pos + |
| `REFUND` | Pengembalian ke kategori | + | | "terpakai" pos asal − pada tanggalnya |
| `EXPENSE` | Pengeluaran pos HARIAN/STOK/SAKU | − | | HARIAN → terpakai hari itu; STOK → terpakai bulan; SAKU → Saku Sisa − |
| `FIXED_PAYMENT` | Bayar Iuran Mess / AI | − | | Pos tetap LUNAS; selisih estimasi → Saku Sisa (R-42) |
| `SAVING_DEPOSIT` | Nabung rutin, sisa → tabungan, setor manual | − | + | Nabung rutin: bagian alokasi. Lainnya: Saku Sisa − |
| `SAVING_WITHDRAW` | Ambil tabungan (`reason`: DARURAT/RENCANA) | + | − | Saku Sisa + |
| `DEBT_PAYOFF` | Mode Darurat dari Saku Sisa | | | Hutang pos − ; Saku Sisa − |
| `CARRY_OVER` | Bawa sisa ke bulan depan | | | Saku Sisa(M) − ; Saku Sisa(M+1) + |
| `UNRECORDED` | Selisih kurang saat cocokkan saldo | − | | Saku Sisa − |
| `SURPLUS_FOUND` | Selisih lebih saat cocokkan saldo | + | | Saku Sisa + |
| `CORRECTION` | Koreksi bulan tertutup (`signedAmount`) | ∓ | | Terpakai pos di bulan berjalan ± |

Mode Darurat yang memakai Tabungan = `SAVING_WITHDRAW(DARURAT)` lalu `DEBT_PAYOFF`, dalam satu transaksi database.

`uangPegangan = uangAwal(onboarding) + Σ efek "Uang pegangan"` pada tabel di atas. Pos TETAP yang belum dibayar dan Saldo Pending **termasuk** uang pegangan (uangnya memang ada).

### 7.4 Entity Room (bentuk minimal, boleh disempurnakan tanpa mengubah makna)

- `category` — id, key, name, kind (`DAILY`, `STOCK`, `FIXED`, `SAVING`, `POCKET`), iconKey, colorKey, sortOrder, active, dailyAmount?, monthlyAmount?, dueDay?, hasSlots
- `month_plan` — yearMonth (PK), salaryTxId?, isEstimate, targetReserve, tripExtra, carryIn, status (`OPEN`/`CLOSED`), createdAt
- `month_allocation` — yearMonth, categoryId, dailyAmount?, monthlyAmount (snapshot alokasi bulan itu)
- `tx` — id, date, createdAt, updatedAt, type, categoryId?, amount, signedAmount?, slot?, note?, destination?, reason?, refYearMonth?
- `fixed_obligation` — yearMonth, categoryId, estimate, status (`UNPAID`/`PAID`/`CANCELLED`), paidTxId?
- `day_mark` — date (PK), doneMarked
- `month_closure` — yearMonth (PK), verdict, verdictAmount, snapshotJson, reconciledActual?, closedAt
- DataStore: gaji template, perkiraan tanggal gajian, jam notifikasi, ambang aman, trip standar, estimasi per trip, hari pulang, nama panggilan, tema, tanggal mulai, uang awal, tabungan awal, URI folder backup, checklist transfer, flag onboarding

---

## 8. Layar & alur

Mockup: `design/Main.dc.html` (Beranda), `design/Input.dc.html` (Input), `design/DetailMakan.dc.html` (Detail Harian). Layar lain mengikuti gaya yang sama.

### 8.1 Beranda (`design/Main.dc.html`)
- **Header:** tanggal lengkap ("Senin, 28 September") + "Halo, {nama}" + tombol notifikasi (daftar pengingat aktif).
- **Kartu hero (gradien biru):** Saku Sisa (angka besar) + label bulan. Dua chip: "Hutang makan" (total hutang harian; sembunyikan jika 0) dan "Tutup buku · n hari lagi".
  - Jika Target Cadangan > 0: tambah baris "Cadangan: terkumpul X / Y" + progress bar.
  - Jika gaji telat: chip "Gaji belum masuk · ditalangi Rp X".
  - Jika ada Saldo Pending: chip "Gaji {bulan} aman · aktif tgl 1".
  - Warna status Sisa bebas (R-16) wajib disertai teks.
- **Banner Gajian (emas):** tampil mulai tanggal 27 sampai gaji bulan depan diinput, **atau** saat bulan berjalan belum punya gaji. Teks kedua memuat Target Cadangan bulan depan jika > 0.
- **Baris "Catat pengeluaran"** + tombol **Pemasukan** (hijau muda).
- **Grid kategori** 2 kolom: Makan, Buah, Transport OE, Protein; Lain-lain lebar penuh. Sabtu/Minggu: Transport di posisi pertama (R-33). Setiap tile: ikon berwarna, nama, satu baris status (teks), progress bar tipis, badge opsional (Hutang, Trip n/S, Stok).
- **Bottom nav:** Beranda · Riwayat · **[+] hijau tengah** · Laporan · Pengaturan. Tombol + membuka pemilih kategori (sheet berisi 5 tile) dari tab mana pun.
- Banner Tutup Buku (jika tertunda) muncul di atas grid.

### 8.2 Input pengeluaran (`design/Input.dc.html`) — bottom sheet
Urutan: handle → header (ikon pos, nama, sisa jatah/budget, chip tanggal) → slot (hanya Makan) → nominal besar → **kotak preview dampak** (R-51, R-22) → chip nominal cepat → catatan opsional → numpad (1–9, 000, 0, hapus) → tombol "Simpan · Rp X".
- Chip nominal cepat: 4 nominal paling sering pos itu (60 hari terakhir). Fallback: Makan 10/15/20/25rb · Buah 5/10rb · Transport 60/120rb · Protein 93/100rb · Lain-lain 10/20/50/100rb.
- Preview dampak (contoh): HARIAN → "Lebih Rp 10.000 dari jatah — hutang makan jadi Rp 25.000"; STOK → "Melebihi budget Rp X — diambil dari Saku Sisa"; SAKU → "Saku Sisa jadi Rp X" (+ R-51).
- R-61 (tanya hari ini/kemarin), R-65 (anti-typo), R-62 (feedback + urungkan).
- Kartu feedback setelah simpan: "Makan +Rp 25.000 · Hari ini 60rb / 50rb · Hutang Rp 25.000" dengan warna + ikon status.

### 8.3 Detail pos HARIAN (`design/DetailMakan.dc.html`, tema gelap)
Gauge setengah lingkaran (terpakai vs jatah hari ini, angka besar = sisa jatah) → rincian per slot → kartu hutang (nominal, kalimat "Tahan di Rp X sampai malam, hutang lunas otomatis jam 22:00" jika memungkinkan, tombol **Mode darurat**) → grafik batang 7 hari terakhir dengan garis putus-putus jatah dan label "+X" pada hari lebih → statistik bulan: hari hemat, hari lebih, total ke Saku Sisa → daftar transaksi hari ini.
Dibuka dengan tap lama pada tile, atau tap kartu feedback.

### 8.4 Detail pos STOK
Tema terang. Budget, terpakai, sisa, status; khusus Transport: daftar trip per akhir pekan ("Sab 3 Okt · Rp 120.000"), "Trip n dari S(M)"; daftar transaksi bulan ini.

### 8.5 Pemasukan (sheet)
Pilih jenis (6.8) → nominal (numpad sama) → tujuan (default sesuai jenis) → catatan → simpan.

### 8.6 Gajian (layar penuh, 4 langkah)
1. **Nominal** (terisi gaji terakhir) + tanggal terima (default hari ini, bisa mundur) + bulan target (R-02).
2. **Penyesuaian** — hanya jika R-06 terpicu.
3. **Preview split:** daftar pos dengan rumus terlihat ("Makan · 50.000 × 31 hari = 1.550.000"), total, **Saku Sisa awal**, kartu kuning Target Cadangan (R-14) jika ada. Tombol **Konfirmasi**.
4. **Checklist:** "Transfer Rp 300.000 ke rekening tabungan" (R-57). Selesai → kembali ke Beranda dengan feedback "Gaji Oktober tersimpan · aktif 1 Okt".

### 8.7 Tutup Buku (layar penuh, stepper sesuai 6.10)
Setiap langkah satu layar dengan satu keputusan utama. Kartu verdict besar di langkah 1 dan di akhir.

### 8.8 Tabungan
Saldo besar (ungu), riwayat masuk/keluar, tombol **Setor** dan **Ambil** (R-56, tahan 3 detik, layar merah untuk DARURAT).

### 8.9 Riwayat
Pemilih bulan → daftar transaksi dikelompokkan per tanggal (header tanggal + total hari). Filter chip per pos. Tap → sheet edit (R-63). Bulan tertutup: badge "Terkunci", aksi edit diganti "Buat koreksi" (R-64).

### 8.10 Laporan
Tab **Mingguan** | **Bulanan** + tombol **Export** di kanan atas.
- **Mingguan (Senin–Minggu):** total per pos; grafik garis harian Makan & Buah vs jatah; hari paling boros; perbandingan dengan minggu lalu (naik/turun %); hutang awal→akhir minggu; perubahan Saku Sisa.
- **Bulanan:** kartu verdict (untuk bulan berjalan tampil "sementara"); donut per pos + chip legenda; tabel budget vs realisasi; aliran tabungan; hutang; statistik hari hemat/lebih; hasil cocokkan saldo.

### 8.11 Pengaturan
Nama panggilan · pos & nominal (berlaku bulan depan) · tanggal jatuh tempo pos tetap · hari pulang & estimasi per trip · perkiraan tanggal gajian · jam notifikasi · ambang aman · tema terang/gelap · folder auto-backup · Export / Backup / Pulihkan · panduan izin notifikasi & optimasi baterai · versi app.
- **Mode Uji Tanggal (tersembunyi):** ketuk "versi app" 7× → bisa memajukan "hari ini" untuk menguji Gajian, Tutup Buku, dan notifikasi. Selama aktif, banner merah "MODE UJI" tampil di semua layar. Semua data yang dibuat saat mode uji diberi tanda dan **dihapus otomatis** saat mode dimatikan (data asli tidak tersentuh).

### 8.12 Onboarding
Layar pertama: dua tile **[Mulai Baru]** / **[Pulihkan dari Backup]**.
Mulai Baru:
1. Nama panggilan (default "Ko").
2. Konfirmasi pos & nominal default (bagian 5), bisa diubah.
3. "Uang pegangan lu sekarang berapa?" dan "Tabungan sekarang berapa?"
4. **Periode awal** (mulai di tengah bulan): jatah HARIAN dihitung dari hari ini sampai akhir bulan; budget STOK diprorata `budget × sisaHari ÷ D(M)` (dibulatkan ke ribuan); pos TETAP bulan ini dianggap sudah dibayar; `SakuSisaAwal = uangPegangan − (jatah harian sisa bulan + stok prorata)`. Jika minus, tampilkan warning (R-14 berlaku).
5. Izin notifikasi → panduan mematikan optimasi baterai untuk app ini (bahasa sederhana, sebut Xiaomi/Oppo/Vivo/Realme).
6. Pilih folder auto-backup (boleh dilewati).

### 8.13 Komponen bersama
- **HoldToConfirmButton:** tahan 3 detik dengan cincin progres; lepas sebelum selesai = batal; getar singkat saat berhasil. Wajib untuk semua aksi yang mengurangi Tabungan.
- **AmountText:** format `Rp 45.000`; versi ringkas `Rp 45rb`, `Rp 1,05 jt`. Angka tabular.
- **StatusPill:** warna + ikon + teks.
- **FeedbackCard**, **Numpad**, **CategoryTile**, **QuickAmountChips**, **DateChip**.

---

## 9. Notifikasi

Channel terpisah: Harian, Pengingat, Tutup Buku. Semua jadwal pakai waktu lokal dan dijadwal ulang saat boot/perubahan waktu.

| Kapan | Isi | Aksi |
|---|---|---|
| **Setiap hari 22:00** (satu notifikasi) | Jika sudah ada catatan: status Makan & Buah hari ini (hemat/pas/lebih + nominal), hutang, Saku Sisa. Jika belum ada catatan: "Belum ada catatan hari ini." | Tombol **Makan**, **Transport**, **Lainnya** (membuka input) + **Hari ini beres** |
| Minggu 22:00 | Notifikasi harian di atas ditambah satu baris ringkasan minggu | Buka laporan mingguan |
| Tanggal jatuh tempo pos TETAP, 09:00 | "AI jatuh tempo. Bayar berapa?" | Buka konfirmasi pembayaran |
| Tanggal 29, 12:00 (jika gaji bulan depan belum diinput) | "Gaji sudah masuk?" | Buka Gajian |
| Tanggal 1, 07:00 | "{Bulan} selesai. Yuk tutup buku." | Buka Tutup Buku |
| H-5 akhir bulan, 09:00 (jika cadangan kurang) | R-14 butir 4 | Buka Beranda |
| Esok hari 09:00 (checklist belum dicentang, maks 3×) | "Udah transfer Rp 300.000 ke tabungan?" | Tandai selesai |

---

## 10. Laporan & export

Menu Export: rentang (**Minggu ini** / **Bulan [pilih]** / **Custom**) → format (**PDF** / **Excel**) → share sheet atau simpan (SAF).

- **PDF** (A4 portrait, `PdfDocument`): judul & rentang, kartu verdict (untuk bulan), ringkasan per pos, grafik batang sederhana, status hutang & cadangan, daftar transaksi (paginasi otomatis). Font dan warna sesuai tema terang.
- **Excel (.xlsx)**, sheet:
  1. `Ringkasan` — pemasukan, pengeluaran, tabungan, verdict, cadangan
  2. `Transaksi` — tanggal, jenis, pos, slot, nominal, catatan
  3. `Per Kategori` — budget, terpakai, sisa, status
  4. `Harian Makan & Buah` — tanggal, jatah, terpakai, selisih, hutang akhir hari, ke Saku Sisa
- Nama file: `catatuang-{rentang}-{yyyyMMdd}.pdf|xlsx`. Nominal ditulis sebagai angka (bukan teks) di Excel.

---

## 11. Backup & pulihkan

- **Format:** JSON `{ schemaVersion, appVersion, exportedAt, settings, categories, monthPlans, allocations, transactions, fixedObligations, dayMarks, closures }`. Nama: `catatuang-backup-yyyyMMdd-HHmm.json`.
- **Auto-backup mingguan** (Minggu 23:00) ke folder pilihan pengguna (URI persisten SAF). Simpan 8 file terakhir, hapus yang lebih lama **di folder itu saja**.
- **Kirim backup** manual (share sheet) tersedia di Pengaturan dan di langkah terakhir Tutup Buku.
- **Pulihkan:** pilih file → validasi `schemaVersion` & struktur → preview ("8 bulan data · 1.240 transaksi · terakhir 23 Sep 2026") → konfirmasi → **ganti seluruh data** dalam satu transaksi database → langsung ke Beranda. Tanpa login.
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
| `gold` | `#F2C14E`, `#FFF4DB`, teks `#7A5200`, tombol `#8A5A00` | Banner Gajian |
| `background` | `#EEF0F8` | Latar layar |
| `surface` | `#FFFFFF` | Kartu |
| `textPrimary` / `textSecondary` | `#1E1E2D` / `#5F6478` | Teks |
| `divider` | `#D5D8E4` | Garis, border input |

### Warna — tema gelap (dipakai di layar Detail Harian, dan seluruh app jika tema gelap aktif)
`background #12152A` · `surface #1C2140` · `track #2C3360` · `text #F2F4FF` · `textSecondary #A3A9C7` · `primary #5B8CFF` · `success #3DD598` · `danger #FF6B81` / teks `#FF8FA3` · `dangerSurface #2B1C30`.

### Ikon pos (latar / ikon)
Makan `#FFE9D6 / #B85A00` · Buah `#DDF5EA / #17865A` · Transport `#E3E9FF / #3563E9` · Protein `#EFE7FF / #6A3FD0` · Lain-lain `#EEF0F4 / #5F6478`.

### Tipografi (Plus Jakarta Sans)
Angka hero 34sp/800 · angka input 48sp/800 · judul layar 22sp/800 · judul kartu 15–16sp/700 · isi 13–14sp/500 · caption minimal 11–12sp/600. Semua angka uang memakai **tabular figures** (`fontFeatureSettings = "tnum"`).

### Bentuk & jarak
Radius: hero 24dp · kartu 20dp · sheet 28dp (atas) · tombol utama 16dp · chip/input 12dp. Padding layar 20dp. Grid 8dp. Bayangan halus (`y 4dp, blur 14dp, alpha 6%`). **Target sentuh minimal 44dp**; tombol numpad 50dp.

### Aksesibilitas
Kontras teks ≥ 4.5:1. Semua tombol ikon punya `contentDescription`. Status selalu teks + warna.

---

## 13. Test wajib (`core/engine`)

Semua lulus sebelum UI terkait dibuat. Nilai dalam rupiah.

| ID | Skenario | Hasil yang diharapkan |
|---|---|---|
| T-01 | Hutang Makan: Senin pakai 65.000, Selasa 40.000, Rabu 35.000 (jatah 50.000, hutang awal 0) | Senin: hutang 15.000, Sisa +0 · Selasa: hutang 5.000, Sisa +0 · Rabu: hutang 0, Saku Sisa +10.000 |
| T-02 | Kebutuhan & cadangan **Oktober 2026**, gaji 3.300.000 | D=31, S=5, Kebutuhan 3.350.000, SakuSisaAwal −50.000, TripTambahan 120.000, **Target 170.000** |
| T-03 | **November 2026**, gaji 3.300.000 | D=30, S=4, SakuSisaAwal +10.000, Target 0 |
| T-04 | **Februari 2027**, gaji 3.300.000 | D=28, S=4, Kebutuhan 3.170.000, SakuSisaAwal +130.000 |
| T-05 | Gaji 3.000.000 untuk bulan 30 hari | R-06 terpicu; usulan penyesuaian tidak menyentuh pos TETAP; Nabung dipotong terakhir |
| T-06 | Gaji diterima 29 Sep 2026 | Bulan target Oktober; berstatus pending sampai 1 Okt; Saku Sisa September tidak berubah |
| T-07 | Gaji Oktober diterima 3 Okt; pengeluaran 1–2 Okt 90.000 | 1–2 Okt dihitung dengan estimasi template + talangan 90.000; setelah gaji masuk, semua saldo sama dengan jika gaji masuk tepat waktu |
| T-08 | Makan lebih 20.000 pada 31 Okt, 1 Nov hemat 15.000 | Hutang terbawa ke November; 1 Nov hutang jadi 5.000 |
| T-09 | Mode Darurat hutang 30.000, Saku Sisa 20.000 | Saku Sisa → 0, SAVING_WITHDRAW(DARURAT) 10.000, hutang 0; verdict bulan itu BONCOS 10.000 |
| T-10 | Transport 5 trip di Oktober 2026 @120.000 | Kelebihan 120.000 mengurangi Saku Sisa; ReservasiSisa jadi 0; SisaBebas tidak berubah akibat trip ke-5 |
| T-11 | Trip Sabtu 31 Okt + Minggu 1 Nov | Keduanya dihitung trip ke-5 Oktober |
| T-12 | Tutup Buku dengan Saku Sisa −40.000 | Wajib penutupan dari Tabungan 40.000 (DARURAT); verdict BONCOS 40.000 |
| T-13 | Saku Sisa akhir 30.000, tanpa hutang (ambang 50.000) | PAS-PASAN |
| T-14 | Saku Sisa akhir 120.000, tanpa hutang, tanpa ambil tabungan darurat | BERHASIL NABUNG; tampil 300.000 + sisa yang dipindah |
| T-15 | Ambil tabungan alasan RENCANA 500.000 | Tidak memicu BONCOS |
| T-16 | Pos TETAP AI estimasi 390.000, dibayar 405.000 | Saku Sisa −15.000 |
| T-17 | Cocokkan saldo: hitung 250.000, aktual 185.000 | UNRECORDED 65.000; Saku Sisa −65.000; verdict dihitung ulang |
| T-18 | Transaksi kemarin ditambahkan setelah hari tertutup | Hutang & Saku Sisa dihitung ulang dengan benar (R-60) |
| T-19 | `defaultSlot`: 07:30, 12:00, 19:00; tanggal kemarin | Sarapan, Siang, Malam; Malam |
| T-20 | Input pukul 00:30 | Wajib tanya hari ini/kemarin (R-61) |
| T-21 | Anti-typo: median 45.000, input 450.000 | Perlu konfirmasi; input 90.000 → tidak |
| T-22 | Pengembalian 60.000 ke Transport | Terpakai Transport −60.000 |
| T-23 | THR 3.000.000 dengan tujuan default | Tabungan +3.000.000; Saku Sisa tetap |
| T-24 | Onboarding 16 Sep (D=30), uang pegangan 900.000 | Jatah harian 15 hari; STOK prorata 15/30; SakuSisaAwal sesuai rumus 8.12 |
| T-25 | Round-trip backup: ekspor lalu impor | Seluruh `LedgerState` identik |

Tambahkan test lain bila menemukan kasus pinggiran. Test UI minimal: navigasi 4 tap dari Beranda sampai tersimpan.

---

## 14. Roadmap per fase

Setiap fase: kode dikomit ke git, test lulus, APK rilis ter-build dan ter-install di HP pengguna.

| Fase | Isi | Selesai jika |
|---|---|---|
| **0. Fondasi** | Proyek Gradle, tema & font, navigasi kosong, **keystore rilis** (`keystore.properties` di luar git), build `assembleRelease` | APK rilis ter-install; berkas keystore + password sudah disimpan pengguna |
| **1. Mesin** | `core/engine` + seluruh test bab 13 (kecuali yang butuh UI), Room + DataStore + repository | Semua test lulus |
| **2. Harian** | Onboarding (Mulai Baru), Beranda, Input, Detail Harian, Detail Stok, Riwayat (edit/hapus/urungkan) | Bisa dipakai mencatat sehari-hari |
| **3. Uang masuk** | Gajian (4 langkah), Pemasukan, Tabungan, Pos TETAP, warning cadangan | Siklus gaji bisa dijalankan |
| **4. Tutup Buku** | Stepper 6.10, cocokkan saldo, verdict, bulan read-only, Koreksi, Mode Uji Tanggal (8.11) | Satu bulan bisa ditutup |
| **5. Notifikasi** | Semua jadwal bab 9, aksi dari notifikasi, reschedule boot | Notif 22:00 muncul tepat di HP pengguna |
| **6. Laporan** | Mingguan, Bulanan, Export PDF & XLSX | File terbuka normal di HP & laptop |
| **7. Keamanan data** | Backup JSON, auto-backup, Pulihkan (termasuk dari onboarding), migrasi | Instal ulang → pulihkan → data utuh |
| **8. Polish** | Widget Glance, tema gelap, anti-typo, uji aksesibilitas | — |

---

## 15. Di luar cakupan

**Tidak dibuat:** cloud sync, login/akun, multi-pengguna, multi-mata uang, integrasi bank/e-wallet, sinkron Google Calendar, iklan, analytics.

**Backlog (prioritas rendah, setelah Fase 8):** Pinjaman — `LOAN_OUT` (minjemin: uang pegangan −, tidak dihitung pengeluaran) dan `LOAN_RETURN` (uang pegangan +), dengan nama peminjam dan daftar piutang terbuka.
