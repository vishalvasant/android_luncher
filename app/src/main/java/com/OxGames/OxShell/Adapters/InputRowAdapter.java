package com.OxGames.OxShell.Adapters;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.OxGames.OxShell.Data.DynamicInputRow;
import com.OxGames.OxShell.Helpers.AndroidHelpers;
import com.OxGames.OxShell.Helpers.MathHelpers;
import com.OxGames.OxShell.Interfaces.AdapterListener;
import com.OxGames.OxShell.Views.DynamicInputItemView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InputRowAdapter extends RecyclerView.Adapter<InputRowAdapter.RowViewHolder> {
    private Context context;
    private List<DynamicInputRow.DynamicInput> items;
    private static final int BUTTON_DIP = 128;
    private static final int MAX_VISIBLE_ITEMS = 4;
    private static final int PADDING = 20;
    private int rowWidth;
    private List<RowViewHolder> viewHolders;

    private List<AdapterListener> listeners;

    public InputRowAdapter(Context context, DynamicInputRow.DynamicInput... items) {
        this.context = context;
        this.items = new ArrayList();
        this.viewHolders = new ArrayList<>();
        //Log.d("InputRowAdapter", "Creating row with " + items.length + " item(s)");
        if (items != null)
            Collections.addAll(this.items, items);
        listeners = new ArrayList<>();
    }

    // TODO: remove listeners when dynamic view hidden (potential memory leak)
    public void addListener(AdapterListener listener) {
        listeners.add(listener);
    }
    public void removeListener(AdapterListener listener) {
        listeners.remove(listener);
    }
    public void clearListeners() {
        listeners.clear();
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DynamicInputItemView view = new DynamicInputItemView(context);
        view.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        RowViewHolder viewHolder = new RowViewHolder(view);
        viewHolders.add(viewHolder);
        return viewHolder;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // TODO: readjust when orientation changes
    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        //Log.d("InputRowAdapter", "Placing item @" + position + " in row");
        DynamicInputRow.DynamicInput item = items.get(position);
        holder.bindItem(item);
        refreshHolder(holder);
//        int maxVisibleItems = Math.min(MAX_VISIBLE_ITEMS, items.size());
//        int relativeIndex = position % maxVisibleItems;
//        int currentVisibleItems = Math.min(MAX_VISIBLE_ITEMS, items.size() - (position - relativeIndex));
//        //int maxWidth = Math.round((rowWidth - ((currentVisibleItems - 1) * paddingPx)) / (float)currentVisibleItems);
//        int buttonPx = Math.round(AndroidHelpers.getScaledDpToPixels(context, BUTTON_DIP));
//        int maxWidth;
//        if (item.inputType == DynamicInputRow.DynamicInput.InputType.button)
//            maxWidth = buttonPx;
//        else {
//            maxWidth = Math.round(rowWidth / (float)currentVisibleItems);
//            int visibleBtnCount = 0;
//            for (int i = 0; i < currentVisibleItems; i++) {
//                DynamicInputRow.DynamicInput currentItem = items.get(position + (i - relativeIndex));
//                if (currentItem.inputType == DynamicInputRow.DynamicInput.InputType.button)
//                    visibleBtnCount++;
//            }
//            if (visibleBtnCount > 0)
//                maxWidth = Math.round((rowWidth - (visibleBtnCount * buttonPx)) / (float) (currentVisibleItems - visibleBtnCount));
//            //maxWidth = Math.round((rowWidth - (visibleBtnCount * buttonPx) - ((currentVisibleItems - 1) * paddingPx)) / (float) (currentVisibleItems - visibleBtnCount));
//        }
//
//        int paddingPx = Math.round(AndroidHelpers.getScaledDpToPixels(context, PADDING));
//        //Log.d("InputRowAdapter", "Parent width at " + rowWidth + " current visible items: " + currentVisibleItems + " current item width: " + maxWidth + " resummed: " + (maxWidth * currentVisibleItems + (currentVisibleItems - 1) * paddingPx));
//        holder.setWidth(maxWidth);
//        //holder.setPadding(0, 0, relativeIndex < currentVisibleItems - 1 ? paddingPx : 0, 0);
//        holder.setPadding(0, 0, position < items.size() - 1 ? paddingPx : 0, 0);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RowViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        for (AdapterListener listener : listeners)
            if (listener != null)
                listener.onViewsReady();
    }

    public void refreshItems() {
        for (RowViewHolder holder : viewHolders)
            refreshHolder(holder);
    }
    private void refreshHolder(RowViewHolder holder) {
        int maxVisibleItems = Math.min(MAX_VISIBLE_ITEMS, items.size());
        DynamicInputRow.DynamicInput item = ((DynamicInputItemView)holder.itemView).getInputItem();
        int relativeIndex = item.col % maxVisibleItems;
        // TODO: don't consider items that are not visible
        int currentVisibleItems = Math.min(MAX_VISIBLE_ITEMS, items.size() - (item.col - relativeIndex));
        //int maxWidth = Math.round((rowWidth - ((currentVisibleItems - 1) * paddingPx)) / (float)currentVisibleItems);
        int buttonPx = Math.round(AndroidHelpers.getScaledDpToPixels(context, BUTTON_DIP));
        int maxWidth;
        if (item.inputType == DynamicInputRow.DynamicInput.InputType.button || item.inputType == DynamicInputRow.DynamicInput.InputType.image)
            maxWidth = buttonPx;
        else {
            maxWidth = Math.round(rowWidth / (float)currentVisibleItems);
            // of the visible items alongside us, figure out which ones are buttons and use that information to more properly set the width
            int visibleBtnCount = 0;
            for (int i = 0; i < currentVisibleItems; i++) {
                DynamicInputRow.DynamicInput currentItem = items.get(item.col + (i - relativeIndex));
                if (currentItem.inputType == DynamicInputRow.DynamicInput.InputType.button || currentItem.inputType == DynamicInputRow.DynamicInput.InputType.image)
                    visibleBtnCount++;
            }
            if (visibleBtnCount > 0)
                maxWidth = Math.round((rowWidth - (visibleBtnCount * buttonPx)) / (float) (currentVisibleItems - visibleBtnCount));
            //maxWidth = Math.round((rowWidth - (visibleBtnCount * buttonPx) - ((currentVisibleItems - 1) * paddingPx)) / (float) (currentVisibleItems - visibleBtnCount));
        }

        int paddingPx = Math.round(AndroidHelpers.getScaledDpToPixels(context, PADDING));
        //Log.d("InputRowAdapter", "Parent width at " + rowWidth + " current visible items: " + currentVisibleItems + " current item width: " + maxWidth + " resummed: " + (maxWidth * currentVisibleItems + (currentVisibleItems - 1) * paddingPx));
        holder.setWidth(maxWidth);
        //holder.setPadding(0, 0, relativeIndex < currentVisibleItems - 1 ? paddingPx : 0, 0);
        holder.setPadding(0, 0, item.col < items.size() - 1 ? paddingPx : 0, 0);
    }

    public void setRowWidth(int width) {
        //Log.d("InputRowAdapter", "Row width set to " + width);
        this.rowWidth = width;
    }

    public class RowViewHolder extends RecyclerView.ViewHolder {
        //private View itemView;
        public RowViewHolder(@NonNull View itemView) {
            super(itemView);
            //this.itemView = itemView;
        }
        public void bindItem(DynamicInputRow.DynamicInput item) {
            ((DynamicInputItemView)itemView).setInputItem(item);
        }
        public void setPadding(int left, int top, int right, int bottom) {
            itemView.setPadding(left, top, right, bottom);
        }
        public void setWidth(int px) {
            ViewGroup.LayoutParams params = itemView.getLayoutParams();
            params.width = px;
            itemView.setLayoutParams(params);
        }
        public boolean requestFocus() {
            return itemView.requestFocus();
        }
    }
}
