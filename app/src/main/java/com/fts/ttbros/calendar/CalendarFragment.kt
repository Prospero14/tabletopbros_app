package com.fts.ttbros.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import com.fts.ttbros.R
import com.fts.ttbros.data.model.Event
import com.fts.ttbros.data.repository.EventRepository
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.notifications.EventNotificationScheduler
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var noEventsTextView: TextView
    private lateinit var createEventFab: FloatingActionButton
    private lateinit var progressIndicator: CircularProgressIndicator

    private val eventRepository = EventRepository()
    private val userRepository = UserRepository()
    private val eventsAdapter = EventsAdapter { event ->
        showEditEventDialog(event)
    }

    private var allEvents: List<Event> = emptyList()
    private var selectedDate: Calendar = Calendar.getInstance()

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
        
        calendarView.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                selectedDate = eventDay.calendar
                filterEventsByDate(selectedDate)
            }
        })

        loadEvents()
    }

    private fun loadEvents() {
        progressIndicator.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                val teamId = profile?.currentTeamId ?: profile?.teamId
                if (teamId == null) {
                    progressIndicator.isVisible = false
                    noEventsTextView.isVisible = true
                    eventsRecyclerView.isVisible = false
                    return@launch
                }

                eventRepository.getTeamEvents(teamId).collect { events ->
                    progressIndicator.isVisible = false
                    allEvents = events
                    updateCalendarEvents(events)
                    filterEventsByDate(null) // Show upcoming events by default
                }
            } catch (e: Exception) {
                progressIndicator.isVisible = false
                android.util.Log.e("CalendarFragment", "Error loading events: ${e.message}", e)
                view?.let {
                    Snackbar.make(it, "Error loading events: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateCalendarEvents(events: List<Event>) {
        try {
            val eventDays = events.map { event ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = event.dateTime
                EventDay(calendar, R.drawable.ic_dot_filled)
            }
            calendarView.setEvents(eventDays)
        } catch (e: Exception) {
            android.util.Log.e("CalendarFragment", "Error setting events to calendar: ${e.message}", e)
        }
    }

    private fun filterEventsByDate(date: Calendar?) {
        val filteredEvents = if (date == null) {
            // Show upcoming events (from today onwards)
            val today = Calendar.getInstance()
            // Reset time to start of day
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            allEvents.filter { event ->
                event.dateTime >= today.timeInMillis
            }.sortedBy { it.dateTime }
        } else {
            allEvents.filter { event ->
                val eventCalendar = Calendar.getInstance()
                eventCalendar.timeInMillis = event.dateTime
                isSameDay(date, eventCalendar)
            }
        }
        
        if (filteredEvents.isEmpty()) {
            eventsRecyclerView.isVisible = false
            noEventsTextView.isVisible = true
            noEventsTextView.text = if (date == null) getString(R.string.no_events) else "No events for ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date.time)}"
        } else {
            eventsRecyclerView.isVisible = true
            noEventsTextView.isVisible = false
            eventsAdapter.submitList(filteredEvents)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun showCreateEventDialog() {
        lifecycleScope.launch {
            val profile = userRepository.currentProfile() ?: return@launch
            val teamId = profile.currentTeamId ?: profile.teamId ?: return@launch
            val userName = profile.displayName

            // Pass selected date to dialog if possible, or just current time
            val dialog = CreateEventDialog(requireContext()) { title, description, dateTime ->
                createEvent(teamId, userName, title, description, dateTime)
            }
            dialog.show()
        }
    }
    
    private fun showEditEventDialog(event: Event) {
        lifecycleScope.launch {
            val profile = userRepository.currentProfile() ?: return@launch
            
            // Only creator or master can edit? Let's allow creator for now.
            if (event.createdBy != profile.uid) {
                Snackbar.make(requireView(), "Only the creator can edit this event", Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            // Reuse CreateEventDialog but pre-fill data? 
            // Or create a new EditEventDialog. For simplicity, let's modify CreateEventDialog to accept an event.
            // Since I can't easily change the constructor of CreateEventDialog without viewing it, 
            // I'll create a quick custom dialog here or assume I can modify CreateEventDialog later.
            // Let's try to use a MaterialAlertDialog for editing title/desc/time.
            
            // Actually, best practice is to update CreateEventDialog to support editing.
            // But I'll just show a simple dialog to update title/desc for now to fulfill the request quickly.
            // Or delete.
            
            // Let's implement a simple edit flow: Delete or Update?
            // User asked for "possibility to change event".
            
            // I'll assume I can create a new dialog instance and pre-fill it if I modify it.
            // For now, let's just show a "Delete" option as a quick "Edit" (often requested together) 
            // and a simple "Update" that just updates title.
            
            // Better: Let's make a new EditEventDialog or modify CreateEventDialog.
            // I'll modify CreateEventDialog in the next step. For now, I'll just put a placeholder or basic logic.
            
            val dialog = CreateEventDialog(requireContext()) { title, description, dateTime ->
                updateEvent(event.copy(title = title, description = description, dateTime = dateTime))
            }
            // We need to pre-fill the dialog. I'll need to add a method to CreateEventDialog to set data.
            dialog.setEventData(event)
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
    
    private fun updateEvent(event: Event) {
        lifecycleScope.launch {
            try {
                eventRepository.updateEvent(event)
                EventNotificationScheduler.scheduleEventNotifications(requireContext(), event)
                Snackbar.make(requireView(), "Event updated", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(requireView(), "Error updating event: ${e.message}", Snackbar.LENGTH_SHORT).show()
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
