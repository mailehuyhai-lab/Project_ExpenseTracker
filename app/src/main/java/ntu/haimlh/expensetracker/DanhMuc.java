package ntu.haimlh.expensetracker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * MODEL - Danh mục giao dịch (Category).
 * <p>
 * Mỗi danh mục gồm 3 phần: tên hiển thị, icon Emoji trực quan và
 * một màu pastel nhạt dùng làm nền hình tròn cho icon đó.
 * Danh mục được lưu vào SQLite dưới dạng TEXT (tên danh mục),
 * nên khi đọc lên chỉ cần tra cứu lại bằng {@link #timTheoTen(String, int)}.
 */
public class DanhMuc {

    private final String ten;   // VD: "Ăn uống"
    private final String icon;  // VD: "🍔"
    private final int mauNen;   // VD: R.color.pastel_orange

    public DanhMuc(String ten, String icon, int mauNen) {
        this.ten = ten;
        this.icon = icon;
        this.mauNen = mauNen;
    }

    public String getTen() {
        return ten;
    }

    public String getIcon() {
        return icon;
    }

    public int getMauNen() {
        return mauNen;
    }

    /** Chuỗi hiển thị trên Chip khi chọn danh mục. VD: "🍔  Ăn uống" */
    public String getNhanChip() {
        return icon + "  " + ten;
    }

    // ==================================================================
    //  DANH SÁCH DANH MỤC CỐ ĐỊNH
    // ==================================================================

    /** Danh mục dành cho giao dịch CHI TIÊU */
    private static final List<DanhMuc> DS_CHI = Collections.unmodifiableList(Arrays.asList(
            new DanhMuc("Ăn uống", "🍔", R.color.pastel_orange),
            new DanhMuc("Mua sắm", "🛒", R.color.pastel_pink),
            new DanhMuc("Di chuyển", "🚗", R.color.pastel_blue),
            new DanhMuc("Nhà cửa", "🏠", R.color.pastel_teal),
            new DanhMuc("Hoá đơn", "💡", R.color.pastel_yellow),
            new DanhMuc("Giải trí", "🎮", R.color.pastel_purple),
            new DanhMuc("Sức khoẻ", "🏥", R.color.pastel_red),
            new DanhMuc("Học tập", "📚", R.color.pastel_indigo),
            new DanhMuc("Du lịch", "✈️", R.color.pastel_sky),
            new DanhMuc("Khác", "📦", R.color.pastel_gray)
    ));

    /** Danh mục dành cho giao dịch THU NHẬP */
    private static final List<DanhMuc> DS_THU = Collections.unmodifiableList(Arrays.asList(
            new DanhMuc("Lương", "💸", R.color.pastel_green),
            new DanhMuc("Thưởng", "💰", R.color.pastel_yellow),
            new DanhMuc("Đầu tư", "📈", R.color.pastel_teal),
            new DanhMuc("Kinh doanh", "🏦", R.color.pastel_blue),
            new DanhMuc("Được tặng", "🎁", R.color.pastel_pink),
            new DanhMuc("Khác", "📦", R.color.pastel_gray)
    ));

    /** Danh mục dự phòng khi dữ liệu cũ không khớp danh mục nào. */
    private static final DanhMuc MAC_DINH = new DanhMuc("Khác", "📦", R.color.pastel_gray);

    /**
     * Lấy danh sách danh mục tương ứng với loại giao dịch.
     *
     * @param loai {@link GiaoDich#LOAI_THU} hoặc {@link GiaoDich#LOAI_CHI}
     */
    public static List<DanhMuc> danhSachTheoLoai(int loai) {
        return loai == GiaoDich.LOAI_THU ? DS_THU : DS_CHI;
    }

    /**
     * Tra cứu danh mục theo tên đã lưu trong SQLite.
     * Ưu tiên tìm trong danh sách của đúng loại giao dịch, sau đó tìm ở
     * danh sách còn lại, cuối cùng trả về danh mục "Khác" để không bao giờ null.
     */
    public static DanhMuc timTheoTen(String ten, int loai) {
        if (ten != null && !ten.trim().isEmpty()) {
            DanhMuc tim = timTrongDanhSach(danhSachTheoLoai(loai), ten);
            if (tim != null) {
                return tim;
            }
            // Tìm bổ sung ở danh sách của loại còn lại (phòng dữ liệu cũ)
            int loaiKhac = (loai == GiaoDich.LOAI_THU) ? GiaoDich.LOAI_CHI : GiaoDich.LOAI_THU;
            tim = timTrongDanhSach(danhSachTheoLoai(loaiKhac), ten);
            if (tim != null) {
                return tim;
            }
        }
        return MAC_DINH;
    }

    /** Tìm tuyến tính theo tên, không phân biệt hoa/thường. */
    private static DanhMuc timTrongDanhSach(List<DanhMuc> danhSach, String ten) {
        for (DanhMuc dm : danhSach) {
            if (dm.getTen().equalsIgnoreCase(ten.trim())) {
                return dm;
            }
        }
        return null;
    }
}
