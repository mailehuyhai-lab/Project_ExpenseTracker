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

// Lớp này lo phần nhập bằng giọng nói: xin quyền micro,
// mở hộp thoại Google Speech rồi đưa chữ nhận được về cho Activity
public class VoiceInputHelper {

    // mã request để phân biệt khi nhận kết quả về
    public static final int REQUEST_QUYEN_MICRO = 9001;
    public static final int REQUEST_GIONG_NOI = 9002;

    private final Activity activity;

    // được gọi khi có chữ nhận dạng được
    public interface OnKetQuaListener {
        void onKetQua(String vanBan);
    }

    public VoiceInputHelper(Activity activity) {
        this.activity = activity;
    }

    // hàm chính cho nút mic: đủ quyền thì mở luôn hộp thoại, chưa có thì xin quyền
    public void batDauNghe(OnKetQuaListener listener) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            moHopThoaiGiongNoi(listener);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_QUYEN_MICRO);
            listenerTam = listener;   // giữ tạm, đợi người dùng trả lời hộp thoại quyền
        }
    }

    private OnKetQuaListener listenerTam;

    // Activity phải gọi hàm này trong onRequestPermissionsResult
    public boolean xuLyKetQuaXinQuyen(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_QUYEN_MICRO) {
            return false;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // được cấp quyền thì mở hộp thoại nghe luôn
            moHopThoaiGiongNoi(listenerTam);
        } else {
            Toast.makeText(activity, R.string.msg_thieu_quyen_micro, Toast.LENGTH_SHORT).show();
        }
        listenerTam = null;
        return true;
    }

    // mở hộp thoại nhận dạng giọng nói của Google.
    // máy nào không có app nghe giọng nói (máy ảo thường vậy) thì hiện thông báo, đỡ bị crash
    private void moHopThoaiGiongNoi(OnKetQuaListener listener) {
        listenerTam = listener;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // cho nó nghe tiếng Việt
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

    // Activity phải gọi hàm này trong onActivityResult để nhận chữ về
    public boolean xuLyKetQuaGiongNoi(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_GIONG_NOI) {
            return false;
        }
        if (resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> ketQua = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            if (ketQua != null && !ketQua.isEmpty() && listenerTam != null) {
                String vanBan = ketQua.get(0);   // lấy câu đầu, thường khớp nhất
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
