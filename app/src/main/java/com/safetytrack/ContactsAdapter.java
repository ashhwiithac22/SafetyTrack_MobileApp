//ContactsAdapter.java
package com.safetytrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.safetytrack.models.Contact;

import java.util.List;

public class ContactsAdapter extends BaseAdapter {
    private List<Contact> contactList;
    private LayoutInflater inflater;

    public ContactsAdapter(ContactsActivity context, List<Contact> contactList) {
        this.contactList = contactList;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return contactList.size();
    }

    @Override
    public Object getItem(int position) {
        return contactList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_contact, parent, false);
            holder = new ViewHolder();
            holder.tvContactName = convertView.findViewById(R.id.tvContactName);
            holder.tvContactPhone = convertView.findViewById(R.id.tvContactPhone);
            holder.cbSelect = convertView.findViewById(R.id.cbSelect);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Contact contact = contactList.get(position);

        holder.tvContactName.setText(contact.getName());
        holder.tvContactPhone.setText(contact.getPhoneNumber());
        holder.cbSelect.setChecked(contact.isSelected());

        // Prevent checkbox from handling its own clicks (let the list item handle it)
        holder.cbSelect.setClickable(false);
        holder.cbSelect.setFocusable(false);

        return convertView;
    }

    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }

    static class ViewHolder {
        TextView tvContactName;
        TextView tvContactPhone;
        CheckBox cbSelect;
    }
}