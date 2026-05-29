package com.cookandroid.metatool;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Android 10 이상에서 이미지의 EXIF 위치 정보 접근 권한을 요청하기 위한 코드
    private static final int REQ_MEDIA_LOCATION = 1001;

    // 메인 화면의 이미지 미리보기, 파일명 표시, 업로드/분석 버튼
    private ImageView ivPreview;
    private TextView tvSelectedPath;
    private Button btnSelectImage, btnAnalyze;

    // 사용자가 선택한 이미지의 Uri
    private Uri selectedImageUri;

    // 갤러리에서 선택한 이미지 결과를 받기 위한 런처
    private ActivityResultLauncher<Intent> pickImageLauncher;

    // 분석 화면에서 돌아온 경우에만 메인 화면을 초기화하기 위한 플래그
    private boolean shouldResetOnResume = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("  MetaTool");

        // ActionBar 왼쪽에 앱 로고를 표시
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayUseLogoEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setLogo(R.drawable.metatool_icon);
        }

        // Android 10(Q) 이상에서 이미지 위치 메타데이터(EXIF GPS) 접근 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION},
                        REQ_MEDIA_LOCATION
                );
            }
        }

        // activity_main.xml의 View 연결
        ivPreview = findViewById(R.id.ivPreview);
        tvSelectedPath = findViewById(R.id.tvSelectedPath);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnAnalyze = findViewById(R.id.btnAnalyze);

        // 이미지 선택 결과를 받아 미리보기와 파일명을 표시
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedImageUri = uri;
                            ivPreview.setImageURI(uri);
                            tvSelectedPath.setText("선택된 파일: " + getReadableFileInfo(uri));
                            btnAnalyze.setEnabled(true);
                        } else {
                            Toast.makeText(this, "이미지를 선택하지 않았습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // 갤러리에서 분석할 이미지 선택
        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        // 선택한 이미지 Uri를 메타데이터 분석 화면으로 전달
        btnAnalyze.setOnClickListener(v -> {
            // 분석 화면에서 돌아왔을 때 선택 이미지 정보를 초기화하기 위해 사용
            shouldResetOnResume = true;

            Intent intent = new Intent(MainActivity.this, AnalyzeActivity.class);
            intent.putExtra("image_uri", selectedImageUri.toString());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 갤러리 복귀 시에는 유지하고, 분석 화면 복귀 시에만 선택 상태 초기화
        if (shouldResetOnResume) {
            resetSelectedImage();
            shouldResetOnResume = false;
        }
    }

    // 이미지 미리보기, 파일명, 분석 버튼 상태를 초기화
    private void resetSelectedImage() {
        selectedImageUri = null;

        if (ivPreview != null) {
            ivPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        if (tvSelectedPath != null) {
            tvSelectedPath.setText("선택된 파일: 없음");
        }

        if (btnAnalyze != null) {
            btnAnalyze.setEnabled(false);
        }
    }

    // Uri에서 사용자에게 표시할 파일명만 조회
    private String getReadableFileInfo(Uri uri) {
        if (uri == null) return "알 수 없음";

        String displayName = null;

        // ContentResolver를 이용해 파일명.확장자 조회
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
        }

        // 파일명을 얻지 못한 경우 Uri의 마지막 경로를 대체값으로 사용
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = uri.getLastPathSegment();
        }

        if (displayName == null || displayName.trim().isEmpty()) {
            return "알 수 없음";
        }

        return displayName;
    }

    // ActionBar에 개인정보 보호 리포트 메뉴 추가
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, "개인정보 보호 리포트");
        return true;
    }

    // 메뉴 선택 시 SQLite 기반 개인정보 보호 리포트 화면으로 이동
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                Intent intent = new Intent(MainActivity.this, StatsActivity.class);
                startActivity(intent);
                return true;
        }

        return false;
    }
}
