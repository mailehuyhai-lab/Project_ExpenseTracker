package ntu.haimlh.expensetracker;

import android.content.Context;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// Class chứa các hàm định dạng tiền và ngày tháng dùng chung cho cả app
public final class FormatUtils {

    // ngày lưu DB thì "yyyy-MM-dd", hiện lên màn hình thì "dd/MM/yyyy"
    public static final String PATTERN_DB = "yyyy-MM-dd";
    public static final String PATTERN_HIEN_THI = "dd/MM/yyyy";

    private FormatUtils() {
    }

    // bộ format số: cố tình dùng dấu CHẤM phân cách nghìn như kiểu Việt Nam,
    // không theo locale máy (máy cài tiếng Anh sẽ ra dấu phẩy)
    private static DecimalFormat taoBoDinhDang() {
        DecimalFormatSymbols kyHieu = new DecimalFormatSymbols(Locale.US);
        kyHieu.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", kyHieu);
    }

    // 150000 -> "150.000"
    public static String dinhDangSo(double soTien) {
        return taoBoDinhDang().format(Math.abs(soTien));
    }

    // 150000 -> "150.000 đ"
    public static String dinhDangTien(double soTien) {
        return dinhDangSo(soTien) + " đ";
    }

    // tiền kèm dấu: thu là "+150.000 đ", chi là "-150.000 đ"
    public static String dinhDangTienCoDau(double soTien, boolean laThu) {
        return (laThu ? "+" : "-") + dinhDangTien(soTien);
    }

    // số dư âm thì thêm dấu trừ phía trước
    public static String dinhDangSoDu(double soDu) {
        return (soDu < 0 ? "-" : "") + dinhDangTien(soDu);
    }

    // bỏ hết ký tự không phải số, ví dụ "1.500.000 đ" -> "1500000"
    public static String chiLaySo(String chuoi) {
        if (chuoi == null) {
            return "";
        }
        return chuoi.replaceAll("[^0-9]", "");
    }

    // dùng trong TextWatcher ô nhập tiền: gõ "1500000" nó tự thành "1.500.000"
    public static String themDauPhanCach(String chuoiSo) {
        String so = chiLaySo(chuoiSo);
        if (so.isEmpty()) {
            return "";
        }
        // giới hạn 15 số thôi, dài quá parse long bị tràn
        if (so.length() > 15) {
            so = so.substring(0, 15);
        }
        return taoBoDinhDang().format(Long.parseLong(so));
    }

    // hôm nay dạng "yyyy-MM-dd"
    public static String ngayHomNay() {
        return new SimpleDateFormat(PATTERN_DB, Locale.US).format(new Date());
    }

    // ghép năm/tháng/ngày thành chuỗi "yyyy-MM-dd"
    public static String taoNgayDb(int nam, int thang, int ngay) {
        return String.format(Locale.US, "%04d-%02d-%02d", nam, thang, ngay);
    }

    // "2026-08-21" -> "21/08/2026", dữ liệu lỗi thì trả nguyên chuỗi
    public static String doiSangHienThi(String ngayDb) {
        Date date = parse(ngayDb);
        if (date == null) {
            return ngayDb == null ? "" : ngayDb;
        }
        return new SimpleDateFormat(PATTERN_HIEN_THI, Locale.US).format(date);
    }

    // hiện ngày dễ đọc: là hôm nay/hôm qua thì ghi chữ, còn lại hiện dd/MM/yyyy
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

    // cộng/trừ ngày trên chuỗi "yyyy-MM-dd" (dùng để tính hôm qua)
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

    // dòng "Tháng 8, 2026" ở đầu màn hình chính
    public static String thangNamHienTai(Calendar cal) {
        return "Tháng " + (cal.get(Calendar.MONTH) + 1) + ", " + cal.get(Calendar.YEAR);
    }

    // chuyển chuỗi "yyyy-MM-dd" sang Date, sai định dạng thì trả null
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
