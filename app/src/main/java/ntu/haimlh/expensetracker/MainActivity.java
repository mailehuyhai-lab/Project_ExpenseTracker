package ntu.haimlh.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;
import java.util.List;

/**
 * MÀN HÌNH CHÍNH (Dashboard).
 * <p>
 * Nhiệm vụ:
 * 1. Đọc dữ liệu từ SQLite và hiển thị Tổng số dư / Tổng thu / Tổng chi.
 * 2. Hiển thị danh sách giao dịch bằng RecyclerView (kèm Empty State).
 * 3. Cho phép xoá giao dịch bằng cách nhấn giữ một dòng.
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvSoDu, tvTongThu, tvTongChi, tvSoLuong, tvThangNam;
    private RecyclerView recyclerView;
    private View layoutEmpty;
    private ExtendedFloatingActionButton fabAdd;

    private DatabaseHelper dbHelper;
    private GiaoDichAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        anhXaView();
        khoiTaoRecyclerView();
        khoiTaoFab();

        // Hiển thị tháng/năm hiện tại ở đầu card tổng quan
        tvThangNam.setText(FormatUtils.thangNamHienTai(Calendar.getInstance()));
    }

    /**
     * Load lại dữ liệu mỗi khi màn hình quay lại hiển thị
     * (ví dụ sau khi vừa thêm giao dịch mới xong).
     */
    @Override
    protected void onResume() {
        super.onResume();
        taiDuLieu();
    }

    // ==================================================================
    //  KHỞI TẠO GIAO DIỆN
    // ==================================================================

    private void anhXaView() {
        tvSoDu = findViewById(R.id.tvSoDu);
        tvTongThu = findViewById(R.id.tvTongThu);
        tvTongChi = findViewById(R.id.tvTongChi);
        tvSoLuong = findViewById(R.id.tvSoLuong);
        tvThangNam = findViewById(R.id.tvThangNam);
        recyclerView = findViewById(R.id.recyclerView);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        fabAdd = findViewById(R.id.fabAdd);
    }

    private void khoiTaoRecyclerView() {
        adapter = new GiaoDichAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);

        // Nhấn giữ 1 dòng -> hộp thoại xác nhận xoá
        adapter.setOnItemLongClickListener(this::hienThiHopThoaiXoa);
    }

    private void khoiTaoFab() {
        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AddGiaoDichActivity.class)));

        // Cuộn xuống -> thu gọn nút thành hình tròn, cuộn lên -> mở rộng lại.
        // Tiểu tiết nhỏ này giúp danh sách thoáng hơn và cảm giác rất mượt.
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 8 && fabAdd.isExtended()) {
                    fabAdd.shrink();
                } else if (dy < -8 && !fabAdd.isExtended()) {
                    fabAdd.extend();
                }
            }
        });
    }

    // ==================================================================
    //  ĐỌC & HIỂN THỊ DỮ LIỆU
    // ==================================================================

    /** Đọc toàn bộ giao dịch từ SQLite rồi cập nhật lên giao diện. */
    private void taiDuLieu() {
        List<GiaoDich> danhSach = dbHelper.getAllGiaoDich();

        adapter.capNhatDuLieu(danhSach);
        // Chạy lại animation "rơi xuống" cho các item mỗi lần vào màn hình
        recyclerView.scheduleLayoutAnimation();

        capNhatTrangThaiRong();
        capNhatThongKe();
    }

    /** Ẩn/hiện Empty State dựa theo số item đang có trong Adapter. */
    private void capNhatTrangThaiRong() {
        boolean rong = adapter.getItemCount() == 0;
        layoutEmpty.setVisibility(rong ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(rong ? View.GONE : View.VISIBLE);
    }

    /**
     * Cập nhật Tổng số dư / Tổng thu / Tổng chi.
     * Các con số này được tính trực tiếp bằng SQL (SUM) trong DatabaseHelper.
     */
    private void capNhatThongKe() {
        double tongThu = dbHelper.getTongThu();
        double tongChi = dbHelper.getTongChi();
        double soDu = tongThu - tongChi;

        tvTongThu.setText(FormatUtils.dinhDangTienCoDau(tongThu, true));
        tvTongChi.setText(FormatUtils.dinhDangTienCoDau(tongChi, false));
        tvSoDu.setText(FormatUtils.dinhDangSoDu(soDu));

        tvSoLuong.setText(getString(R.string.so_luong_giao_dich, dbHelper.demSoGiaoDich()));
    }

    // ==================================================================
    //  XOÁ GIAO DỊCH
    // ==================================================================

    /**
     * Hiện hộp thoại xác nhận (bo góc 24dp theo style của app) trước khi xoá.
     * Xoá thành công thì cập nhật lại danh sách + các con số thống kê.
     */
    private void hienThiHopThoaiXoa(GiaoDich giaoDich, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_xoa_title)
                .setMessage(getString(R.string.dialog_xoa_message, layTenHienThi(giaoDich)))
                .setIcon(R.drawable.ic_delete)
                .setNegativeButton(R.string.huy, null)
                .setPositiveButton(R.string.xoa, (dialog, which) -> xoaGiaoDich(giaoDich, position))
                .show();
    }

    private void xoaGiaoDich(GiaoDich giaoDich, int position) {
        if (!dbHelper.deleteGiaoDich(giaoDich.getId())) {
            return;
        }

        adapter.xoaItem(position);
        capNhatTrangThaiRong();
        capNhatThongKe();

        Snackbar.make(findViewById(android.R.id.content),
                        R.string.msg_da_xoa, Snackbar.LENGTH_SHORT)
                .setAnchorView(fabAdd)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.text_primary))
                .setTextColor(ContextCompat.getColor(this, R.color.surface_white))
                .show();
    }

    /** Tên dùng để hiển thị trong hộp thoại (nếu bỏ trống thì lấy tên danh mục). */
    private String layTenHienThi(GiaoDich giaoDich) {
        String ten = giaoDich.getTen();
        if (ten == null || ten.trim().isEmpty()) {
            return DanhMuc.timTheoTen(giaoDich.getDanhMuc(), giaoDich.getLoai()).getTen();
        }
        return ten;
    }
}
