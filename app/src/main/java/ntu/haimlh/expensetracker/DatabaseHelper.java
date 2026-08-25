package ntu.haimlh.expensetracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

// Lớp lo toàn bộ phần SQLite của app.
// Bảng giao_dich có các cột: id, ten, so_tien, loai (1=thu, 0=chi),
// danh_muc, ngay ("yyyy-MM-dd"), created_at (mốc thời gian tạo).
// Lưu ý: không cần gọi db.close(), Android tự quản giúp rồi.
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "expense_tracker.db";

    // lên version 2 là lúc thêm 3 cột danh_muc, ngay, created_at
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_NAME = "giao_dich";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TEN = "ten";
    public static final String COLUMN_SO_TIEN = "so_tien";
    public static final String COLUMN_LOAI = "loai";
    public static final String COLUMN_DANH_MUC = "danh_muc";
    public static final String COLUMN_NGAY = "ngay";
    public static final String COLUMN_CREATED_AT = "created_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // tạo bảng khi app chạy lần đầu
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_TEN + " TEXT NOT NULL, "
                + COLUMN_SO_TIEN + " REAL NOT NULL, "
                + COLUMN_LOAI + " INTEGER NOT NULL, "
                + COLUMN_DANH_MUC + " TEXT NOT NULL DEFAULT 'Khác', "
                + COLUMN_NGAY + " TEXT NOT NULL DEFAULT '', "
                + COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0)";
        db.execSQL(createTable);
    }

    // khi nâng version thì thêm cột mới bằng ALTER TABLE,
    // không DROP bảng vì sẽ mất hết dữ liệu người dùng
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + COLUMN_DANH_MUC + " TEXT NOT NULL DEFAULT 'Khác'");
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + COLUMN_NGAY + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0");

            // giao dịch cũ chưa có ngày thì gán tạm ngày hôm nay
            ContentValues values = new ContentValues();
            values.put(COLUMN_NGAY, FormatUtils.ngayHomNay());
            db.update(TABLE_NAME, values, COLUMN_NGAY + " = ''", null);
        }
    }

    // thêm 1 giao dịch, trả về id mới (nếu trả -1 là lỗi)
    public long insertGiaoDich(GiaoDich gd) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TEN, gd.getTen());
        values.put(COLUMN_SO_TIEN, gd.getSoTien());
        values.put(COLUMN_LOAI, gd.getLoai());
        values.put(COLUMN_DANH_MUC, gd.getDanhMuc());
        values.put(COLUMN_NGAY, gd.getNgay());
        values.put(COLUMN_CREATED_AT, gd.getCreatedAt());

        return db.insert(TABLE_NAME, null, values);
    }

    // xoá theo id
    public boolean deleteGiaoDich(int id) {
        SQLiteDatabase db = getWritableDatabase();
        int soDongBiXoa = db.delete(TABLE_NAME,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        return soDongBiXoa > 0;
    }

    // lấy hết giao dịch, ngày mới nhất nằm trên,
    // cùng ngày thì thằng nhập sau đứng trên
    public List<GiaoDich> getAllGiaoDich() {
        List<GiaoDich> danhSach = new ArrayList<>();

        String sql = "SELECT * FROM " + TABLE_NAME
                + " ORDER BY " + COLUMN_NGAY + " DESC, "
                + COLUMN_CREATED_AT + " DESC, "
                + COLUMN_ID + " DESC";

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                danhSach.add(docTuCursor(cursor));
            }
        }
        return danhSach;
    }

    // đọc 1 dòng trong Cursor ra object GiaoDich
    private GiaoDich docTuCursor(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
        String ten = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEN));
        double soTien = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SO_TIEN));
        int loai = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LOAI));
        String danhMuc = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DANH_MUC));
        String ngay = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NGAY));
        long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
        return new GiaoDich(id, ten, soTien, loai, danhMuc, ngay, createdAt);
    }

    // tổng tiền thu nhập
    public double getTongThu() {
        return tinhTongTheoLoai(GiaoDich.LOAI_THU);
    }

    // tổng tiền chi tiêu
    public double getTongChi() {
        return tinhTongTheoLoai(GiaoDich.LOAI_CHI);
    }

    // cộng tổng số tiền theo loại, IFNULL để bảng rỗng ra 0 chứ không phải null
    private double tinhTongTheoLoai(int loai) {
        String sql = "SELECT IFNULL(SUM(" + COLUMN_SO_TIEN + "), 0) FROM " + TABLE_NAME
                + " WHERE " + COLUMN_LOAI + " = ?";

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(loai)})) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    // đếm xem đang có bao nhiêu giao dịch
    public int demSoGiaoDich() {
        return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), TABLE_NAME);
    }
}
