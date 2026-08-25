package ntu.haimlh.expensetracker;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BỘ PHÂN TÍCH CÂU GIỌNG NÓI TIẾNG VIỆT THÀNH GIAO DỊCH.
 * <p>
 * Nhận văn bản do Google Speech-to-Text trả về (VD: "chi 50k tiền ăn sáng",
 * "thu một trăm nghìn", "tiêu 2 triệu rưỡi mua sách") và tách thành 3 thành phần:
 * <ol>
 *     <li><b>Loại giao dịch</b> - tìm từ khoá ("thu", "chi", "tiêu", "trả"...)
 *         xuất hiện SỚM NHẤT trong câu.</li>
 *     <li><b>Số tiền</b> - đọc được cả dạng chữ số ("100000", "50k", "2tr5"),
 *         dạng chữ tiếng Việt ("một trăm nghìn", "hai triệu rưỡi",
 *         "chín trăm chín mươi chín nghìn") và tiếng lóng ("cành", "lít", "củ").</li>
 *     <li><b>Nội dung</b> - toàn bộ phần chữ còn lại sau khi cắt bỏ từ khoá
 *         loại và cụm số tiền.</li>
 * </ol>
 * <p>
 * Lớp này là Java THUẦN (không import gì của Android) nên có thể viết Unit Test
 * hoặc chạy thử trực tiếp trên máy tính mà không cần máy ảo Android.
 */
public final class VoiceParser {

    /** Trả về khi không tìm thấy từ khoá loại giao dịch nào trong câu. */
    public static final int LOAI_KHONG_RO = -1;

    // ==================================================================
    //  BỘ TỪ VỰNG (key đã viết thường + bỏ dấu để so khớp nhanh)
    // ==================================================================

    /**
     * Từ khoá báo hiệu TIỀN VÀO. Từ khoá nhiều từ đặt trước: khi 2 từ khoá
     * cùng khớp tại một vị trí thì ưu tiên từ DÀI hơn ("thu nhap" thắng "thu").
     */
    private static final String[] TU_KHOA_THU = {
            "thu nhap", "thu ve", "nhan duoc", "duoc tang", "thu", "luong"};

    /** Từ khoá báo hiệu TIỀN RA. */
    private static final String[] TU_KHOA_CHI = {
            "chi tieu", "thanh toan", "chi", "tieu", "tra", "mua",
            "mat", "nop", "an", "uong"};

    /** Giá trị các CHỮ SỐ tiếng Việt (đã bỏ dấu). */
    private static final Map<String, Double> CHU_SO = new HashMap<>();

    /**
     * Hệ số nhân của các ĐƠN VỊ tiền.
     * Gồm cả tiếng lóng: k/nghìn/ngàn/lít = nghìn, cành = trăm nghìn,
     * củ/cây/tr = triệu.
     */
    private static final Map<String, Double> DON_VI = new HashMap<>();

    /** Từ gây nhiễu bỏ qua khi đọc cụm tiền ("lẻ", "linh", "đồng"...). */
    private static final Set<String> TU_BO_QUA = new HashSet<>();

    static {
        CHU_SO.put("khong", 0d);
        CHU_SO.put("mot", 1d);
        CHU_SO.put("hai", 2d);
        CHU_SO.put("ba", 3d);
        CHU_SO.put("bon", 4d);
        CHU_SO.put("tu", 4d);      // "hai tư" = 24 (kiểu nói miền Nam)
        CHU_SO.put("nam", 5d);
        CHU_SO.put("lam", 5d);     // "lăm" chỉ đứng sau "mười/mươi"
        CHU_SO.put("sau", 6d);
        CHU_SO.put("bay", 7d);
        CHU_SO.put("tam", 8d);
        CHU_SO.put("chin", 9d);
        // "mười" và "rưỡi" xử lý ĐẶC BIỆT trong thuật toán nên không nằm bảng này

        DON_VI.put("chuc", 10d);
        DON_VI.put("tram", 100d);
        DON_VI.put("k", 1_000d);
        DON_VI.put("nghin", 1_000d);
        DON_VI.put("ngan", 1_000d);
        DON_VI.put("lit", 1_000d);        // "bảy lít" = 7.000đ
        DON_VI.put("canh", 100_000d);     // "năm cành" = 500.000đ
        DON_VI.put("trieu", 1_000_000d);
        DON_VI.put("tr", 1_000_000d);     // "2 tr"
        DON_VI.put("cu", 1_000_000d);     // "năm củ"
        DON_VI.put("cay", 1_000_000d);
        DON_VI.put("ty", 1_000_000_000d);

        TU_BO_QUA.add("le");       // "mười lẻ năm"
        TU_BO_QUA.add("linh");     // "một trăm linh năm"
        TU_BO_QUA.add("dong");     // "nghìn đồng"
        TU_BO_QUA.add("vnd");
        TU_BO_QUA.add("d");        // chữ "đ" (sau khi bỏ dấu thành "d")
        TU_BO_QUA.add("gia");      // "giá ..."
        TU_BO_QUA.add("tien");     // "tiền cơm" - "tiền" không phải nội dung số
    }

