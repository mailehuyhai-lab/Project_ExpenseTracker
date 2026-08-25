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

// Adapter cho danh sách giao dịch ở màn hình chính
public class GiaoDichAdapter extends RecyclerView.Adapter<GiaoDichAdapter.GiaoDichViewHolder> {

    // nhấn giữ 1 dòng thì báo ra ngoài (để hiện hộp thoại xoá)
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

    // thay toàn bộ danh sách bằng dữ liệu mới đọc từ database
    public void capNhatDuLieu(List<GiaoDich> danhSachMoi) {
        danhSach.clear();
        if (danhSachMoi != null) {
            danhSach.addAll(danhSachMoi);
        }
        notifyDataSetChanged();
    }

    // xoá 1 dòng, dùng notifyItemRemoved để có animation trượt chứ không nhấp nháy cả list
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

        // icon danh mục: nền trắng chung 1 drawable rồi tô màu theo từng danh mục
        DanhMuc danhMuc = DanhMuc.timTheoTen(gd.getDanhMuc(), gd.getLoai());
        holder.tvIcon.setText(danhMuc.getIcon());
        holder.tvIcon.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(context, danhMuc.getMauNen())));

        // nếu người dùng không nhập tên thì lấy tên danh mục
        holder.tvTen.setText(gd.getTenHienThi());

        // dòng dưới: "Ăn uống • Hôm nay"
        holder.tvDanhMucNgay.setText(context.getString(R.string.dinh_dang_danh_muc_ngay,
                danhMuc.getTen(),
                FormatUtils.hienThiNgayThanThien(context, gd.getNgay())));

        // thu thì "+" màu xanh, chi thì "-" màu đỏ
        holder.tvSoTien.setText(FormatUtils.dinhDangTienCoDau(gd.getSoTien(), laThu));
        holder.tvSoTien.setTextColor(ContextCompat.getColor(context,
                laThu ? R.color.green_income : R.color.red_expense));
        holder.tvLoai.setText(laThu ? R.string.thu_nhap : R.string.chi_tieu);

        // nhấn giữ để xoá
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
