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

// Màn hình thêm giao dịch mới
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

    private VoiceInputHelper voiceInputHelper;   // lo phần nghe giọng nói

    // loại đang chọn, mặc định là chi
    private int loaiDangChon = GiaoDich.LOAI_CHI;

    private String ngayDangChon;   // dạng "yyyy-MM-dd"

    // cờ chống lặp: TextWatcher tự setText lại ô tiền thì phải bỏ qua
    private boolean dangDinhDangSoTien = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_giao_dich);

        dbHelper = new DatabaseHelper(this);
        ngayDangChon = FormatUtils.ngayHomNay();   // mặc định là hôm nay

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

    // ô số tiền: người dùng gõ số nó tự thêm dấu chấm ngăn cách
    private void khoiTaoOSoTien() {
        edtSoTien.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // nếu là do mình tự setText thì thôi, không sẽ lặp vô hạn
                if (dangDinhDangSoTien) {
                    return;
                }
                dangDinhDangSoTien = true;

                String hienTai = s.toString();
                String daDinhDang = FormatUtils.themDauPhanCach(hienTai);
                if (!daDinhDang.equals(hienTai)) {
                    edtSoTien.setText(daDinhDang);
                    // đẩy con trỏ về cuối
                    edtSoTien.setSelection(daDinhDang.length());
                }

                dangDinhDangSoTien = false;
            }
        });
    }

    // nút chuyển Thu / Chi
    private void khoiTaoChonLoai() {
        toggleLoai.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;   // chỉ quan tâm nút vừa được chọn
            }
            loaiDangChon = (checkedId == R.id.btnThu) ? GiaoDich.LOAI_THU : GiaoDich.LOAI_CHI;
            taoChipDanhMuc();   // thu và chi khác danh mục nên dựng lại chip
        });

        // check sau khi gắn listener để chip được tạo ra luôn ở lần đầu
        toggleLoai.check(R.id.btnChi);
    }

    // dựng lại các chip danh mục theo loại đang chọn, chọn sẵn cái đầu
    private void taoChipDanhMuc() {
        chipGroupDanhMuc.removeAllViews();

        List<DanhMuc> danhSach = DanhMuc.danhSachTheoLoai(loaiDangChon);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < danhSach.size(); i++) {
            DanhMuc dm = danhSach.get(i);

            Chip chip = (Chip) inflater.inflate(R.layout.item_chip_danh_muc, chipGroupDanhMuc, false);
            chip.setId(View.generateViewId());
            chip.setText(dm.getNhanChip());
            chip.setTag(dm.getTen());          // giữ tên gốc để ghi vào DB

            chipGroupDanhMuc.addView(chip);

            if (i == 0) {
                chip.setChecked(true);
            }
        }
    }

    // lấy tên danh mục đang được chọn
    private String layDanhMucDangChon() {
        int checkedId = chipGroupDanhMuc.getCheckedChipId();
        if (checkedId != View.NO_ID) {
            Chip chip = chipGroupDanhMuc.findViewById(checkedId);
            if (chip != null && chip.getTag() != null) {
                return chip.getTag().toString();
            }
        }
        // hiếm khi rơi vào đây: không chip nào chọn thì lấy cái đầu
        return DanhMuc.danhSachTheoLoai(loaiDangChon).get(0).getTen();
    }

    // phần chọn ngày
    private void khoiTaoChonNgay() {
        capNhatHienThiNgay();
        cardNgay.setOnClickListener(v -> moHopThoaiChonNgay());
    }

    // mở lịch chọn ngày. lưu ý: MaterialDatePicker tính theo giờ UTC,
    // phải dùng Calendar UTC hết nếu không sẽ lệch 1 ngày
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

    // hiện dạng "Hôm nay, 21/08/2026"
    private void capNhatHienThiNgay() {
        tvNgay.setText(getString(R.string.dinh_dang_ngay_day_du,
                FormatUtils.hienThiNgayThanThien(this, ngayDangChon),
                FormatUtils.doiSangHienThi(ngayDangChon)));
    }

    // tách "yyyy-MM-dd" thành mảng năm/tháng/ngày, lỗi thì lấy hôm nay
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
            finish();   // MainActivity tự load lại trong onResume
        } else {
            Toast.makeText(this, R.string.msg_luu_that_bai, Toast.LENGTH_SHORT).show();
        }
    }

    // chưa nhập tiền thì rung ô nhập + hiện Snackbar đỏ
    private void baoLoiSoTien() {
        edtSoTien.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
        edtSoTien.requestFocus();

        Snackbar.make(findViewById(android.R.id.content),
                        R.string.msg_nhap_so_tien, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.red_expense_dark))
                .setTextColor(ContextCompat.getColor(this, R.color.surface_white))
                .show();
    }

    // nút micro: bấm là nghe giọng nói
    private void khoiTaoNutMicro() {
        voiceInputHelper = new VoiceInputHelper(this);
        btnMic.setOnClickListener(v -> voiceInputHelper.batDauNghe(this::dienDuLieuTuGiongNoi));
    }

    // Hàm này nhận chữ mà Google Speech trả về rồi điền vào form:
    // VoiceParser bóc ra số tiền + loại + nội dung, thiếu cái nào là báo đọc lại
    private void dienDuLieuTuGiongNoi(String vanBan) {
        VoiceParser.KetQua kq = VoiceParser.parse(vanBan);

        // thiếu số tiền hoặc loại thì chưa điền được, nhắc người dùng đọc lại
        if (!kq.hopLe()) {
            Toast.makeText(this, R.string.msg_voice_ko_hieu, Toast.LENGTH_LONG).show();
            return;
        }

        // đổi loại thu/chi, listener lúc nãy sẽ tự dựng lại chip
        toggleLoai.check(kq.loai == GiaoDich.LOAI_THU ? R.id.btnThu : R.id.btnChi);
        loaiDangChon = kq.loai;

        // đổ số tiền vào ô, TextWatcher sẽ tự thêm dấu chấm nghìn
        dangDinhDangSoTien = false;
        edtSoTien.setText(FormatUtils.themDauPhanCach(String.valueOf(kq.soTien)));

        // nếu nội dung nói đúng bằng tên một danh mục thì tick chip luôn,
        // còn lại đưa hết vào ô ghi chú cho người dùng xem lại
        String noiDung = kq.noiDung;
        String tenSach = noiDung.replaceFirst("(?i)^ti\\p{L}*\\s+", "");   // bỏ chữ "tiền" đầu câu
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

    // tìm chip theo tên rồi tick nó lên
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
