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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth

class TeamsFragment : Fragment() {

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
        return inflater.inflate(R.layout.fragment_teams, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        playerView = view.findViewById(R.id.playerView)
        teamCodeTextView = view.findViewById(R.id.teamCodeTextView)
        playersRecyclerView = view.findViewById(R.id.playersRecyclerView)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        
        val context = context ?: return
        playersRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        playersRecyclerView.adapter = teamsAdapter
        loadData()
    }

    private fun loadData() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
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
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                userRepository.switchTeam(teamId)
                // Refresh data
                loadData()
                val view = view
                if (isAdded && view != null) {
                    Snackbar.make(view, "Switched team", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val view = view
                if (isAdded && view != null) {
                    Snackbar.make(view, "Error switching team: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showJoinTeamDialog() {
        val context = context ?: return
        if (!isAdded) return
        val input = com.google.android.material.textfield.TextInputEditText(context)
        input.hint = getString(R.string.enter_group_code)
        
        val container = android.widget.FrameLayout(context)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        params.leftMargin = margin
        params.rightMargin = margin
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.join_group)
            .setView(container)
            .setPositiveButton(R.string.join_group) { _, _ ->
                val code = input.text?.toString()?.trim().orEmpty().uppercase()
                if (code.length >= 4) {
                    joinGroup(code)
                } else {
                    val view = view
                    if (isAdded && view != null) {
                        Snackbar.make(view, R.string.error_group_code, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun joinGroup(code: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val team = teamRepository.findTeamByCode(code)
                val view = view
                if (team == null) {
                    if (isAdded && view != null) {
                        Snackbar.make(view, R.string.error_group_not_found, Snackbar.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                userRepository.addTeam(team.id, team.code, UserRole.PLAYER, team.system, "Team ${team.code}")
                loadData()
                if (isAdded && view != null) {
                    Snackbar.make(view, R.string.success_joined_group, Snackbar.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                val view = view
                if (isAdded && view != null) {
                    Snackbar.make(view, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun chooseSystemAndCreateTeam() {
        val context = context ?: return
        if (!isAdded) return
        val options = arrayOf(
            getString(R.string.vampire_masquerade),
            getString(R.string.dungeons_dragons),
            getString(R.string.viedzmin_2e)
        )
        val values = arrayOf("vtm_5e", "dnd_5e", "viedzmin_2e")
        var selectedIndex = 0
        
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.select_game_system)
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                if (which >= 0 && which < values.size) {
                    selectedIndex = which
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selectedIndex >= 0 && selectedIndex < values.size) {
                    showTeamNameDialog(values[selectedIndex])
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showTeamNameDialog(system: String) {
        val context = context ?: return
        if (!isAdded) return
        val input = com.google.android.material.textfield.TextInputEditText(context)
        input.hint = "Название команды"
        
        val container = android.widget.FrameLayout(context)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        params.leftMargin = margin
        params.rightMargin = margin
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(context)
            .setTitle("Название команды")
            .setView(container)
            .setPositiveButton(R.string.create_new_group) { _, _ ->
                val teamName = input.text?.toString()?.trim().orEmpty()
                createTeamWithSystem(system, teamName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createTeamWithSystem(system: String, teamName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = userRepository.currentProfile() ?: return@launch
                val currentUser = Firebase.auth.currentUser
                val currentView = view
                if (currentUser == null) {
                    if (isAdded && currentView != null) {
                        Snackbar.make(currentView, "User not authenticated", Snackbar.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                val team = teamRepository.createTeam(currentUser, system)
                val finalTeamName = teamName.ifBlank { "Team ${team.code}" }
                userRepository.addTeam(team.id, team.code, UserRole.MASTER, team.system, finalTeamName)
                
                loadData()
                val rootView = view
                if (isAdded && rootView != null) {
                    Snackbar.make(rootView, "Team created", Snackbar.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                val rootView = view
                if (isAdded && rootView != null) {
                    Snackbar.make(rootView, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressIndicator.isVisible = isLoading
    }

    // Adapter for Teams
    inner class TeamsAdapter(private val onTeamClick: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var teams: List<com.fts.ttbros.data.model.TeamMembership> = emptyList()
        private val TYPE_TEAM = 0
        private val TYPE_ACTION = 1

        fun submitList(newTeams: List<com.fts.ttbros.data.model.TeamMembership>) {
            teams = newTeams
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (position < teams.size) TYPE_TEAM else TYPE_ACTION
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_TEAM) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_player, parent, false)
                TeamViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_form_button, parent, false)
                ActionViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is TeamViewHolder) {
                if (position < 0 || position >= teams.size) {
                    android.util.Log.w("TeamsAdapter", "Invalid position: $position, teams size: ${teams.size}")
                    return
                }
                holder.bind(teams[position])
            } else if (holder is ActionViewHolder) {
                // First action is Join, second is Create
                if (position == teams.size) {
                    holder.bind(getString(R.string.join_group)) { showJoinTeamDialog() }
                } else if (position == teams.size + 1) {
                    holder.bind(getString(R.string.create_new_group)) { chooseSystemAndCreateTeam() }
                } else {
                    android.util.Log.w("TeamsAdapter", "Invalid action position: $position")
                }
            }
        }

        override fun getItemCount(): Int = teams.size + 2 // +2 for Join and Create buttons

        inner class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTextView: TextView = itemView.findViewById(R.id.playerNameTextView)
            private val systemTextView: TextView = itemView.findViewById(R.id.systemTextView)

            init {
                itemView.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < teams.size) {
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
        
        inner class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val button: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.button)
            
            fun bind(text: String, onClick: () -> Unit) {
                button.text = text
                button.setOnClickListener { onClick() }
            }
        }
    }
}
