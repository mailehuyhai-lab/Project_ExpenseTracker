package ntu.haimlh.expensetracker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * ADAPTER - Đổ dữ liệu danh sách giao dịch vào RecyclerView.
 * <p>
 * Mỗi dòng gồm: icon Emoji của danh mục (nền tròn pastel), tên giao dịch,
 * dòng phụ "Danh mục • Ngày" và số tiền có dấu +/- kèm màu tương ứng.
 */
public class GiaoDichAdapter extends RecyclerView.Adapter<GiaoDichAdapter.GiaoDichViewHolder> {

    /** Callback khi người dùng NHẤN GIỮ một dòng (để hiện hộp thoại xoá). */
    public interface OnItemLongClickListener {
        void onItemLongClick(GiaoDich giaoDich, int position);
    }

    private final Context context;
    private final List<GiaoDich> danhSach = new ArrayList<>();
    private OnItemLongClickListener longClickListener;

    public GiaoDichAdapter(Context context) {
        this.context = context;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    /** Thay toàn bộ dữ liệu (dùng khi load lại từ SQLite). */
    public void capNhatDuLieu(List<GiaoDich> danhSachMoi) {
        danhSach.clear();
        if (danhSachMoi != null) {
            danhSach.addAll(danhSachMoi);
        }
        notifyDataSetChanged();
    }

    /**
     * Xoá 1 dòng khỏi danh sách đang hiển thị kèm hiệu ứng mượt.
     * Dùng notifyItemRemoved thay cho notifyDataSetChanged để RecyclerView
     * tự chạy animation trượt/mờ dần cho đúng dòng bị xoá.
     */
    public void xoaItem(int position) {
        if (position >= 0 && position < danhSach.size()) {
            danhSach.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, danhSach.size() - position);
        }
    }

    @NonNull
    @Override
    public GiaoDichViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_giao_dich, parent, false);
        return new GiaoDichViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GiaoDichViewHolder holder, int position) {
        GiaoDich gd = danhSach.get(position);
        boolean laThu = gd.isThu();

        // 1. Tra cứu danh mục để lấy icon Emoji + màu pastel
        DanhMuc danhMuc = DanhMuc.timTheoTen(gd.getDanhMuc(), gd.getLoai());
        holder.tvIcon.setText(danhMuc.getIcon());
        // Nền tròn dùng chung 1 drawable (bg_circle màu trắng) rồi TÔ MÀU bằng tint,
        // nhờ vậy không cần tạo 10+ file drawable cho 10+ danh mục.
        holder.tvIcon.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(context, danhMuc.getMauNen())));

        // 2. Tên hiển thị (model tự fallback sang tên danh mục khi tên bị trống)
        holder.tvTen.setText(gd.getTenHienThi());

        // 3. Dòng phụ: "Ăn uống • Hôm nay"
        holder.tvDanhMucNgay.setText(context.getString(R.string.dinh_dang_danh_muc_ngay,
                danhMuc.getTen(),
                FormatUtils.hienThiNgayThanThien(context, gd.getNgay())));

        // 4. Số tiền: Thu -> "+" màu xanh, Chi -> "-" màu đỏ
        holder.tvSoTien.setText(FormatUtils.dinhDangTienCoDau(gd.getSoTien(), laThu));
        holder.tvSoTien.setTextColor(ContextCompat.getColor(context,
                laThu ? R.color.green_income : R.color.red_expense));
        holder.tvLoai.setText(laThu ? R.string.thu_nhap : R.string.chi_tieu);

        // 5. Nhấn giữ để xoá
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener == null) {
                return false;
            }
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                longClickListener.onItemLongClick(danhSach.get(pos), pos);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return danhSach.size();
    }

    /** ViewHolder giữ tham chiếu tới các View trong item_giao_dich.xml */
    static class GiaoDichViewHolder extends RecyclerView.ViewHolder {

        final TextView tvIcon;
        final TextView tvTen;
        final TextView tvDanhMucNgay;
        final TextView tvSoTien;
        final TextView tvLoai;

        GiaoDichViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvIcon);
            tvTen = itemView.findViewById(R.id.tvTen);
            tvDanhMucNgay = itemView.findViewById(R.id.tvDanhMucNgay);
            tvSoTien = itemView.findViewById(R.id.tvSoTien);
            tvLoai = itemView.findViewById(R.id.tvLoai);
        }
    }
}
