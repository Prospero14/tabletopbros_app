package com.fts.ttbros.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.model.Event
import com.fts.ttbros.data.repository.EventRepository
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.notifications.EventNotificationScheduler
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import kotlinx.coroutines.launch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var noEventsTextView: TextView
    private lateinit var createEventFab: FloatingActionButton
    private lateinit var progressIndicator: CircularProgressIndicator

    private val eventRepository = EventRepository()
    private val userRepository = UserRepository()
    private val eventsAdapter = EventsAdapter { event ->
        // Handle event click if needed
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarView = view.findViewById(R.id.calendarView)
        eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView)
        noEventsTextView = view.findViewById(R.id.noEventsTextView)
        createEventFab = view.findViewById(R.id.createEventFab)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        eventsRecyclerView.adapter = eventsAdapter

        createEventFab.setOnClickListener {
            showCreateEventDialog()
        }

        loadEvents()
    }

    private fun loadEvents() {
        progressIndicator.isVisible = true
        lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                val teamId = profile?.currentTeamId ?: return@launch

                eventRepository.getTeamEvents(teamId).collect { events ->
                    progressIndicator.isVisible = false
                    if (events.isEmpty()) {
                        eventsRecyclerView.isVisible = false
                        noEventsTextView.isVisible = true
                    } else {
                        eventsRecyclerView.isVisible = true
                        noEventsTextView.isVisible = false
                        eventsAdapter.submitList(events)
                    }
                }
            } catch (e: Exception) {
                progressIndicator.isVisible = false
                Snackbar.make(requireView(), "Error loading events: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateEventDialog() {
        lifecycleScope.launch {
            val profile = userRepository.currentProfile()
            val teamId = profile?.currentTeamId ?: return@launch
            val userName = profile.displayName

            val dialog = CreateEventDialog(requireContext()) { title, description, dateTime ->
                createEvent(teamId, userName, title, description, dateTime)
            }
            dialog.show()
        }
    }

    private fun createEvent(teamId: String, userName: String, title: String, description: String, dateTime: Long) {
        lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile() ?: return@launch
                val event = Event(
                    teamId = teamId,
                    title = title,
                    description = description,
                    dateTime = dateTime,
                    createdBy = profile.uid,
                    createdByName = userName
                )

                val eventId = eventRepository.createEvent(event)
                
                // Schedule notifications
                EventNotificationScheduler.scheduleEventNotifications(
                    requireContext(),
                    event.copy(id = eventId)
                )

                Snackbar.make(requireView(), R.string.event_created, Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(requireView(), "Error creating event: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    inner class EventsAdapter(
        private val onEventClick: (Event) -> Unit
    ) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {

        private var events: List<Event> = emptyList()

        fun submitList(newEvents: List<Event>) {
            events = newEvents
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
            return EventViewHolder(view)
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            holder.bind(events[position])
        }

        override fun getItemCount() = events.size

        inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleTextView: TextView = itemView.findViewById(R.id.eventTitleTextView)
            private val dateTimeTextView: TextView = itemView.findViewById(R.id.eventDateTimeTextView)
            private val descriptionTextView: TextView = itemView.findViewById(R.id.eventDescriptionTextView)
            private val creatorTextView: TextView = itemView.findViewById(R.id.eventCreatorTextView)

            fun bind(event: Event) {
                titleTextView.text = event.title

                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                dateTimeTextView.text = dateFormat.format(Date(event.dateTime))

                if (event.description.isNotBlank()) {
                    descriptionTextView.text = event.description
                    descriptionTextView.isVisible = true
                } else {
                    descriptionTextView.isVisible = false
                }

                creatorTextView.text = "Created by ${event.createdByName}"

                itemView.setOnClickListener {
                    onEventClick(event)
                }
            }
        }
    }
}