    /**
     * Token chứa chữ số: nhóm 1 là phần số ("50000", "1.5"),
     * nhóm 2 là đơn vị dính liền ("k", "tr"), nhóm 3 là số đuôi ("2tr<b>5</b>").
     */
    private static final Pattern MA_TOKEN_SO =
            Pattern.compile("(\\d+(?:\\.\\d+)?)([a-z]*)(\\d*)");

    private VoiceParser() {
        // Lớp tiện ích chỉ chứa hàm static, không cho khởi tạo
    }

    // ==================================================================
    //  KẾT QUẢ PARSE
    // ==================================================================

    /** Đối tượng chứa 3 thông tin tách được từ một câu giọng nói. */
    public static class KetQua {
        /** {@link GiaoDich#LOAI_THU}, {@link GiaoDich#LOAI_CHI} hoặc {@link #LOAI_KHONG_RO}. */
        public int loai = LOAI_KHONG_RO;

        /** Số tiền đọc được (0 = không tìm thấy). Hợp lệ thì luôn > 0. */
        public long soTien = 0;

        /** Phần nội dung còn lại, GIỮ NGUYÊN dấu tiếng Việt của câu gốc. */
        public String noiDung = "";

        /** Đủ cả loại lẫn số tiền thì mới đủ dữ kiện tạo giao dịch tự động. */
        public boolean hopLe() {
            return loai != LOAI_KHONG_RO && soTien > 0;
        }
    }

    /** Một đoạn tiền tìm được: giá trị + chỉ số token bắt đầu/kết thúc. */
    private static class DoanTien {
        final long giaTri;
        final int tu;
        final int den;

        DoanTien(long giaTri, int tu, int den) {
            this.giaTri = giaTri;
            this.tu = tu;
            this.den = den;
        }
    }

    // ==================================================================
    //  API CHÍNH CHO ACTIVITY GỌI
    // ==================================================================

    /**
     * Phân tích câu giọng nói gốc (có thể chứa dấu câu, hoa/thường lẫn lộn).
     *
     * @param cauGoc văn bản nhận từ RecognizerIntent
     * @return {@link KetQua} - không bao giờ trả về null
     */
    public static KetQua parse(String cauGoc) {
        KetQua kq = new KetQua();

        String[][] cap = tachToken(cauGoc);
        String[] goc = cap[0];   // token GIỮ dấu (ghép nội dung hiển thị)
        String[] hoa = cap[1];   // token thường + bỏ dấu (chỉ dùng để so khớp)
        if (goc.length == 0) {
            return kq;
        }

        // ---- BƯỚC 1: TÌM LOẠI GIAO DỊCH ----
        int[] ttLoai = timTuKhoaLoai(hoa);
        kq.loai = ttLoai[2];

        // ---- BƯỚC 2: TÌM SỐ TIỀN ----
        // Quét bắt đầu NGAY SAU từ khoá loại (nếu không có loại thì quét từ đầu).
        int batDauTim = ttLoai[0] < 0 ? 0 : ttLoai[0] + ttLoai[1];
        DoanTien doanTien = timSoTien(hoa, batDauTim);
        kq.soTien = doanTien.giaTri;

        // ---- BƯỚC 3: GHÉP NỘI DUNG ----
        // Mọi token KHÔNG thuộc khoảng (từ khoá loại) và (cụm số tiền) là nội dung.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < goc.length; i++) {
            boolean trongKhoangLoai = ttLoai[0] >= 0
                    && i >= ttLoai[0] && i < ttLoai[0] + ttLoai[1];
            boolean trongKhoangTien = doanTien.giaTri > 0
                    && i >= doanTien.tu && i <= doanTien.den;
            if (trongKhoangLoai || trongKhoangTien) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(goc[i]);
        }
        kq.noiDung = sb.toString().trim();

