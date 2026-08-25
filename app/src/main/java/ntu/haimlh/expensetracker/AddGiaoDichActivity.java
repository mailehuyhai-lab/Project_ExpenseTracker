package ntu.haimlh.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * MÀN HÌNH THÊM GIAO DỊCH.
 * <p>
 * Các điểm nổi bật:
 * - Ô nhập số tiền tự thêm dấu chấm phân cách hàng nghìn (TextWatcher).
 * - Chọn Thu/Chi bằng Segmented Control (MaterialButtonToggleGroup).
 * - Danh sách danh mục dạng Chip, tự đổi theo loại giao dịch đang chọn.
 * - Chọn ngày bằng MaterialDatePicker, mặc định là ngày hôm nay.
 */
public class AddGiaoDichActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private EditText edtSoTien;
    private MaterialButtonToggleGroup toggleLoai;
    private ChipGroup chipGroupDanhMuc;
    private TextInputEditText edtTen;
    private MaterialCardView cardNgay;
    private TextView tvNgay;
    private View btnLuu;
    private ImageView btnMic;

    private DatabaseHelper dbHelper;

    /** Trợ giúp Speech-to-Text: xin quyền micro + mở hộp thoại nhận dạng giọng nói. */
    private VoiceInputHelper voiceInputHelper;

    /** Loại giao dịch đang được chọn (mặc định là Chi tiêu). */
    private int loaiDangChon = GiaoDich.LOAI_CHI;

    /** Ngày đang chọn, định dạng "yyyy-MM-dd". */
    private String ngayDangChon;

    /** Cờ chống lặp vô hạn khi TextWatcher tự sửa nội dung ô nhập tiền. */
    private boolean dangDinhDangSoTien = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_giao_dich);

        dbHelper = new DatabaseHelper(this);
        ngayDangChon = FormatUtils.ngayHomNay();   // Mặc định: hôm nay

        anhXaView();
        khoiTaoToolbar();
        khoiTaoOSoTien();
        khoiTaoChonLoai();
        khoiTaoChonNgay();
        khoiTaoNutMicro();

        btnLuu.setOnClickListener(v -> luuGiaoDich());
    }

    private void anhXaView() {
        toolbar = findViewById(R.id.toolbar);
        edtSoTien = findViewById(R.id.edtSoTien);
        toggleLoai = findViewById(R.id.toggleLoai);
        chipGroupDanhMuc = findViewById(R.id.chipGroupDanhMuc);
        edtTen = findViewById(R.id.edtTen);
        cardNgay = findViewById(R.id.cardNgay);
        tvNgay = findViewById(R.id.tvNgay);
        btnLuu = findViewById(R.id.btnLuu);
        btnMic = findViewById(R.id.btnMic);
    }

    private void khoiTaoToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ==================================================================
    //  Ô NHẬP SỐ TIỀN - TỰ THÊM DẤU PHÂN CÁCH HÀNG NGHÌN
    // ==================================================================

    private void khoiTaoOSoTien() {
        edtSoTien.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Không dùng
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Không dùng
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Khi chính TextWatcher đang setText thì bỏ qua để tránh đệ quy
                if (dangDinhDangSoTien) {
                    return;
                }
                dangDinhDangSoTien = true;

                String hienTai = s.toString();
                String daDinhDang = FormatUtils.themDauPhanCach(hienTai);
                if (!daDinhDang.equals(hienTai)) {
                    edtSoTien.setText(daDinhDang);
                    // Đưa con trỏ về cuối cho tự nhiên khi vừa gõ thêm số
                    edtSoTien.setSelection(daDinhDang.length());
                }

                dangDinhDangSoTien = false;
            }
        });
    }

    // ==================================================================
    //  CHỌN LOẠI GIAO DỊCH (THU / CHI)
    // ==================================================================

    private void khoiTaoChonLoai() {
        toggleLoai.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;   // Chỉ xử lý nút vừa được CHỌN
            }
            loaiDangChon = (checkedId == R.id.btnThu) ? GiaoDich.LOAI_THU : GiaoDich.LOAI_CHI;
            taoChipDanhMuc();   // Danh mục của Thu và Chi khác nhau -> tạo lại
        });

        // Gọi check() SAU khi đã gắn listener để danh mục được tạo lần đầu
        toggleLoai.check(R.id.btnChi);
    }

    // ==================================================================
    //  DANH MỤC DẠNG CHIP
    // ==================================================================

    /**
     * Tạo lại toàn bộ Chip danh mục theo loại giao dịch đang chọn.
     * Chip đầu tiên được chọn sẵn để người dùng luôn có 1 danh mục hợp lệ.
     */
    private void taoChipDanhMuc() {
        chipGroupDanhMuc.removeAllViews();

        List<DanhMuc> danhSach = DanhMuc.danhSachTheoLoai(loaiDangChon);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < danhSach.size(); i++) {
            DanhMuc dm = danhSach.get(i);

            Chip chip = (Chip) inflater.inflate(R.layout.item_chip_danh_muc, chipGroupDanhMuc, false);
            chip.setId(View.generateViewId());
            chip.setText(dm.getNhanChip());
            chip.setTag(dm.getTen());          // Lưu tên "thuần" để ghi vào SQLite

            chipGroupDanhMuc.addView(chip);

            if (i == 0) {
                chip.setChecked(true);
            }
        }
    }

    /** @return tên danh mục đang được chọn, luôn khác null. */
    private String layDanhMucDangChon() {
        int checkedId = chipGroupDanhMuc.getCheckedChipId();
        if (checkedId != View.NO_ID) {
            Chip chip = chipGroupDanhMuc.findViewById(checkedId);
            if (chip != null && chip.getTag() != null) {
                return chip.getTag().toString();
            }
        }
        // Trường hợp hiếm: không có chip nào được chọn -> lấy danh mục đầu tiên
        return DanhMuc.danhSachTheoLoai(loaiDangChon).get(0).getTen();
    }

    // ==================================================================
    //  CHỌN NGÀY GIAO DỊCH
    // ==================================================================

    private void khoiTaoChonNgay() {
        capNhatHienThiNgay();
        cardNgay.setOnClickListener(v -> moHopThoaiChonNgay());
    }

    /**
     * Mở MaterialDatePicker.
     * LƯU Ý: MaterialDatePicker làm việc với mốc thời gian theo múi giờ UTC,
     * nên phải dùng Calendar UTC khi đọc/ghi để không bị lệch 1 ngày.
     */
    private void moHopThoaiChonNgay() {
        int[] ymd = tachNgay(ngayDangChon);

        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(ymd[0], ymd[1] - 1, ymd[2]);

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.chon_ngay)
                .setSelection(utc.getTimeInMillis())
                .build();

        picker.addOnPositiveButtonClickListener(millis -> {
            Calendar chon = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            chon.setTimeInMillis(millis);
            ngayDangChon = FormatUtils.taoNgayDb(
                    chon.get(Calendar.YEAR),
                    chon.get(Calendar.MONTH) + 1,
                    chon.get(Calendar.DAY_OF_MONTH));
            capNhatHienThiNgay();
        });

        picker.show(getSupportFragmentManager(), "chon_ngay");
    }

    /** Hiển thị dạng "Hôm nay, 21/08/2026" cho dễ đọc. */
    private void capNhatHienThiNgay() {
        tvNgay.setText(getString(R.string.dinh_dang_ngay_day_du,
                FormatUtils.hienThiNgayThanThien(this, ngayDangChon),
                FormatUtils.doiSangHienThi(ngayDangChon)));
    }

    /** Tách chuỗi "yyyy-MM-dd" thành mảng {năm, tháng, ngày}. */
    private int[] tachNgay(String ngayDb) {
        try {
            String[] phan = ngayDb.split("-");
            return new int[]{
                    Integer.parseInt(phan[0]),
                    Integer.parseInt(phan[1]),
                    Integer.parseInt(phan[2])};
        } catch (Exception e) {
            Calendar homNay = Calendar.getInstance();
            return new int[]{
                    homNay.get(Calendar.YEAR),
                    homNay.get(Calendar.MONTH) + 1,
                    homNay.get(Calendar.DAY_OF_MONTH)};
        }
    }

    // ==================================================================
    //  LƯU GIAO DỊCH VÀO SQLITE
    // ==================================================================

    private void luuGiaoDich() {
        // 1. Lấy số tiền: bỏ hết dấu chấm phân cách rồi mới parse
        String chuoiSo = FormatUtils.chiLaySo(edtSoTien.getText().toString());
        double soTien = chuoiSo.isEmpty() ? 0 : Double.parseDouble(chuoiSo);

        if (soTien <= 0) {
            baoLoiSoTien();
            return;
        }

        // 2. Danh mục + tên giao dịch (bỏ trống thì lấy tên danh mục)
        String danhMuc = layDanhMucDangChon();
        String ten = edtTen.getText() == null ? "" : edtTen.getText().toString().trim();
        if (ten.isEmpty()) {
            ten = danhMuc;
        }

        // 3. Ghi vào DB
        GiaoDich giaoDich = new GiaoDich(ten, soTien, loaiDangChon, danhMuc, ngayDangChon);
        long id = dbHelper.insertGiaoDich(giaoDich);

        if (id > 0) {
            Toast.makeText(this, R.string.msg_luu_thanh_cong, Toast.LENGTH_SHORT).show();
            finish();   // MainActivity sẽ tự load lại dữ liệu trong onResume()
        } else {
            Toast.makeText(this, R.string.msg_luu_that_bai, Toast.LENGTH_SHORT).show();
        }
    }

    /** Báo lỗi khi chưa nhập số tiền: rung ô nhập + Snackbar màu đỏ. */
    private void baoLoiSoTien() {
        edtSoTien.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
        edtSoTien.requestFocus();

        Snackbar.make(findViewById(android.R.id.content),
                        R.string.msg_nhap_so_tien, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.red_expense_dark))
                .setTextColor(ContextCompat.getColor(this, R.color.surface_white))
                .show();
    }

    // ==================================================================
    //  NHẬP LIỆU BẰNG GIỌNG NÓI (VOICE INPUT)
    //  Luồng: bấm micro -> xin quyền RECORD_AUDIO -> mở Google Speech-to-Text
    //  -> nhận văn bản -> VoiceParser tách [Loại][Số tiền][Nội dung] -> điền UI.
    // ==================================================================

    private void khoiTaoNutMicro() {
        voiceInputHelper = new VoiceInputHelper(this);
        btnMic.setOnClickListener(v -> voiceInputHelper.batDauNghe(this::dienDuLieuTuGiongNoi));
    }

    /**
     * Được gọi khi Speech-to-Text trả về văn bản. Parse rồi điền vào form:
     * <ol>
     *     <li>Đổi loại Thu/Chi bằng {@code toggleLoai.check()} - listener có sẵn sẽ
     *         tự dựng lại danh mục chip tương ứng.</li>
     *     <li>Ghi số tiền vào ô tiền (đã có TextWatcher tự thêm dấu phân cách).</li>
     *     <li>Nếu nội dung trùng tên một danh mục thì chọn danh mục đó luôn,
     *     ngược lại đưa vào ô "Tên giao dịch / Ghi chú".</li>
     * </ol>
     */
    private void dienDuLieuTuGiongNoi(String vanBan) {
        VoiceParser.KetQua kq = VoiceParser.parse(vanBan);

        // Thiếu số tiền HOẶC loại thì không đủ dữ kiện để tự điền -> nhắc đọc lại
        if (!kq.hopLe()) {
            Toast.makeText(this, R.string.msg_voice_ko_hieu, Toast.LENGTH_LONG).show();
            return;
        }

        // 1. Loại giao dịch: check() phát sự kiện cho listener đã gắn ở khoiTaoChonLoai()
        toggleLoai.check(kq.loai == GiaoDich.LOAI_THU ? R.id.btnThu : R.id.btnChi);
        loaiDangChon = kq.loai;

        // 2. Số tiền: setText dạng số thuần, TextWatcher sẽ tự thêm dấu chấm nghìn
        dangDinhDangSoTien = false;
        edtSoTien.setText(FormatUtils.themDauPhanCach(String.valueOf(kq.soTien)));

        // 3. Nội dung: nếu trùng tên danh mục (VD nói "... tiền ăn uống") thì chọn chip,
        //    còn lại đưa hết vào ô ghi chú. Nói "tiền ăn sáng" mà không có danh mục
        //    "Ăn sáng" thì giữ nguyên chuỗi đẹp cho người dùng xem lại.
        String noiDung = kq.noiDung;
        String tenSach = noiDung.replaceFirst("(?i)^ti\\p{L}*\\s+", "");   // bỏ "tiền"/"tiền" đầu câu
        DanhMuc dm = DanhMuc.timTheoTen(tenSach, loaiDangChon);
        boolean trungDanhMuc = !tenSach.isEmpty()
                && dm.getTen().equalsIgnoreCase(tenSach.trim());

        if (trungDanhMuc) {
            timVaChonChip(dm.getTen());
            edtTen.setText("");
        } else {
            edtTen.setText(noiDung);
        }

        Toast.makeText(this,
                getString(R.string.msg_voice_thanh_cong,
                        FormatUtils.dinhDangTien(kq.soTien)),
                Toast.LENGTH_SHORT).show();
    }

    /** Tìm chip theo tên danh mục (tag) và tick chọn nó. */
    private void timVaChonChip(String tenDanhMuc) {
        for (int i = 0; i < chipGroupDanhMuc.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupDanhMuc.getChildAt(i);
            Object tag = chip.getTag();
            if (tag != null && tag.toString().equalsIgnoreCase(tenDanhMuc)) {
                chip.setChecked(true);
                return;
            }
        }
    }

    // Chuyển tiếp kết quả quyền + hộp thoại giọng nói về trợ giúp xử lý
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        voiceInputHelper.xuLyKetQuaXinQuyen(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        voiceInputHelper.xuLyKetQuaGiongNoi(requestCode, resultCode, data);
    }
}
