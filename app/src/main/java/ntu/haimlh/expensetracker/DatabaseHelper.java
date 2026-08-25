package ntu.haimlh.expensetracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * LỚP TRUY CẬP DỮ LIỆU - Quản lý cơ sở dữ liệu SQLite của ứng dụng.
 * <p>
 * Bảng "giao_dich":
 * <pre>
 * | id | ten | so_tien | loai | danh_muc | ngay | created_at |
 * </pre>
 * - loai: 1 = thu nhập, 0 = chi tiêu <br>
 * - ngay: chuỗi "yyyy-MM-dd" (sắp xếp trực tiếp bằng chuỗi được) <br>
 * - created_at: millis, dùng để sắp xếp các giao dịch trong cùng một ngày
 * <p>
 * LƯU Ý QUAN TRỌNG: không gọi db.close() sau mỗi truy vấn.
 * SQLiteOpenHelper tự giữ và tái sử dụng một kết nối duy nhất; đóng nó
 * liên tục sẽ gây chậm và có thể sinh lỗi "attempt to re-open an already-closed
 * object". Chúng ta chỉ đóng Cursor (dùng try-with-resources).
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "expense_tracker.db";

    /** Version 2: bổ sung 3 cột danh_muc, ngay, created_at */
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

    // ==================================================================
    //  KHỞI TẠO & NÂNG CẤP BẢNG
    // ==================================================================

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

    /**
     * Nâng cấp DB mà KHÔNG làm mất dữ liệu cũ:
     * dùng ALTER TABLE để thêm cột mới thay vì DROP TABLE.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + COLUMN_DANH_MUC + " TEXT NOT NULL DEFAULT 'Khác'");
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + COLUMN_NGAY + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN "
                    + COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0");

            // Các giao dịch cũ chưa có ngày -> gán ngày hôm nay để vẫn hiển thị đẹp
            ContentValues values = new ContentValues();
            values.put(COLUMN_NGAY, FormatUtils.ngayHomNay());
            db.update(TABLE_NAME, values, COLUMN_NGAY + " = ''", null);
        }
    }

    // ==================================================================
    //  THÊM / XOÁ
    // ==================================================================

    /**
     * Thêm một giao dịch mới.
     *
     * @return id vừa được sinh ra, hoặc -1 nếu thất bại.
     */
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

    /**
     * Xoá giao dịch theo id.
     *
     * @return true nếu có đúng 1 dòng bị xoá.
     */
    public boolean deleteGiaoDich(int id) {
        SQLiteDatabase db = getWritableDatabase();
        int soDongBiXoa = db.delete(TABLE_NAME,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        return soDongBiXoa > 0;
    }

    // ==================================================================
    //  ĐỌC DANH SÁCH
    // ==================================================================

    /**
     * Lấy toàn bộ giao dịch, sắp xếp: ngày mới nhất trước,
     * cùng ngày thì giao dịch nhập sau nằm trên.
     */
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

    /** Đọc 1 dòng dữ liệu từ Cursor thành đối tượng GiaoDich. */
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

    // ==================================================================
    //  THỐNG KÊ: TỔNG THU - TỔNG CHI - SỐ DƯ
    // ==================================================================

    /** @return tổng tiền của tất cả giao dịch THU NHẬP. */
    public double getTongThu() {
        return tinhTongTheoLoai(GiaoDich.LOAI_THU);
    }

    /** @return tổng tiền của tất cả giao dịch CHI TIÊU. */
    public double getTongChi() {
        return tinhTongTheoLoai(GiaoDich.LOAI_CHI);
    }

    /**
     * Tính tổng số tiền theo loại giao dịch.
     * Dùng IFNULL để khi bảng rỗng thì SUM trả về 0 thay vì NULL.
     */
    private double tinhTongTheoLoai(int loai) {
        String sql = "SELECT IFNULL(SUM(" + COLUMN_SO_TIEN + "), 0) FROM " + TABLE_NAME
                + " WHERE " + COLUMN_LOAI + " = ?";

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(loai)})) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    /** @return tổng số giao dịch đang có trong DB (dùng cho nhãn "n giao dịch"). */
    public int demSoGiaoDich() {
        return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), TABLE_NAME);
    }
}
