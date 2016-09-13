package com.directdev.portal.adapter

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.directdev.portal.R
import com.directdev.portal.model.JournalModel
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import kotlinx.android.synthetic.main.item_journal.view.*
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.*

class JournalRecyclerAdapter(val realm: Realm, context: Context, data: OrderedRealmCollection<JournalModel>?, autoUpdate: Boolean) : RealmRecyclerViewAdapter<JournalModel, JournalRecyclerAdapter.ViewHolder>(context, data, autoUpdate) {
    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.item_journal, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.bindData(context, getItem(position) as JournalModel)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindData(ctx: Context, item: JournalModel) {
            itemView.txtDate.text = DateTime.parse(item.id.substring(0, 10)).toString(DateTimeFormat.forPattern("dd MMM ''yy"))
            itemView.txtDay.text = DateTime(item.date).dayOfWeek().getAsText(Locale.US)

            itemView.recyclerSchedule.layoutManager = LinearLayoutManager(ctx)
            itemView.recyclerSchedule.adapter = SessionRecyclerAdapter(ctx, item.session, true)
        }
    }
}

