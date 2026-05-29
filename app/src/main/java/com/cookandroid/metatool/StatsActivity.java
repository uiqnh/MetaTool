package com.cookandroid.metatool;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class StatsActivity extends AppCompatActivity {

    // 개인정보 보호 리포트 화면에 표시할 통계 TextView
    private TextView tvSummaryReport;
    private TextView tvHighRiskProtected;
    private TextView tvMediumRiskProtected;
    private TextView tvLowRiskProtected;
    private Button btnGoMain;

    // SQLite 통계 데이터를 관리하는 DB Helper
    private PrivacyStatsDBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        setTitle("개인정보 보호 리포트");

        // activity_stats.xml의 View 연결
        tvSummaryReport = findViewById(R.id.tvSummaryReport);
        tvHighRiskProtected = findViewById(R.id.tvHighRiskProtected);
        tvMediumRiskProtected = findViewById(R.id.tvMediumRiskProtected);
        tvLowRiskProtected = findViewById(R.id.tvLowRiskProtected);
        btnGoMain = findViewById(R.id.btnGoMain);

        // 메인 화면으로 돌아가기
        btnGoMain.setOnClickListener(v -> {
            Intent intent = new Intent(StatsActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // DB Helper 생성 후 저장된 통계 조회
        dbHelper = new PrivacyStatsDBHelper(this);
        loadStats();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 화면 복귀 시 최신 통계를 다시 조회
        loadStats();
    }

    // SQLite에서 개인정보 보호 통계 데이터를 조회하여 화면에 표시
    private void loadStats() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql =
                "SELECT total_protected_images, total_removed_metadata, " +
                        "high_risk_protected, medium_risk_protected, low_risk_protected " +
                        "FROM " + PrivacyStatsDBHelper.TABLE_PRIVACY_STATS + " WHERE id = 1";

        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                setSummaryReport(cursor.getInt(0), cursor.getInt(1));
                tvHighRiskProtected.setText(cursor.getInt(2) + "회");
                tvMediumRiskProtected.setText(cursor.getInt(3) + "회");
                tvLowRiskProtected.setText(cursor.getInt(4) + "회");
            } else {
                showEmptyStats();
            }
        } catch (Exception e) {
            showEmptyStats();
        }
    }

    // 통계 데이터가 없거나 조회에 실패했을 때 기본값 표시
    private void showEmptyStats() {
        setSummaryReport(0, 0);
        tvHighRiskProtected.setText("0회");
        tvMediumRiskProtected.setText("0회");
        tvLowRiskProtected.setText("0회");
    }

    // 보호한 이미지 수와 제거한 메타데이터 수를 요약 문장으로 표시
    private void setSummaryReport(int totalProtectedImages, int totalRemovedMetadata) {
        String imageCount = String.valueOf(totalProtectedImages);
        String metadataCount = String.valueOf(totalRemovedMetadata);

        String prefix1 = "총 ";
        String middle = "장의 이미지에서 ";
        String suffix = "개의 메타데이터를 제거하여 개인정보 노출 위험을 줄였습니다.";

        String message = prefix1 + imageCount + middle + metadataCount + suffix;

        SpannableString spannable = new SpannableString(message);

        int imageStart = prefix1.length();
        int imageEnd = imageStart + imageCount.length();

        int metadataStart = imageEnd + middle.length();
        int metadataEnd = metadataStart + metadataCount.length();

        applyNumberStyle(spannable, imageStart, imageEnd);
        applyNumberStyle(spannable, metadataStart, metadataEnd);

        tvSummaryReport.setText(spannable);
    }

    // 요약 문장의 숫자 부분을 강조 표시
    private void applyNumberStyle(SpannableString spannable, int start, int end) {
        spannable.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        spannable.setSpan(
                new RelativeSizeSpan(2f),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        spannable.setSpan(
                new ForegroundColorSpan(Color.rgb(120, 200, 80)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    // ActionBar에 통계 초기화 메뉴 추가
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, "통계 초기화");
        return true;
    }

    // 통계 초기화 메뉴 선택 시 확인 대화상자 표시
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                showResetConfirmDialog();
                return true;
        }

        return false;
    }

    // 통계 초기화 전 사용자 확인을 받는 대화상자
    private void showResetConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("통계 초기화")
                .setMessage("개인정보 보호 리포트 통계를 정말 초기화하시겠습니까?")
                .setPositiveButton("초기화", (dialog, which) -> {
                    dbHelper.resetPrivacyStats();
                    loadStats();
                    Toast.makeText(this, "통계가 초기화되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
