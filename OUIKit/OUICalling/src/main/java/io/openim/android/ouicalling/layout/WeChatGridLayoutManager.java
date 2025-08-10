package io.openim.android.ouicalling.layout;

import android.content.Context;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.View;

/**
 * 微信风格九宫格布局管理器
 * 实现微信群视频通话的九宫格布局效果，支持动态调整网格大小
 */
public class WeChatGridLayoutManager extends GridLayoutManager {
    
    private static final String TAG = "WeChatGridLayoutManager";
    
    private int memberCount = 0;
    private boolean isMainVideoMode = false;
    
    public WeChatGridLayoutManager(Context context) {
        super(context, 1); // 默认1列
    }
    
    /**
     * 更新成员数量并重新计算网格布局
     */
    public void updateMemberCount(int count) {
        this.memberCount = count;
        setSpanCount(calculateOptimalSpanCount(count));
        Log.d(TAG, "更新成员数量: " + count + ", 网格列数: " + getSpanCount());
    }
    
    /**
     * 设置是否为主视频模式
     * @param isMainMode true表示有主视频，其他视频在底部小窗口显示
     */
    public void setMainVideoMode(boolean isMainMode) {
        this.isMainVideoMode = isMainMode;
        if (isMainMode) {
            setSpanCount(calculateMainModeSpanCount());
        } else {
            setSpanCount(calculateOptimalSpanCount(memberCount));
        }
        Log.d(TAG, "主视频模式: " + isMainMode + ", 网格列数: " + getSpanCount());
    }
    
    /**
     * 根据成员数量计算最优网格列数（微信风格）
     */
    private int calculateOptimalSpanCount(int count) {
        if (count <= 0) return 1;
        if (count == 1) return 1;        // 1人：1x1
        if (count == 2) return 2;        // 2人：1x2
        if (count <= 4) return 2;        // 3-4人：2x2
        if (count <= 6) return 3;        // 5-6人：2x3 或 3x2
        if (count <= 9) return 3;        // 7-9人：3x3
        return 3; // 最多9人，超出显示前9个
    }
    
    /**
     * 主视频模式下的网格列数计算
     */
    private int calculateMainModeSpanCount() {
        // 主视频模式下，底部小窗口默认4列显示
        return Math.min(4, Math.max(1, memberCount - 1));
    }
    
    /**
     * 获取动态行数
     */
    public int getCalculatedRowCount() {
        if (memberCount <= 0) return 1;
        
        int spanCount = getSpanCount();
        return (int) Math.ceil((double) memberCount / spanCount);
    }
    
    /**
     * 获取指定位置的项目跨度大小（用于不规则布局）
     */
    @Override
    public void setSpanSizeLookup(SpanSizeLookup spanSizeLookup) {
        super.setSpanSizeLookup(new WeChatSpanSizeLookup());
    }
    
    /**
     * 微信风格跨度大小查找器
     */
    private class WeChatSpanSizeLookup extends SpanSizeLookup {
        
        @Override
        public int getSpanSize(int position) {
            if (isMainVideoMode && position == 0) {
                // 主视频模式下，第一个项目占满整行
                return getSpanCount();
            }
            
            // 针对特殊情况的跨度调整
            if (memberCount == 3 && getSpanCount() == 2) {
                // 3人模式：2x2网格中，第3个人占满第二行
                return position == 2 ? 2 : 1;
            }
            
            if (memberCount == 5 && getSpanCount() == 3) {
                // 5人模式：优化布局为 2+3 的形式
                if (position < 2) return getSpanCount() / 2; // 前两个各占1.5列
                return 1; // 后面的正常占1列
            }
            
            return 1; // 默认占1列
        }
        
        @Override
        public int getSpanIndex(int position, int spanCount) {
            if (isMainVideoMode && position == 0) {
                return 0; // 主视频从第0列开始
            }
            
            // 针对特殊布局的列索引计算
            if (memberCount == 3 && spanCount == 2) {
                if (position == 2) return 0; // 第3个人从第0列开始
            }
            
            return super.getSpanIndex(position, spanCount);
        }
    }
    
    // 注意：onMeasureChild 不是 GridLayoutManager 的公开方法，所以移除这个重写
    // 如果需要自定义尺寸，可以通过其他方式实现
    
    /**
     * 根据宽度计算最优的项目高度（微信风格16:9或4:3比例）
     */
    private int calculateOptimalItemHeight(int itemWidth) {
        // 微信群视频通话通常使用4:3比例以显示更多内容
        return (int) (itemWidth * 3.0f / 4.0f);
    }
    
    /**
     * 获取当前布局的详细信息
     */
    public String getLayoutInfo() {
        return String.format("成员数: %d, 列数: %d, 行数: %d, 主视频模式: %b", 
                           memberCount, getSpanCount(), getCalculatedRowCount(), isMainVideoMode);
    }
}