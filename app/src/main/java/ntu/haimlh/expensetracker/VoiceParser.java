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

// Class đọc câu giọng nói tiếng Việt rồi bóc ra 3 thứ:
// loại giao dịch (thu hay chi), số tiền, và phần nội dung còn lại.
// Ví dụ "chi 50k tiền ăn sáng" -> chi / 50000 / "tiền ăn sáng".
// Chỉ dùng java thuần không dính Android để tiện chạy thử trên máy tính.
public final class VoiceParser {

    // không tìm ra từ khóa thu/chi nào trong câu
    public static final int LOAI_KHONG_RO = -1;

    // các từ khóa nhận biết THU NHẬP (viết thường + bỏ dấu cho dễ so),
    // cụm dài đặt trước để "thu nhap" được ưu tiên hơn "thu"
    private static final String[] TU_KHOA_THU = {
            "thu nhap", "thu ve", "nhan duoc", "duoc tang", "thu", "luong"};

    // các từ khóa nhận biết CHI TIÊU
    private static final String[] TU_KHOA_CHI = {
            "chi tieu", "thanh toan", "chi", "tieu", "tra", "mua",
            "mat", "nop", "an", "uong"};

    // giá trị các chữ số viết bằng chữ (đã bỏ dấu)
    private static final Map<String, Double> CHU_SO = new HashMap<>();

    // hệ số của các đơn vị tiền, gồm luôn tiếng lóng:
    // k/lít = nghìn, cành = trăm nghìn, củ/cây/tr = triệu
    private static final Map<String, Double> DON_VI = new HashMap<>();

    // mấy từ bỏ qua khi đọc cụm tiền ("lẻ", "linh", "đồng"...)
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
        // "mười" và "rưỡi" xử lý riêng bên dưới nên không nằm bảng này

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

    // bắt token dạng số như "100000", "50k", "2tr5":
    // nhóm 1 phần số, nhóm 2 đơn vị dính liền, nhóm 3 số đuôi
    private static final Pattern MA_TOKEN_SO =
            Pattern.compile("(\\d+(?:\\.\\d+)?)([a-z]*)(\\d*)");

    private VoiceParser() {
    }

    // chứa 3 thứ tách được từ câu nói
    public static class KetQua {
        // LOAI_THU / LOAI_CHI / LOAI_KHONG_RO
        public int loai = LOAI_KHONG_RO;

        public long soTien = 0;      // 0 là không tìm thấy

        // phần nội dung còn lại, giữ nguyên dấu của câu gốc
        public String noiDung = "";

        // đủ loại + số tiền thì mới điền được form
        public boolean hopLe() {
            return loai != LOAI_KHONG_RO && soTien > 0;
        }
    }

    // một đoạn tiền đọc được: giá trị + vị trí token đầu/cuối
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

