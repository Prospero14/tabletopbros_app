package com.fts.ttbros.character

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.fts.ttbros.R
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.TeamRepository
import com.fts.ttbros.data.repository.UserRepository
import kotlinx.coroutines.launch

class CharacterFragment : Fragment() {

    private lateinit var playerView: LinearLayout
    private lateinit var teamCodeTextView: TextView
    private lateinit var playersRecyclerView: RecyclerView
    private lateinit var progressIndicator: CircularProgressIndicator
    
    private val userRepository = UserRepository()
    private val teamRepository = TeamRepository()
    private val teamsAdapter = TeamsAdapter { teamId ->
        switchTeam(teamId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_character, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        playerView = view.findViewById(R.id.playerView)
        teamCodeTextView = view.findViewById(R.id.teamCodeTextView)
        playersRecyclerView = view.findViewById(R.id.playersRecyclerView)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        
        playersRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        playersRecyclerView.adapter = teamsAdapter
        loadData()
    }

    private fun loadData() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                if (profile == null) return@launch

                // Always show list of teams
                playerView.isVisible = false
                playersRecyclerView.isVisible = true
                
                teamsAdapter.submitList(profile.teams)

            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, e.localizedMessage ?: "Error loading data", Snackbar.LENGTH_LONG).show()
                }
            } finally {
                setLoading(false)
            }
        }
    }
    
    private fun switchTeam(teamId: String) {
        lifecycleScope.launch {
            try {
                userRepository.switchTeam(teamId)
                // Refresh data
                loadData()
                Snackbar.make(requireView(), "Switched team", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(requireView(), "Error switching team: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressIndicator.isVisible = isLoading
    }

    // Adapter for Teams
    inner class TeamsAdapter(private val onTeamClick: (String) -> Unit) : RecyclerView.Adapter<TeamsAdapter.TeamViewHolder>() {

        private var teams: List<com.fts.ttbros.data.model.TeamMembership> = emptyList()

        fun submitList(newTeams: List<com.fts.ttbros.data.model.TeamMembership>) {
            teams = newTeams
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_player, parent, false)
            return TeamViewHolder(view)
        }

        override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
            holder.bind(teams[position])
        }

        override fun getItemCount(): Int = teams.size

        inner class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTextView: TextView = itemView.findViewById(R.id.playerNameTextView)
            private val systemTextView: TextView = itemView.findViewById(R.id.systemTextView)

            init {
                itemView.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onTeamClick(teams[position].teamId)
                    }
                }
            }

            fun bind(team: com.fts.ttbros.data.model.TeamMembership) {
                val name = team.teamName.ifBlank { "Team ${team.teamCode}" }
                nameTextView.text = "$name (${team.role.name})"
                
                val context = itemView.context
                systemTextView.text = when(team.teamSystem) {
                    "vtm_5e" -> context.getString(R.string.vampire_masquerade)
                    "dnd_5e" -> context.getString(R.string.dungeons_dragons)
                    "viedzmin_2e" -> context.getString(R.string.viedzmin_2e)
                    else -> team.teamSystem
                }
            }
        }
    }
}
