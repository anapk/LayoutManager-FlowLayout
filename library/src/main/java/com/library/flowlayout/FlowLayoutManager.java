package com.library.flowlayout;

import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xiangcheng on 17/3/18.
 * 一种流式布局的LayoutManager
 */

public class FlowLayoutManager extends RecyclerView.LayoutManager {

    private static final String TAG = FlowLayoutManager.class.getSimpleName();
    final FlowLayoutManager self = this;

    private int width, height;
    private int left, top, right;
    //最大容器的宽度
    private int usedMaxWidth;
    //竖直方向上的偏移量
    private int verticalScrollOffset = 0;
    //计算显示的内容的高度
    private int totalHeight = 0;
    private Row row = new Row();
    private List<Row> lineRows = new ArrayList<>();

    //保存所有的Item的上下左右的偏移量信息
    private SparseArray<Rect> allItemFrames = new SparseArray<>();

    public FlowLayoutManager() {
        setAutoMeasureEnabled(true);
    }

    public class Item {
        int useHeight;
        View view;
        Rect rect;

        public Item(int useHeight, View view, Rect rect) {
            this.useHeight = useHeight;
            this.view = view;
            this.rect = rect;
        }
    }

    public class Row {
        public void setCuTop(float cuTop) {
            this.cuTop = cuTop;
        }

        public void setMaxHeight(float maxHeight) {
            this.maxHeight = maxHeight;
        }

        float cuTop;
        float maxHeight;

        List<Item> views = new ArrayList<>();

