package ntu.haimlh.expensetracker;

import android.content.Context;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * LỚP TIỆN ÍCH - Định dạng tiền tệ và ngày tháng theo chuẩn Việt Nam.
 * <p>
 * Đặt riêng ra một lớp để tránh lặp code ở Adapter và các Activity.
 * Lớp này chỉ chứa hàm static nên được khai báo final + constructor private.
 */
public final class FormatUtils {

    /** Định dạng ngày lưu trong SQLite (sắp xếp được bằng chuỗi). */
    public static final String PATTERN_DB = "yyyy-MM-dd";

    /** Định dạng ngày hiển thị cho người dùng. */
    public static final String PATTERN_HIEN_THI = "dd/MM/yyyy";

    private FormatUtils() {
        // Không cho phép khởi tạo đối tượng
    }

    // ==================================================================
    //  1. TIỀN TỆ
    // ==================================================================

    /**
     * Tạo bộ định dạng số dùng dấu CHẤM làm dấu phân cách hàng nghìn.
     * Không dùng Locale mặc định của máy để kết quả luôn giống nhau
     * trên mọi thiết bị (máy cài tiếng Anh vẫn ra "150.000").
     */
    private static DecimalFormat taoBoDinhDang() {
        DecimalFormatSymbols kyHieu = new DecimalFormatSymbols(Locale.US);
        kyHieu.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", kyHieu);
    }

    /** 150000 -> "150.000" (chỉ số, không kèm đơn vị). */
    public static String dinhDangSo(double soTien) {
        return taoBoDinhDang().format(Math.abs(soTien));
    }

    /** 150000 -> "150.000 đ". */
    public static String dinhDangTien(double soTien) {
        return dinhDangSo(soTien) + " đ";
    }

    /**
     * Định dạng tiền kèm dấu theo loại giao dịch.
     *
     * @param laThu true -> "+150.000 đ" (thu nhập); false -> "-150.000 đ" (chi tiêu)
     */
    public static String dinhDangTienCoDau(double soTien, boolean laThu) {
        return (laThu ? "+" : "-") + dinhDangTien(soTien);
    }

    /**
     * Định dạng số dư: số dư âm sẽ có dấu trừ ở trước.
     */
    public static String dinhDangSoDu(double soDu) {
        return (soDu < 0 ? "-" : "") + dinhDangTien(soDu);
    }

    /** Bỏ mọi ký tự không phải chữ số. VD: "1.500.000 đ" -> "1500000". */
    public static String chiLaySo(String chuoi) {
        if (chuoi == null) {
            return "";
        }
        return chuoi.replaceAll("[^0-9]", "");
    }

    /**
     * Thêm dấu chấm phân cách hàng nghìn cho chuỗi chỉ gồm chữ số.
     * Dùng trong TextWatcher của ô nhập số tiền. VD: "1500000" -> "1.500.000".
     */
    public static String themDauPhanCach(String chuoiSo) {
        String so = chiLaySo(chuoiSo);
        if (so.isEmpty()) {
            return "";
        }
        // Giới hạn 15 chữ số để không tràn kiểu long khi parse
        if (so.length() > 15) {
            so = so.substring(0, 15);
        }
        return taoBoDinhDang().format(Long.parseLong(so));
    }

    // ==================================================================
    //  2. NGÀY THÁNG
    // ==================================================================

    /** @return ngày hôm nay theo định dạng "yyyy-MM-dd". */
    public static String ngayHomNay() {
        return new SimpleDateFormat(PATTERN_DB, Locale.US).format(new Date());
    }

    /** Chuyển 3 thành phần ngày/tháng/năm thành chuỗi "yyyy-MM-dd". */
    public static String taoNgayDb(int nam, int thang, int ngay) {
        return String.format(Locale.US, "%04d-%02d-%02d", nam, thang, ngay);
    }

    /** "2026-08-21" -> "21/08/2026". Nếu dữ liệu lỗi thì trả về nguyên chuỗi. */
    public static String doiSangHienThi(String ngayDb) {
        Date date = parse(ngayDb);
        if (date == null) {
            return ngayDb == null ? "" : ngayDb;
        }
        return new SimpleDateFormat(PATTERN_HIEN_THI, Locale.US).format(date);
    }

    /**
     * Hiển thị ngày thân thiện cho người dùng:
     * "Hôm nay", "Hôm qua" hoặc "21/08/2026".
     */
    public static String hienThiNgayThanThien(Context context, String ngayDb) {
        if (ngayDb != null) {
            String homNay = ngayHomNay();
            if (ngayDb.equals(homNay)) {
                return context.getString(R.string.hom_nay);
            }
            if (ngayDb.equals(congNgay(homNay, -1))) {
                return context.getString(R.string.hom_qua);
            }
        }
        return doiSangHienThi(ngayDb);
    }

    /** Cộng/trừ số ngày vào một chuỗi ngày "yyyy-MM-dd". */
    public static String congNgay(String ngayDb, int soNgay) {
        Date date = parse(ngayDb);
        if (date == null) {
            return ngayDb;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, soNgay);
        return new SimpleDateFormat(PATTERN_DB, Locale.US).format(cal.getTime());
    }

    /** Chuỗi "Tháng 8, 2026" hiển thị ở đầu màn hình chính. */
    public static String thangNamHienTai(Calendar cal) {
        return "Tháng " + (cal.get(Calendar.MONTH) + 1) + ", " + cal.get(Calendar.YEAR);
    }

    /** Parse chuỗi "yyyy-MM-dd" thành Date, trả về null nếu sai định dạng. */
    private static Date parse(String ngayDb) {
        if (ngayDb == null || ngayDb.trim().isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat(PATTERN_DB, Locale.US).parse(ngayDb.trim());
        } catch (ParseException e) {
            return null;
        }
    }
}
