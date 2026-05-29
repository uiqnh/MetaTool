package com.cookandroid.metatool;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

// ScrollView 안에서 ListView의 스크롤 중첩 문제를 해결하기 위한 커스텀 ListView
public class NonScrollListView extends ListView {

    public NonScrollListView(Context context) {
        super(context);
    }

    public NonScrollListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NonScrollListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // ListView의 자체 스크롤을 막고, 모든 항목이 한 번에 보이도록 높이를 재계산
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int expandedHeightSpec = MeasureSpec.makeMeasureSpec(
                Integer.MAX_VALUE >> 2,
                MeasureSpec.AT_MOST
        );

        super.onMeasure(widthMeasureSpec, expandedHeightSpec);

        getLayoutParams().height = getMeasuredHeight();
    }
}
