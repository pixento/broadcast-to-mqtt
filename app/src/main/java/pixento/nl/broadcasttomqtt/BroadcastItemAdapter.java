package pixento.nl.broadcasttomqtt;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.Locale;

/**
 * Created by corniel on 30-8-2017.
 */

public class BroadcastItemAdapter extends BaseAdapter {
    private final Context context;
    private final BroadcastItemList items;
    
    BroadcastItemAdapter(Context context, BroadcastItemList items) {
        this.context = context;
        this.items = items;
    }
    
    @Override
    public int getCount() {
        return this.items.size();
    }
    
    @Override
    public Object getItem(int i) {
        return items.get(i);
    }
    
    @Override
    public long getItemId(int i) {
        return i;
    }
    
    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        BroadcastItem item = (BroadcastItem) this.getItem(i);
        ViewHolder viewHolder;
    
        //LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        // Check if we are reuing an existing view, otherwise inflate new view
        if (convertView == null) {
            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.broadcast_list_item, parent, false);
            
            // Find the textviews
            viewHolder.aliasView = (TextView) convertView.findViewById(R.id.bc_alias);
            viewHolder.actionView = (TextView) convertView.findViewById(R.id.intent_action);
            viewHolder.countView = (TextView) convertView.findViewById(R.id.count);
            viewHolder.dateView = (TextView) convertView.findViewById(R.id.last_time);
            
            // Set the holder into the convertView
            convertView.setTag(viewHolder);
        }
        else {
            // Recycle existing view
            viewHolder = (ViewHolder) convertView.getTag();
        }
    
        // Set the text views content
        viewHolder.aliasView.setText(item.alias);
        viewHolder.actionView.setText(item.action);
        viewHolder.countView.setText(String.format(Locale.getDefault(), "%d times", item.count_executed));
    
        String date = "Never";
        if(item.last_executed != null) {
            date = DateUtils.getRelativeTimeSpanString(
                    item.last_executed.getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            ).toString();
        }
        viewHolder.dateView.setText(date);
            
        return convertView;
    }
    
    static class ViewHolder {
        TextView aliasView;
        TextView actionView;
        TextView countView;
        TextView dateView;
    }
}
