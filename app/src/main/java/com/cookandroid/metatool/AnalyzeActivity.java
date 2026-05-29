package com.cookandroid.metatool;

import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 이미지 메타데이터 분석, 지도 표시, 선택 삭제 및 저장을 담당하는 화면
public class AnalyzeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ListView listExif;
    private Button btnSaveResult;
    private TextView tvNoLocation;

    // EXIF GPS 정보를 지도에 표시하기 위한 Google Map 객체
    private GoogleMap gMap;
    private MapFragment mapFrag;
    private Double gpsLatitude = null;
    private Double gpsLongitude = null;
    private String gpsLatitudeValue = null;
    private String gpsLongitudeValue = null;

    // 선택한 이미지 Uri, 저장 품질, 원본 삭제 여부
    private Uri selectedImageUri;
    private int selectedQuality = 100;
    private boolean deleteOriginalAfterSave = false;

    // 실제 저장/삭제 처리에 사용하는 EXIF 데이터
    private final List<MetadataItem> metadataItems = new ArrayList<>();

    // 화면에 보여줄 위험도 그룹 + 메타데이터 행
    private final List<RiskListItem> riskListItems = new ArrayList<>();

    // 사용자가 제거 대상으로 선택한 태그
    private final Set<String> selectedTagSet = new HashSet<>();

    // Android 11 이상에서 원본 이미지 삭제 요청 결과를 받는 런처
    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;

    // ListView에 표시할 항목 유형
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_METADATA = 1;
    private static final int TYPE_EMPTY = 2;
    private static final int TYPE_SAFE = 3;

    // 메타데이터 위험도 분류 기준
    private static final String RISK_HIGH = "고위험";
    private static final String RISK_MEDIUM = "중위험";
    private static final String RISK_LOW = "저위험";
    private static final String RISK_OTHER = "일반 정보";

    // 실제 EXIF 태그가 아닌 화면 표시 및 처리용 가상 태그
    private static final String TAG_GPS_LOCATION_GROUP = "GPS_LOCATION_GROUP";
    private static final String TAG_FILE_SIZE = "FILE_SIZE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);
        setTitle("메타데이터 분석 결과");

        // activity_analyze.xml의 View 연결
        listExif = findViewById(R.id.listExif);
        btnSaveResult = findViewById(R.id.btnSaveResult);
        tvNoLocation = findViewById(R.id.tvNoGps);

        // Google Map Fragment 연결
        FragmentManager fragmentManager = getFragmentManager();
        mapFrag = (MapFragment) fragmentManager.findFragmentById(R.id.map);
        if (mapFrag != null) {
            mapFrag.getMapAsync(this);
        }

        listExif.setChoiceMode(ListView.CHOICE_MODE_NONE);

        // 시스템 원본 이미지 삭제 요청 결과 처리
        deleteRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "원본 이미지가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "삭제가 취소되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // MainActivity에서 전달받은 이미지 Uri로 EXIF 분석 시작
        String uriString = getIntent().getStringExtra("image_uri");
        if (uriString != null) {
            selectedImageUri = Uri.parse(uriString);
            loadExifData(selectedImageUri);
        } else {
            Toast.makeText(this, "이미지 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 제거 설정 Bottom Sheet 표시
        btnSaveResult.setOnClickListener(v -> showSaveBottomSheet());
    }

    @Override
    // Google Map이 준비되면 지도 설정 후 GPS 위치 표시
    public void onMapReady(GoogleMap map) {
        gMap = map;
        gMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        gMap.getUiSettings().setZoomControlsEnabled(true);

        showGpsOnMapIfAvailable();
    }

    // 이미지 Uri에서 EXIF 정보를 읽어 메타데이터 목록 구성
    private void loadExifData(Uri imageUri) {
        metadataItems.clear();
        riskListItems.clear();
        selectedTagSet.clear();

        gpsLatitude = null;
        gpsLongitude = null;
        gpsLatitudeValue = null;
        gpsLongitudeValue = null;

        // 분석할 주요 EXIF 태그 목록
        String[] tags = {
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_IMAGE_WIDTH,
                ExifInterface.TAG_IMAGE_LENGTH,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH
        };

        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            if (inputStream != null) {
                ExifInterface exifInterface = new ExifInterface(inputStream);

                float[] latLong = new float[2];
                if (exifInterface.getLatLong(latLong)) {
                    gpsLatitude = (double) latLong[0];
                    gpsLongitude = (double) latLong[1];
                }

                gpsLatitudeValue = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
                gpsLongitudeValue = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);

                boolean gpsLocationItemAdded = false;

                for (String tag : tags) {
                    String value = exifInterface.getAttribute(tag);

                    if (value != null && !value.trim().isEmpty()) {

                        // 위도/경도는 따로 표시하지 않고, 하나의 GPS 위치 정보 항목으로 묶어서 표시
                        if (tag.equals(ExifInterface.TAG_GPS_LATITUDE)
                                || tag.equals(ExifInterface.TAG_GPS_LONGITUDE)) {

                            if (!gpsLocationItemAdded
                                    && gpsLatitude != null
                                    && gpsLongitude != null
                                    && gpsLatitudeValue != null
                                    && gpsLongitudeValue != null) {

                                String displayValue = gpsLatitude + "°, "
                                        + gpsLongitude + "°";

                                MetadataItem item = new MetadataItem(
                                        TAG_GPS_LOCATION_GROUP,
                                        "",
                                        getTagDisplayName(TAG_GPS_LOCATION_GROUP),
                                        displayValue,
                                        getRiskLevel(TAG_GPS_LOCATION_GROUP),
                                        isRemovableTag(TAG_GPS_LOCATION_GROUP)
                                );

                                metadataItems.add(item);
                                gpsLocationItemAdded = true;
                            }

                            continue;
                        }

                        String displayValue;

                        if (tag.equals(ExifInterface.TAG_IMAGE_WIDTH)) {
                            displayValue = value + " px";
                        } else if (tag.equals(ExifInterface.TAG_IMAGE_LENGTH)) {
                            displayValue = value + " px";
                        } else {
                            displayValue = value;
                        }

                        MetadataItem item = new MetadataItem(
                                tag,
                                value,
                                getTagDisplayName(tag),
                                displayValue,
                                getRiskLevel(tag),
                                isRemovableTag(tag)
                        );

                        metadataItems.add(item);
                    }
                }
            }
        } catch (Exception e) {
            riskListItems.add(RiskListItem.empty("EXIF 정보를 읽는 중 오류 발생: " + e.getMessage()));
        }

        addFileSizeInfo(imageUri);

        buildRiskListItems();

        if (riskListItems.isEmpty()) {
            riskListItems.add(RiskListItem.empty("표시할 EXIF 정보가 없습니다."));
        }

        RiskExifAdapter adapter = new RiskExifAdapter();
        listExif.setAdapter(adapter);

        showGpsOnMapIfAvailable();
    }

    // EXIF가 아닌 파일 속성인 이미지 용량을 일반 정보에 추가
    private void addFileSizeInfo(Uri imageUri) {
        long fileSize = getImageFileSize(imageUri);

        if (fileSize < 0) return;

        MetadataItem item = new MetadataItem(
                TAG_FILE_SIZE,
                String.valueOf(fileSize),
                getTagDisplayName(TAG_FILE_SIZE),
                formatFileSize(fileSize),
                getRiskLevel(TAG_FILE_SIZE),
                isRemovableTag(TAG_FILE_SIZE)
        );

        // 일반 정보 섹션 안에서 파일 용량이 가장 먼저 표시되도록 삽입
        int insertIndex = metadataItems.size();

        for (int i = 0; i < metadataItems.size(); i++) {
            if (RISK_OTHER.equals(metadataItems.get(i).riskLevel)) {
                insertIndex = i;
                break;
            }
        }

        metadataItems.add(insertIndex, item);
    }

    // Uri에서 이미지 파일 용량 조회
    private long getImageFileSize(Uri uri) {
        if (uri == null) return -1;

        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }

        return -1;
    }

    // 파일 용량을 B, KB, MB 단위로 변환
    private String formatFileSize(long size) {
        if (size < 0) return "알 수 없음";

        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    // 메타데이터를 위험도별 그룹으로 정리
    private void buildRiskListItems() {
        boolean hasSensitiveMetadata = hasRiskMetadata(RISK_HIGH)
                || hasRiskMetadata(RISK_MEDIUM)
                || hasRiskMetadata(RISK_LOW);

        if (!hasSensitiveMetadata && !metadataItems.isEmpty()) {
            riskListItems.add(RiskListItem.safe(
                    "민감한 메타데이터가 발견되지 않았습니다!",
                    "이 이미지는 안전하게 공유할 수 있습니다."
            ));
        }

        addRiskSection(RISK_HIGH, "촬영 위치가 직접 노출될 수 있는 정보");
        addMetadataItemsByRisk(RISK_HIGH);

        addRiskSection(RISK_MEDIUM, "촬영 시점이나 사용 기기를 유추할 수 있는 정보");
        addMetadataItemsByRisk(RISK_MEDIUM);

        addRiskSection(RISK_LOW, "편집 환경 등 간접적으로 노출될 수 있는 정보");
        addMetadataItemsByRisk(RISK_LOW);

        addRiskSection(RISK_OTHER, "");
        addMetadataItemsByRisk(RISK_OTHER);

        // 메타데이터가 없는 위험도 섹션은 화면에서 제거
        removeEmptySections();
    }

    // 특정 위험도에 해당하는 메타데이터가 있는지 확인
    private boolean hasRiskMetadata(String riskLevel) {
        for (MetadataItem item : metadataItems) {
            if (riskLevel.equals(item.riskLevel)) {
                return true;
            }
        }

        return false;
    }

    // 위험도 섹션 헤더 추가
    private void addRiskSection(String riskLevel, String description) {
        riskListItems.add(RiskListItem.header(riskLevel, description));
    }

    // 특정 위험도에 해당하는 메타데이터 항목 추가
    private void addMetadataItemsByRisk(String riskLevel) {
        for (MetadataItem item : metadataItems) {
            if (riskLevel.equals(item.riskLevel)) {
                riskListItems.add(RiskListItem.metadata(item));
            }
        }
    }

    // 메타데이터가 없는 위험도 섹션 제거
    private void removeEmptySections() {
        List<RiskListItem> cleaned = new ArrayList<>();

        for (int i = 0; i < riskListItems.size(); i++) {
            RiskListItem current = riskListItems.get(i);

            if (current.type == TYPE_HEADER) {
                boolean hasMetadata = false;

                for (int j = i + 1; j < riskListItems.size(); j++) {
                    RiskListItem next = riskListItems.get(j);

                    if (next.type == TYPE_HEADER) break;

                    if (next.type == TYPE_METADATA) {
                        hasMetadata = true;
                        break;
                    }
                }

                if (hasMetadata) {
                    cleaned.add(current);
                }

            } else {
                cleaned.add(current);
            }
        }

        riskListItems.clear();
        riskListItems.addAll(cleaned);
    }

    // 제거 가능한 메타데이터인지 판단
    private boolean isRemovableTag(String tag) {
        return !tag.equals(ExifInterface.TAG_IMAGE_WIDTH)
                && !tag.equals(ExifInterface.TAG_IMAGE_LENGTH)
                && !tag.equals(TAG_FILE_SIZE);
    }

    // 태그별 개인정보 노출 위험도 분류
    private String getRiskLevel(String tag) {
        if (tag.equals(TAG_GPS_LOCATION_GROUP)) {
            return RISK_HIGH;
        }

        if (tag.equals(ExifInterface.TAG_DATETIME)
                || tag.equals(ExifInterface.TAG_MAKE)
                || tag.equals(ExifInterface.TAG_MODEL)) {
            return RISK_MEDIUM;
        }

        if (tag.equals(ExifInterface.TAG_SOFTWARE)) {
            return RISK_LOW;
        }

        return RISK_OTHER;
    }

    // GPS 정보가 있으면 지도에 표시하고, 없으면 안내 문구 표시
    private void showGpsOnMapIfAvailable() {
        if (gpsLatitude == null || gpsLongitude == null) {
            if (mapFrag != null && mapFrag.getView() != null) {
                mapFrag.getView().setVisibility(View.GONE);
            }

            if (tvNoLocation != null) {
                tvNoLocation.setVisibility(View.VISIBLE);
                tvNoLocation.setText("위치 정보 없음");
            }

            return;
        }

        if (mapFrag != null && mapFrag.getView() != null) {
            mapFrag.getView().setVisibility(View.VISIBLE);
        }

        if (tvNoLocation != null) {
            tvNoLocation.setVisibility(View.GONE);
        }

        if (gMap == null) return;

        LatLng photoLocation = new LatLng(gpsLatitude, gpsLongitude);

        gMap.clear();
        gMap.addMarker(new MarkerOptions()
                .position(photoLocation)
                .title("사진 촬영 위치")
                .snippet("위도: " + gpsLatitude + ", 경도: " + gpsLongitude));
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(photoLocation, 12));
    }

    // 선택한 메타데이터, 저장 품질, 원본 삭제 여부를 Bottom Sheet로 확인
    private void showSaveBottomSheet() {
        if (selectedTagSet.isEmpty()) {
            Toast.makeText(this, "제거할 메타데이터를 하나 이상 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(20), dp(12), dp(20), dp(20));

        // Bottom Sheet 상단 라운딩 배경
        GradientDrawable bottomSheetBackground = new GradientDrawable();
        bottomSheetBackground.setColor(Color.WHITE);
        bottomSheetBackground.setStroke(dp(1), Color.rgb(220, 220, 220));
        bottomSheetBackground.setCornerRadii(new float[]{
                dp(24), dp(24),   // 왼쪽 상단
                dp(24), dp(24),   // 오른쪽 상단
                0, 0,             // 오른쪽 하단
                0, 0              // 왼쪽 하단
        });
        rootLayout.setBackground(bottomSheetBackground);

        // Bottom Sheet 상단 손잡이 바
        View handleBar = new View(this);
        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(Color.rgb(190, 190, 190));
        handleBackground.setCornerRadius(dp(3));
        handleBar.setBackground(handleBackground);

        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(46), dp(5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(16));
        rootLayout.addView(handleBar, handleParams);

        TextView selectedTitle = new TextView(this);
        selectedTitle.setText("선택한 메타데이터");
        selectedTitle.setTextSize(15);
        selectedTitle.setTypeface(null, Typeface.BOLD);
        selectedTitle.setTextColor(Color.DKGRAY);
        selectedTitle.setPadding(0, 0, 0, dp(6));
        rootLayout.addView(selectedTitle);

        TextView selectedMetadataText = new TextView(this);
        selectedMetadataText.setText(getSelectedMetadataText());
        selectedMetadataText.setTextSize(14);
        selectedMetadataText.setTextColor(Color.BLACK);
        selectedMetadataText.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable selectedBackground = new GradientDrawable();
        selectedBackground.setColor(Color.rgb(245, 245, 245));
        selectedBackground.setStroke(dp(1), Color.LTGRAY);
        selectedBackground.setCornerRadius(dp(4));
        selectedMetadataText.setBackground(selectedBackground);

        ScrollView selectedScrollView = new ScrollView(this);
        selectedScrollView.addView(selectedMetadataText);
        rootLayout.addView(
                selectedScrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(140)
                )
        );

        TextView qualityTitle = new TextView(this);
        qualityTitle.setText("저장 품질 선택");
        qualityTitle.setTextSize(15);
        qualityTitle.setTypeface(null, Typeface.BOLD);
        qualityTitle.setTextColor(Color.DKGRAY);
        qualityTitle.setPadding(0, dp(16), 0, dp(6));
        rootLayout.addView(qualityTitle);

        Spinner bottomSheetQualitySpinner = new Spinner(this);

        List<String> qualityLabels = new ArrayList<>();
        qualityLabels.add("원본 (100%)");
        qualityLabels.add("고품질 (75%)");
        qualityLabels.add("중간 품질 (50%)");
        qualityLabels.add("저품질 (25%)");
        final int[] qualityValues = {100, 75, 50, 25};

        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, qualityLabels
        );
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bottomSheetQualitySpinner.setAdapter(qualityAdapter);

        int selectedPosition = 0;
        for (int i = 0; i < qualityValues.length; i++) {
            if (qualityValues[i] == selectedQuality) {
                selectedPosition = i;
                break;
            }
        }
        bottomSheetQualitySpinner.setSelection(selectedPosition);

        bottomSheetQualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                selectedQuality = qualityValues[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedQuality = 100;
            }
        });

        rootLayout.addView(
                bottomSheetQualitySpinner,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout deleteOptionLayout = new LinearLayout(this);
        deleteOptionLayout.setOrientation(LinearLayout.HORIZONTAL);
        deleteOptionLayout.setGravity(Gravity.CENTER_VERTICAL);
        deleteOptionLayout.setPadding(0, dp(16), 0, 0);

        TextView deleteOptionText = new TextView(this);
        deleteOptionText.setText("원본 이미지 삭제");
        deleteOptionText.setTextSize(15);
        deleteOptionText.setTextColor(Color.DKGRAY);

        Switch deleteOriginalSwitch = new Switch(this);
        deleteOriginalSwitch.setChecked(deleteOriginalAfterSave);
        deleteOriginalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            deleteOriginalAfterSave = isChecked;
        });

        LinearLayout.LayoutParams deleteTextParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );

        deleteOptionLayout.addView(deleteOptionText, deleteTextParams);
        deleteOptionLayout.addView(deleteOriginalSwitch);

        rootLayout.addView(deleteOptionLayout);

        Button btnSaveInBottomSheet = new Button(this);
        btnSaveInBottomSheet.setText("저장하기");
        btnSaveInBottomSheet.setAllCaps(false);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, dp(20), 0, 0);
        rootLayout.addView(btnSaveInBottomSheet, buttonParams);

        btnSaveInBottomSheet.setOnClickListener(v -> {
            deleteOriginalAfterSave = deleteOriginalSwitch.isChecked();
            bottomSheetDialog.dismiss();
            saveImageWithSelectedExifRemoved();
        });

        bottomSheetDialog.setContentView(rootLayout);

        // 기본 Bottom Sheet 배경을 투명하게 만들어 커스텀 라운딩 배경이 보이도록 처리
        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
            );

            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        bottomSheetDialog.show();
    }

    // Bottom Sheet에 표시할 선택된 메타데이터 목록 생성
    private String getSelectedMetadataText() {
        StringBuilder builder = new StringBuilder();

        for (MetadataItem item : metadataItems) {
            if (selectedTagSet.contains(item.tag)) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }

                builder.append("• ")
                        .append(item.displayName)
                        .append(": ")
                        .append(item.displayValue);
            }
        }

        if (builder.length() == 0) {
            return "선택된 메타데이터가 없습니다.";
        }

        return builder.toString();
    }

    // 선택한 메타데이터를 제외하고 새 이미지로 저장
    private void saveImageWithSelectedExifRemoved() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "선택된 이미지가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedTagSet.isEmpty()) {
            Toast.makeText(this, "제거할 메타데이터를 하나 이상 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Bitmap bitmap;
            try (InputStream imgStream = getContentResolver().openInputStream(selectedImageUri)) {
                bitmap = BitmapFactory.decodeStream(imgStream);
            }

            if (bitmap == null) {
                Toast.makeText(this, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            File tempFile = new File(getCacheDir(), "temp_clean.jpg");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, selectedQuality, fos);
                fos.flush();
            }
            bitmap.recycle();

            ExifInterface exif = new ExifInterface(tempFile.getAbsolutePath());

            for (MetadataItem item : metadataItems) {
                if (selectedTagSet.contains(item.tag)) continue;

                String tag = item.tag;
                String value = item.value;

                if (tag.equals(TAG_GPS_LOCATION_GROUP)) {
                    if (gpsLatitudeValue != null && gpsLongitudeValue != null) {
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, gpsLatitudeValue);
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, gpsLongitudeValue);
                        restoreGpsRef(ExifInterface.TAG_GPS_LATITUDE_REF, exif);
                        restoreGpsRef(ExifInterface.TAG_GPS_LONGITUDE_REF, exif);
                    }
                    continue;
                }

                // 파일 용량은 EXIF 태그가 아니라 파일 속성이므로 복원 대상에서 제외
                if (tag.equals(TAG_FILE_SIZE)) {
                    continue;
                }

                exif.setAttribute(tag, value);

                if (tag.equals(ExifInterface.TAG_DATETIME)) {
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, value);
                    exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, value);
                }
            }

            exif.saveAttributes();

            String fileName = System.currentTimeMillis() + "_meta_removed.jpg";
            Uri savedUri = saveTempFileToMediaStore(tempFile, fileName);
            tempFile.delete();

            if (savedUri == null) {
                Toast.makeText(this, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            handleSaveComplete(selectedTagSet.size());

        } catch (Exception e) {
            Toast.makeText(this, "저장 중 오류 발생: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 저장 완료 후 통계 반영 및 원본 삭제 여부 처리
    private void handleSaveComplete(int removedCount) {
        savePrivacyStats();

        if (deleteOriginalAfterSave) {
            deleteOriginalImage();
            return;
        }

        Toast.makeText(
                this,
                removedCount + "개의 메타데이터가 제거된 이미지가 저장되었습니다. (품질: " + selectedQuality + "%)",
                Toast.LENGTH_LONG
        ).show();

        finish();
    }

    // 제거한 메타데이터 개수를 SQLite 통계에 반영
    private void savePrivacyStats() {
        int removedCount = selectedTagSet.size();
        int highRiskCount = countSelectedRisk(RISK_HIGH);
        int mediumRiskCount = countSelectedRisk(RISK_MEDIUM);
        int lowRiskCount = countSelectedRisk(RISK_LOW);

        PrivacyStatsDBHelper dbHelper = new PrivacyStatsDBHelper(this);
        dbHelper.updatePrivacyStats(
                removedCount,
                highRiskCount,
                mediumRiskCount,
                lowRiskCount
        );
    }

    // 선택된 메타데이터 중 특정 위험도 항목 수 계산
    private int countSelectedRisk(String riskLevel) {
        int count = 0;

        for (MetadataItem item : metadataItems) {
            if (selectedTagSet.contains(item.tag)
                    && riskLevel.equals(item.riskLevel)) {
                count++;
            }
        }

        return count;
    }

    // 원본 이미지 삭제를 위해 MediaStore에서 사용할 수 있는 Uri로 변환
    private Uri resolveToMediaStoreItemUri(Uri anyUri) {
        if (anyUri == null) return null;

        String auth = anyUri.getAuthority();

        if ("media".equals(auth)) {
            try (Cursor c = getContentResolver().query(
                    anyUri, new String[]{MediaStore.Images.Media._ID}, null, null, null
            )) {
                if (c != null && c.moveToFirst()) {
                    long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                }
            } catch (Exception ignored) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && DocumentsContract.isDocumentUri(this, anyUri)) {
            try {
                String docId = DocumentsContract.getDocumentId(anyUri);
                if (docId != null && docId.contains(":")) {
                    String[] parts = docId.split(":");
                    long id = Long.parseLong(parts[1]);
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                }
            } catch (Exception ignored) {
            }
        }

        try (Cursor c = getContentResolver().query(
                anyUri, new String[]{MediaStore.Images.Media._ID}, null, null, null
        )) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    // 선택한 원본 이미지를 삭제
    private void deleteOriginalImage() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "삭제할 원본 이미지가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri itemUri = resolveToMediaStoreItemUri(selectedImageUri);
        if (itemUri == null) {
            Toast.makeText(this, "삭제 가능한 원본 이미지 Uri를 찾지 못했습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.app.PendingIntent pi = MediaStore.createDeleteRequest(
                        getContentResolver(),
                        Collections.singletonList(itemUri)
                );
                deleteRequestLauncher.launch(
                        new IntentSenderRequest.Builder(pi.getIntentSender()).build()
                );
            } else {
                int rows = getContentResolver().delete(itemUri, null, null);
                if (rows > 0) {
                    Toast.makeText(this, "원본 이미지가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "원본 이미지 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "삭제 권한이 없습니다. 갤러리에서 직접 삭제해 주세요.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "삭제 중 오류 발생: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // GPS 위도/경도 방향값을 원본 EXIF에서 복원
    private void restoreGpsRef(String refTag, ExifInterface targetExif) {
        try (InputStream s = getContentResolver().openInputStream(selectedImageUri)) {
            if (s == null) return;
            ExifInterface src = new ExifInterface(s);
            String refValue = src.getAttribute(refTag);
            if (refValue != null && !refValue.isEmpty()) {
                targetExif.setAttribute(refTag, refValue);
            }
        } catch (Exception ignored) {
        }
    }

    // 임시 파일을 갤러리의 MetaTool 폴더에 저장
    private Uri saveTempFileToMediaStore(File tempFile, String displayName) {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MetaTool");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;

        try (OutputStream out = resolver.openOutputStream(uri);
             InputStream in = new FileInputStream(tempFile)) {

            if (out == null) return null;

            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            out.flush();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues finish = new ContentValues();
                finish.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, finish, null, null);
            }

            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    // EXIF 태그명을 사용자에게 보여줄 한글 이름으로 변환
    private String getTagDisplayName(String tag) {
        switch (tag) {
            case ExifInterface.TAG_DATETIME: return "촬영 날짜";
            case ExifInterface.TAG_MAKE: return "제조사";
            case ExifInterface.TAG_MODEL: return "모델명";
            case ExifInterface.TAG_SOFTWARE: return "소프트웨어 버전";
            case ExifInterface.TAG_IMAGE_WIDTH: return "가로 크기";
            case ExifInterface.TAG_IMAGE_LENGTH: return "세로 크기";
            case TAG_FILE_SIZE: return "파일 용량";
            case TAG_GPS_LOCATION_GROUP: return "GPS 위치 정보";
            case ExifInterface.TAG_GPS_LATITUDE: return "위도";
            case ExifInterface.TAG_GPS_LONGITUDE: return "경도";
            case ExifInterface.TAG_F_NUMBER: return "조리개 값";
            case ExifInterface.TAG_EXPOSURE_TIME: return "노출 시간";
            case ExifInterface.TAG_ISO_SPEED_RATINGS: return "ISO 감도";
            case ExifInterface.TAG_FOCAL_LENGTH: return "초점 거리";
            default: return tag;
        }
    }

    // ActionBar에 지도 유형 변경 메뉴 추가
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, "일반 지도");
        menu.add(0, 2, 0, "위성 지도");
        return true;
    }

    // 메뉴 선택에 따라 지도 유형 변경
    public boolean onOptionsItemSelected(MenuItem item) {
        if (gMap == null) return false;

        switch(item.getItemId()) {
            case 1:
                gMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                return true;
            case 2:
                gMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                return true;
        }
        return false;
    }

    // 실제 메타데이터 한 개의 정보를 저장하는 클래스
    private static class MetadataItem {
        String tag;
        String value;
        String displayName;
        String displayValue;
        String riskLevel;
        boolean removable;

        MetadataItem(String tag, String value, String displayName, String displayValue,
                     String riskLevel, boolean removable) {
            this.tag = tag;
            this.value = value;
            this.displayName = displayName;
            this.displayValue = displayValue;
            this.riskLevel = riskLevel;
            this.removable = removable;
        }
    }

    // 위험도 헤더, 메타데이터 항목, 안내 문구를 구분하기 위한 클래스
    private static class RiskListItem {
        int type;
        String title;
        String description;
        MetadataItem metadataItem;

        static RiskListItem header(String title, String description) {
            RiskListItem item = new RiskListItem();
            item.type = TYPE_HEADER;
            item.title = title;
            item.description = description;
            return item;
        }

        static RiskListItem metadata(MetadataItem metadataItem) {
            RiskListItem item = new RiskListItem();
            item.type = TYPE_METADATA;
            item.metadataItem = metadataItem;
            return item;
        }

        static RiskListItem empty(String message) {
            RiskListItem item = new RiskListItem();
            item.type = TYPE_EMPTY;
            item.title = message;
            return item;
        }

        static RiskListItem safe(String title, String description) {
            RiskListItem item = new RiskListItem();
            item.type = TYPE_SAFE;
            item.title = title;
            item.description = description;
            return item;
        }
    }

    // 위험도별 메타데이터 목록을 ListView에 표시하는 Adapter
    private class RiskExifAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return riskListItems.size();
        }

        @Override
        public Object getItem(int position) {
            return riskListItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            return riskListItems.get(position).type == TYPE_METADATA;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RiskListItem item = riskListItems.get(position);

            if (item.type == TYPE_HEADER) {
                return createHeaderView(item);
            }

            if (item.type == TYPE_EMPTY) {
                return createEmptyView(item.title);
            }

            if (item.type == TYPE_SAFE) {
                return createSafeView(item);
            }

            return createMetadataView(item.metadataItem);
        }

        // 위험도 섹션 헤더 View 생성
        private View createHeaderView(RiskListItem item) {
            LinearLayout layout = new LinearLayout(AnalyzeActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(dp(8), dp(10), dp(8), dp(6));

            TextView title = new TextView(AnalyzeActivity.this);
            title.setText(getRiskIcon(item.title) + " " + item.title);
            title.setTextColor(getRiskColor(item.title));
            title.setTextSize(15);
            title.setTypeface(null, Typeface.BOLD);

            layout.addView(title);

            if (item.description != null && !item.description.isEmpty()) {
                TextView description = new TextView(AnalyzeActivity.this);
                description.setText(item.description);
                description.setTextColor(Color.DKGRAY);
                description.setTextSize(12);
                description.setPadding(dp(22), dp(2), 0, 0);

                layout.addView(description);
            }

            return layout;
        }

        // 표시할 메타데이터가 없을 때 안내 View 생성
        private View createEmptyView(String message) {
            TextView textView = new TextView(AnalyzeActivity.this);
            textView.setText(message);
            textView.setTextSize(15);
            textView.setTextColor(Color.DKGRAY);
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(dp(12), dp(24), dp(12), dp(24));
            return textView;
        }

        // 민감한 메타데이터가 없을 때 안전 안내 View 생성
        private View createSafeView(RiskListItem item) {
            LinearLayout outer = new LinearLayout(AnalyzeActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(232, 248, 239));
            background.setStroke(dp(1), Color.rgb(165, 220, 185));
            background.setCornerRadius(dp(4));
            outer.setBackground(background);

            LinearLayout titleRow = new LinearLayout(AnalyzeActivity.this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView icon = new TextView(AnalyzeActivity.this);
            icon.setText("✓");
            icon.setTextColor(Color.rgb(0, 170, 75));
            icon.setTextSize(18);
            icon.setTypeface(null, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);

            TextView title = new TextView(AnalyzeActivity.this);
            title.setText(item.title);
            title.setTextColor(Color.rgb(0, 150, 65));
            title.setTextSize(16);
            title.setTypeface(null, Typeface.BOLD);
            title.setPadding(dp(10), 0, 0, 0);

            titleRow.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            titleRow.addView(title);

            TextView description = new TextView(AnalyzeActivity.this);
            description.setText(item.description);
            description.setTextColor(Color.DKGRAY);
            description.setTextSize(13);
            description.setPadding(dp(38), dp(4), 0, 0);

            outer.addView(titleRow);
            outer.addView(description);

            return outer;
        }

        // 메타데이터 항목 카드 View 생성
        private View createMetadataView(MetadataItem item) {
            LinearLayout outer = new LinearLayout(AnalyzeActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(dp(10), dp(8), dp(10), dp(8));

            GradientDrawable background = new GradientDrawable();
            background.setColor(getRiskBackgroundColor(item.riskLevel));
            background.setStroke(dp(1), getRiskBorderColor(item.riskLevel));
            background.setCornerRadius(dp(4));
            outer.setBackground(background);

            LinearLayout row = new LinearLayout(AnalyzeActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox checkBox = null;
            View leftSpace = null;

            if (item.removable) {
                checkBox = new CheckBox(AnalyzeActivity.this);
                checkBox.setChecked(selectedTagSet.contains(item.tag));
                checkBox.setOnCheckedChangeListener(null);
            } else {
                selectedTagSet.remove(item.tag);
                leftSpace = new View(AnalyzeActivity.this);
            }

            LinearLayout textBox = new LinearLayout(AnalyzeActivity.this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setPadding(dp(4), 0, 0, 0);

            TextView title = new TextView(AnalyzeActivity.this);
            title.setText(item.displayName);
            title.setTextColor(getRiskColor(item.riskLevel));
            title.setTextSize(16);
            title.setTypeface(null, Typeface.BOLD);
            title.setPadding(0, 0, 0, dp(4));

            TextView value = new TextView(AnalyzeActivity.this);
            value.setText(item.displayValue);
            value.setTextColor(Color.BLACK);
            value.setTextSize(13);
            value.setPadding(dp(8), dp(6), dp(8), dp(6));

            GradientDrawable valueBackground = new GradientDrawable();
            valueBackground.setColor(Color.WHITE);
            valueBackground.setStroke(dp(1), Color.LTGRAY);
            valueBackground.setCornerRadius(dp(3));
            value.setBackground(valueBackground);

            textBox.addView(title);
            textBox.addView(value);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            );

            if (item.removable) {
                row.addView(checkBox);
            } else {
                row.addView(leftSpace, new LinearLayout.LayoutParams(dp(30), dp(48)));
            }

            row.addView(textBox, textParams);
            outer.addView(row);

            if (item.removable) {
                CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedTagSet.add(item.tag);
                    } else {
                        selectedTagSet.remove(item.tag);
                    }
                };

                checkBox.setOnCheckedChangeListener(listener);

                outer.setOnClickListener(v -> {
                    boolean newChecked = !selectedTagSet.contains(item.tag);

                    if (newChecked) {
                        selectedTagSet.add(item.tag);
                    } else {
                        selectedTagSet.remove(item.tag);
                    }

                    notifyDataSetChanged();
                });
            } else {
                outer.setOnClickListener(null);
            }

            LinearLayout wrapper = new LinearLayout(AnalyzeActivity.this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(0, 0, 0, dp(4));

            wrapper.addView(outer);

            return wrapper;
        }
    }

    // 위험도별 글자 색상 반환
    private int getRiskColor(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return Color.rgb(190, 0, 30);
        }

        if (RISK_MEDIUM.equals(riskLevel)) {
            return Color.rgb(230, 160, 0);
        }

        if (RISK_LOW.equals(riskLevel)) {
            return Color.rgb(50, 120, 255);
        }

        return Color.rgb(90, 90, 90);
    }

    // 위험도별 배경 색상 반환
    private int getRiskBackgroundColor(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return Color.rgb(255, 239, 241);
        }

        if (RISK_MEDIUM.equals(riskLevel)) {
            return Color.rgb(255, 249, 230);
        }

        if (RISK_LOW.equals(riskLevel)) {
            return Color.rgb(239, 246, 255);
        }

        return Color.rgb(245, 245, 245);
    }

    // 위험도별 테두리 색상 반환
    private int getRiskBorderColor(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return Color.rgb(245, 190, 195);
        }

        if (RISK_MEDIUM.equals(riskLevel)) {
            return Color.rgb(245, 220, 150);
        }

        if (RISK_LOW.equals(riskLevel)) {
            return Color.rgb(190, 215, 255);
        }

        return Color.rgb(210, 210, 210);
    }

    // 위험도 섹션에 표시할 아이콘 반환
    private String getRiskIcon(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel) || RISK_MEDIUM.equals(riskLevel) || RISK_LOW.equals(riskLevel)) {
            return "⚠";
        }

        return "•";
    }

    // dp 값을 현재 화면 밀도에 맞는 px 값으로 변환
    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
