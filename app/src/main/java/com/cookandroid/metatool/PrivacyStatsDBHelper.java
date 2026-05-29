package com.cookandroid.metatool;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class PrivacyStatsDBHelper extends SQLiteOpenHelper {

    // 앱 내부에 생성되는 SQLite 데이터베이스 이름과 버전
    private static final String DATABASE_NAME = "metatool.db";
    private static final int DATABASE_VERSION = 1;

    // 개인정보 보호 통계를 저장하는 테이블 이름
    public static final String TABLE_PRIVACY_STATS = "privacy_stats";

    public PrivacyStatsDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // 데이터베이스가 처음 생성될 때 통계 테이블을 생성
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableSql =
                "CREATE TABLE " + TABLE_PRIVACY_STATS + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "total_protected_images INTEGER DEFAULT 0, " +
                        "total_removed_metadata INTEGER DEFAULT 0, " +
                        "high_risk_protected INTEGER DEFAULT 0, " +
                        "medium_risk_protected INTEGER DEFAULT 0, " +
                        "low_risk_protected INTEGER DEFAULT 0" +
                        ");";

        db.execSQL(createTableSql);

        // 통계 값을 누적 관리하기 위해 초기 데이터 1행을 삽입
        db.execSQL(
                "INSERT INTO " + TABLE_PRIVACY_STATS + " (" +
                        "total_protected_images, total_removed_metadata, " +
                        "high_risk_protected, medium_risk_protected, low_risk_protected" +
                        ") VALUES (0, 0, 0, 0, 0);"
        );
    }

    // 데이터베이스 버전이 변경될 때 기존 테이블을 삭제하고 다시 생성
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRIVACY_STATS);
        onCreate(db);
    }

    // 메타데이터 제거 작업이 완료될 때 개인정보 보호 통계를 누적 업데이트
    public void updatePrivacyStats(
            int removedCount,
            int highRiskCount,
            int mediumRiskCount,
            int lowRiskCount
    ) {
        SQLiteDatabase db = getWritableDatabase();

        String sql =
                "UPDATE " + TABLE_PRIVACY_STATS + " SET " +
                        "total_protected_images = total_protected_images + 1, " +
                        "total_removed_metadata = total_removed_metadata + ?, " +
                        "high_risk_protected = high_risk_protected + ?, " +
                        "medium_risk_protected = medium_risk_protected + ?, " +
                        "low_risk_protected = low_risk_protected + ? " +
                        "WHERE id = 1";

        db.execSQL(sql, new Object[]{
                removedCount,
                highRiskCount,
                mediumRiskCount,
                lowRiskCount
        });
    }

    // 개인정보 보호 리포트 초기화 시 기존 통계를 삭제하고 초기 상태로 재생성
    public void resetPrivacyStats() {
        SQLiteDatabase db = getWritableDatabase();

        db.execSQL("DELETE FROM " + TABLE_PRIVACY_STATS);

        db.execSQL(
                "INSERT INTO " + TABLE_PRIVACY_STATS + " (" +
                        "id, total_protected_images, total_removed_metadata, " +
                        "high_risk_protected, medium_risk_protected, low_risk_protected" +
                        ") VALUES (1, 0, 0, 0, 0, 0);"
        );
    }
}