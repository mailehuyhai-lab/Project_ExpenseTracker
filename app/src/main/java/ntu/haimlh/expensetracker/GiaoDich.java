package ntu.haimlh.expensetracker;

// Class giao dịch: mỗi object ứng với 1 dòng trong bảng giao_dich của SQLite
public class GiaoDich {

    public static final int LOAI_CHI = 0;   // chi tiêu
    public static final int LOAI_THU = 1;   // thu nhập

    private int id;              // khoá chính tự tăng
    private String ten;          // tên / ghi chú, VD "Cà phê với bạn"
    private double soTien;       // luôn dương, dấu +/- do loai quyết định
    private int loai;            // LOAI_THU hoặc LOAI_CHI
    private String danhMuc;      // VD "Ăn uống", "Lương"
    private String ngay;         // "yyyy-MM-dd" để sắp xếp được luôn
    private long createdAt;      // thời điểm tạo, xếp thứ tự các giao dịch cùng ngày

    public GiaoDich() {
    }

    // thêm mới: chưa có id, SQLite tự sinh
    public GiaoDich(String ten, double soTien, int loai, String danhMuc, String ngay) {
        this.ten = ten;
        this.soTien = soTien;
        this.loai = loai;
        this.danhMuc = danhMuc;
        this.ngay = ngay;
        this.createdAt = System.currentTimeMillis();
    }

    // đọc từ database lên (đã có id)
    public GiaoDich(int id, String ten, double soTien, int loai,
                    String danhMuc, String ngay, long createdAt) {
        this.id = id;
        this.ten = ten;
        this.soTien = soTien;
        this.loai = loai;
        this.danhMuc = danhMuc;
        this.ngay = ngay;
        this.createdAt = createdAt;
    }

    // ------------------------- Getter -------------------------

    public int getId() {
        return id;
    }

    public String getTen() {
        return ten;
    }

    public double getSoTien() {
        return soTien;
    }

    public int getLoai() {
        return loai;
    }

    public String getDanhMuc() {
        return danhMuc;
    }

    public String getNgay() {
        return ngay;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // ------------------------- Tiện ích -------------------------

    // có phải giao dịch thu không
    public boolean isThu() {
        return loai == LOAI_THU;
    }

    // tên hiện lên màn hình, nếu để trống thì lấy tên danh mục
    public String getTenHienThi() {
        if (ten != null && !ten.trim().isEmpty()) {
            return ten;
        }
        return DanhMuc.timTheoTen(danhMuc, loai).getTen();
    }
}
