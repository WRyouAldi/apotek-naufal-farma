# Blueprint UI/UX — Apotek Naufal Farma

## 1. Tujuan
Aplikasi dipisahkan berdasarkan tugas agar pengguna tidak melihat fungsi database, pencarian, transaksi, dan laporan dalam satu halaman.

Prinsip:
- Satu halaman = satu tujuan utama.
- Beranda hanya dashboard/launcher.
- CRUD hanya di Daftar Item.
- Cek Harga hanya untuk lookup.
- Transaksi hanya untuk kasir.
- Barang Keluar hanya untuk pencatatan stok keluar.
- Laporan hanya untuk membaca/mengekspor laporan.
- Bottom navigation maksimal 5 menu agar tetap nyaman di layar ponsel.

## 2. Arsitektur navigasi

Beranda
  ├─ Cek Harga
  ├─ Scan Barcode → Cek Harga
  ├─ Transaksi
  ├─ Barang Keluar
  └─ Daftar Item

Bottom navigation:
  Beranda | Cek Harga | Transaksi | Keluar | Laporan

Daftar Item diakses dari Beranda karena merupakan fungsi administrasi/master data, bukan aktivitas kasir harian.

## 3. Halaman Beranda
Tujuan: orientasi dan akses cepat.
- Header identitas Naufal Farma.
- Status database dan total produk.
- Aksi Cepat: Cek Harga, Scan Barcode, Transaksi, Barang Keluar.
- Shortcut terpisah ke Daftar Item.
- Tidak menampilkan search produk, CRUD, import CSV, keranjang, atau laporan detail.

## 4. Halaman Cek Harga
Tujuan: menemukan informasi produk secepat mungkin.
- Search nama/kode/barcode.
- Scan barcode.
- Hasil: nama, kode/barcode, satuan, stok, harga jual.
- Aksi opsional: masukkan produk ke Transaksi.
- Tidak ada Edit/Hapus/Import/Export.

## 5. Halaman Daftar Item
Tujuan: administrasi master produk.
- Search.
- Daftar produk.
- Tambah Item.
- Edit Item.
- Hapus Item.
- Import CSV: Tambah + Update, Tambah saja, Update saja, Ganti semua.
- Export CSV.
- Form produk: Nama, Kode, Barcode, Jenis, Merek, Satuan, Harga Jual, Stok, Rak.
- Seluruh operasi menggunakan database native persistent.

## 6. Halaman Transaksi
Tujuan: alur kasir.
- Tambah/cari barang.
- Keranjang.
- Qty.
- Total.
- Uang dibayar.
- Kembalian.
- Simpan transaksi.
- Riwayat/backup/restore tetap tersedia tetapi tidak menjadi konten utama halaman.

## 7. Halaman Barang Keluar
Tujuan: mencatat barang keluar berdasarkan Harga Beli.
- Search produk.
- Scan barcode.
- Tambah ke daftar barang keluar.
- Qty dan subtotal.
- Total qty dan jumlah harga.
- Simpan, cetak, PDF.
- Riwayat barang keluar.

## 8. Halaman Laporan
Tujuan: membaca dan menghasilkan laporan.
- Pilih tanggal.
- Ringkasan transaksi/penjualan.
- Detail item.
- Preview.
- PDF, Excel, cetak.
- Tidak bercampur dengan master data.

## 9. Visual system
Liquid Glass digunakan secara selektif:
- bottom navigation
- floating/overlay
- modal
- scanner

Surface solid/soft digunakan untuk:
- form
- daftar data
- keranjang
- laporan

Identitas:
- Emerald/green sebagai primary.
- Neutral/mint sebagai background.
- Merah hanya untuk destructive action/status.
- Biru/oranye sebagai aksen fungsi, bukan warna utama.

## 10. Mobile rules
- Tidak ada horizontal overflow.
- Tombol minimal nyaman disentuh.
- Heading dan field tidak bertumpuk.
- Daftar memakai satu kolom pada layar kecil.
- Form CRUD menjadi single-column di layar <=430px.
- Bottom navigation tetap terlihat tetapi tidak menutup konten.
- Halaman selalu dimulai dari posisi scroll atas saat navigasi.

## 11. Data architecture
Single source of truth untuk produk adalah SQLite native persistent.
- Cek Harga membaca database.
- Daftar Item membaca dan mengubah database.
- Transaksi memakai data produk yang sama.
- Barang Keluar memakai data produk yang sama.
- Embedded ITEMS hanya menjadi fallback/seed, bukan sumber CRUD utama.

## 12. Acceptance checklist
- Beranda tidak menampilkan CRUD/import/search produk.
- Cek Harga tidak menampilkan CRUD.
- Daftar Item memiliki CRUD + Import/Export.
- Transaksi memiliki keranjang dan pembayaran.
- Barang Keluar memiliki halaman sendiri.
- Laporan memiliki halaman sendiri.
- Tidak ada dua fungsi utama yang tampil bersamaan.
- Navigasi back dan bottom navigation konsisten.
- Database product count konsisten di seluruh halaman.