        return kq;
    }

    // ==================================================================
    //  CHUẨN HOÁ VĂN BẢN
    // ==================================================================

    /**
     * Tách câu thành HAI mảng token song song cùng độ dài:
     * bản gốc giữ dấu và bản chuẩn hoá (thường + bỏ dấu).
     * Xử lý theo token giúp khi ghép nội dung ta lấy đúng bản gốc có dấu đẹp.
     * <p>
     * Xử lý dấu chấm/phẩy trong số trước khi xoá dấu câu:
     * <ul>
     *     <li>"50.000" / "50,000" (đúng 3 chữ số phía sau) -> xoá dấu: "50000"</li>
     *     <li>"1.5" / "1,5" (1-2 chữ số phía sau) -> thay bằng từ "phay"</li>
     * </ul>
     */
    private static String[][] tachToken(String cau) {
        if (cau == null || cau.trim().isEmpty()) {
            return new String[][]{new String[0], new String[0]};
        }

        String sach = cau
                .replaceAll("(?<=[0-9])\\.(?=[0-9]{3}([^0-9]|$))", "")
                .replaceAll("(?<=[0-9]),(?=[0-9]{3}([^0-9]|$))", "")
                .replaceAll("(?<=[0-9])[.,](?=[0-9]{1,2}([^0-9]|$))", " phay ")
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .trim();

        List<String> dsGoc = new ArrayList<>();
        List<String> dsHoa = new ArrayList<>();
        for (String tu : sach.split("\\s+")) {
            dsGoc.add(tu);
            dsHoa.add(boDau(tu.toLowerCase(Locale.ROOT)));
        }
        return new String[][]{
                dsGoc.toArray(new String[0]),
                dsHoa.toArray(new String[0])};
    }

    /**
     * Bỏ dấu thanh tiếng Việt: "Triệu" -> "trieu".
     * Kỹ thuật: tách ký tự Unicode thành (ký tự gốc + dấu) bằng NFD, xoá hết dấu,
     * rồi đổi "đ" thành "d" (chữ đ không tách rời được bằng NFD).
     */
    private static String boDau(String tu) {
        String khongDau = Normalizer.normalize(tu, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return khongDau.replace('đ', 'd');
    }

    // ==================================================================
    //  BƯỚC 1: DÒ TỪ KHOÁ LOẠI GIAO DỊCH
    // ==================================================================

    /**
     * Tìm từ khoá xuất hiện SỚM NHẤT trong câu (theo vị trí token);
     * nếu nhiều từ khoá cùng vị trí thì từ khoá dài hơn thắng.
     *
     * @return {vị trí bắt đầu, độ dài (số token), loại}
     *         hoặc {-1, 0, LOAI_KHONG_RO} nếu câu không nói thu/chi
     */
    private static int[] timTuKhoaLoai(String[] hoa) {
        int viTriNhat = -1;
        int daiNhat = 0;
        boolean laThu = false;

        // Một lượt quét duy nhất qua cả 2 bộ: giữ vị trí SỚM NHẤT,
        // bằng vị trí thì ưu tiên cụm DÀI HƠN
        String[][] haiBo = {TU_KHOA_THU, TU_KHOA_CHI};
        for (int b = 0; b < haiBo.length; b++) {
            boolean boThu = (b == 0);
            for (String cum : haiBo[b]) {
                String[] phan = cum.split(" ");
                for (int i = 0; i <= hoa.length - phan.length; i++) {
                    if (!khopCuem(hoa, i, phan)) {
                        continue;
                    }
                    if (viTriNhat < 0 || i < viTriNhat || (i == viTriNhat && phan.length > daiNhat)) {
                        viTriNhat = i;
                        daiNhat = phan.length;
                        laThu = boThu;
                    }
                }
            }
        }

        if (viTriNhat < 0) {
            return new int[]{-1, 0, LOAI_KHONG_RO};
        }
        return new int[]{viTriNhat, daiNhat,
                laThu ? GiaoDich.LOAI_THU : GiaoDich.LOAI_CHI};
    }

    /** @return true nếu cụm {@code phan} khớp đủ các token bắt đầu tại {@code viTri}. */
    private static boolean khopCuem(String[] hoa, int viTri, String[] phan) {
        for (int k = 0; k < phan.length; k++) {
            if (!hoa[viTri + k].equals(phan[k])) {
                return false;
            }
        }
        return true;
    }

    // ==================================================================
    //  BƯỚC 2: ĐỌC SỐ TIỀN TRONG DÃY TOKEN
    // ==================================================================

    /** Quét từng vị trí, trả về cụm tiền ĐẦU TIÊN đọc được. */
    private static DoanTien timSoTien(String[] hoa, int tuBatDau) {
        for (int i = tuBatDau; i < hoa.length; i++) {
            DoanTien dt = docCumTien(hoa, i);
            if (dt != null) {
                return dt;
            }
        }
        return new DoanTien(0, -1, -2);
    }

    /**
     * THUẬT TOÁN ĐỌC MỘT CỤM TIỀN bắt đầu đúng tại vị trí {@code batDau}.
     * <p>
     * Mô hình theo cách người Việt đọc số, mỗi NHÓM có cấu trúc
     * <b>[hàng trăm] + [phần dưới &lt; 100]</b> rồi bị ĐƠN VỊ LỚN chốt:
     * <ul>
     *     <li>{@code nhomTram} - giá trị hàng trăm của nhóm hiện tại (0..900)</li>
     *     <li>{@code duoi} - phần dưới 100 đang dồn ("hai mươi lăm" = 25)</li>
     *     <li>{@code tong} - các nhóm ĐÃ được đơn vị lớn (nghìn/triệu/tỷ) chốt</li>
     * </ul>
     * VD "chín trăm chín mươi chín nghìn": nhomTram=900, duoi=99,
     * gặp "nghìn" -> (900+99)×1000 = 999.000. Còn "một triệu năm trăm nghìn":
     * nhóm 1 chốt 1.000.000, nhóm 2 (nhomTram=500) chốt 500.000.
     * Gặp từ lạ (động từ, món ăn...) thì DỪNG - nhờ vậy số đếm phía sau
     * ("mua 2 quyển sách") không bị nuốt vào số tiền.
     *
     * @return đoạn tiền đọc được, hoặc null nếu tại đây không phải số tiền
     */
    private static DoanTien docCumTien(String[] hoa, int batDau) {
        double tong = 0;          // tổng các nhóm đã chốt bởi đơn vị lớn
        double nhomTram = 0;      // hàng trăm của nhóm hiện tại
        double duoi = 0;          // phần < 100 của nhóm hiện tại
        double donViTruoc = 0;    // đơn vị lớn vừa chốt gần nhất (dùng cho "rưỡi")
        boolean coDonVi = false;  // đã gặp đơn vị chưa (quyết định nhân bù "nghìn" ngầm)
        boolean soNguyenVuaRoi = false; // token trước là chữ số thuần (ghép "50"+"000")
        boolean cheDoPhay = false;      // đang đọc thập phân sau từ "phẩy"
        int soLeDaDoc = 0;              // số chữ số đã đọc sau "phẩy"
        int cuoiYNghia = batDau - 1;    // token CUỐI CÙNG có góp giá trị vào số tiền
        int j = batDau;

        while (j < hoa.length) {
            String t = hoa[j];

            // ---- A. CHỮ SỐ VIẾT BẰNG CHỮ (một, hai...) ----
            if (CHU_SO.containsKey(t)) {
                double v = CHU_SO.get(t);
                if (cheDoPhay) {
                    // "một phẩy năm" -> 1.5: chữ số đầu sau "phẩy" là phần MỚI của nhóm,
                    // các chữ số sau cộng dồn theo hàng (5 -> 0,5; rồi "2" -> 0,52)
                    duoi = duoi * Math.pow(10, -soLeDaDoc)
                            + v / Math.pow(10, soLeDaDoc + 1);
                    soLeDaDoc++;
                } else if (t.equals("khong")) {
                    // "không" chỉ có ý nghĩa trong "linh/ba mươi không..." -> bỏ qua
                } else {
                    duoi += v;   // "mười hai" -> 10+2; "hai mươi lăm" -> 20 rồi +5
                }
                cuoiYNghia = j;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- B. "MƯỜI" / "MƯƠI": nhân 10 phần dưới ----
            if (t.equals("muoi")) {
                duoi = (duoi == 0 ? 10 : duoi * 10);
                cuoiYNghia = j;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- C. "CHỤC": cũng là ×10 phần dưới ("ba chục nghìn" = 30.000) ----
            if (t.equals("chuc")) {
                duoi = (duoi == 0 ? 10 : duoi * 10);
                cuoiYNghia = j;
                coDonVi = true;
                j++;
                continue;
            }

            // ---- D. "TRĂM": nâng phần dồn hiện có lên hàng trăm ----
            if (t.equals("tram")) {
                // Nếu nhóm cũ đã đầy ("...trăm") mà lại gặp thêm "trăm" nghĩa là
                // mở nhóm mới: chốt nhóm cũ ×1000 vào tổng (hiếm gặp, phòng hờ)
                if (nhomTram > 0 && duoi >= 100) {
                    tong += (nhomTram + duoi) * 1000;
                    nhomTram = 0;
                    duoi = 0;
                }
                nhomTram += (duoi == 0 ? 1 : duoi) * 100;
                duoi = 0;
                coDonVi = true;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- E. ĐƠN VỊ LỚN (nghìn, k, lít, triệu, tr, củ, tỷ...) ----
            // Chốt nhóm hiện tại: (giá trị nhóm, trống thì lấy 1) × hệ số đơn vị.
            // Nếu đang dở phần thập phân ("1 phẩy 5 | triệu") thì phần lẻ được nhân
            // theo đơn vị luôn: 1.5 × 1.000.000 = 1.500.000.
            Double heSoLon = DON_VI.get(t);
            boolean donViNho = t.equals("chuc") || t.equals("tram");
            if (heSoLon != null && !donViNho && !t.equals("canh")) {
                double giaTriNhom = nhomTram + duoi;
                tong += (giaTriNhom == 0 ? 1 : giaTriNhom) * heSoLon;
                nhomTram = 0;
                duoi = 0;
                donViTruoc = heSoLon;
                cuoiYNghia = j;
                coDonVi = true;
                cheDoPhay = false;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- F. "CÀNH" = trăm nghìn, coi như đơn vị lớn riêng ----
            if (t.equals("canh")) {
                double giaTriNhom = nhomTram + duoi;
                tong += (giaTriNhom == 0 ? 1 : giaTriNhom) * DON_VI.get("canh");
                nhomTram = 0;
                duoi = 0;
                donViTruoc = DON_VI.get("canh");
                cuoiYNghia = j;
                coDonVi = true;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- G. "RƯỠI" = nửa đơn vị lớn đứng trước ----
            // "hai triệu rưỡi" = 2.500.000. Nếu chưa có đơn vị lớn ("năm rưỡi") thì cộng 0,5.
            if (t.equals("ruoi")) {
                double giaTriNhom = nhomTram + duoi;
                if (donViTruoc >= 1_000d && giaTriNhom == 0) {
                    tong += donViTruoc / 2;
                    donViTruoc = 0;
                } else {
                    duoi += 0.5;
                }
                cuoiYNghia = j;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- H. TỪ NHIỄU BỎ QUA (lẻ, linh, đồng, tiền...) ----
            if (TU_BO_QUA.contains(t)) {
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- I. CHỮ SỐ Ả RẬP ("100000", "50k", "2tr5") ----
            Matcher m = MA_TOKEN_SO.matcher(t);
            if (m.matches()) {
                String phanSo = m.group(1);
                String hauTo = m.group(2);
                String duoiSo = m.group(3);
                Double heSo = hauTo.isEmpty() ? null : DON_VI.get(hauTo);

                if (heSo != null) {
                    // Số + đơn vị dính liền: "50k", "2tr", "2tr5"
                    double giaTri = docGiaTriSo(phanSo);
                    if (!duoiSo.isEmpty()) {
                        int bu = Integer.parseInt(duoiSo);
                        giaTri += (heSo >= 1_000_000d) ? bu / 10d : bu * 100d;
                    }
                    tong += giaTri * heSo;
                    donViTruoc = heSo;
                    cuoiYNghia = j;
                    coDonVi = true;
                    nhomTram = 0;
                    duoi = 0;
                    soNguyenVuaRoi = false;
                    j++;
                    continue;
                }

                double giaTri = docGiaTriSo(phanSo);
                if (giaTri < 0) {
                    break;   // định dạng lỗi -> không phải cụm tiền
                }
                if (cheDoPhay) {
                    // Phần lẻ viết bằng CHỮ SỐ: "1" + "phẩy" + "5" -> 1 + 5/10
                    duoi = duoi * Math.pow(10, -soLeDaDoc)
                            + giaTri / Math.pow(10, soLeDaDoc + 1);
                    soLeDaDoc++;
                } else if (soNguyenVuaRoi && (nhomTram + duoi) > 0) {
                    // Hai cụm chữ số đứng cạnh: "50" "000" -> ghép thành 50000
                    double hienTai = nhomTram + duoi;
                    double moi = hienTai * Math.pow(10, phanSo.length())
                            + Double.parseDouble(phanSo.replace(".", ""));
                    nhomTram = 0;
                    duoi = moi;
                } else if (tong > 0 && donViTruoc >= 1_000_000d && giaTri < 1000) {
                    // "1 triệu 500" -> phần bù nhỏ sau đơn vị lớn
                    duoi = giaTri;
                } else if (tong == 0 && nhomTram == 0 && duoi == 0) {
                    duoi = giaTri;   // số đầu tiên của cụm
                } else {
                    break;   // số thứ hai độc lập ("50k mua 2 quyển") -> dừng
                }
                cuoiYNghia = j;
                soNguyenVuaRoi = true;
                j++;
                continue;
            }

            // ---- J. TỪ "PHẨY": chuyển sang đọc thập phân ----
            if (t.equals("phay")) {
                cheDoPhay = true;
                soLeDaDoc = 0;
                j++;
                continue;
            }

            // ---- Từ lạ (động từ, tên món...) -> kết thúc cụm tiền ----
            break;
        }

        if (j == batDau) {
            return null;   // không đọc được token số nào
        }

        double giaTri = tong + nhomTram + duoi;

        // Phần bù nhỏ sau đơn vị lớn: "1 triệu 500" -> 1.000.000 + 500×1000
        // (bỏ qua nếu giá trị là số lẻ thập phân kiểu 2.5)
        if (!leThapPhan(duoi) && tong > 0 && donViTruoc >= 1_000_000d
                && duoi > 0 && duoi < 1000 && nhomTram == 0) {
            giaTri = tong + duoi * (donViTruoc / 1000);
        }

        // QUY TẮC HỘI THOẠI: nói "năm", "năm trăm", "ba chục" khi ghi tiền
        // ai cũng hiểu ngầm là ...nghìn. Giá trị < 1000 và chưa có đơn vị
        // nghìn trở lên thì tự nhân 1000 ("chi năm" = 5.000đ).
        if (giaTri > 0 && giaTri < 1000 && (!coDonVi || donViTruoc <= 100)) {
            giaTri *= 1000;
        }

        long ketQua = Math.round(giaTri);
        if (ketQua <= 0 || ketQua > 99_999_999_999L) {
            return null;   // ngoài khoảng hợp lệ coi như không đọc được
        }
        return new DoanTien(ketQua, batDau, cuoiYNghia);
    }

    /** Đọc chuỗi số "50000" hoặc "1.5" thành số; lỗi định dạng trả về -1. */
    private static double docGiaTriSo(String chuoiSo) {
        try {
            int viTriCham = chuoiSo.indexOf('.');
            if (viTriCham >= 0 && chuoiSo.length() - viTriCham - 1 <= 2) {
                return Double.parseDouble(chuoiSo);   // số thập phân: 1.5
            }
            return Double.parseDouble(chuoiSo.replace(".", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** @return true nếu số có phần lẻ thập phân (2.5 -> true, 25 -> false). */
    private static boolean leThapPhan(double so) {
        return so != Math.floor(so) || Double.isInfinite(so);
    }
}
