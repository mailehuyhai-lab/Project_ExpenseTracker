package ntu.haimlh.expensetracker;

/**
 * MODEL - Đại diện cho một giao dịch thu/chi trong ứng dụng.
 * <p>
 * Mỗi đối tượng {@link GiaoDich} tương ứng với 1 dòng trong bảng SQLite "giao_dich".
 */
public class GiaoDich {

    /** Giao dịch CHI TIÊU (tiền ra) */
    public static final int LOAI_CHI = 0;

    /** Giao dịch THU NHẬP (tiền vào) */
    public static final int LOAI_THU = 1;

    private int id;              // Khoá chính, tự tăng
    private String ten;          // Tên / ghi chú giao dịch. VD: "Cà phê với bạn"
    private double soTien;       // Số tiền (luôn là số dương, dấu +/- do "loai" quyết định)
    private int loai;            // LOAI_THU hoặc LOAI_CHI
    private String danhMuc;      // Tên danh mục. VD: "Ăn uống", "Lương"
    private String ngay;         // Ngày giao dịch, định dạng "yyyy-MM-dd" để dễ sắp xếp
    private long createdAt;      // Thời điểm tạo (millis) - dùng sắp xếp các giao dịch cùng ngày

    public GiaoDich() {
    }

    /**
     * Constructor dùng khi THÊM MỚI (chưa có id vì SQLite sẽ tự sinh).
     */
    public GiaoDich(String ten, double soTien, int loai, String danhMuc, String ngay) {
        this.ten = ten;
        this.soTien = soTien;
        this.loai = loai;
        this.danhMuc = danhMuc;
        this.ngay = ngay;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Constructor đầy đủ - dùng khi ĐỌC dữ liệu từ SQLite lên.
     */
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

    // ------------------------- Getter / Setter -------------------------

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTen() {
        return ten;
    }

    public void setTen(String ten) {
        this.ten = ten;
    }

    public double getSoTien() {
        return soTien;
    }

    public void setSoTien(double soTien) {
        this.soTien = soTien;
    }

    public int getLoai() {
        return loai;
    }

    public void setLoai(int loai) {
        this.loai = loai;
    }

    public String getDanhMuc() {
        return danhMuc;
    }

    public void setDanhMuc(String danhMuc) {
        this.danhMuc = danhMuc;
    }

    public String getNgay() {
        return ngay;
    }

    public void setNgay(String ngay) {
        this.ngay = ngay;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    // ------------------------- Tiện ích -------------------------

    /** @return true nếu đây là giao dịch thu nhập (tiền vào). */
    public boolean isThu() {
        return loai == LOAI_THU;
    }
}
