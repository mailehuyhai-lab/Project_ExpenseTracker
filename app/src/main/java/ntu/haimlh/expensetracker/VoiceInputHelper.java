package ntu.haimlh.expensetracker;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 * TRỢ GIÚP NHẬP LIỆU BẰNG GIỌNG NÓI.
 * <p>
 * Gói gọn toàn bộ công việc "cầu nối" giữa Activity và Google Speech-to-Text:
 * <ol>
 *     <li>Xin quyền runtime {@code RECORD_AUDIO} (Android 6.0+ phải xin khi chạy).</li>
 *     <li>Phát Intent {@link RecognizerIntent#ACTION_RECOGNIZE_SPEECH} để mở
 *         hộp thoại nhận dạng giọng nói của Google (miễn phí, không cần API key).</li>
 *     <li>Nhận kết quả về trong {@code onActivityResult} rồi gọi lại callback.</li>
 * </ol>
 */
public class VoiceInputHelper {

    /** Mã request khi xin quyền RECORD_AUDIO (để đối chiếu trong onRequestPermissionsResult). */
    public static final int REQUEST_QUYEN_MICRO = 9001;

    /** Mã request khi mở hộp thoại Speech-to-Text (để đối chiếu trong onActivityResult). */
    public static final int REQUEST_GIONG_NOI = 9002;

    private final Activity activity;

    /** Được gọi khi có văn bản nhận diện được (chỉ gọi khi resultCode == OK). */
    public interface OnKetQuaListener {
        void onKetQua(String vanBan);
    }

    public VoiceInputHelper(Activity activity) {
        this.activity = activity;
    }

    // ==================================================================
    //  1. XIN QUYỀN MICRO
    // ==================================================================

    /**
     * Điểm vào duy nhất cho nút Micro: kiểm tra quyền -> thiếu thì xin, đủ thì mở STT.
     */
    public void batDauNghe(OnKetQuaListener listener) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            moHopThoaiGiongNoi(listener);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_QUYEN_MICRO);
            // Lưu listener tạm để dùng tiếp trong onRequestPermissionsResult
            listenerTam = listener;
        }
    }

    /** Listener chờ sau khi người dùng trả lời hộp thoại xin quyền. */
    private OnKetQuaListener listenerTam;

    /**
     * Activity PHẢI chuyển tiếp kết quả xin quyền vào đây
     * (gọi từ {@code onRequestPermissionsResult}).
     *
     * @return true nếu đã xử lý mã request này
     */
    public boolean xuLyKetQuaXinQuyen(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_QUYEN_MICRO) {
            return false;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Người dùng đồng ý -> mở ngay hộp thoại nghe giọng nói
            moHopThoaiGiongNoi(listenerTam);
        } else {
            Toast.makeText(activity, R.string.msg_thieu_quyen_micro, Toast.LENGTH_SHORT).show();
        }
        listenerTam = null;
        return true;
    }

    // ==================================================================
    //  2. MỞ HỘP THOẠI GOOGLE SPEECH-TO-TEXT
    // ==================================================================

    /**
     * Phát Intent ACTION_RECOGNIZE_SPEECH tới app Google trên máy.
     * Nếu máy KHÔNG có app nhận dạng giọng nói nào (máy ảo thường không có)
     * thì bắt ActivityNotFoundException và thông báo thân thiện thay vì crash.
     */
    private void moHopThoaiGiongNoi(OnKetQuaListener listener) {
        listenerTam = listener;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // Gợi ý cụm mẫu để engine ưu tiên các câu kiểu "chi 50k ăn sáng"
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Nhận dạng tiếng Việt
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("vi", "VN"));
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, new Locale("vi", "VN"));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                activity.getString(R.string.prompt_giong_noi));

        try {
            activity.startActivityForResult(intent, REQUEST_GIONG_NOI);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.msg_khong_co_stt, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Activity PHẢI chuyển tiếp kết quả hộp thoại vào đây
     * (gọi từ {@code onActivityResult}).
     *
     * @return true nếu đã xử lý mã request này
     */
    public boolean xuLyKetQuaGiongNoi(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_GIONG_NOI) {
            return false;
        }
        if (resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> ketQua = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            if (ketQua != null && !ketQua.isEmpty() && listenerTam != null) {
                String vanBan = ketQua.get(0);   // ứng viên khớp tốt nhất
                if (!vanBan.trim().isEmpty()) {
                    listenerTam.onKetQua(vanBan);
                }
            }
        }
        // resultCode = RESULT_CANCELED nghĩa là người dùng bấm Huỷ -> im lặng
        listenerTam = null;
        return true;
    }
}
