package ntu.haimlh.expensetracker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Class danh mục: gồm tên, icon emoji và màu nền cho icon.
// Trong DB chỉ lưu tên danh mục thôi, cần thì tra lại bằng timTheoTen
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

    // chữ hiện trên chip khi bấm chọn, ví dụ "🍔  Ăn uống"
    public String getNhanChip() {
        return icon + "  " + ten;
    }

    // hai list danh mục cố định, chi và thu khác nhau
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

    private static final List<DanhMuc> DS_THU = Collections.unmodifiableList(Arrays.asList(
            new DanhMuc("Lương", "💸", R.color.pastel_green),
            new DanhMuc("Thưởng", "💰", R.color.pastel_yellow),
            new DanhMuc("Đầu tư", "📈", R.color.pastel_teal),
            new DanhMuc("Kinh doanh", "🏦", R.color.pastel_blue),
            new DanhMuc("Được tặng", "🎁", R.color.pastel_pink),
            new DanhMuc("Khác", "📦", R.color.pastel_gray)
    ));

    // danh mục dự phòng khi không tìm thấy
    private static final DanhMuc MAC_DINH = new DanhMuc("Khác", "📦", R.color.pastel_gray);

    // lấy list danh mục tương ứng loại thu hay chi
    public static List<DanhMuc> danhSachTheoLoai(int loai) {
        return loai == GiaoDich.LOAI_THU ? DS_THU : DS_CHI;
    }

    // tìm danh mục theo tên đã lưu trong DB.
    // tìm trong list đúng loại trước, không thấy thì sang list loại kia,
    // vẫn không thấy thì trả về "Khác" cho chắc, đỡ bị null
    public static DanhMuc timTheoTen(String ten, int loai) {
        if (ten != null && !ten.trim().isEmpty()) {
            DanhMuc tim = timTrongDanhSach(danhSachTheoLoai(loai), ten);
            if (tim != null) {
                return tim;
            }
            // thử tìm thêm ở list loại còn lại
            int loaiKhac = (loai == GiaoDich.LOAI_THU) ? GiaoDich.LOAI_CHI : GiaoDich.LOAI_THU;
            tim = timTrongDanhSach(danhSachTheoLoai(loaiKhac), ten);
            if (tim != null) {
                return tim;
            }
        }
        return MAC_DINH;
    }

    // duyệt list để tìm theo tên, so sánh không phân biệt hoa thường
    private static DanhMuc timTrongDanhSach(List<DanhMuc> danhSach, String ten) {
        for (DanhMuc dm : danhSach) {
            if (dm.getTen().equalsIgnoreCase(ten.trim())) {
                return dm;
            }
        }
        return null;
    }
}