    // hàm chính: nhận câu gốc từ Google Speech trả về KetQua
    public static KetQua parse(String cauGoc) {
        KetQua kq = new KetQua();

        String[][] cap = tachToken(cauGoc);
        String[] goc = cap[0];   // bản giữ dấu để ghép nội dung
        String[] hoa = cap[1];   // bản thường bỏ dấu để so sánh
        if (goc.length == 0) {
            return kq;
        }

        // bước 1: tìm chữ thu hay chi
        int[] ttLoai = timTuKhoaLoai(hoa);
        kq.loai = ttLoai[2];

        // bước 2: tìm số tiền, quét sau từ khóa loại luôn cho chắc
        int batDauTim = ttLoai[0] < 0 ? 0 : ttLoai[0] + ttLoai[1];
        DoanTien doanTien = timSoTien(hoa, batDauTim);
        kq.soTien = doanTien.giaTri;

        // bước 3: phần nào không thuộc từ khóa và cụm tiền thì coi là nội dung
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

    // tách câu thành 2 mảng song song: một giữ dấu, một thường + bỏ dấu.
    // dấu chấm trong số cũng xử lý ở đây:
    // "50.000" (sau dấu đúng 3 số) thì xóa hẳn dấu,
    // còn "1.5" thì đổi thành từ "phay" để đọc thập phân bên dưới
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

    // bỏ dấu tiếng Việt: "Triệu" -> "trieu", "đ" -> "d"
    private static String boDau(String tu) {
        String khongDau = Normalizer.normalize(tu, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return khongDau.replace('đ', 'd');
    }

    // quét hết câu tìm từ khóa thu/chi, lấy thằng xuất hiện sớm nhất,
    // cùng vị trí thì chọn cụm dài hơn ("thu nhap" thắng "thu")
    // trả về mảng {vị trí bắt đầu, độ dài, loại}
    private static int[] timTuKhoaLoai(String[] hoa) {
        int viTriNhat = -1;
        int daiNhat = 0;
        boolean laThu = false;

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

    // kiểm tra các token tại viTri có khớp nguyên cụm phan hay không
    private static boolean khopCuem(String[] hoa, int viTri, String[] phan) {
        for (int k = 0; k < phan.length; k++) {
            if (!hoa[viTri + k].equals(phan[k])) {
                return false;
            }
        }
        return true;
    }

    // chạy từng vị trí trong câu, gặp cụm tiền đầu tiên là trả về luôn
    private static DoanTien timSoTien(String[] hoa, int tuBatDau) {
        for (int i = tuBatDau; i < hoa.length; i++) {
            DoanTien dt = docCumTien(hoa, i);
            if (dt != null) {
                return dt;
            }
        }
        return new DoanTien(0, -1, -2);
    }

    // đọc một cụm tiền bắt đầu đúng tại vị trí batDau.
    // cách làm: chia theo kiểu đọc của người Việt, mỗi nhóm gồm [hàng trăm]
    // + phần dưới trăm, gặp đơn vị lớn (nghìn/triệu...) thì nhân vào tổng.
    // ví dụ "chín trăm chín mươi chín nghìn" -> (900+99)*1000 = 999000.
    // gặp từ lạ (động từ, món ăn...) là dừng ngay để không nuốt số đếm phía sau
    private static DoanTien docCumTien(String[] hoa, int batDau) {
        double tong = 0;                // tổng các nhóm đã bị đơn vị lớn nhân rồi
        double nhomTram = 0;            // hàng trăm của nhóm đang đọc
        double duoi = 0;                // phần dưới trăm đang dồn ("hai mươi lăm" = 25)
        double donViTruoc = 0;          // đơn vị lớn gần nhất (cần cho "rưỡi")
        boolean coDonVi = false;        // đã gặp đơn vị chưa (tính chuyện nhân nghìn ngầm)
        boolean soNguyenVuaRoi = false; // token trước là chữ số thuần ("50" + "000")
        boolean cheDoPhay = false;      // đang đọc phần lẻ sau chữ "phẩy"
        int soLeDaDoc = 0;              // đã đọc mấy chữ số sau "phẩy"
        int cuoiYNghia = batDau - 1;    // token cuối cùng góp giá trị vào số tiền
        int j = batDau;

        while (j < hoa.length) {
            String t = hoa[j];

            // ---- A. chữ số viết bằng chữ (một, hai...) ----
            if (CHU_SO.containsKey(t)) {
                double v = CHU_SO.get(t);
                if (cheDoPhay) {
                    // "một phẩy năm" -> 1.5, cộng dồn từng chữ số sau "phẩy"
                    duoi = duoi * Math.pow(10, -soLeDaDoc)
                            + v / Math.pow(10, soLeDaDoc + 1);
                    soLeDaDoc++;
                } else if (t.equals("khong")) {
                    // "không" chỉ có nghĩa trong "linh/ba mươi không..." nên bỏ qua
                } else {
                    duoi += v;
                }
                cuoiYNghia = j;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- B. "mười/mươi" thì nhân 10 phần đang dồn ----
            if (t.equals("muoi")) {
                duoi = (duoi == 0 ? 10 : duoi * 10);
                cuoiYNghia = j;
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- C. "chục" cũng là ×10 ("ba chục nghìn" = 30.000) ----
            if (t.equals("chuc")) {
                duoi = (duoi == 0 ? 10 : duoi * 10);
                cuoiYNghia = j;
                coDonVi = true;
                j++;
                continue;
            }

            // ---- D. "trăm" nâng phần đang dồn lên hàng trăm ----
            if (t.equals("tram")) {
                // gặp "trăm" thứ hai tức mở nhóm mới: chốt nhóm cũ trước
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

            // ---- E. đơn vị lớn (nghìn, k, lít, triệu, củ, tỷ...) ----
            // lấy giá trị nhóm đang có nhân hệ số đơn vị, nhóm trống thì coi là 1.
            // "một phẩy năm triệu" cũng rơi vào đây ra 1500000 luôn
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

            // ---- F. "cành" = trăm nghìn, xử lý như một đơn vị lớn riêng ----
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

            // ---- G. "rưỡi" = nửa đơn vị lớn đứng trước ("hai triệu rưỡi" = 2.500.000) ----
            // chưa có đơn vị lớn nào thì chỉ cộng 0,5 ("năm rưỡi")
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

            // ---- H. từ nhiễu bỏ qua (lẻ, linh, đồng, tiền...) ----
            if (TU_BO_QUA.contains(t)) {
                soNguyenVuaRoi = false;
                j++;
                continue;
            }

            // ---- I. số viết bằng chữ số ("100000", "50k", "2tr5") ----
            Matcher m = MA_TOKEN_SO.matcher(t);
            if (m.matches()) {
                String phanSo = m.group(1);
                String hauTo = m.group(2);
                String duoiSo = m.group(3);
                Double heSo = hauTo.isEmpty() ? null : DON_VI.get(hauTo);

                if (heSo != null) {
                    // số dính luôn đơn vị: "50k", "2tr", "2tr5"
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
                    break;   // lỗi định dạng, không phải cụm tiền
                }
                if (cheDoPhay) {
                    // phần lẻ viết bằng số: "1" + "phẩy" + "5" -> 1.5
                    duoi = duoi * Math.pow(10, -soLeDaDoc)
                            + giaTri / Math.pow(10, soLeDaDoc + 1);
                    soLeDaDoc++;
                } else if (soNguyenVuaRoi && (nhomTram + duoi) > 0) {
                    // hai cụm số đứng cạnh nhau: "50" "000" ghép thành 50000
                    double hienTai = nhomTram + duoi;
                    double moi = hienTai * Math.pow(10, phanSo.length())
                            + Double.parseDouble(phanSo.replace(".", ""));
                    nhomTram = 0;
                    duoi = moi;
                } else if (tong > 0 && donViTruoc >= 1_000_000d && giaTri < 1000) {
                    // kiểu "1 triệu 500" -> nhớ lại nhân ở dưới
                    duoi = giaTri;
                } else if (tong == 0 && nhomTram == 0 && duoi == 0) {
                    duoi = giaTri;   // số đầu tiên của cụm
                } else {
                    break;   // gặp số thứ hai riêng biệt ("50k mua 2 quyển") thì dừng
                }
                cuoiYNghia = j;
                soNguyenVuaRoi = true;
                j++;
                continue;
            }

            // ---- J. chữ "phẩy": chuyển sang đọc thập phân ----
            if (t.equals("phay")) {
                cheDoPhay = true;
                soLeDaDoc = 0;
                j++;
                continue;
            }

            // từ lạ (động từ, tên món...) -> hết cụm tiền ở đây
            break;
        }

        if (j == batDau) {
            return null;   // chưa đọc được token số nào
        }

        double giaTri = tong + nhomTram + duoi;

        // kiểu "1 triệu 500" -> 1.000.000 + 500 x 1000
        // (nếu phần đuôi là số lẻ như 2.5 thì bỏ qua)
        if (!leThapPhan(duoi) && tong > 0 && donViTruoc >= 1_000_000d
                && duoi > 0 && duoi < 1000 && nhomTram == 0) {
            giaTri = tong + duoi * (donViTruoc / 1000);
        }

        // kiểu người nói "chi năm", "chi ba chục" ai cũng hiểu là ...nghìn,
        // nên giá trị nhỏ hơn 1000 và chưa có đơn vị thì tự nhân 1000
        if (giaTri > 0 && giaTri < 1000 && (!coDonVi || donViTruoc <= 100)) {
            giaTri *= 1000;
        }

        long ketQua = Math.round(giaTri);
        if (ketQua <= 0 || ketQua > 99_999_999_999L) {
            return null;   // ngoài khoảng này coi như không đọc được
        }
        return new DoanTien(ketQua, batDau, cuoiYNghia);
    }

    // đọc chuỗi "50000" hoặc "1.5" thành số, lỗi trả về -1
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

    // số có phần lẻ không? 2.5 -> true, 25 -> false
    private static boolean leThapPhan(double so) {
        return so != Math.floor(so) || Double.isInfinite(so);
    }
}
