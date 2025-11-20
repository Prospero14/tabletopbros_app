package com.fts.ttbros.character

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.fts.ttbros.R
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.TeamRepository
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.FragmentCharacterBinding
import kotlinx.coroutines.launch

class CharacterFragment : Fragment() {

    private var _binding: FragmentCharacterBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()
    private val teamRepository = TeamRepository()
    private val playersAdapter = PlayersAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCharacterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.playersRecyclerView.adapter = playersAdapter
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
                Snackbar.make(binding.root, e.localizedMessage ?: "Error loading data", Snackbar.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showMasterView() {
        binding.playerView.isVisible = false
        binding.playersRecyclerView.isVisible = true
    }

    private fun showPlayerView(code: String?) {
        binding.playerView.isVisible = true
        binding.playersRecyclerView.isVisible = false
        binding.teamCodeTextView.text = getString(R.string.dialog_team_code_message, code ?: "N/A")
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressIndicator.isVisible = isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
