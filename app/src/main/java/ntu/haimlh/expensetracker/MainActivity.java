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

// Màn hình chính: hiện số dư, danh sách giao dịch và cho xoá bằng cách nhấn giữ
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

        tvThangNam.setText(FormatUtils.thangNamHienTai(Calendar.getInstance()));
    }

    // quay lại màn hình này thì load lại dữ liệu (vd sau khi thêm xong)
    @Override
    protected void onResume() {
        super.onResume();
        taiDuLieu();
    }

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

        // nhấn giữ 1 dòng thì hiện hộp thoại xoá
        adapter.setOnItemLongClickListener(this::hienThiHopThoaiXoa);
    }

    private void khoiTaoFab() {
        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AddGiaoDichActivity.class)));

        // cuộn xuống thì thu gọn nút thêm, cuộn lên thì mở ra lại
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

    // đọc hết giao dịch trong SQLite rồi đưa lên màn hình
    private void taiDuLieu() {
        List<GiaoDich> danhSach = dbHelper.getAllGiaoDich();

        adapter.capNhatDuLieu(danhSach);
        recyclerView.scheduleLayoutAnimation();   // chạy animation rơi xuống cho list

        capNhatTrangThaiRong();
        capNhatThongKe();
    }

    // không có giao dịch nào thì hiện màn hình trống
    private void capNhatTrangThaiRong() {
        boolean rong = adapter.getItemCount() == 0;
        layoutEmpty.setVisibility(rong ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(rong ? View.GONE : View.VISIBLE);
    }

    // tính lại tổng thu / tổng chi / số dư ở card trên đầu
    private void capNhatThongKe() {
        double tongThu = dbHelper.getTongThu();
        double tongChi = dbHelper.getTongChi();
        double soDu = tongThu - tongChi;

        tvTongThu.setText(FormatUtils.dinhDangTienCoDau(tongThu, true));
        tvTongChi.setText(FormatUtils.dinhDangTienCoDau(tongChi, false));
        tvSoDu.setText(FormatUtils.dinhDangSoDu(soDu));

        tvSoLuong.setText(getString(R.string.so_luong_giao_dich, dbHelper.demSoGiaoDich()));
    }

    // hỏi người dùng có chắc muốn xoá không
    private void hienThiHopThoaiXoa(GiaoDich giaoDich, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_xoa_title)
                .setMessage(getString(R.string.dialog_xoa_message, giaoDich.getTenHienThi()))
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
}
