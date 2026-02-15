package com.rulerhao.media_protector;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FolderAdapter extends BaseAdapter {

    private final List<File> folders = new ArrayList<>();
    private final LayoutInflater inflater;

    public FolderAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setFolders(List<File> newFolders) {
        folders.clear();
        folders.addAll(newFolders);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return folders.size();
    }

    @Override
    public Object getItem(int position) {
        return folders.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_folder, parent, false);
            holder = new ViewHolder();
            holder.folderIcon = convertView.findViewById(R.id.folderIcon);
            holder.folderName = convertView.findViewById(R.id.folderName);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        File folder = folders.get(position);
        holder.folderName.setText(folder.getName());
        holder.folderIcon.setImageResource(android.R.drawable.ic_menu_view);

        return convertView;
    }

    private static class ViewHolder {
        ImageView folderIcon;
        TextView folderName;
    }
}