        public void addViews(Item view) {
            views.add(view);
        }
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getItemCount() == 0) {
            detachAndScrapAttachedViews(recycler);
            return;
        }
        if (state.isPreLayout()) {
            return;
        }
        if (getChildCount() == 0) {
            width = getWidth();
            height = getHeight();
            Log.d("TAG", "widthSize:" + width + ",heightSize:" + height);
            left = getPaddingLeft();
            right = getPaddingRight();
            top = getPaddingTop();
            usedMaxWidth = width - left - right;
        }
        totalHeight = 0;
        int cuLineTop = top;
        //当前行使用的宽度
        int cuLineWidth = 0;
        int itemLeft;
        int itemTop;
        int maxHeightItem = 0;
        row = new Row();
        for (int i = 0; i < getItemCount(); i++) {
            View childAt = recycler.getViewForPosition(i);
            if (View.GONE == childAt.getVisibility()) {
                continue;
            }
            measureChildWithMargins(childAt, 0, 0);
            int childWidth = getDecoratedMeasuredWidth(childAt);
            int childHeight = getDecoratedMeasuredHeight(childAt);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) childAt.getLayoutParams();
            int childUseWidth = childWidth;
            int childUseHeight = childHeight;
            //如果加上当前的item还小于最大的宽度的话
            if (cuLineWidth + childUseWidth <= usedMaxWidth) {
                itemLeft = left + cuLineWidth;
                itemTop = cuLineTop;
                Rect frame = allItemFrames.get(i);
                if (frame == null) {
                    frame = new Rect();
                }
                frame.set(itemLeft, itemTop, itemLeft + childWidth, itemTop + childHeight);
                allItemFrames.put(i, frame);
                cuLineWidth += childUseWidth;
                maxHeightItem = Math.max(maxHeightItem, childUseHeight);
                row.addViews(new Item(childUseHeight, childAt, frame));
                row.setCuTop(cuLineTop);
                row.setMaxHeight(maxHeightItem);
            } else {
                //换行
                formatAboveRow();
                cuLineTop += maxHeightItem;
                totalHeight += maxHeightItem;
                itemTop = cuLineTop;
                itemLeft = left;
                Log.d("TAG", "itemTop:" + itemTop);
                Rect frame = allItemFrames.get(i);
                if (frame == null) {
                    frame = new Rect();
                }
                frame.set(itemLeft, itemTop, itemLeft + childWidth, itemTop + childHeight);
                allItemFrames.put(i, frame);
                cuLineWidth = childUseWidth;
                maxHeightItem = childUseHeight;
                row.addViews(new Item(childUseHeight, childAt, frame));
                row.setCuTop(cuLineTop);
                row.setMaxHeight(maxHeightItem);
            }
            //不要忘了最后一行进行刷新下布局
            if (i == getItemCount() - 1) {
                formatAboveRow();
                totalHeight += maxHeightItem;
            }

        }
        totalHeight = Math.max(totalHeight, getVerticalSpace());
        fillLayout(recycler, state);
    }

    private void fillLayout(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (state.isPreLayout()) { // 跳过preLayout，preLayout主要用于支持动画
            return;
        }

        // 当前scroll offset状态下的显示区域
        Rect displayFrame = new Rect(0, verticalScrollOffset, getHorizontalSpace(), verticalScrollOffset + getVerticalSpace());

        /**
         * 将滑出屏幕的Items回收到Recycle缓存中
         */
        Rect childFrame = new Rect();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            childFrame.left = getDecoratedLeft(child);
            childFrame.top = getDecoratedTop(child);
            childFrame.right = getDecoratedRight(child);
            childFrame.bottom = getDecoratedBottom(child);
            //如果Item没有在显示区域，就说明需要回收
            if (!Rect.intersects(displayFrame, childFrame)) {
                //回收掉滑出屏幕的View
                removeAndRecycleView(child, recycler);
            }
        }

        //重新显示需要出现在屏幕的子View

        for (int j = 0; j < lineRows.size(); j++) {
            Log.d("TAG", "J:" + j);
            Row row = lineRows.get(j);
            float lineTop = row.cuTop;
            float lineBottom = lineTop + row.maxHeight;
            Log.d("TAG", "lineTop:" + lineTop);
            Log.d("TAG", "lineBottom:" + lineBottom);
            Log.d("TAG", "displayFrame.bottom:" + displayFrame.bottom);
            Log.d("TAG", "displayFrame.top:" + displayFrame.top);

            if (lineTop < displayFrame.bottom && displayFrame.top < lineBottom) {
                List<Item> views = row.views;
                Log.d(TAG, "views.size:" + views.size());
                for (int i = 0; i < views.size(); i++) {
                    View scrap = views.get(i).view;
                    measureChildWithMargins(scrap, 0, 0);
                    addView(scrap);
                    Rect frame = views.get(i).rect;

                    //将这个item布局出来
                    layoutDecoratedWithMargins(scrap,
                            frame.left,
                            frame.top - verticalScrollOffset,
                            frame.right,
                            frame.bottom - verticalScrollOffset);
                }
            }
        }
    }

    /**
     * 计算每一行没有居中的viewgroup，让居中显示
     */
    private void formatAboveRow() {
        List<Item> views = row.views;
        for (int i = 0; i < views.size(); i++) {
            View view = views.get(i).view;
            int position = getPosition(view);
            if (allItemFrames.get(position).top < row.cuTop + (row.maxHeight - views.get(i).useHeight) / 2) {
                Rect frame = allItemFrames.get(position);
                if (frame == null) {
                    frame = new Rect();
                }
                frame.set(allItemFrames.get(position).left, (int) (row.cuTop + (row.maxHeight - views.get(i).useHeight) / 2), allItemFrames.get(position).right, (int) (row.cuTop + (row.maxHeight - views.get(i).useHeight) / 2 + getDecoratedMeasuredHeight(view)));
                allItemFrames.put(position, frame);

            }
        }
        Row lineRow = row;
        Log.d(TAG, "lineRow:" + lineRow.cuTop);
        lineRows.add(lineRow);
        row = new Row();
    }

    /**
     * 竖直方向需要滑动的条件
     *
     * @return
     */
    @Override
    public boolean canScrollVertically() {
        return true;
    }

    //监听竖直方向滑动的偏移量
    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
                                  RecyclerView.State state) {

        Log.d("TAG", "totalHeight:" + totalHeight);
        //实际要滑动的距离
        int travel = dy;

        //如果滑动到最顶部
        if (verticalScrollOffset + dy < 0) {//限制滑动到顶部之后，不让继续向上滑动了
            travel = -verticalScrollOffset;//verticalScrollOffset=0
        } else if (verticalScrollOffset + dy > totalHeight - getVerticalSpace()) {//如果滑动到最底部
            travel = totalHeight - getVerticalSpace() - verticalScrollOffset;//verticalScrollOffset=totalHeight - getVerticalSpace()
        }

        //将竖直方向的偏移量+travel
        verticalScrollOffset += travel;

        // 平移容器内的item
        offsetChildrenVertical(-travel);
        fillLayout(recycler, state);
        return travel;
    }

    private int getVerticalSpace() {
        return self.getHeight() - self.getPaddingBottom() - self.getPaddingTop();
    }

    public int getHorizontalSpace() {
        return self.getWidth() - self.getPaddingLeft() - self.getPaddingRight();
    }

}
