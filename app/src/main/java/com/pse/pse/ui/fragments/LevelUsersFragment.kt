package com.trustledger.aitrustledger.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.pse.pse.ui.fragments.BaseFragment
import com.trustledger.aitrustledger.adapters.ReferralDataAdapter
import com.trustledger.aitrustledger.databinding.FragmentLevelUsersBinding
import com.trustledger.aitrustledger.models.UserListModel

class LevelUsersFragment : BaseFragment(){

    private var _binding: FragmentLevelUsersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLevelUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        /* arguments ---------------------------------------------------- */
        val users = arguments?.getParcelableArrayList<UserListModel>("users") ?: emptyList()
        val level = arguments?.getInt("level") ?: 0
        binding.title.text = "Level $level Users"

        /* RecyclerView -------------------------------------------------- */
        binding.rvLevelUser.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ReferralDataAdapter(users)
        }
    }

    override fun onDestroyView() {
        _binding = null; super.onDestroyView()
    }
}
