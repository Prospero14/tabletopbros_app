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
    private val playersAdapter = PlayersAdapter()

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
        
        playersRecyclerView.adapter = playersAdapter
        loadData()
    }

    private fun loadData() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                if (profile == null) {
                    // Should not happen if MainActivity checks auth
                    return@launch
                }

                if (profile.role == UserRole.MASTER) {
                    showMasterView()
                    if (!profile.teamId.isNullOrBlank()) {
                        val members = teamRepository.fetchMembers(profile.teamId)
                        playersAdapter.submitList(members)
                    }
                } else {
                    showPlayerView(profile.teamCode)
                }

            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, e.localizedMessage ?: "Error loading data", Snackbar.LENGTH_LONG).show()
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showMasterView() {
        playerView.isVisible = false
        playersRecyclerView.isVisible = true
    }

    private fun showPlayerView(code: String?) {
        playerView.isVisible = true
        playersRecyclerView.isVisible = false
        teamCodeTextView.text = getString(R.string.dialog_team_code_message, code ?: "N/A")
    }

    private fun setLoading(isLoading: Boolean) {
        progressIndicator.isVisible = isLoading
    }

    // Simple Adapter for Players
    class PlayersAdapter : RecyclerView.Adapter<PlayersAdapter.PlayerViewHolder>() {

        private var members: List<TeamRepository.Member> = emptyList()

        fun submitList(newMembers: List<TeamRepository.Member>) {
            members = newMembers
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_player, parent, false)
            return PlayerViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
            holder.bind(members[position])
        }

        override fun getItemCount(): Int = members.size

        class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTextView: TextView = itemView.findViewById(R.id.playerNameTextView)

            fun bind(member: TeamRepository.Member) {
                // Use display name if available, otherwise email (simplified for now as Member only has email)
                // We might need to fetch full profiles later, but for now email/uid is what we have in Member
                val displayName = member.email.substringBefore("@")
                nameTextView.text = "$displayName (${member.role.name})"
            }
        }
    }
}
