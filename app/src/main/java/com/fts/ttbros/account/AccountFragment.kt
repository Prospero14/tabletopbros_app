package com.fts.ttbros.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fts.ttbros.LoginActivity
import com.fts.ttbros.R
import com.fts.ttbros.data.repository.UserRepository
import com.google.android.material.button.MaterialButton

class AccountFragment : Fragment() {

    private val userRepository = UserRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_account, container, false)
        
        view.findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener {
            signOut()
        }
        
        return view
    }

    private fun signOut() {
        userRepository.signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}
